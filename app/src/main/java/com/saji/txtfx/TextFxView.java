package com.saji.txtfx;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

public class TextFxView extends View {
    private String previewText = "YOUR LYRICS\nSTART HERE";
    private int styleIndex = 0;
    private int textColor = Color.WHITE;
    private float textSizeSp = 42f;
    private Typeface typeface = Typeface.create("sans-serif", Typeface.BOLD);
    private long startedAt = SystemClock.uptimeMillis();

    public TextFxView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setTextValue(String value) {
        previewText = value == null ? "" : value;
        invalidate();
    }

    public void setStyleIndex(int value) {
        styleIndex = Math.max(0, Math.min(21, value));
        invalidate();
    }

    public void setTextColorValue(int value) {
        textColor = value;
        invalidate();
    }

    public void setTextSizeSp(float value) {
        textSizeSp = Math.max(16f, Math.min(112f, value));
        invalidate();
    }

    public void setTypefaceValue(Typeface value) {
        if (value != null) typeface = value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawFrame(canvas, getWidth(), getHeight(),
                SystemClock.uptimeMillis() - startedAt,
                previewText, styleIndex, textColor, textSizeSp,
                Color.rgb(13, 15, 27), false, typeface);
        postInvalidateDelayed(32);
    }

    public static void drawFrame(Canvas canvas, int width, int height, long timeMs,
                                 String text, int style, int color, float sizeSp,
                                 int backgroundColor, boolean safeFrame) {
        drawFrame(canvas, width, height, timeMs, text, style, color, sizeSp,
                backgroundColor, safeFrame, Typeface.create("sans-serif", Typeface.BOLD));
    }

    public static void drawFrame(Canvas canvas, int width, int height, long timeMs,
                                  String text, int style, int color, float sizeSp,
                                  int backgroundColor, boolean safeFrame, Typeface font) {
        if (width <= 0 || height <= 0) return;
        canvas.drawColor(backgroundColor);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTypeface(font);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(sizeSp * width / 400f);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);

        String value = (text == null || text.trim().isEmpty()) ? " " : text;
        float cycle = (timeMs % 2600L) / 2600f;
        float enter = Math.min(1f, cycle * 4.2f);
        float eased = 1f - (float)Math.pow(1f - enter, 3);
        float alpha = 1f;
        float dx = 0f, dy = 0f, sx = 1f, sy = 1f, rotation = 0f;
        float centerX = width / 2f;
        float centerY = height / 2f;

        switch (style) {
            case 0: alpha = 0.18f + 0.82f * (float)Math.sin(Math.PI * eased); break;
            case 1: dy = height * 0.34f * (1f - eased); break;
            case 2: dx = -width * 0.45f * (1f - eased); break;
            case 3: sx = sy = 0.35f + 0.65f * eased; break;
            case 4: sx = sy = 0.55f + 0.45f * (float)Math.sin(eased * Math.PI / 2f); break;
            case 5: dy = (1f - eased) * height * 0.22f - Math.abs((float)Math.sin(timeMs / 160.0)) * height * 0.025f; break;
            case 8: rotation = (1f - eased) * -14f + (float)Math.sin(timeMs / 900.0) * 2f; break;
            case 9: paint.setShadowLayer(width * 0.035f, 0, 0, color); break;
            case 10: paint.setShadowLayer(width * 0.055f, 0, 0, color); break;
            case 11: dx = (float)Math.sin(timeMs / 43.0) * width * 0.012f; break;
            case 13: sy = Math.max(0.08f, Math.abs((float)Math.cos(timeMs / 700.0))); break;
            case 15: sx = sy = 0.94f + 0.08f * (float)Math.sin(timeMs / 210.0); break;
            case 17: dx = (float)Math.sin(timeMs / 1200.0) * width * 0.035f; dy = (float)Math.cos(timeMs / 1100.0) * height * 0.025f; break;
            case 18: dx = (float)Math.sin(timeMs / 34.0) * width * 0.012f; break;
            case 19: sx = sy = 0.94f + 0.06f * (float)Math.sin(timeMs / 100.0); break;
            case 20: paint.setShadowLayer(width * 0.02f, 0, 0, Color.WHITE); break;
            default: break;
        }

