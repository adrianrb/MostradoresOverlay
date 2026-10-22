package com.mostradores.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class GaugeView extends View {

    private static final float START_ANGLE = 135f;
    private static final float SWEEP_ANGLE = 270f;

    private final String label;
    private final int color;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private float target;
    private float shown;
    private String detail = "";
    private boolean desconhecido;

    private final Runnable animator = new Runnable() {
        @Override
        public void run() {
            float delta = target - shown;
            if (Math.abs(delta) < 0.3f) {
                shown = target;
                invalidate();
                return;
            }
            shown += delta * 0.25f;
            invalidate();
            postOnAnimation(this);
        }
    };

    public GaugeView(Context context, String label, int color) {
        super(context);
        this.label = label;
        this.color = color;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(0x33FFFFFF);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(color);

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        tickPaint.setColor(0x66FFFFFF);

        needlePaint.setStyle(Paint.Style.STROKE);
        needlePaint.setStrokeCap(Paint.Cap.ROUND);
        needlePaint.setColor(0xFFFFFFFF);

        hubPaint.setStyle(Paint.Style.FILL);
        hubPaint.setColor(color);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(0xCCFFFFFF);
        labelPaint.setFakeBoldText(true);

        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setColor(0xFFFFFFFF);
        valuePaint.setFakeBoldText(true);

        detailPaint.setTextAlign(Paint.Align.CENTER);
        detailPaint.setColor(0x99FFFFFF);
    }

    public void setPercent(float percent, String detailText) {
        if (detailText != null) {
            detail = detailText;
        }
        if (percent < 0f) {
            desconhecido = true;
            target = 0f;
            shown = 0f;
            removeCallbacks(animator);
            invalidate();
            return;
        }
        desconhecido = false;
        float clamped = Math.max(0f, Math.min(100f, percent));
        if (Math.abs(clamped - target) < 0.01f) {
            invalidate();
            return;
        }
        target = clamped;
        removeCallbacks(animator);
        postOnAnimation(animator);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        float size = Math.min(w, h);
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = size / 2f;
        float stroke = size * 0.11f;

        arcRect.set(
                cx - radius + stroke / 2f,
                cy - radius + stroke / 2f,
                cx + radius - stroke / 2f,
                cy + radius - stroke / 2f);

        trackPaint.setStrokeWidth(stroke);
        canvas.drawArc(arcRect, START_ANGLE, SWEEP_ANGLE, false, trackPaint);

        drawTicks(canvas, cx, cy, radius, stroke, size);

        float ratio = desconhecido ? 0f : shown / 100f;
        arcPaint.setStrokeWidth(stroke);
        if (ratio > 0.001f) {
            canvas.drawArc(arcRect, START_ANGLE, SWEEP_ANGLE * ratio, false, arcPaint);
        }

        if (!desconhecido) {
            double angle = Math.toRadians(START_ANGLE + SWEEP_ANGLE * ratio);
            float needleLen = (radius - stroke * 1.4f) * 0.86f;
            float nx = cx + needleLen * (float) Math.cos(angle);
            float ny = cy + needleLen * (float) Math.sin(angle);
            needlePaint.setStrokeWidth(Math.max(1.5f, size * 0.022f));
            canvas.drawLine(cx, cy, nx, ny, needlePaint);
        }
        canvas.drawCircle(cx, cy, size * 0.045f, hubPaint);

        labelPaint.setTextSize(size * 0.15f);
        canvas.drawText(label, cx, cy - size * 0.24f, labelPaint);

        valuePaint.setTextSize(size * 0.26f);
        canvas.drawText(desconhecido ? "—" : Math.round(shown) + "%",
                cx, cy + size * 0.20f, valuePaint);

        detailPaint.setTextSize(size * 0.105f);
        if (detail.length() > 0) {
            canvas.drawText(detail, cx, cy + size * 0.36f, detailPaint);
        }
    }

    private void drawTicks(Canvas canvas, float cx, float cy, float radius, float stroke, float size) {
        float outer = radius - stroke * 1.05f;
        tickPaint.setStrokeWidth(Math.max(1f, size * 0.012f));
        for (int i = 0; i <= 20; i++) {
            boolean major = (i % 2) == 0;
            float len = major ? size * 0.10f : size * 0.05f;
            double a = Math.toRadians(START_ANGLE + SWEEP_ANGLE * i / 20f);
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            canvas.drawLine(
                    cx + outer * cos, cy + outer * sin,
                    cx + (outer - len) * cos, cy + (outer - len) * sin,
                    tickPaint);
        }
    }
}
