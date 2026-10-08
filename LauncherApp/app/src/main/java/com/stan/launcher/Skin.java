package com.stan.launcher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;

import java.util.Locale;

/**
 * 皮肤工具：
 * 1. 从当前主题读取皮肤属性（布局用 ?attr/...，代码里用这里的方法）；
 * 2. 根据品牌 / 型号猜测是否是墨水屏设备，用于首次启动自动选择配色。
 */
final class Skin {

    /**
     * 墨水屏设备特征词：命中的是品牌、型号或设备名中的片段。
     * 只保留辨识度足够高的词，避免普通机型被误判。
     */
    private static final String[] EINK_MARKS = {
            "eink", "e-ink", "epaper", "e-paper",
            "onyx", "boox", "hisense", "kindle", "kobo", "ireader", "inkpalm",
            "moke", "moaan", "bigme", "meebook", "hanvon", "likebook",
            "pocketbook", "tolino", "yotaphone", "duokan", "boyue", "dasung",
            "sovos", "inkbook", "matepad paper"
    };

    private Skin() {
    }

    /** 当前是否使用墨水屏配色（首次由设备型号自动判断，之后以用户选择为准）。 */
    static boolean isEink(Context context) {
        return new AppRepo(context).isEinkMode();
    }

    /** 设备本身是否像墨水屏。 */
    static boolean isEinkDevice() {
        String info = (Build.MANUFACTURER + "|" + Build.BRAND + "|" + Build.MODEL + "|"
                + Build.DEVICE + "|" + Build.PRODUCT + "|" + Build.HARDWARE)
                .toLowerCase(Locale.US);
        for (String mark : EINK_MARKS) {
            if (info.contains(mark)) {
                return true;
            }
        }
        return false;
    }

    /** 读取主题里的颜色属性；取不到时返回兜底色。 */
    static int color(Context context, int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attr, value, true)) {
            return fallback;
        }
        if (value.resourceId != 0) {
            try {
                return context.getResources().getColor(value.resourceId);
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return value.data;
    }

    /** 读取主题里的资源属性（颜色 / drawable）；取不到时返回 null。 */
    static Drawable drawable(Context context, int attr) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attr, value, true) || value.resourceId == 0) {
            return null;
        }
        try {
            return context.getResources().getDrawable(value.resourceId);
        } catch (Exception ignored) {
            return null;
        }
    }
}
