package com.stan.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 桌面上的单个应用图标：图标 + 名称，编辑模式下显示隐藏按钮并支持拖动排序。
 */
public class AppIconView extends LinearLayout {

    public interface Listener {
        boolean isEditMode();

        void onEnterEditMode();

        void onClickApp(AppIconView view);

        void onHideApp(AppIconView view);

        void onDragStart(AppIconView view, float rawX, float rawY);

        void onDragMove(AppIconView view, float rawX, float rawY);

        void onDragEnd(AppIconView view);
    }

    private static final long LONG_PRESS_DELAY = 400L;

    private final ImageView iconView;
    private final TextView labelView;
    private final TextView hideBadge;
    private final FrameLayout iconBox;

    private final Listener listener;
    private final int touchSlop;

    /** 墨水屏皮肤：不使用半透明 / 缩放 / 阴影，改用实线方框表达拖动。 */
    private final boolean eink;
    private final Drawable draggingBg;

    private AppInfo app;
    private boolean editMode;
    private boolean dragging;
    private boolean longPressed;
    private boolean moved;
    private float downRawX;
    private float downRawY;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            longPressed = true;
            if (!listener.isEditMode()) {
                listener.onEnterEditMode();
            }
            // 从图标自身中心开始拖动，避免图标瞬间跳到手指位置
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            startDrag(loc[0] + getWidth() / 2f, loc[1] + getHeight() / 2f);
        }
    };

    public AppIconView(Context context, AppInfo app, Listener listener) {
        super(context);
        this.app = app;
        this.listener = listener;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.eink = Skin.isEink(context);
        this.draggingBg = Skin.drawable(context, R.attr.lpDraggingBg);

        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setWillNotDraw(true);

        iconBox = new FrameLayout(context);
        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        iconView.setLayoutParams(iconLp);
        iconBox.addView(iconView);

        hideBadge = new TextView(context);
        hideBadge.setText("×");
        hideBadge.setTextColor(Color.WHITE);
        hideBadge.setGravity(Gravity.CENTER);
        Drawable badgeBg = Skin.drawable(context, R.attr.lpBadgeBg);
        if (badgeBg != null) {
            hideBadge.setBackground(badgeBg);
        } else {
            hideBadge.setBackgroundResource(R.drawable.bg_badge_hide);
        }
        hideBadge.setVisibility(GONE);
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(dp(24), dp(24));
        badgeLp.gravity = Gravity.TOP | Gravity.LEFT;
        hideBadge.setLayoutParams(badgeLp);
        hideBadge.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onHideApp(AppIconView.this);
            }
        });
        iconBox.addView(hideBadge);

        addView(iconBox, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        labelView = new TextView(context);
        labelView.setTextColor(Skin.color(context, R.attr.lpTextPrimary, Color.WHITE));
        labelView.setGravity(Gravity.CENTER);
        labelView.setMaxLines(2);
        labelView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LayoutParams labelLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(6);
        addView(labelView, labelLp);

        bind(app);
    }

    public void bind(AppInfo info) {
        this.app = info;
        iconView.setImageDrawable(info.icon);
        labelView.setText(info.label);
        setContentDescription(info.label);
    }

    public AppInfo getApp() {
        return app;
    }

    /** 依据单元格大小调整图标与文字尺寸。 */
    public void applyCellSize(int cellWidth, int cellHeight) {
        int iconSize = Math.max(dp(36), Math.min((int) (cellWidth * 0.60f), dp(64)));
        LayoutParams lp = (LayoutParams) iconBox.getLayoutParams();
        lp.width = iconSize;
        lp.height = iconSize;
        iconBox.setLayoutParams(lp);

        int badge = Math.max(dp(16), iconSize / 3);
        FrameLayout.LayoutParams badgeLp = (FrameLayout.LayoutParams) hideBadge.getLayoutParams();
        badgeLp.width = badge;
        badgeLp.height = badge;
        hideBadge.setLayoutParams(badgeLp);
        hideBadge.setTextSize(TypedValue.COMPLEX_UNIT_PX, badge * 0.62f);

        float textSize = Math.min(cellWidth * 0.17f, dp(13));
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(dp(10), textSize));

        LayoutParams labelLp = (LayoutParams) labelView.getLayoutParams();
        labelLp.width = cellWidth - dp(4);
        labelView.setLayoutParams(labelLp);
    }

    /** 编辑模式：只显示隐藏按钮，不做摇摆动画（图标抖动会干扰拖动定位）。 */
    public void setEditMode(boolean editing) {
        if (editMode == editing) {
            return;
        }
        editMode = editing;
        hideBadge.setVisibility(editing ? VISIBLE : GONE);
        setRotation(0f);
    }

    public void setDragging(boolean dragging) {
        this.dragging = dragging;
        if (eink) {
            // 墨水屏：半透明会变成麻点、缩放与阴影会拖出灰边，只留一个实线方框
            setAlpha(1f);
            setScaleX(1f);
            setScaleY(1f);
            setElevation(0f);
            setTranslationZ(0f);
            setBackground(dragging ? draggingBg : null);
            return;
        }
        if (dragging) {
            setAlpha(0.92f);
            setScaleX(1.12f);
            setScaleY(1.12f);
            setElevation(dp(12));
            setTranslationZ(dp(12));
        } else {
            setAlpha(1f);
            setScaleX(1f);
            setScaleY(1f);
            setElevation(0f);
            setTranslationZ(0f);
        }
    }

    /** 按压反馈：墨水屏不做半透明，避免灰阶麻点与残影。 */
    private void applyPressAlpha(float alpha) {
        if (!eink) {
            setAlpha(alpha);
        }
    }

    private void startDrag(float rawX, float rawY) {
        if (dragging) {
            return;
        }
        removeCallbacks(longPressRunnable);
        setDragging(true);
        listener.onDragStart(this, rawX, rawY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float rawX = event.getRawX();
        float rawY = event.getRawY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = rawX;
                downRawY = rawY;
                longPressed = false;
                moved = false;
                applyPressAlpha(0.7f);
                if (listener.isEditMode()) {
                    applyPressAlpha(0.92f);
                    // 编辑模式下直接按下即可拖动
                    startDrag(rawX, rawY);
                } else {
                    postDelayed(longPressRunnable, LONG_PRESS_DELAY);
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    listener.onDragMove(this, rawX, rawY);
                } else if (!longPressed) {
                    if (Math.hypot(rawX - downRawX, rawY - downRawY) > touchSlop) {
                        moved = true;
                        removeCallbacks(longPressRunnable);
                        applyPressAlpha(1f);
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                removeCallbacks(longPressRunnable);
                getParent().requestDisallowInterceptTouchEvent(false);
                if (dragging) {
                    listener.onDragEnd(this);
                } else {
                    applyPressAlpha(1f);
                    if (!longPressed && !moved) {
                        listener.onClickApp(this);
                    }
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPressRunnable);
                getParent().requestDisallowInterceptTouchEvent(false);
                applyPressAlpha(1f);
                if (dragging) {
                    listener.onDragEnd(this);
                }
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
