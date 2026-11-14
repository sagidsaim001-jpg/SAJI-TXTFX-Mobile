package com.saji.txtfx;

import android.graphics.Color;
import android.graphics.Typeface;

import org.json.JSONException;
import org.json.JSONObject;

public final class CaptionCue {
    public String text;
    public long startMs;
    public long endMs;
    public int color;
    public float size;
    public int verticalPosition;
    public String animation;
    public String font;

    public CaptionCue(String text, long startMs, long endMs) {
        this.text = text == null ? "" : text;
        this.startMs = Math.max(0, startMs);
        this.endMs = Math.max(this.startMs + 250, endMs);
        this.color = Color.WHITE;
        this.size = 48f;
        this.verticalPosition = 78;
        this.animation = "Fade";
        this.font = "sans-serif";
    }

    public JSONObject toJson() throws JSONException {
        JSONObject value = new JSONObject();
        value.put("text", text);
        value.put("startMs", startMs);
        value.put("endMs", endMs);
        value.put("color", color);
        value.put("size", size);
        value.put("verticalPosition", verticalPosition);
        value.put("animation", animation);
        value.put("font", font);
        return value;
    }

    public static CaptionCue fromJson(JSONObject value) {
        CaptionCue cue = new CaptionCue(
                value.optString("text", ""),
                value.optLong("startMs", 0),
                value.optLong("endMs", 2500));
        cue.color = value.optInt("color", Color.WHITE);
        cue.size = (float) value.optDouble("size", 48);
        cue.verticalPosition = value.optInt("verticalPosition", 78);
        cue.animation = value.optString("animation", "Fade");
        cue.font = value.optString("font", "sans-serif");
        return cue;
    }

    public Typeface getTypeface() {
        int style = Typeface.BOLD;
        if ("serif".equals(font)) return Typeface.create("serif", style);
        if ("monospace".equals(font)) return Typeface.create("monospace", style);
        if ("sans-serif-light".equals(font)) return Typeface.create("sans-serif-light", Typeface.NORMAL);
        return Typeface.create("sans-serif", style);
    }
}