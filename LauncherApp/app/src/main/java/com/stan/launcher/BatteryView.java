package com.stan.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * 自绘的电量图标：电池外框 + 按百分比填充的内部 + 充电闪电。
 */
public class BatteryView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF body = new RectF();
    private final Path bolt = new Path();

    private int level = 100;
    private boolean charging = false;

    /** 墨水屏皮肤：只用纯黑，避免彩色退化成与背景接近的中灰。 */
    private boolean eink;

    /** 由 Activity 按当前皮肤调用。 */
    public void setEinkMode(boolean eink) {
        if (this.eink == eink) {
            return;
        }
        this.eink = eink;
        invalidate();
    }

    public BatteryView(Context context) {
        this(context, null);
    }

    public BatteryView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setBattery(int level, boolean charging) {
        if (this.level == level && this.charging == charging) {
            return;
        }
        this.level = Math.max(0, Math.min(100, level));
        this.charging = charging;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float capW = Math.max(2f, w * 0.06f);
        float stroke = Math.max(1.2f, h * 0.09f);

        float right = w - capW - stroke / 2f;
        body.set(stroke / 2f, stroke / 2f, right, h - stroke / 2f);
        float radius = h * 0.28f;

        int frameColor;
        int levelColor;
        // 墨水屏只有灰阶：绿色 (#8FE38F) 会退化成接近白底的中灰，几乎看不见，
        // 因此墨水屏统一用纯黑填充，只靠填充宽度表达电量。
        if (eink) {
            frameColor = Color.BLACK;
            levelColor = Color.BLACK;
        } else {
            frameColor = Skin.color(getContext(), R.attr.lpTextSecondary, 0xFF9FB0C9);
            if (level <= 20) {
                levelColor = getResources().getColor(R.color.battery_low);
            } else if (level <= 45) {
                levelColor = 0xFFFFC46B;
            } else {
                levelColor = getResources().getColor(R.color.battery_ok);
            }
        }

        // 内部填充；墨水屏充电时不填充，否则黑色方块会把闪电吃掉
        boolean drawFill = !(eink && charging);
        float padding = stroke * 1.3f;
        float innerW = (body.width() - padding * 2f) * (level / 100f);
        if (drawFill && innerW > 0.5f) {
            RectF inner = new RectF(
                    body.left + padding,
                    body.top + padding,
                    body.left + padding + innerW,
                    body.bottom - padding);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(levelColor);
            canvas.drawRoundRect(inner, radius * 0.5f, radius * 0.5f, paint);
        }

        // 外框 + 右侧触点
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(frameColor);
        canvas.drawRoundRect(body, radius, radius, paint);

        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(right + stroke / 2f, h * 0.3f, w, h * 0.7f),
                capW / 2f, capW / 2f, paint);

        if (charging) {
            float cx = body.centerX();
            float cy = body.centerY();
            float s = Math.min(body.width(), body.height()) * 0.9f;
            bolt.reset();
            bolt.moveTo(cx + s * 0.10f, cy - s * 0.42f);
            bolt.lineTo(cx - s * 0.26f, cy + s * 0.06f);
            bolt.lineTo(cx - s * 0.02f, cy + s * 0.06f);
            bolt.lineTo(cx - s * 0.10f, cy + s * 0.44f);
            bolt.lineTo(cx + s * 0.26f, cy - s * 0.04f);
            bolt.lineTo(cx + s * 0.02f, cy - s * 0.04f);
            bolt.close();
            paint.setColor(eink ? Color.BLACK : 0xFF10243A);
            canvas.drawPath(bolt, paint);
        }
    }
}
