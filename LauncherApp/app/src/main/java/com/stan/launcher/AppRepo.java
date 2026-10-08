package com.stan.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 应用清单仓库：负责扫描已安装应用、读取/保存「隐藏状态」与「自定义排列顺序」。
 */
public class AppRepo {

    private static final String PREF_NAME = "minimal_launcher";
    private static final String KEY_HIDDEN = "hidden_apps";
    private static final String KEY_ORDER = "app_order";
    private static final String KEY_SLOTS = "app_slots";
    private static final String KEY_EINK = "eink_mode";

    private final Context context;
    private final SharedPreferences prefs;

    public AppRepo(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** 扫描出所有可启动的应用（含图标），按名称排序。应在子线程调用。 */
    public List<AppInfo> scanApps() {
        PackageManager pm = context.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(main, 0);

        List<AppInfo> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null) {
                continue;
            }
            String pkg = info.activityInfo.packageName;
            String cls = info.activityInfo.name;
            AppInfo app = new AppInfo(
                    String.valueOf(info.loadLabel(pm)),
                    pkg,
                    cls);
            if (!seen.add(app.key())) {
                continue;
            }
            try {
                app.icon = info.loadIcon(pm);
            } catch (Exception ignored) {
                app.icon = pm.getDefaultActivityIcon();
            }
            if (app.icon == null) {
                app.icon = pm.getDefaultActivityIcon();
            }
            list.add(app);
        }
        sortByLabel(list);
        return list;
    }

    /** 按已保存顺序重排；未记录的新应用追加在末尾，并立即落盘，保证顺序稳定。 */
    public List<AppInfo> applySavedOrder(List<AppInfo> apps) {
        // 顺序用换行分隔的字符串保存（StringSet 无法保留顺序）
        List<String> saved = new ArrayList<>();
        String raw = prefs.getString(KEY_ORDER, null);
        if (!TextUtils.isEmpty(raw)) {
            Collections.addAll(saved, raw.split("\n"));
        }

        List<AppInfo> result = new ArrayList<>(apps);
        if (!saved.isEmpty()) {
            final List<String> order = saved;
            Collections.sort(result, new Comparator<AppInfo>() {
                @Override
                public int compare(AppInfo a, AppInfo b) {
                    int ia = order.indexOf(a.key());
                    int ib = order.indexOf(b.key());
                    if (ia < 0) {
                        ia = Integer.MAX_VALUE;
                    }
                    if (ib < 0) {
                        ib = Integer.MAX_VALUE;
                    }
                    if (ia != ib) {
                        return ia < ib ? -1 : 1;
                    }
                    return compareLabel(a.label, b.label);
                }
            });
        }
        saveOrder(result);
        return result;
    }

    public void saveOrder(List<AppInfo> apps) {
        List<String> keys = new ArrayList<>();
        for (AppInfo app : apps) {
            keys.add(app.key());
        }
        prefs.edit().putString(KEY_ORDER, TextUtils.join("\n", keys)).apply();
    }

    public void resetOrder() {
        prefs.edit().remove(KEY_ORDER).remove(KEY_SLOTS).apply();
    }

    /* ---------------------------- 桌面格位（自由位置） ---------------------------- */

    /**
     * 读取「应用 → 桌面格位」记录。
     * 格位即网格里的第几格（按行优先计数），允许中间留空，因此第一行可以只放 1 个、
     * 第二行放 2 个。用「key=slot」逐行保存（StringSet 无法保留顺序）。
     */
    public Map<String, Integer> savedSlots() {
        Map<String, Integer> map = new HashMap<>();
        String raw = prefs.getString(KEY_SLOTS, null);
        if (TextUtils.isEmpty(raw)) {
            return map;
        }
        for (String line : raw.split("\n")) {
            int eq = line.lastIndexOf('=');
            if (eq <= 0 || eq >= line.length() - 1) {
                continue;
            }
            try {
                map.put(line.substring(0, eq), Integer.parseInt(line.substring(eq + 1).trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return map;
    }

    private void saveSlots(Map<String, Integer> map) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            Integer slot = entry.getValue();
            if (slot == null || slot < 0) {
                continue;
            }
            lines.add(entry.getKey() + "=" + slot);
        }
        prefs.edit().putString(KEY_SLOTS, TextUtils.join("\n", lines)).apply();
    }

    /**
     * 为桌面要显示的应用分配格位：
     * 1. 优先沿用上次保存的位置（图标“待在自己原来的格子里”不会被挤走）；
     * 2. 没记录过的新应用排在所有已占用格位之后，不动用户特意留出的空位。
     */
    public List<Integer> assignSlots(List<AppInfo> apps) {
        Map<String, Integer> saved = savedSlots();
        Set<Integer> used = new HashSet<>();
        int[] result = new int[apps.size()];
        Arrays.fill(result, -1);
        for (int i = 0; i < apps.size(); i++) {
            Integer slot = saved.get(apps.get(i).key());
            if (slot != null && slot >= 0 && used.add(slot)) {
                result[i] = slot;
            }
        }
        int cursor = 0;
        for (Integer slot : used) {
            cursor = Math.max(cursor, slot + 1);
        }
        for (int i = 0; i < apps.size(); i++) {
            if (result[i] >= 0) {
                continue;
            }
            while (used.contains(cursor)) {
                cursor++;
            }
            result[i] = cursor;
            used.add(cursor);
            cursor++;
        }
        List<Integer> list = new ArrayList<>(apps.size());
        for (int slot : result) {
            list.add(slot);
        }
        return list;
    }

    /**
     * 保存桌面布局：orderedApps[i] 占据 slots[i] 格。
     * 隐藏的应用虽不在桌面上，仍保留它的格位（重新显示时回到原位）；
     * 若其格位已被桌面上的应用占用，则丢弃旧位置，重新显示时自动排到末尾。
     */
    public void saveLayout(List<AppInfo> orderedApps, List<Integer> slots) {
        Map<String, Integer> map = savedSlots();
        Set<String> placed = new HashSet<>();
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < orderedApps.size() && i < slots.size(); i++) {
            Integer slot = slots.get(i);
            if (slot == null || slot < 0) {
                continue;
            }
            String key = orderedApps.get(i).key();
            map.put(key, slot);
            placed.add(key);
            used.add(slot);
        }
        for (Iterator<Map.Entry<String, Integer>> it = map.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Integer> entry = it.next();
            if (!placed.contains(entry.getKey()) && used.contains(entry.getValue())) {
                it.remove();
            }
        }
        saveSlots(map);

        // 顺序同步为「桌面上的应用按格位排列 + 其余保持原顺序」，新装应用仍排在最后
        List<String> order = new ArrayList<>();
        for (AppInfo app : orderedApps) {
            order.add(app.key());
        }
        String raw = prefs.getString(KEY_ORDER, null);
        if (!TextUtils.isEmpty(raw)) {
            for (String key : raw.split("\n")) {
                if (!TextUtils.isEmpty(key) && !order.contains(key)) {
                    order.add(key);
                }
            }
        }
        prefs.edit().putString(KEY_ORDER, TextUtils.join("\n", order)).apply();
    }

    /**
     * 是否使用墨水屏配色。
     * 用户没手动选过时按设备型号自动判断（墨水屏设备默认开启，其它设备默认关闭）。
     */
    public boolean isEinkMode() {
        if (prefs.contains(KEY_EINK)) {
            return prefs.getBoolean(KEY_EINK, false);
        }
        return Skin.isEinkDevice();
    }

    /** 用户是否手动选择过墨水屏模式（用于文案提示是自动识别还是手动开启）。 */
    public boolean hasEinkChoice() {
        return prefs.contains(KEY_EINK);
    }

    public void setEinkMode(boolean eink) {
        prefs.edit().putBoolean(KEY_EINK, eink).apply();
    }

    public Set<String> hiddenKeys() {
        return new HashSet<>(prefs.getStringSet(KEY_HIDDEN, new HashSet<String>()));
    }

    public boolean isHidden(String key) {
        return hiddenKeys().contains(key);
    }

    /** 取消所有隐藏，恢复显示全部应用。 */
    public void showAll() {
        prefs.edit().remove(KEY_HIDDEN).apply();
    }

    /** 一次性隐藏全部应用。 */
    public void hideAll(List<AppInfo> apps) {
        Set<String> keys = new HashSet<>();
        for (AppInfo app : apps) {
            keys.add(app.key());
        }
        prefs.edit().putStringSet(KEY_HIDDEN, keys).apply();
    }

    /** 判断给定应用是否已全部隐藏。 */
    public boolean isAllHidden(List<AppInfo> apps) {
        if (apps.isEmpty()) {
            return false;
        }
        Set<String> hidden = hiddenKeys();
        for (AppInfo app : apps) {
            if (!hidden.contains(app.key())) {
                return false;
            }
        }
        return true;
    }

    public void setHidden(String key, boolean hidden) {
        Set<String> keys = hiddenKeys();
        if (hidden) {
            keys.add(key);
        } else {
            keys.remove(key);
        }
        prefs.edit().putStringSet(KEY_HIDDEN, keys).apply();
    }

    /** 过滤出桌面需要显示的应用（保持整体顺序）。 */
    public List<AppInfo> visibleApps(List<AppInfo> all) {
        Set<String> hidden = hiddenKeys();
        List<AppInfo> out = new ArrayList<>();
        for (AppInfo app : all) {
            if (!hidden.contains(app.key())) {
                out.add(app);
            }
        }
        return out;
    }

    /** 启动应用。 */
    public void launch(AppInfo app) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            context.startActivity(intent);
        } catch (Exception ignored) {
            // 应用可能已被卸载或禁用
        }
    }

    private static void sortByLabel(List<AppInfo> list) {
        Collections.sort(list, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo a, AppInfo b) {
                return compareLabel(a.label, b.label);
            }
        });
    }

    private static int compareLabel(String a, String b) {
        Collator collator = Collator.getInstance(Locale.CHINA);
        collator.setStrength(Collator.PRIMARY);
        return collator.compare(a == null ? "" : a, b == null ? "" : b);
    }

    /** 供外部构造去重集合。 */
    public static Set<String> newKeySet() {
        return new LinkedHashSet<>();
    }

    /** 图标为空时的兜底。 */
    public Drawable fallbackIcon() {
        return context.getPackageManager().getDefaultActivityIcon();
    }
}