        if (style == 6) {
            int total = value.length();
            int visible = Math.max(0, Math.min(total, (int)(total * eased)));
            value = value.substring(0, visible);
        } else if (style == 14) {
            String[] words = value.split("\\s+");
            int visible = Math.max(1, Math.min(words.length, (int)Math.ceil(words.length * eased)));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < visible; i++) {
                if (i > 0) builder.append(' ');
                builder.append(words[i]);
            }
            value = builder.toString();
        }

        canvas.save();
        canvas.translate(centerX + dx, centerY + dy);
        canvas.rotate(rotation);
        canvas.scale(sx, sy);

        if (style == 16) {
            canvas.save();
            canvas.clipRect(-width, -height, -width + (2f * width * eased), height);
        }
        if (style == 21) {
            Paint bars = new Paint(Paint.ANTI_ALIAS_FLAG);
            bars.setColor(Color.argb(160, 0, 0, 0));
            canvas.drawRect(-width, -height * 0.42f, width, -height * 0.30f, bars);
            canvas.drawRect(-width, height * 0.30f, width, height * 0.42f, bars);
            dy += (1f - eased) * height * 0.12f;
            canvas.translate(0, dy);
        }

        paint.setAlpha((int)(255 * Math.max(0f, Math.min(1f, alpha))));
        float lineHeight = paint.getTextSize() * 1.22f;
        String[] lines = value.split("\\n", -1);
        float totalHeight = lineHeight * lines.length;
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = -totalHeight / 2f - fm.ascent;
        if (style == 7) {
            for (int li = 0; li < lines.length; li++) {
                String spaced = addTracking(lines[li], Math.max(1, (int)(paint.getTextSize() * 0.045f)));
                canvas.drawText(spaced, 0, baseline + li * lineHeight, paint);
            }
        } else if (style == 12) {
            for (int li = 0; li < lines.length; li++) {
                String line = lines[li];
                float charWidth = paint.measureText("M");
                float totalWidth = paint.measureText(line);
                float x = -totalWidth / 2f;
                for (int ci = 0; ci < line.length(); ci++) {
                    String ch = String.valueOf(line.charAt(ci));
                    float wobble = (float)Math.sin(timeMs / 150.0 + ci * 0.65) * paint.getTextSize() * 0.13f;
                    canvas.drawText(ch, x + paint.measureText(line.substring(0, ci)) + paint.measureText(ch) / 2f,
                            baseline + li * lineHeight + wobble, paint);
                }
            }
        } else {
            for (int li = 0; li < lines.length; li++) {
                if (style == 11) {
                    Paint ghost = new Paint(paint);
                    ghost.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
                    ghost.setColor(Color.CYAN);
                    ghost.setAlpha(100);
                    canvas.drawText(lines[li], -width * 0.012f, baseline + li * lineHeight, ghost);
                    ghost.setColor(Color.MAGENTA);
                    canvas.drawText(lines[li], width * 0.012f, baseline + li * lineHeight, ghost);
                }
                canvas.drawText(lines[li], 0, baseline + li * lineHeight, paint);
            }
        }

        if (style == 16) canvas.restore();
        canvas.restore();

        if (safeFrame) {
            Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
            guide.setColor(Color.argb(80, 255, 255, 255));
            guide.setStyle(Paint.Style.STROKE);
            guide.setStrokeWidth(1f);
            canvas.drawRect(width * 0.08f, height * 0.08f, width * 0.92f, height * 0.92f, guide);
        }
    }

    private static String addTracking(String value, int spacing) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            result.append(value.charAt(i));
            if (i < value.length() - 1) {
                for (int n = 0; n < spacing / 2; n++) result.append(' ');
            }
        }
        return result.toString();
    }
}
