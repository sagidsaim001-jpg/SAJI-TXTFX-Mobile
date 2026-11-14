package com.saji.txtfx;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public final class GifExporter {
    private static final int FPS = 12;
    private static final int FRAMES = 36;

    private GifExporter() { }

    public static void export(Context context, String text, int style, int color,
                              float size, int ratio, Typeface typeface,
                              Mp4Exporter.ResultCallback callback) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            boolean success = false;
            String message;
            try {
                File gif = render(app, text, style, color, size, ratio, typeface);
                Uri saved = publish(app, gif);
                gif.delete();
                success = saved != null;
                message = success
                        ? "Transparent GIF saved in Pictures/SAJI TXTFX"
                        : "GIF export failed while saving the file.";
            } catch (Exception e) {
                message = "GIF export failed: " + (e.getMessage() == null ? "Unknown error" : e.getMessage());
            }
            final boolean ok = success;
            final String result = message;
            new Handler(Looper.getMainLooper()).post(() -> callback.onComplete(result, ok));
        }, "SAJI-TXTFX-GIF").start();
    }

    private static File render(Context context, String text, int style, int color,
                               float size, int ratio, Typeface typeface) throws Exception {
        int width = ratio == 2 ? 640 : 360;
        int height = ratio == 1 ? 360 : (ratio == 2 ? 360 : 640);
        if (ratio == 1) width = 360;
        if (ratio == 0) { width = 360; height = 640; }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        File file = new File(context.getCacheDir(), "saji-txtfx-" + System.currentTimeMillis() + ".gif");
        try (OutputStream raw = new java.io.FileOutputStream(file)) {
            raw.write(new byte[]{'G','I','F','8','9','a'});
            writeShort(raw, width);
            writeShort(raw, height);
            raw.write(0xF7); // Global 256-color table.
            raw.write(0);    // Background index.
            raw.write(0);    // Pixel aspect ratio.

            // Index zero is transparent. The remaining table entries use the chosen text color.
            raw.write(0); raw.write(0); raw.write(0);
            int r = (color >> 16) & 255;
            int g = (color >> 8) & 255;
            int b = color & 255;
            for (int i = 1; i < 256; i++) {
                raw.write(r); raw.write(g); raw.write(b);
            }

            // Loop continuously.
            raw.write(new byte[]{0x21, (byte)0xFF, 0x0B});
            raw.write("NETSCAPE2.0".getBytes("US-ASCII"));
            raw.write(new byte[]{0x03, 0x01, 0x00, 0x00, 0x00});

            for (int frame = 0; frame < FRAMES; frame++) {
                TextFxView.drawFrame(canvas, width, height, frame * 1000L / FPS,
                        text, style, color, size, Color.TRANSPARENT, false, typeface);
                int[] pixels = new int[width * height];
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                byte[] indexes = new byte[pixels.length];
                for (int i = 0; i < pixels.length; i++) {
                    int alpha = (pixels[i] >>> 24) & 255;
                    indexes[i] = (byte)(alpha >= 112 ? 1 : 0);
                }

                // Graphic control extension: transparent index zero, 8/100 second frame delay.
                raw.write(new byte[]{0x21, (byte)0xF9, 0x04, 0x09, 0x08, 0x00, 0x00, 0x00});
                raw.write(0x2C);
                writeShort(raw, 0); writeShort(raw, 0);
                writeShort(raw, width); writeShort(raw, height);
                raw.write(0);
                raw.write(8); // LZW minimum code size.
                byte[] compressed = lzw(indexes);
                int offset = 0;
                while (offset < compressed.length) {
                    int count = Math.min(255, compressed.length - offset);
                    raw.write(count);
                    raw.write(compressed, offset, count);
                    offset += count;
                }
                raw.write(0); // End of image data blocks.
            }
            raw.write(0x3B); // GIF trailer.
        } catch (Exception e) {
            file.delete();
            throw e;
        } finally {
            bitmap.recycle();
        }
        return file;
    }

    private static byte[] lzw(byte[] pixels) throws Exception {
        final int clearCode = 256;
        final int endCode = 257;
        Map<Integer, Integer> dictionary = new HashMap<>();
        BitWriter bits = new BitWriter();
        int nextCode = 258;
        int codeSize = 9;
        bits.write(clearCode, codeSize);
        if (pixels.length == 0) {
            bits.write(endCode, codeSize);
            return bits.finish();
        }

        int prefix = pixels[0] & 255;
        for (int i = 1; i < pixels.length; i++) {
            int symbol = pixels[i] & 255;
            int key = (prefix << 8) | symbol;
            Integer combined = dictionary.get(key);
            if (combined != null) {
                prefix = combined;
            } else {
                bits.write(prefix, codeSize);
                if (nextCode < 4096) {
                    dictionary.put(key, nextCode++);
                    if (nextCode > (1 << codeSize) && codeSize < 12) codeSize++;
                } else {
                    bits.write(clearCode, codeSize);
                    dictionary.clear();
                    nextCode = 258;
                    codeSize = 9;
                }
                prefix = symbol;
            }
        }
        bits.write(prefix, codeSize);
        bits.write(endCode, codeSize);
        return bits.finish();
    }

    private static void writeShort(OutputStream out, int value) throws Exception {
        out.write(value & 255);
        out.write((value >> 8) & 255);
    }

    private static Uri publish(Context context, File source) throws Exception {
        String name = "SAJI_TXTFX_" + System.currentTimeMillis() + ".gif";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/gif");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SAJI TXTFX");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("Could not create a GIF in Pictures.");
        try (FileInputStream input = new FileInputStream(source);
             OutputStream output = context.getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IllegalStateException("Could not write the GIF file.");
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            context.getContentResolver().update(uri, done, null, null);
            return uri;
        } catch (Exception e) {
            context.getContentResolver().delete(uri, null, null);
            throw e;
        }
    }

    private static final class BitWriter {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private int accumulator = 0;
        private int bitCount = 0;

        void write(int code, int size) {
            accumulator |= code << bitCount;
            bitCount += size;
            while (bitCount >= 8) {
                output.write(accumulator & 255);
                accumulator >>>= 8;
                bitCount -= 8;
            }
        }

        byte[] finish() {
            if (bitCount > 0) output.write(accumulator & 255);
            return output.toByteArray();
        }
    }
}
