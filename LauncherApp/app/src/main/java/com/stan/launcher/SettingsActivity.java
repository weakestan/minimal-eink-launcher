package com.stan.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 桌面设置：全部应用总开关、逐个应用的显示 / 隐藏、默认桌面入口与恢复默认排序。
 */
public class SettingsActivity extends Activity {

    /** 设置发生变化时置位，桌面在 onResume 时据此重新加载。 */
    public static boolean sDataChanged;

    private AppRepo repo;
    private AppSettingAdapter adapter;
    private final List<AppInfo> apps = new ArrayList<>();

    private boolean eink;

    private View masterRow;
    private Switch masterSwitch;
    private Switch einkSwitch;
    private TextView masterDesc;
    private TextView einkDesc;
    private TextView homeDesc;
    private TextView homeAction;
    private TextView homeKeyDesc;
    private TextView homeKeyAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repo = new AppRepo(this);
        // 必须在 setContentView 之前定皮肤
        eink = repo.isEinkMode();
        setTheme(eink ? R.style.Theme_Launcher_Settings_Eink : R.style.Theme_Launcher_Settings);
        setContentView(R.layout.activity_settings);

        adapter = new AppSettingAdapter(this, apps, repo, new AppSettingAdapter.Listener() {
            @Override
            public void onVisibilityChanged(AppInfo app, boolean visible) {
                repo.setHidden(app.key(), !visible);
                sDataChanged = true;
                updateMasterRow();
            }
        });

        ListView listView = findViewById(R.id.app_list);
        listView.setAdapter(adapter);

        masterRow = findViewById(R.id.master_row);
        masterSwitch = findViewById(R.id.master_switch);
        masterDesc = findViewById(R.id.master_desc);
        einkSwitch = findViewById(R.id.eink_switch);
        einkDesc = findViewById(R.id.eink_desc);
        homeDesc = findViewById(R.id.default_home_desc);
        homeAction = findViewById(R.id.default_home_action);
        homeKeyDesc = findViewById(R.id.home_key_desc);
        homeKeyAction = findViewById(R.id.home_key_action);

        findViewById(R.id.back_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // 总开关：一次显示或隐藏全部应用
        masterRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (apps.isEmpty()) {
                    return;
                }
                boolean showAll = !masterSwitch.isChecked();
                if (showAll) {
                    repo.showAll();
                    Toast.makeText(SettingsActivity.this, R.string.show_all_done, Toast.LENGTH_SHORT).show();
                } else {
                    repo.hideAll(apps);
                    Toast.makeText(SettingsActivity.this, R.string.hide_all_done, Toast.LENGTH_SHORT).show();
                }
                sDataChanged = true;
                adapter.notifyDataSetChanged();
                updateMasterRow();
            }
        });

        // 墨水屏模式：整行点击切换，切换后重建界面以套用新主题
        updateEinkRow();
        findViewById(R.id.eink_row).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                repo.setEinkMode(!einkSwitch.isChecked());
                recreate();
            }
        });

        // 默认桌面：跳转系统「默认应用」设置
        findViewById(R.id.default_home_row).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openHomeSettings();
            }
        });

        // Home 键接管：跳转系统无障碍设置（华为中国区 ROM 无法设默认桌面时的替代方案）
        findViewById(R.id.home_key_row).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAccessibilitySettings();
            }
        });

        findViewById(R.id.reset_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                repo.resetOrder();
                sDataChanged = true;
                Toast.makeText(SettingsActivity.this, R.string.reset_done, Toast.LENGTH_SHORT).show();
                loadApps();
            }
        });

        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHomeRow();
        updateHomeKeyRow();
    }

    private void loadApps() {
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
                        apps.clear();
                        apps.addAll(scanned);
                        adapter.notifyDataSetChanged();
                        updateMasterRow();
                    }
                });
            }
        }, "settings-scan").start();
    }

    /** 刷新墨水屏模式开关与说明文案。 */
    private void updateEinkRow() {
        boolean on = repo.isEinkMode();
        einkSwitch.setChecked(on);
        if (!on) {
            einkDesc.setText(R.string.eink_desc_off);
        } else if (repo.hasEinkChoice()) {
            einkDesc.setText(R.string.eink_desc_on);
        } else {
            einkDesc.setText(R.string.eink_desc_auto);
        }
    }

    /** 刷新总开关状态与说明文案。 */
    private void updateMasterRow() {
        boolean anyHidden = !repo.hiddenKeys().isEmpty();
        masterSwitch.setChecked(!anyHidden);
        if (!anyHidden) {
            masterDesc.setText(R.string.master_desc_on);
        } else if (repo.isAllHidden(apps)) {
            masterDesc.setText(R.string.master_desc_off);
        } else {
            masterDesc.setText(R.string.master_desc_part);
        }
    }

    /** 当前是否已是系统默认桌面。 */
    private boolean isDefaultLauncher() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo info = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return info != null && info.activityInfo != null
                    && getPackageName().equals(info.activityInfo.packageName);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void updateHomeRow() {
        boolean isDefault = isDefaultLauncher();
        homeDesc.setText(isDefault ? R.string.default_home_desc_on : R.string.default_home_desc_off);
        homeAction.setText(isDefault ? R.string.default_home_action_done : R.string.default_home_action);
        homeAction.setTextColor(isDefault
                ? Skin.color(this, R.attr.lpTextSecondary, getResources().getColor(R.color.text_secondary))
                : Skin.color(this, R.attr.lpAccent, getResources().getColor(R.color.accent)));
    }

    private void openHomeSettings() {
        if (isDefaultLauncher()) {
            Toast.makeText(this, R.string.default_home_desc_on, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception e2) {
                Toast.makeText(this, R.string.default_home_unavailable, Toast.LENGTH_LONG).show();
            }
        }
    }

    /** 刷新 Home 键接管行状态。 */
    private void updateHomeKeyRow() {
        boolean enabled = HomeKeyService.isEnabled(this);
        homeKeyDesc.setText(enabled ? R.string.home_key_desc_on : R.string.home_key_desc_off);
        homeKeyAction.setText(enabled ? R.string.home_key_action_done : R.string.home_key_action);
        homeKeyAction.setTextColor(enabled
                ? Skin.color(this, R.attr.lpTextSecondary, getResources().getColor(R.color.text_secondary))
                : Skin.color(this, R.attr.lpAccent, getResources().getColor(R.color.accent)));
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, R.string.home_key_unavailable, Toast.LENGTH_LONG).show();
        }
    }
}
