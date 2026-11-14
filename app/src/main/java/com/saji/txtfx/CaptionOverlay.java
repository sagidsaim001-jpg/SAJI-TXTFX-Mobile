package com.saji.txtfx;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.media3.common.OverlaySettings;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.StaticOverlaySettings;

import java.util.List;

/**
 * A single, time-aware caption layer. One bounded bitmap is reused for all cues,
 * keeping export memory predictable even when a project has many captions.
 */
@UnstableApi
public final class CaptionOverlay extends BitmapOverlay {
    private final List<CaptionCue> cues;
    private Bitmap bitmap;
    private Canvas canvas;
    private int canvasWidth = 1280;
    private int canvasHeight = 720;

    public CaptionOverlay(List<CaptionCue> cues) {
        this.cues = cues;
    }

    @Override
    public void configure(Size videoSize) {
        int sourceWidth = Math.max(1, videoSize.getWidth());
        int sourceHeight = Math.max(1, videoSize.getHeight());
        float scale = Math.min(1f, 1440f / Math.max(sourceWidth, sourceHeight));
        canvasWidth = Math.max(2, Math.round(sourceWidth * scale));
        canvasHeight = Math.max(2, Math.round(sourceHeight * scale));
        if ((canvasWidth & 1) == 1) canvasWidth--;
        if ((canvasHeight & 1) == 1) canvasHeight--;
        makeBitmap();
    }

    private void makeBitmap() {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
    }

    @Override
    public Bitmap getBitmap(long presentationTimeUs) throws VideoFrameProcessingException {
        if (bitmap == null || bitmap.isRecycled()) makeBitmap();
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        long timeMs = presentationTimeUs / 1000L;
        for (CaptionCue cue : cues) {
            if (cue == null || cue.text == null || cue.text.trim().isEmpty()) continue;
            if (timeMs < cue.startMs || timeMs >= cue.endMs) continue;
            drawCue(cue, timeMs);
        }
        return bitmap;
    }

    private void drawCue(CaptionCue cue, long timeMs) {
        float scaleFactor = canvasHeight / 720f;
        float fontSize = Math.max(22f, cue.size * scaleFactor);
        String visibleText = cue.text;
        float progress = clamp((timeMs - cue.startMs) / 420f);
        float remaining = clamp((cue.endMs - timeMs) / 260f);
        float alpha = 1f;
        float scale = 1f;
        float offsetX = 0f;
        float offsetY = 0f;

        switch (cue.animation == null ? "Fade" : cue.animation) {
            case "Fade":
                alpha = progress;
                break;
            case "Rise":
                offsetY = (1f - progress) * canvasHeight * 0.075f;
                alpha = progress;
                break;
            case "Slide":
                offsetX = (1f - progress) * canvasWidth * 0.12f;
                alpha = progress;
                break;
            case "Zoom":
                scale = 0.62f + 0.38f * progress;
                alpha = progress;
                break;
            case "Bounce":
                scale = 0.72f + 0.28f * progress + (1f - progress) * 0.12f
                        * (float)Math.sin((timeMs - cue.startMs) / 55f);
                break;
            case "Typewriter":
                int count = Math.max(1, Math.min(visibleText.length(),
                        (int)Math.ceil(visibleText.length() * progress)));
                visibleText = visibleText.substring(0, count);
                break;
            case "Pulse":
                scale = 1f + 0.055f * (float)Math.sin(timeMs / 115f);
                break;
            case "Glitch":
                if (((timeMs / 70L) & 1L) == 0L) offsetX = canvasWidth * 0.009f;
                else offsetX = -canvasWidth * 0.009f;
                break;
            case "Flip":
                scale = Math.max(0.12f, Math.abs((float)Math.cos((timeMs - cue.startMs) / 420f)));
                break;
            case "Glow":
                break;
            default:
                break;
        }
        alpha *= Math.min(1f, remaining + 0.12f);
        if (alpha <= 0.01f || visibleText.isEmpty()) return;

        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setColor(cue.color);
        paint.setTextSize(fontSize);
        paint.setTypeface(cue.getTypeface());
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setAlpha(Math.max(0, Math.min(255, Math.round(alpha * 255f))));
        if ("Glow".equals(cue.animation)) {
            paint.setShadowLayer(fontSize * 0.34f, 0, 0, cue.color);
        } else {
            paint.setShadowLayer(fontSize * 0.12f, 0, fontSize * 0.045f, Color.BLACK);
        }

        int maxWidth = Math.max(120, Math.round(canvasWidth * 0.88f));
        StaticLayout layout = StaticLayout.Builder
                .obtain(visibleText, 0, visibleText.length(), paint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setLineSpacing(fontSize * 0.10f, 1f)
                .build();

        float centerX = canvasWidth * 0.5f + offsetX;
        float centerY = canvasHeight * (Math.max(8, Math.min(92, cue.verticalPosition)) / 100f) + offsetY;
        float left = centerX - layout.getWidth() * 0.5f;
        float top = centerY - layout.getHeight() * 0.5f;

        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale, layout.getWidth() * 0.5f, layout.getHeight() * 0.5f);

        Paint outline = new Paint(paint);
        outline.clearShadowLayer();
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeJoin(Paint.Join.ROUND);
        outline.setStrokeWidth(Math.max(2f, fontSize * 0.085f));
        outline.setColor(Color.argb(paint.getAlpha(), 8, 9, 17));
        TextPaint outlinePaint = new TextPaint(outline);
        StaticLayout outlineLayout = StaticLayout.Builder
                .obtain(visibleText, 0, visibleText.length(), outlinePaint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setLineSpacing(fontSize * 0.10f, 1f)
                .build();
        outlineLayout.draw(canvas);
        layout.draw(canvas);
        canvas.restore();
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    @Override
    public OverlaySettings getOverlaySettings(long presentationTimeUs) {
        return new StaticOverlaySettings.Builder().build();
    }

    @Override
    public void release() throws VideoFrameProcessingException {
        super.release();
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        bitmap = null;
        canvas = null;
    }
}