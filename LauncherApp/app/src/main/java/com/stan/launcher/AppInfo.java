package com.stan.launcher;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

/** 一个可启动的应用条目。 */
public class AppInfo {

    public final String label;
    public final String packageName;
    public final String activityName;
    public Drawable icon;

    public AppInfo(String label, String packageName, String activityName) {
        this.label = label;
        this.packageName = packageName;
        this.activityName = activityName;
    }

    /** 唯一标识，用于持久化隐藏状态与排列顺序。 */
    public String key() {
        return packageName + "/" + activityName;
    }

    public ComponentName component() {
        return new ComponentName(packageName, activityName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AppInfo)) {
            return false;
        }
        return key().equals(((AppInfo) o).key());
    }

    @Override
    public int hashCode() {
        return key().hashCode();
    }
}
