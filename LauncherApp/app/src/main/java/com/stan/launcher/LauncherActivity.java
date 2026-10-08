package com.stan.launcher;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 极简桌面主界面：顶部自绘状态栏（日期/时间/蓝牙/WiFi/电量/设置）+ 应用图标网格。
 */
public class LauncherActivity extends Activity implements AppGridLayout.Callback {

    private static final String[] WEEK_DAYS = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    private AppRepo repo;

    private AppGridLayout grid;
    private ScrollView scrollView;
    private LinearLayout hintBar;
    private TextView timeText;
    private TextView dateText;
    private TextView batteryText;
    private WifiSignalView wifiView;
    private BatteryView batteryView;
    private ImageView btIcon;

    private final List<AppInfo> allApps = new ArrayList<>();
    private boolean appsDirty = true;
    private boolean editMode;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("M月d日", Locale.CHINA);

    private int tickCount;
    private int lastBtState = -1;
    private int lastBatteryPercent = -1;
    private boolean eink;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateClock();
            tickCount++;
            if (tickCount % 3 == 0) {
                updateWifi();
                updateBluetooth();
            }
            handler.postDelayed(this, 1000L);
        }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBattery(intent);
        }
    };

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBluetooth();
        }
    };

    private final BroadcastReceiver netReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateWifi();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repo = new AppRepo(this);
        // 必须在 setContentView 之前定皮肤：布局里的 ?attr/... 在 inflate 时就要取到值
        eink = repo.isEinkMode();
        setTheme(eink ? R.style.Theme_Launcher_Eink : R.style.Theme_Launcher);
        setContentView(R.layout.activity_launcher);

        timeText = findViewById(R.id.time_text);
        dateText = findViewById(R.id.date_text);
        batteryText = findViewById(R.id.battery_text);
        batteryView = findViewById(R.id.battery_view);
        wifiView = findViewById(R.id.wifi_view);
        btIcon = findViewById(R.id.bt_icon);
        hintBar = findViewById(R.id.hint_bar);
        scrollView = findViewById(R.id.app_scroll);
        grid = findViewById(R.id.app_grid);

        grid.setCallback(this);
        grid.setScrollParent(scrollView);
        grid.setColumns(columnsForScreen());
        batteryView.setEinkMode(eink);

        View settingsBtn = findViewById(R.id.settings_btn);
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LauncherActivity.this, SettingsActivity.class));
            }
        });

        findViewById(R.id.done_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitEditMode();
            }
        });

        applyImmersive();
    }

    /** 根据屏幕宽度决定每行图标数量。 */
    private int columnsForScreen() {
        int widthDp = (int) (getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        if (widthDp >= 720) {
            return 7;
        }
        if (widthDp >= 600) {
            return 6;
        }
        return 5;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repo.isEinkMode() != eink) {
            // 刚从设置页改了配色：重建界面以套用新主题
            recreate();
            return;
        }
        applyImmersive();
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        registerReceiver(btReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(netReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        if (appsDirty || SettingsActivity.sDataChanged) {
            SettingsActivity.sDataChanged = false;
            loadApps();
        }
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
        exitEditMode();
        try {
            unregisterReceiver(batteryReceiver);
            unregisterReceiver(btReceiver);
            unregisterReceiver(netReceiver);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /** 作为默认桌面再次被 Home 键唤起时，回到常规状态。 */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        exitEditMode();
        if (scrollView != null) {
            if (eink) {
                // 墨水屏不做平滑滚动，避免中间帧全部刷新留下残影
                scrollView.scrollTo(0, 0);
            } else {
                scrollView.smoothScrollTo(0, 0);
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersive();
        }
    }

    /** 隐藏系统状态栏，使用桌面自绘状态栏。 */
    private void applyImmersive() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    /* --------------------------- 应用列表加载 --------------------------- */

    private void loadApps() {
        appsDirty = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppInfo> scanned = repo.applySavedOrder(repo.scanApps());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        allApps.clear();
                        allApps.addAll(scanned);
                        refreshGrid();
                    }
                });
            }
        }, "app-scan").start();
    }

    private void refreshGrid() {
        // 位置用「格位」表达：每个图标待在自己那一格里，格位可以留空
        List<AppInfo> visible = repo.visibleApps(allApps);
        grid.setApps(visible, repo.assignSlots(visible));
        grid.setEditMode(editMode);
    }

    /* --------------------------- AppGridLayout.Callback --------------------------- */

    @Override
    public void onAppClick(AppInfo app) {
        repo.launch(app);
    }

    @Override
    public void onEditModeRequested() {
        enterEditMode();
    }

    @Override
    public void onHideRequested(AppInfo app) {
        repo.setHidden(app.key(), true);
        refreshGrid();
        Toast.makeText(this, "已隐藏「" + app.label + "」，可在设置中恢复", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLayoutChanged() {
        // 只保存桌面上这些图标的格位；隐藏应用原有的格位由 AppRepo 保留
        repo.saveLayout(grid.appsBySlot(), grid.slotsBySlot());
    }

    private void enterEditMode() {
        if (editMode) {
            return;
        }
        editMode = true;
        if (eink) {
            // 墨水屏不做淡入淡出：过渡帧会留下残影，直接显示
            hintBar.animate().cancel();
            hintBar.setAlpha(1f);
            hintBar.setVisibility(View.VISIBLE);
        } else {
            hintBar.setVisibility(View.VISIBLE);
            hintBar.setAlpha(0f);
            hintBar.animate().alpha(1f).setDuration(180).start();
        }
        grid.setEditMode(true);
    }

    private void exitEditMode() {
        if (!editMode) {
            return;
        }
        editMode = false;
        if (eink) {
            hintBar.animate().cancel();
            hintBar.setAlpha(1f);
            hintBar.setVisibility(View.GONE);
        } else {
            hintBar.animate().alpha(0f).setDuration(150).withEndAction(new Runnable() {
                @Override
                public void run() {
                    hintBar.setVisibility(View.GONE);
                }
            }).start();
        }
        grid.setEditMode(false);
    }

    @Override
    public void onBackPressed() {
        if (editMode) {
            exitEditMode();
            return;
        }
        super.onBackPressed();
    }

    /* --------------------------- 状态栏刷新 --------------------------- */

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        // 内容没变就不 setText：墨水屏上多余的 invalidate 会带来整屏刷新与残影
        String time = timeFormat.format(now.getTime());
        if (!time.contentEquals(timeText.getText())) {
            timeText.setText(time);
        }
        String date = dateFormat.format(now.getTime()) + " " + WEEK_DAYS[now.get(Calendar.DAY_OF_WEEK) - 1];
        if (!date.contentEquals(dateText.getText())) {
            dateText.setText(date);
        }
    }

    private void updateBattery(Intent intent) {
        if (intent == null) {
            return;
        }
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        int percent = (level < 0 || scale <= 0) ? 0 : Math.round(level * 100f / scale);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        batteryView.setBattery(percent, charging);
        if (percent != lastBatteryPercent) {
            lastBatteryPercent = percent;
            batteryText.setText(percent + "%");
        }
    }

    private void updateWifi() {
        boolean connected = false;
        int level = 0;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network network = cm.getActiveNetwork();
                NetworkCapabilities caps = network == null ? null : cm.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    connected = true;
                    WifiManager wm = (WifiManager) getApplicationContext()
                            .getSystemService(Context.WIFI_SERVICE);
                    android.net.wifi.WifiInfo info = wm == null ? null : wm.getConnectionInfo();
                    int rssi = info == null ? -127 : info.getRssi();
                    if (rssi <= -126 || rssi == 0) {
                        // 无法读取信号强度时（权限受限），按已连接展示满格
                        level = 4;
                    } else {
                        level = WifiManager.calculateSignalLevel(rssi, 5) - 1;
                    }
                }
            }
        } catch (Exception ignored) {
            connected = false;
        }
        wifiView.setSignal(level, connected);
    }

    private void updateBluetooth() {
        int state = -1;
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) {
                state = adapter.getState();
            }
        } catch (Exception ignored) {
            state = -1;
        }
        if (state < 0) {
            // 无权限或无蓝牙模块时，退化读取系统设置开关
            try {
                int on = Settings.Global.getInt(getContentResolver(), Settings.Global.BLUETOOTH_ON, 0);
                state = on == 1 ? BluetoothAdapter.STATE_ON : BluetoothAdapter.STATE_OFF;
            } catch (Exception ignored) {
                state = BluetoothAdapter.STATE_OFF;
            }
        }
        if (state == lastBtState) {
            return;
        }
        lastBtState = state;
        boolean on = state == BluetoothAdapter.STATE_ON;
        btIcon.setAlpha(1f);
        btIcon.setColorFilter(on
                        ? Skin.color(this, R.attr.lpAccent, getResources().getColor(R.color.accent))
                        : Skin.color(this, R.attr.lpIconDim, getResources().getColor(R.color.icon_dim)),
                android.graphics.PorterDuff.Mode.SRC_IN);
    }
}
