package com.stan.launcher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自定义图标网格容器：
 * - 每个图标占一个「格位」，格位之间可以留空，因此支持「第一行 1 个、第二行 2 个」这类自由摆放；
 * - 编辑模式下长按/按下即可拖动图标：拖到空格直接占位（原位置留空），拖到已有图标则两者换位；
 * - 拖动中的图标会被提到最上层，不会被同一行/下一行的其它图标盖住；
 * - 拖动到上下边缘时自动滚动。
 */
public class AppGridLayout extends ViewGroup implements AppIconView.Listener {

    public interface Callback {
        /** 点击图标启动应用。 */
        void onAppClick(AppInfo app);

        /** 需要进入编辑模式（长按触发）。 */
        void onEditModeRequested();

        /** 请求隐藏某个应用。 */
        void onHideRequested(AppInfo app);

        /** 图标位置发生变化，需要持久化。 */
        void onLayoutChanged();
    }

    private Callback callback;
    private ScrollView scrollParent;

    private int columns = 5;
    private int cellWidth;
    private int cellHeight;
    private boolean editMode;

    private final List<AppInfo> apps = new ArrayList<>();
    /** 与 apps 平行：第 i 个应用所占的格位。 */
    private final List<Integer> slots = new ArrayList<>();
    private final List<AppIconView> views = new ArrayList<>();

    private AppIconView dragView;
    private int dragIndex = -1;
    private int dragSlot = -1;
    private float dragDownRawX;
    private float dragDownRawY;
    private int dragBaseLeft;
    private int dragBaseTop;
    private boolean orderChanged;

    /** 当前占用的最大格位，用于推算行数。 */
    private int maxSlot;

    public AppGridLayout(Context context) {
        this(context, null);
    }

    public AppGridLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setScrollParent(ScrollView parent) {
        this.scrollParent = parent;
    }

    public void setColumns(int columns) {
        this.columns = Math.max(2, columns);
        requestLayout();
    }

    public int getColumns() {
        return columns;
    }

    public boolean isEditMode() {
        return editMode;
    }

    /** 重新设置要显示的应用及各自的格位；slotList 为空或长度不足时按顺序紧凑排布。 */
    public void setApps(List<AppInfo> list, List<Integer> slotList) {
        resetDrag();
        apps.clear();
        apps.addAll(list);
        slots.clear();
        for (int i = 0; i < apps.size(); i++) {
            Integer saved = (slotList != null && i < slotList.size()) ? slotList.get(i) : null;
            slots.add(saved != null && saved >= 0 ? saved : i);
        }
        dedupeSlots();

        removeAllViews();
        views.clear();
        for (AppInfo app : apps) {
            AppIconView view = new AppIconView(getContext(), app, this);
            view.setEditMode(editMode);
            views.add(view);
            addView(view);
        }
        requestLayout();
    }

    /** 桌面上显示的应用，按格位从小到大排列。 */
    public List<AppInfo> appsBySlot() {
        List<AppInfo> out = new ArrayList<>();
        for (int index : sortedIndexes()) {
            out.add(apps.get(index));
        }
        return out;
    }

    /** 与 {@link #appsBySlot()} 一一对应的格位列表。 */
    public List<Integer> slotsBySlot() {
        List<Integer> out = new ArrayList<>();
        for (int index : sortedIndexes()) {
            out.add(slots.get(index));
        }
        return out;
    }

    public void setEditMode(boolean editing) {
        if (editMode == editing) {
            return;
        }
        editMode = editing;
        if (!editing) {
            resetDrag();
        }
        for (AppIconView view : views) {
            view.setEditMode(editing);
        }
    }

