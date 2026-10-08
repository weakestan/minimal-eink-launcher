package com.stan.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * 自绘的 WiFi 信号图标：底部圆点 + 三道弧线，按信号等级点亮。
 * level 取值 0~4（0 表示未连接或信号极弱）。
 */
public class WifiSignalView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private int level = 0;
    private boolean connected = false;
    private int activeColor;
    private int dimColor;

    public WifiSignalView(Context context) {
        this(context, null);
    }

    public WifiSignalView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // 跟随皮肤：墨水屏下为纯黑/浅灰，深色主题下为浅蓝白/深灰蓝
        activeColor = Skin.color(context, R.attr.lpTextPrimary,
                context.getResources().getColor(R.color.text_primary));
        dimColor = Skin.color(context, R.attr.lpIconDim,
                context.getResources().getColor(R.color.icon_dim));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setSignal(int level, boolean connected) {
        if (this.level == level && this.connected == connected) {
            return;
        }
        this.level = Math.max(0, Math.min(4, level));
        this.connected = connected;
        invalidate();
    }

    public void setColors(int active, int dim) {
        activeColor = active;
        dimColor = dim;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h * 0.84f;
        float stroke = Math.max(1.6f, h * 0.11f);
        paint.setStrokeWidth(stroke);

        // 底部圆点：只要有连接就点亮
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(connected ? activeColor : dimColor);
        canvas.drawCircle(cx, cy, stroke * 0.72f, paint);

        // 三道弧线，从小到大
        float[] radii = {h * 0.22f, h * 0.40f, h * 0.58f};
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < radii.length; i++) {
            float r = radii[i];
            int threshold = i + 2; // 第 2 级点亮第一道弧，第 4 级点亮全部
            boolean on = connected && level >= threshold;
            paint.setColor(on ? activeColor : dimColor);
            arcRect.set(cx - r, cy - r, cx + r, cy + r);
            canvas.drawArc(arcRect, 225f, 90f, false, paint);
        }
    }
}
