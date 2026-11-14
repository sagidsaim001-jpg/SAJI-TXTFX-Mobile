package com.saji.txtfx;

import java.util.ArrayList;
import java.util.List;

public final class SrtParser {
    private SrtParser() { }

    public static List<CaptionCue> parse(String raw) {
        List<CaptionCue> cues = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return cues;
        String normalized = raw.replace("\r", "").replace("\uFEFF", "");
        String[] blocks = normalized.split("\n\\s*\n");
        for (String block : blocks) {
            String[] lines = block.split("\n");
            int timingLine = -1;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains("-->")) {
                    timingLine = i;
                    break;
                }
            }
            if (timingLine < 0) continue;
            String[] times = lines[timingLine].split("-->");
            if (times.length != 2) continue;
            long start = parseTime(times[0].trim());
            long end = parseTime(times[1].trim().split("\\s+")[0]);
            if (end <= start) continue;
            StringBuilder text = new StringBuilder();
            for (int i = timingLine + 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                if (text.length() > 0) text.append('\n');
                text.append(line.replaceAll("<[^>]+>", ""));
            }
            if (text.length() > 0) cues.add(new CaptionCue(text.toString(), start, end));
        }
        return cues;
    }

    private static long parseTime(String value) {
        String cleaned = value.replace(',', '.');
        String[] parts = cleaned.split(":");
        if (parts.length != 3) return 0;
        try {
            double seconds = Double.parseDouble(parts[2]);
            long hours = Long.parseLong(parts[0]);
            long minutes = Long.parseLong(parts[1]);
            return (hours * 3600L + minutes * 60L) * 1000L + Math.round(seconds * 1000d);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}