    private List<Integer> sortedIndexes() {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < apps.size(); i++) {
            indexes.add(i);
        }
        Collections.sort(indexes, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return slots.get(a) - slots.get(b);
            }
        });
        return indexes;
    }

    /** 同一个格位被两个应用同时占用时（列表变动导致），后者顺延到最近的空位。 */
    private void dedupeSlots() {
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < slots.size(); i++) {
            if (used.add(slots.get(i))) {
                continue;
            }
            int free = 0;
            while (used.contains(free)) {
                free++;
            }
            slots.set(i, free);
            used.add(free);
        }
        updateMaxSlot();
    }

    private void updateMaxSlot() {
        int max = -1;
        for (int slot : slots) {
            max = Math.max(max, slot);
        }
        maxSlot = max;
    }

    private int rows() {
        return maxSlot < 0 ? 1 : maxSlot / columns + 1;
    }

    private void resetDrag() {
        if (dragView != null) {
            dragView.setDragging(false);
            dragView.setTranslationX(0f);
            dragView.setTranslationY(0f);
            dragView = null;
        }
        dragIndex = -1;
        dragSlot = -1;
        orderChanged = false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int available = Math.max(1, width - getPaddingLeft() - getPaddingRight());
        cellWidth = available / columns;
        cellHeight = Math.round(cellWidth * 1.22f);

        int height = getPaddingTop() + getPaddingBottom() + rows() * cellHeight;
        setMeasuredDimension(width, height);

        for (int i = 0; i < views.size(); i++) {
            AppIconView child = views.get(i);
            child.applyCellSize(cellWidth, cellHeight);
            int childSpecW = MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.AT_MOST);
            int childSpecH = MeasureSpec.makeMeasureSpec(cellHeight, MeasureSpec.AT_MOST);
            child.measure(childSpecW, childSpecH);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        layoutCells();
    }

    /**
     * 按格位摆放所有图标。
     *
     * 这里不做位移动画：拖动换位时图标直接落到格子里，
     * 否则每个图标都在起 150ms 动画，拖动过程中整屏图标都在晃。
     */
    private void layoutCells() {
        for (int i = 0; i < views.size(); i++) {
            AppIconView child = views.get(i);
            int slot = slots.get(i);
            int row = slot / columns;
            int col = slot % columns;
            int cellLeft = getPaddingLeft() + col * cellWidth;
            int cellTop = getPaddingTop() + row * cellHeight;
            int left = cellLeft + (cellWidth - child.getMeasuredWidth()) / 2;
            int top = cellTop + (cellHeight - child.getMeasuredHeight()) / 2;

            child.layout(left, top, left + child.getMeasuredWidth(), top + child.getMeasuredHeight());

            if (child == dragView) {
                continue;
            }
            child.animate().cancel();
            child.setTranslationX(0f);
            child.setTranslationY(0f);
        }
    }

    /* ------------------------- AppIconView.Listener ------------------------- */

    @Override
    public void onEnterEditMode() {
        if (editMode) {
            return;
        }
        editMode = true;
        for (AppIconView view : views) {
            view.setEditMode(true);
        }
        if (callback != null) {
            callback.onEditModeRequested();
        }
    }

    @Override
    public void onClickApp(AppIconView view) {
        if (editMode) {
            return;
        }
        if (callback != null) {
            callback.onAppClick(view.getApp());
        }
    }

    @Override
    public void onHideApp(AppIconView view) {
        if (callback != null) {
            callback.onHideRequested(view.getApp());
        }
    }

    @Override
    public void onDragStart(AppIconView view, float rawX, float rawY) {
        if (dragView != null) {
            resetDrag();
        }
        dragView = view;
        dragIndex = views.indexOf(view);
        if (dragIndex < 0) {
            resetDrag();
            return;
        }
        dragSlot = slots.get(dragIndex);
        dragDownRawX = rawX;
        dragDownRawY = rawY;
        orderChanged = false;
        view.setDragging(true);
        // 提到最上层：否则拖动中的图标会被后面的图标（第二行等）盖住，看起来像“那里有遮挡”
        view.bringToFront();
        dragBaseLeft = view.getLeft();
        dragBaseTop = view.getTop();
    }

    @Override
    public void onDragMove(AppIconView view, float rawX, float rawY) {
        if (dragView == null || view != dragView) {
            return;
        }
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        float gx = rawX - loc[0];
        float gy = rawY - loc[1];

        int target = slotAt(gx, gy);
        if (target >= 0 && target != dragSlot) {
            moveToSlot(dragIndex, target);
        }
        autoScroll(gy);
        applyDragTranslation(rawX, rawY);
    }

    @Override
    public void onDragEnd(AppIconView view) {
        if (dragView == null) {
            return;
        }
        AppIconView dragged = dragView;
        dragView = null;
        dragIndex = -1;
        dragSlot = -1;
        dragged.setDragging(false);
        // 直接落回格子，不做回弹动画
        dragged.animate().cancel();
        dragged.setTranslationX(0f);
        dragged.setTranslationY(0f);
        layoutCells();
        if (orderChanged && callback != null) {
            callback.onLayoutChanged();
        }
        orderChanged = false;
    }

    /** 把第 index 个图标放到 targetSlot：目标格为空则直接占位，已有图标则两者互换。 */
    private void moveToSlot(int index, int targetSlot) {
        int occupant = -1;
        for (int i = 0; i < slots.size(); i++) {
            if (i != index && slots.get(i) == targetSlot) {
                occupant = i;
                break;
            }
        }
        int from = slots.get(index);
        if (occupant >= 0) {
            slots.set(occupant, from);
            views.get(occupant).animate().cancel();
        }
        slots.set(index, targetSlot);
        dragSlot = targetSlot;
        orderChanged = true;
        updateMaxSlot();
        // 行数可能变化（新增/减少空行），需要重新测量
        requestLayout();
        layoutCells();
    }

    private void applyDragTranslation(float rawX, float rawY) {
        if (dragView == null) {
            return;
        }
        // 相对拖动起点计算位移：起点位移 + 起点格子 - 当前格子。
        // 注意不能用 getLocationOnScreen() 反推 translation——它包含 translation 自身，
        // 会形成 tx = 目标值 - tx 的反馈，导致图标在两点间来回闪动。
        dragView.setTranslationX(rawX - dragDownRawX + dragBaseLeft - dragView.getLeft());
        dragView.setTranslationY(rawY - dragDownRawY + dragBaseTop - dragView.getTop());
    }

    /** 手指位置对应的格位。允许落到最后一行之外的下一行（便于把图标挪到空行）。 */
    private int slotAt(float gx, float gy) {
        if (cellWidth <= 0 || cellHeight <= 0) {
            return -1;
        }
        if (apps.isEmpty()) {
            return 0;
        }
        int row = (int) ((gy - getPaddingTop()) / cellHeight);
        int col = (int) ((gx - getPaddingLeft()) / cellWidth);
        col = Math.max(0, Math.min(columns - 1, col));
        row = Math.max(0, Math.min(rows(), row));
        return row * columns + col;
    }

    private void autoScroll(float gy) {
        if (scrollParent == null) {
            return;
        }
        float edge = Math.max(cellHeight * 0.85f, 1f);
        int dy = 0;
        if (gy < edge) {
            dy = -(int) ((edge - gy) / 3f);
        } else if (gy > getHeight() - edge) {
            dy = (int) ((gy - (getHeight() - edge)) / 3f);
        }
        if (dy != 0) {
            scrollParent.scrollBy(0, dy);
        }
    }
}
