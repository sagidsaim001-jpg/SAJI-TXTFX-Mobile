package com.saji.txtfx;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public final class Mp4Exporter {
    public interface ResultCallback {
        void onComplete(String message, boolean success);
    }

    private static final int FPS = 24;
    private static final int DURATION_SECONDS = 3;

    private Mp4Exporter() { }

    public static void export(Context context, String text, int style, int color,
                              float size, int ratio, ResultCallback callback) {
        export(context, text, style, color, size, ratio,
                Typeface.create("sans-serif", Typeface.BOLD), callback);
    }

    public static void export(Context context, String text, int style, int color,
                              float size, int ratio, Typeface typeface, ResultCallback callback) {
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            String message;
            boolean success;
            try {
                File output = renderVideo(text, style, color, size, ratio, typeface);
                Uri saved = saveToMovies(appContext, output);
                output.delete();
                message = "Video saved in Movies/SAJI TXTFX";
                success = saved != null;
            } catch (Exception e) {
                message = "Export failed: " + (e.getMessage() == null ? "Video encoder error" : e.getMessage());
                success = false;
            }
            final String resultMessage = message;
            final boolean resultSuccess = success;
            new Handler(Looper.getMainLooper()).post(() -> callback.onComplete(resultMessage, resultSuccess));
        }, "SAJI-TXTFX-Export").start();
    }

    private static File renderVideo(String text, int style, int color, float size,
                                    int ratio, Typeface typeface) throws Exception {
        int width = ratio == 2 ? 1280 : 720;
        int height = ratio == 1 ? 720 : (ratio == 2 ? 720 : 1280);
        if (ratio == 1) width = 720;
        if (ratio == 0) { width = 720; height = 1280; }

        CodecChoice choice = findEncoder();
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, choice.colorFormat);
        format.setInteger(MediaFormat.KEY_BIT_RATE, ratio == 2 ? 5_000_000 : 4_000_000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        MediaCodec codec = MediaCodec.createByCodecName(choice.name);
        File temp = new File(new File(System.getProperty("java.io.tmpdir")), "saji-txtfx-" + System.currentTimeMillis() + ".mp4");
        MediaMuxer muxer = null;
        boolean codecStarted = false;
        boolean muxerStarted = false;
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            codecStarted = true;
            muxer = new MediaMuxer(temp.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            MuxerState state = new MuxerState();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            int totalFrames = FPS * DURATION_SECONDS;

            for (int frame = 0; frame < totalFrames; frame++) {
                long timeMs = frame * 1000L / FPS;
                TextFxView.drawFrame(canvas, width, height, timeMs, text, style,
                        color, size, Color.rgb(0, 255, 0), false, typeface);
                byte[] frameBytes = convertToYuv420(bitmap, choice.colorFormat);
                int inputIndex = codec.dequeueInputBuffer(10_000);
                if (inputIndex < 0) throw new IllegalStateException("Video encoder is busy. Try again.");
                ByteBuffer input = codec.getInputBuffer(inputIndex);
                if (input == null || input.capacity() < frameBytes.length) {
                    throw new IllegalStateException("Encoder input buffer is too small.");
                }
                input.clear();
                input.put(frameBytes);
                codec.queueInputBuffer(inputIndex, 0, frameBytes.length,
                        frame * 1_000_000L / FPS, 0);
                drain(codec, muxer, info, state, false);
            }

            int eosIndex = codec.dequeueInputBuffer(10_000);
            if (eosIndex < 0) throw new IllegalStateException("Could not finish video stream.");
            codec.queueInputBuffer(eosIndex, 0, 0, totalFrames * 1_000_000L / FPS,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);

            boolean done = false;
            int attempts = 0;
            while (!done && attempts++ < 60) {
                done = drain(codec, muxer, info, state, true);
            }
            if (!done) throw new IllegalStateException("Video encoder did not finish.");
            if (!state.started) throw new IllegalStateException("This device did not start an MP4 encoder.");
            muxerStarted = true;
            bitmap.recycle();
            return temp;
        } catch (Exception e) {
            temp.delete();
            throw e;
        } finally {
            try { if (codecStarted) codec.stop(); } catch (Exception ignored) { }
            try { codec.release(); } catch (Exception ignored) { }
            if (muxer != null) {
                try { if (muxerStarted) muxer.stop(); } catch (Exception ignored) { }
                try { muxer.release(); } catch (Exception ignored) { }
            }
        }
    }

    private static boolean drain(MediaCodec codec, MediaMuxer muxer,
                                 MediaCodec.BufferInfo info, MuxerState state,
                                 boolean endOfStream) throws Exception {
        int idle = 0;
        while (true) {
            int outputIndex = codec.dequeueOutputBuffer(info, endOfStream ? 10_000 : 1_000);
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream || ++idle > 8) return false;
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (state.started) throw new IllegalStateException("Encoder output format changed twice.");
                state.track = muxer.addTrack(codec.getOutputFormat());
                muxer.start();
                state.started = true;
            } else if (outputIndex >= 0) {
                ByteBuffer output = codec.getOutputBuffer(outputIndex);
                if (output == null) {
                    codec.releaseOutputBuffer(outputIndex, false);
                    continue;
                }
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                if (info.size > 0) {
                    if (!state.started) throw new IllegalStateException("MP4 muxer was not ready.");
                    output.position(info.offset);
                    output.limit(info.offset + info.size);
                    muxer.writeSampleData(state.track, output, info);
                }
                boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                codec.releaseOutputBuffer(outputIndex, false);
                if (eos) return true;
            }
        }
    }

    private static byte[] convertToYuv420(Bitmap bitmap, int colorFormat) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int frameSize = width * height;
        int[] pixels = new int[frameSize];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        boolean semiPlanar = colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
        byte[] yuv = new byte[frameSize * 3 / 2];
        int yIndex = 0;
        int uvIndex = frameSize;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = pixels[y * width + x];
                int r = (argb >> 16) & 255;
                int g = (argb >> 8) & 255;
                int b = argb & 255;
                int yy = clamp(((66 * r + 129 * g + 25 * b + 128) >> 8) + 16);
                yuv[yIndex++] = (byte) yy;
                if ((y & 1) == 0 && (x & 1) == 0) {
                    int u = clamp(((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128);
                    int v = clamp(((112 * r - 94 * g - 18 * b + 128) >> 8) + 128);
                    if (semiPlanar) {
                        yuv[uvIndex++] = (byte)u;
                        yuv[uvIndex++] = (byte)v;
                    } else {
                        int chromaIndex = (y / 2) * (width / 2) + (x / 2);
                        yuv[frameSize + chromaIndex] = (byte)u;
                        yuv[frameSize + frameSize / 4 + chromaIndex] = (byte)v;
                    }
                }
            }
        }
        return yuv;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static CodecChoice findEncoder() throws Exception {
        MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo info : list.getCodecInfos()) {
            if (!info.isEncoder()) continue;
            for (String type : info.getSupportedTypes()) {
                if (!"video/avc".equalsIgnoreCase(type)) continue;
                int[] formats = info.getCapabilitiesForType(type).colorFormats;
                for (int preferred : new int[]{
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar}) {
                    for (int available : formats) {
                        if (available == preferred) return new CodecChoice(info.getName(), preferred);
                    }
                }
            }
        }
        throw new IllegalStateException("No compatible H.264 YUV420 encoder found on this phone.");
    }

    private static Uri saveToMovies(Context context, File source) throws Exception {
        String name = "SAJI_TXTFX_" + System.currentTimeMillis() + ".mp4";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SAJI TXTFX");
        values.put(MediaStore.Video.Media.IS_PENDING, 1);
        Uri uri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("Could not create a file in Movies.");
        try (FileInputStream input = new FileInputStream(source);
             OutputStream output = context.getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IllegalStateException("Could not write the exported video.");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.flush();
            ContentValues publish = new ContentValues();
            publish.put(MediaStore.Video.Media.IS_PENDING, 0);
            context.getContentResolver().update(uri, publish, null, null);
            return uri;
        } catch (Exception e) {
            context.getContentResolver().delete(uri, null, null);
            throw e;
        }
    }

    private static final class CodecChoice {
        final String name;
        final int colorFormat;
        CodecChoice(String n, int f) { name = n; colorFormat = f; }
    }

    private static final class MuxerState {
        int track = -1;
        boolean started = false;
    }
}
