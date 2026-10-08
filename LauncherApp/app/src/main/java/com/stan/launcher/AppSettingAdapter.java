package com.stan.launcher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 设置页的应用列表适配器：图标 + 名称 + 显示状态开关。
 *
 * 注意：显示/隐藏只由「点击整行」触发，开关本身不接收点击、也不保存自身状态，
 * 这样 ListView 复用或界面状态恢复时都不会误写配置。
 */
public class AppSettingAdapter extends BaseAdapter {

    public interface Listener {
        void onVisibilityChanged(AppInfo app, boolean visible);
    }

    private final Context context;
    private final List<AppInfo> apps;
    private final AppRepo repo;
    private final Listener listener;
    private final LayoutInflater inflater;

    public AppSettingAdapter(Context context, List<AppInfo> apps, AppRepo repo, Listener listener) {
        this.context = context;
        this.apps = apps;
        this.repo = repo;
        this.listener = listener;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return apps.size();
    }

    @Override
    public Object getItem(int position) {
        return apps.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_app_setting, parent, false);
            holder = new ViewHolder();
            holder.icon = convertView.findViewById(R.id.item_icon);
            holder.label = convertView.findViewById(R.id.item_label);
            holder.pkg = convertView.findViewById(R.id.item_pkg);
            holder.toggle = convertView.findViewById(R.id.item_switch);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final AppInfo app = apps.get(position);
        final boolean visible = !repo.isHidden(app.key());

        holder.icon.setImageDrawable(app.icon);
        holder.label.setText(app.label);
        holder.pkg.setText(app.packageName);
        holder.toggle.setChecked(visible);
        convertView.setAlpha(visible ? 1f : 0.5f);

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean nowVisible = repo.isHidden(app.key());
                repo.setHidden(app.key(), !nowVisible);
                notifyDataSetChanged();
                if (listener != null) {
                    listener.onVisibilityChanged(app, nowVisible);
                }
                Toast.makeText(context,
                        nowVisible ? "已显示「" + app.label + "」" : "已隐藏「" + app.label + "」",
                        Toast.LENGTH_SHORT).show();
            }
        });
        return convertView;
    }

    private static class ViewHolder {
        ImageView icon;
        TextView label;
        TextView pkg;
        Switch toggle;
    }
}
