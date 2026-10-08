package com.stan.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * 桌面接管服务。
 *
 * 华为中国区 ROM 在系统层面禁止第三方桌面成为默认桌面
 * （HwPackageManagerService.replacePreferredActivity 拦截，报
 * "not allowed to be default home"），因此改用无障碍服务做接管：
 *
 * 1. 主路径：监听窗口变化，只要系统桌面（华为桌面）一进入前台就立刻把极简桌面拉到前台。
 *    手势导航（上滑回桌面）、Home 键、导航栏按钮、最近任务切回桌面都会经过这条路径，
 *    因为它们的终点都是「系统桌面被显示」。
 * 2. 辅助路径：按键过滤拦截 KEYCODE_HOME（有物理/导航键 Home 的机型可少一次闪烁）。
 */
public class HomeKeyService extends AccessibilityService {

    private static final String TAG = "HomeKeyService";

    /** 两次接管之间的最小间隔，避免与系统桌面互相抢焦点。 */
    private static final long LAUNCH_COOLDOWN_MS = 700L;
    /** 桌面包名缓存有效期。 */
    private static final long HOME_CACHE_MS = 60_000L;

    private Set<String> mHomeComponents;
    private long mHomeComponentsAt;
    private long mLastLaunchAt;

    /** 判断本服务是否已被用户启用（设置页展示状态用）。 */
    public static boolean isEnabled(Context context) {
        try {
            String enabled = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) {
                return false;
            }
            String full = context.getPackageName() + "/" + HomeKeyService.class.getName();
            String shortName = context.getPackageName() + "/." + HomeKeyService.class.getSimpleName();
            for (String item : enabled.split(":")) {
                String name = item.trim();
                if (name.equalsIgnoreCase(full) || name.equalsIgnoreCase(shortName)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "service connected");
        trace("connected api=" + android.os.Build.VERSION.SDK_INT);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        String pkg = resolvePackage(event);
        if (pkg == null || getPackageName().equals(pkg)) {
            return;
        }
        String cls = event.getClassName() == null ? null : event.getClassName().toString();
        if (!isHomeForeground(pkg, cls)) {
            return;
        }
        Log.i(TAG, "system home in foreground: " + pkg + "/" + cls);
        trace("home foreground: " + pkg + "/" + cls);
        openLauncher();
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) {
            return super.onKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0) {
            trace("home key down");
            openLauncher();
        }
        // 按下与抬起都消费掉，避免系统再把华为桌面拉到前台
        return true;
    }

    /** 事件里的包名，个别 ROM 只填 className，这里做兜底。 */
    private String resolvePackage(AccessibilityEvent event) {
        CharSequence pkg = event.getPackageName();
        if (pkg != null && pkg.length() > 0) {
            return pkg.toString();
        }
        CharSequence cls = event.getClassName();
        if (cls != null) {
            String name = cls.toString();
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                return name.substring(0, dot);
            }
        }
        return null;
    }

    /**
     * 当前前台窗口是否就是系统桌面。
     *
     * 只用「包名/Activity 全名」严格比对：
     * - 设置应用里的 FallbackHome 也声明了 HOME，按包名判断会把打开设置误当成回桌面；
     * - 桌面窗口消失/切换时会带出 class=null 的事件，按包名兜底会把「离开桌面」误当成「回到桌面」。
     */
    private boolean isHomeForeground(String pkg, String cls) {
        if (cls == null || cls.length() == 0) {
            return false;
        }
        return homeComponents().contains(pkg + "/" + cls);
    }

    /** 系统桌面组件集合（包名/Activity 全名），排除兜底的 FallbackHome。 */
    private Set<String> homeComponents() {
        long now = SystemClock.uptimeMillis();
        if (mHomeComponents == null || now - mHomeComponentsAt > HOME_CACHE_MS) {
            Set<String> set = new HashSet<>();
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                for (ResolveInfo info : getPackageManager().queryIntentActivities(intent, 0)) {
                    if (info.activityInfo == null || info.activityInfo.name == null) {
                        continue;
                    }
                    if (info.activityInfo.name.contains("FallbackHome")) {
                        continue;
                    }
                    set.add(info.activityInfo.packageName + "/" + info.activityInfo.name);
                }
            } catch (Exception e) {
                Log.w(TAG, "query home activities failed", e);
            }
            mHomeComponents = set;
            mHomeComponentsAt = now;
            trace("home components: " + set);
        }
        return mHomeComponents;
    }

    private void openLauncher() {
        long now = SystemClock.uptimeMillis();
        if (now - mLastLaunchAt < LAUNCH_COOLDOWN_MS) {
            return;
        }
        mLastLaunchAt = now;
        try {
            Intent intent = new Intent(this, LauncherActivity.class);
            // CLEAR_TOP：从本应用自己的设置页回桌面时，桌面要真正显示而不是回到设置页
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            Log.i(TAG, "launcher start requested");
            trace("launcher start requested");
        } catch (Exception e) {
            Log.w(TAG, "launcher start failed", e);
            trace("launcher start failed: " + e);
        }
    }

    /** 调试日志：部分 ROM 会过滤应用 logcat，这里同时落盘，便于排查。 */
    private void trace(String msg) {
        try {
            java.io.File file = new java.io.File(getFilesDir(), "key.log");
            if (file.length() > 64 * 1024) {
                file.delete();
            }
            java.io.FileOutputStream out = new java.io.FileOutputStream(file, true);
            String line = new java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US)
                    .format(new java.util.Date()) + " " + msg + "\n";
            out.write(line.getBytes());
            out.close();
        } catch (Exception ignored) {
        }
    }
}
