package com.saji.txtfx;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;
import androidx.media3.effect.OverlayEffect;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@UnstableApi
public class MainActivity extends Activity {
    private static final int REQUEST_VIDEO = 1001;
    private static final int REQUEST_SRT = 1002;
    private static final int REQUEST_SAVE_PROJECT = 1003;
    private static final int REQUEST_OPEN_PROJECT = 1004;

    private final ArrayList<CaptionCue> cues = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int[] colors = {
            Color.WHITE, Color.YELLOW, Color.CYAN, Color.rgb(255, 110, 145),
            Color.MAGENTA, Color.rgb(130, 255, 175), Color.rgb(255, 165, 75),
            Color.rgb(170, 160, 255)
    };
    private final String[] animations = {
            "Fade", "Rise", "Slide", "Zoom", "Bounce",
            "Typewriter", "Pulse", "Glitch", "Flip", "Glow"
    };
    private final String[] fontLabels = {"Bold Sans", "Serif", "Mono", "Light Sans"};
    private final String[] fontValues = {"sans-serif", "serif", "monospace", "sans-serif-light"};

    private ExoPlayer player;
    private PlayerView playerView;
    private FrameLayout previewFrame;
    private TextView captionPreview;
    private TextView videoName;
    private TextView playbackTime;
    private TextView exportStatus;
    private TextView selectedCaptionLabel;
    private LinearLayout cueList;
    private EditText captionEditor;
    private EditText startEditor;
    private EditText endEditor;
    private SeekBar timelineSeek;
    private SeekBar sizeSeek;
    private SeekBar positionSeek;
    private TextView sizeValue;
    private TextView positionValue;
    private Spinner animationSpinner;
    private Spinner fontSpinner;
    private Button playButton;
    private Button exportButton;
    private ProgressBar exportProgress;
    private View colorRow;
    private Uri videoUri;
    private String sourceName = "No video selected";
    private int selectedIndex = -1;
    private boolean loadingEditor;
    private boolean userSeeking;
    private Transformer activeTransformer;
    private File activeOutputFile;
    private final ProgressHolder progressHolder = new ProgressHolder();

    private final Runnable playbackTicker = new Runnable() {
        @Override public void run() {
            if (player != null) {
                long position = Math.max(0, player.getCurrentPosition());
                long duration = Math.max(1, player.getDuration());
                if (!userSeeking) timelineSeek.setProgress((int)Math.min(1000, position * 1000L / duration));
                playbackTime.setText(formatTime(position) + " / " + (player.getDuration() == C.TIME_UNSET
                        ? "00:00" : formatTime(duration)));
                playButton.setText(player.isPlaying() ? "PAUSE" : "PLAY");
                updatePreviewCaption(position);
            }
            handler.postDelayed(this, 180);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(8, 10, 18));
        getWindow().setNavigationBarColor(Color.rgb(8, 10, 18));
        buildEditor();
        startPlayerTicker();
        restoreLastProject();
    }

    private void buildEditor() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8, 10, 18));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(15), dp(16), dp(28));
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("FX", 18, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(round(Color.rgb(111, 82, 244), 15));
        header.addView(logo, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(11), 0, 0, 0);
        brand.addView(text("SAJI TXTFX", 19, Color.WHITE, true));
        brand.addView(text("VIDEO CAPTION STUDIO", 10, Color.rgb(150, 158, 188), true));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(text("16GB READY", 10, Color.rgb(85, 231, 211), true));
        page.addView(header);
        space(page, 18);

        page.addView(section("VIDEO PROJECT", "Import your own footage and edit over the original video"));
        LinearLayout importRow = new LinearLayout(this);
        importRow.setOrientation(LinearLayout.HORIZONTAL);
        Button importVideo = button("IMPORT VIDEO", true);
        Button loadProject = button("OPEN PROJECT", false);
        addWeighted(importRow, importVideo);
        addWeighted(importRow, loadProject);
        page.addView(importRow);
        videoName = text(sourceName, 11, Color.rgb(145, 154, 184), false);
        videoName.setPadding(0, dp(7), 0, dp(8));
        page.addView(videoName);

        previewFrame = new FrameLayout(this);
        previewFrame.setBackground(round(Color.rgb(16, 19, 31), 18));
        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        previewFrame.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        captionPreview = text("IMPORT A VIDEO TO START", 18, Color.WHITE, true);
        captionPreview.setGravity(Gravity.CENTER);
        captionPreview.setPadding(dp(18), dp(12), dp(18), dp(12));
        captionPreview.setShadowLayer(dp(5), 0, dp(2), Color.BLACK);
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        previewFrame.addView(captionPreview, overlayParams);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(255));
        previewParams.topMargin = dp(4);
        page.addView(previewFrame, previewParams);

        LinearLayout transport = new LinearLayout(this);
        transport.setGravity(Gravity.CENTER_VERTICAL);
        playButton = button("PLAY", true);
        transport.addView(playButton, new LinearLayout.LayoutParams(dp(78), dp(42)));
        playbackTime = text("00:00 / 00:00", 11, Color.rgb(188, 196, 216), true);
        playbackTime.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        transport.addView(playbackTime, new LinearLayout.LayoutParams(0, dp(42), 1));
        page.addView(transport);
        timelineSeek = new SeekBar(this);
        timelineSeek.setMax(1000);
        timelineSeek.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(119, 91, 246)));
        timelineSeek.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.rgb(76, 230, 208)));
        page.addView(timelineSeek);

        LinearLayout projectActions = new LinearLayout(this);
        Button importSrt = button("IMPORT SRT", false);
        Button saveProject = button("SAVE PROJECT", false);
        addWeighted(projectActions, importSrt);
        addWeighted(projectActions, saveProject);
        page.addView(projectActions);
        space(page, 20);

        LinearLayout captionsHeader = new LinearLayout(this);
        captionsHeader.setGravity(Gravity.CENTER_VERTICAL);
        captionsHeader.addView(section("CAPTION TIMELINE", "Each caption has its own timing and style"),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button addCue = button("+ ADD", true);
        captionsHeader.addView(addCue, new LinearLayout.LayoutParams(dp(84), dp(40)));
        page.addView(captionsHeader);

        cueList = new LinearLayout(this);
        cueList.setOrientation(LinearLayout.VERTICAL);
        cueList.setPadding(0, dp(8), 0, dp(4));
        page.addView(cueList);
        space(page, 14);

        page.addView(section("CAPTION EDITOR", "Select a caption above, then customize it"));
        selectedCaptionLabel = text("NO CAPTION SELECTED", 10, Color.rgb(80, 230, 210), true);
        selectedCaptionLabel.setPadding(0, dp(8), 0, dp(7));
        page.addView(selectedCaptionLabel);

        captionEditor = new EditText(this);
        captionEditor.setHint("Type your caption here...");
        captionEditor.setTextColor(Color.WHITE);
        captionEditor.setHintTextColor(Color.rgb(110, 119, 148));
        captionEditor.setTextSize(16);
        captionEditor.setGravity(Gravity.TOP | Gravity.START);
        captionEditor.setMinLines(2);
        captionEditor.setMaxLines(4);
        captionEditor.setPadding(dp(13), dp(12), dp(13), dp(12));
        captionEditor.setBackground(round(Color.rgb(20, 24, 40), 13));
        page.addView(captionEditor, new LinearLayout.LayoutParams(-1, dp(100)));
        captionEditor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                CaptionCue cue = getSelectedCue();
                if (!loadingEditor && cue != null) {
                    cue.text = s.toString();
                    updatePreviewCaption(currentPosition());
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        LinearLayout timingRow = new LinearLayout(this);
        timingRow.setGravity(Gravity.CENTER_VERTICAL);
        startEditor = timeInput("Start seconds");
        endEditor = timeInput("End seconds");
        timingRow.addView(fieldColumn("START (SEC)", startEditor), new LinearLayout.LayoutParams(0, -2, 1));
        timingRow.addView(fieldColumn("END (SEC)", endEditor), new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(timingRow);
        Button applyTiming = button("APPLY TIMING", false);
        LinearLayout.LayoutParams applyTimingParams = new LinearLayout.LayoutParams(-1, dp(42));
        applyTimingParams.topMargin = dp(8);
        page.addView(applyTiming, applyTimingParams);

        LinearLayout duplicateDelete = new LinearLayout(this);
        Button duplicate = button("DUPLICATE", false);
        Button delete = button("DELETE", false);
        addWeighted(duplicateDelete, duplicate);
        addWeighted(duplicateDelete, delete);
        page.addView(duplicateDelete);
        space(page, 16);

        page.addView(section("ANIMATION PRESETS", "10 motion styles, applied separately to each caption"));
        animationSpinner = spinner(animations);
        page.addView(animationSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        animationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                CaptionCue cue = getSelectedCue();
                if (!loadingEditor && cue != null) {
                    cue.animation = animations[position];
                    updatePreviewCaption(currentPosition());
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        space(page, 14);

        page.addView(section("FONT", "Choose the caption typeface"));
        fontSpinner = spinner(fontLabels);
        page.addView(fontSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        fontSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                CaptionCue cue = getSelectedCue();
                if (!loadingEditor && cue != null) {
                    cue.font = fontValues[position];
                    updatePreviewCaption(currentPosition());
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        space(page, 12);

        LinearLayout sizeHeader = labelValue("TEXT SIZE", "48");
        sizeValue = (TextView) sizeHeader.getChildAt(1);
        page.addView(sizeHeader);
        sizeSeek = new SeekBar(this);
        sizeSeek.setMax(72);
        styleSeek(sizeSeek);
        sizeSeek.setProgress(24);
        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                float newSize = 24 + value;
                sizeValue.setText(String.valueOf((int)newSize));
                CaptionCue cue = getSelectedCue();
                if (fromUser && cue != null) {
                    cue.size = newSize;
                    updatePreviewCaption(currentPosition());
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        page.addView(sizeSeek);

        LinearLayout positionHeader = labelValue("VERTICAL POSITION", "78%");
        positionValue = (TextView) positionHeader.getChildAt(1);
        page.addView(positionHeader);
        positionSeek = new SeekBar(this);
        positionSeek.setMax(80);
        styleSeek(positionSeek);
        positionSeek.setProgress(58);
        positionSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                int position = 10 + value;
                positionValue.setText(position + "%");
                CaptionCue cue = getSelectedCue();
                if (fromUser && cue != null) {
                    cue.verticalPosition = position;
                    updatePreviewCaption(currentPosition());
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        page.addView(positionSeek);
        space(page, 12);

        page.addView(section("TEXT COLORS", "Tap a swatch to style the selected caption"));
        HorizontalScrollView colorScroll = new HorizontalScrollView(this);
        colorScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout swatches = new LinearLayout(this);
        swatches.setGravity(Gravity.CENTER_VERTICAL);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        for (int color : colors) {
            View swatch = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(color);
            shape.setStroke(dp(1), Color.rgb(85, 91, 118));
            swatch.setBackground(shape);
            swatch.setOnClickListener(v -> {
                CaptionCue cue = getSelectedCue();
                if (cue != null) {
                    cue.color = color;
                    updateColorSelection(swatches, color);
                    updatePreviewCaption(currentPosition());
                }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(32), dp(32));
            p.setMargins(0, dp(10), dp(12), dp(10));
            swatches.addView(swatch, p);
        }
        colorScroll.addView(swatches);
        colorRow = swatches;
        page.addView(colorScroll);
        space(page, 18);

        exportButton = button("EXPORT FINAL MP4", true);
        page.addView(exportButton, new LinearLayout.LayoutParams(-1, dp(54)));
        exportStatus = text("Original audio stays with your video. Captions are burned into the exported MP4.", 11,
                Color.rgb(148, 157, 185), false);
        exportStatus.setPadding(0, dp(8), 0, dp(6));
        page.addView(exportStatus);
        exportProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        exportProgress.setMax(100);
        exportProgress.setProgress(0);
        page.addView(exportProgress, new LinearLayout.LayoutParams(-1, dp(5)));
        TextView footer = text("SAJI TXTFX  •  MOBILE VIDEO EDITOR", 10, Color.rgb(95, 103, 132), true);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(20), 0, 0);
        page.addView(footer);

        importVideo.setOnClickListener(v -> chooseVideo());
        loadProject.setOnClickListener(v -> chooseProject());
        importSrt.setOnClickListener(v -> chooseSrt());
        saveProject.setOnClickListener(v -> saveProjectAs());
        addCue.setOnClickListener(v -> addCaption());
        playButton.setOnClickListener(v -> togglePlayback());
        applyTiming.setOnClickListener(v -> applyTiming());
        duplicate.setOnClickListener(v -> duplicateCaption());
        delete.setOnClickListener(v -> deleteCaption());
        exportButton.setOnClickListener(v -> exportVideo());
        timelineSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) userSeeking = true;
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (player != null && player.getDuration() > 0) {
                    player.seekTo(player.getDuration() * bar.getProgress() / 1000L);
                }
                userSeeking = false;
            }
        });

        setContentView(root);
        initializePlayer();
        refreshCueList();
        updateEditorFields();
    }

    private void initializePlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                playButton.setText(isPlaying ? "PAUSE" : "PLAY");
            }
        });
    }

    private void startPlayerTicker() {
        handler.removeCallbacks(playbackTicker);
        handler.post(playbackTicker);
    }

    private void chooseVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_VIDEO);
    }

    private void chooseSrt() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/x-subrip", "text/plain", "application/octet-stream"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_SRT);
    }

    private void chooseProject() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_OPEN_PROJECT);
    }

    private void saveProjectAs() {
        if (videoUri == null) {
            toast("Pehle video import karo");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "SAJI-TXTFX-Project.sajitxtfx");
        startActivityForResult(intent, REQUEST_SAVE_PROJECT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQUEST_VIDEO) {
                persistReadPermission(uri, data);
                loadVideo(uri);
            } else if (requestCode == REQUEST_SRT) {
                persistReadPermission(uri, data);
                importSrt(uri);
            } else if (requestCode == REQUEST_SAVE_PROJECT) {
                saveProject(uri);
            } else if (requestCode == REQUEST_OPEN_PROJECT) {
                persistReadPermission(uri, data);
                loadProject(uri);
            }
        } catch (Exception e) {
            toast("File open nahi hui: " + safeMessage(e));
        }
    }

    private void persistReadPermission(Uri uri, Intent data) {
        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) { }
    }

    private void loadVideo(Uri uri) {
        videoUri = uri;
        sourceName = queryDisplayName(uri);
        videoName.setText(sourceName);
        player.stop();
        player.clearMediaItems();
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();
        player.seekTo(0);
        player.setPlayWhenReady(false);
        updatePreviewCaption(0);
        persistCurrentProject();
        toast("Video ready. Ab captions add karo.");
    }

    private void importSrt(Uri uri) throws Exception {
        String raw = readText(uri);
        List<CaptionCue> imported = SrtParser.parse(raw);
        if (imported.isEmpty()) {
            toast("SRT file mein valid captions nahi mile");
            return;
        }
        cues.addAll(imported);
        Collections.sort(cues, (a, b) -> Long.compare(a.startMs, b.startMs));
        selectedIndex = 0;
        refreshCueList();
        updateEditorFields();
        persistCurrentProject();
        toast(imported.size() + " captions import ho gaye");
    }

    private void addCaption() {
        if (videoUri == null) {
            toast("Pehle video import karo");
            return;
        }
        long start = currentPosition();
        long duration = player.getDuration();
        long end = start + 2500;
        if (duration > 0) end = Math.min(end, duration);
        if (end <= start) end = start + 1500;
        CaptionCue cue = new CaptionCue("Your caption here", start, end);
        cues.add(cue);
        selectedIndex = cues.size() - 1;
        refreshCueList();
        updateEditorFields();
        player.pause();
        persistCurrentProject();
    }

    private void selectCaption(int index) {
        if (index < 0 || index >= cues.size()) return;
        selectedIndex = index;
        CaptionCue cue = cues.get(index);
        if (player != null) player.seekTo(cue.startMs);
        refreshCueList();
        updateEditorFields();
        updatePreviewCaption(cue.startMs);
    }

    private void duplicateCaption() {
        CaptionCue cue = getSelectedCue();
        if (cue == null) return;
        CaptionCue copy = new CaptionCue(cue.text, cue.startMs, cue.endMs);
        copy.color = cue.color;
        copy.size = cue.size;
        copy.verticalPosition = cue.verticalPosition;
        copy.animation = cue.animation;
        copy.font = cue.font;
        long offset = Math.max(500, cue.endMs - cue.startMs);
        copy.startMs = cue.endMs;
        copy.endMs = cue.endMs + offset;
        cues.add(copy);
        selectedIndex = cues.size() - 1;
        refreshCueList();
        updateEditorFields();
        persistCurrentProject();
    }

    private void deleteCaption() {
        if (selectedIndex < 0 || selectedIndex >= cues.size()) return;
        cues.remove(selectedIndex);
        selectedIndex = cues.isEmpty() ? -1 : Math.min(selectedIndex, cues.size() - 1);
        refreshCueList();
        updateEditorFields();
        updatePreviewCaption(currentPosition());
        persistCurrentProject();
    }

    private void applyTiming() {
        CaptionCue cue = getSelectedCue();
        if (cue == null) return;
        try {
            long start = parseSeconds(startEditor.getText().toString());
            long end = parseSeconds(endEditor.getText().toString());
            if (start < 0 || end <= start) {
                toast("End time, start time se aage honi chahiye");
                return;
            }
            cue.startMs = start;
            cue.endMs = end;
            Collections.sort(cues, (a, b) -> Long.compare(a.startMs, b.startMs));
            selectedIndex = cues.indexOf(cue);
            refreshCueList();
            updateEditorFields();
            updatePreviewCaption(currentPosition());
            persistCurrentProject();
            toast("Caption timing updated");
        } catch (NumberFormatException e) {
            toast("Time seconds mein likho, jaise 3.5");
        }
    }

    private void refreshCueList() {
        cueList.removeAllViews();
        if (cues.isEmpty()) {
            TextView empty = text("No captions yet. Tap + ADD or import an SRT file.", 12,
                    Color.rgb(130, 139, 168), false);
            empty.setPadding(dp(12), dp(14), dp(12), dp(14));
            empty.setBackground(round(Color.rgb(19, 23, 38), 12));
            cueList.addView(empty);
            return;
        }
        for (int i = 0; i < cues.size(); i++) {
            final int index = i;
            CaptionCue cue = cues.get(i);
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(11), dp(9), dp(11), dp(9));
            item.setBackground(round(i == selectedIndex ? Color.rgb(42, 38, 75) : Color.rgb(18, 22, 36), 12));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView label = text(String.format(Locale.US, "%02d  %s", i + 1,
                    cue.text.replace('\n', ' ')), 13, Color.WHITE, true);
            TextView time = text(formatTime(cue.startMs) + "  →  " + formatTime(cue.endMs)
                    + "    •    " + cue.animation, 10, Color.rgb(145, 155, 184), false);
            info.addView(label);
            info.addView(time);
            item.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            TextView edit = text(i == selectedIndex ? "EDITING" : "EDIT", 10,
                    i == selectedIndex ? Color.rgb(83, 235, 210) : Color.rgb(176, 183, 206), true);
            item.addView(edit);
            item.setOnClickListener(v -> selectCaption(index));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(6);
            cueList.addView(item, params);
        }
    }

    private void updateEditorFields() {
        CaptionCue cue = getSelectedCue();
        loadingEditor = true;
        if (cue == null) {
            selectedCaptionLabel.setText("NO CAPTION SELECTED");
            captionEditor.setText("");
            startEditor.setText("");
            endEditor.setText("");
            sizeValue.setText("48");
            positionValue.setText("78%");
            loadingEditor = false;
            return;
        }
        selectedCaptionLabel.setText("EDITING CAPTION " + (selectedIndex + 1) + " OF " + cues.size());
        captionEditor.setText(cue.text);
        startEditor.setText(String.format(Locale.US, "%.2f", cue.startMs / 1000d));
        endEditor.setText(String.format(Locale.US, "%.2f", cue.endMs / 1000d));
        sizeSeek.setProgress(Math.max(0, Math.min(72, Math.round(cue.size - 24))));
        positionSeek.setProgress(Math.max(0, Math.min(80, cue.verticalPosition - 10)));
        sizeValue.setText(String.valueOf((int)cue.size));
        positionValue.setText(cue.verticalPosition + "%");
        animationSpinner.setSelection(indexOf(animations, cue.animation));
        fontSpinner.setSelection(indexOf(fontValues, cue.font));
        updateColorSelection((LinearLayout) colorRow, cue.color);
        loadingEditor = false;
    }

    private void updateColorSelection(LinearLayout row, int selectedColor) {
        for (int i = 0; i < row.getChildCount(); i++) {
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(colors[i]);
            shape.setStroke(dp(colors[i] == selectedColor ? 3 : 1),
                    colors[i] == selectedColor ? Color.rgb(75, 231, 210) : Color.rgb(85, 91, 118));
            row.getChildAt(i).setBackground(shape);
        }
    }

    private void updatePreviewCaption(long positionMs) {
        CaptionCue active = null;
        for (CaptionCue cue : cues) {
            if (positionMs >= cue.startMs && positionMs < cue.endMs) {
                active = cue;
                break;
            }
        }
        if (active == null) active = getSelectedCue();
        if (active == null || active.text.trim().isEmpty()) {
            captionPreview.setText(videoUri == null ? "IMPORT A VIDEO TO START" : "ADD A CAPTION");
            captionPreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            captionPreview.setTextColor(Color.WHITE);
            captionPreview.setTypeface(Typeface.DEFAULT_BOLD);
            captionPreview.setTranslationY(0);
            return;
        }
        captionPreview.setText(active.text);
        captionPreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(15, active.size * 0.48f));
        captionPreview.setTextColor(active.color);
        captionPreview.setTypeface(active.getTypeface());
        captionPreview.setShadowLayer(dp(active.animation.equals("Glow") ? 9 : 5), 0, dp(2), Color.BLACK);
        captionPreview.setTranslationY((active.verticalPosition / 100f - 0.5f) * previewFrame.getHeight() * 0.76f);
        captionPreview.setAlpha(active.animation.equals("Fade")
                ? Math.max(0.2f, Math.min(1f, (positionMs - active.startMs) / 300f)) : 1f);
    }

    private void togglePlayback() {
        if (videoUri == null || player == null) {
            toast("Pehle video import karo");
            return;
        }
        if (player.isPlaying()) player.pause();
        else player.play();
    }

    private void exportVideo() {
        if (videoUri == null) {
            toast("Pehle video import karo");
            return;
        }
        if (cues.isEmpty()) {
            toast("Export se pehle captions add karo");
            return;
        }
        if (activeTransformer != null) {
            toast("Export already running");
            return;
        }

        hideKeyboard();
        player.pause();
        exportButton.setEnabled(false);
        exportButton.setText("RENDERING VIDEO...");
        exportProgress.setProgress(0);
        exportStatus.setText("Preparing your video and caption layers...");
        File output = new File(getCacheDir(), "saji-caption-export-" + System.currentTimeMillis() + ".mp4");
        activeOutputFile = output;

        try {
            CaptionOverlay captionOverlay = new CaptionOverlay(new ArrayList<>(cues));
            OverlayEffect overlayEffect = new OverlayEffect(Collections.singletonList(captionOverlay));
            Effects effects = new Effects(Collections.emptyList(), Collections.singletonList(overlayEffect));
            EditedMediaItem editedItem = new EditedMediaItem.Builder(MediaItem.fromUri(videoUri))
                    .setEffects(effects)
                    .build();

            activeTransformer = new Transformer.Builder(this)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(new Transformer.Listener() {
                        @Override
                        public void onCompleted(Composition composition, ExportResult exportResult) {
                            finishExportSuccess(output);
                        }

                        @Override
                        public void onError(Composition composition, ExportResult exportResult,
                                           ExportException exportException) {
                            finishExportFailure(output, safeMessage(exportException));
                        }
                    })
                    .build();
            activeTransformer.start(editedItem, output.getAbsolutePath());
            handler.post(exportProgressTicker);
        } catch (Exception e) {
            finishExportFailure(output, safeMessage(e));
        }
    }

    private final Runnable exportProgressTicker = new Runnable() {
        @Override public void run() {
            if (activeTransformer == null) return;
            try {
                int state = activeTransformer.getProgress(progressHolder);
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    int value = Math.max(0, Math.min(100, progressHolder.progress));
                    exportProgress.setProgress(value);
                    exportStatus.setText("Rendering captions into MP4... " + value + "%");
                } else {
                    exportStatus.setText("Rendering video. Keep SAJI TXTFX open...");
                }
            } catch (Exception ignored) { }
            if (activeTransformer != null) handler.postDelayed(this, 450);
        }
    };

    private void finishExportSuccess(File output) {
        handler.removeCallbacks(exportProgressTicker);
        activeTransformer = null;
        exportStatus.setText("Finishing file...");
        new Thread(() -> {
            String message;
            boolean success;
            try {
                Uri saved = publishVideo(output);
                success = saved != null;
                message = success ? "Final MP4 saved in Movies/SAJI TXTFX" : "Video save failed";
            } catch (Exception e) {
                success = false;
                message = "Save failed: " + safeMessage(e);
            }
            output.delete();
            final boolean ok = success;
            final String finalMessage = message;
            runOnUiThread(() -> {
                exportButton.setEnabled(true);
                exportButton.setText("EXPORT FINAL MP4");
                exportProgress.setProgress(ok ? 100 : 0);
                exportStatus.setText(finalMessage);
                toast(finalMessage);
                activeOutputFile = null;
            });
        }, "SAJI-TXTFX-Publish").start();
    }

    private void finishExportFailure(File output, String reason) {
        handler.removeCallbacks(exportProgressTicker);
        if (output != null) output.delete();
        activeTransformer = null;
        runOnUiThread(() -> {
            exportButton.setEnabled(true);
            exportButton.setText("EXPORT FINAL MP4");
            exportProgress.setProgress(0);
            exportStatus.setText("Export failed: " + reason);
            toast("Export failed: " + reason);
            activeOutputFile = null;
        });
    }

    private Uri publishVideo(File source) throws Exception {
        String name = "SAJI_TXTFX_" + System.currentTimeMillis() + ".mp4";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SAJI TXTFX");
        values.put(MediaStore.Video.Media.IS_PENDING, 1);
        Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("Could not create output file");
        try (FileInputStream input = new FileInputStream(source);
             OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IllegalStateException("Cannot write output file");
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
            ContentValues done = new ContentValues();
            done.put(MediaStore.Video.Media.IS_PENDING, 0);
            getContentResolver().update(uri, done, null, null);
            return uri;
        } catch (Exception e) {
            getContentResolver().delete(uri, null, null);
            throw e;
        }
    }

    private JSONObject makeProjectJson() throws Exception {
        JSONObject project = new JSONObject();
        project.put("format", "SAJI_TXTFX_PROJECT");
        project.put("version", 1);
        project.put("videoUri", videoUri == null ? "" : videoUri.toString());
        project.put("sourceName", sourceName);
        JSONArray array = new JSONArray();
        for (CaptionCue cue : cues) array.put(cue.toJson());
        project.put("captions", array);
        return project;
    }

    private void saveProject(Uri target) throws Exception {
        JSONObject project = makeProjectJson();
        try (OutputStream output = getContentResolver().openOutputStream(target)) {
            if (output == null) throw new IllegalStateException("Project file could not be created");
            output.write(project.toString(2).getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
        toast("Project saved. Video and captions settings are stored.");
    }

    private void loadProject(Uri uri) throws Exception {
        JSONObject project = new JSONObject(readText(uri));
        String storedVideo = project.optString("videoUri", "");
        if (storedVideo.isEmpty()) throw new IllegalArgumentException("Project has no linked video");
        videoUri = Uri.parse(storedVideo);
        sourceName = project.optString("sourceName", queryDisplayName(videoUri));
        cues.clear();
        JSONArray array = project.optJSONArray("captions");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) cues.add(CaptionCue.fromJson(item));
            }
        }
        videoName.setText(sourceName + "  •  PROJECT");
        player.stop();
        player.clearMediaItems();
        player.setMediaItem(MediaItem.fromUri(videoUri));
        player.prepare();
        player.seekTo(0);
        selectedIndex = cues.isEmpty() ? -1 : 0;
        refreshCueList();
        updateEditorFields();
        persistCurrentProject();
        toast("Project loaded");
    }

    private void persistCurrentProject() {
        try {
            if (videoUri == null) return;
            getPreferences(MODE_PRIVATE).edit()
                    .putString("lastProject", makeProjectJson().toString())
                    .apply();
        } catch (Exception ignored) { }
    }

    private void restoreLastProject() {
        String raw = getPreferences(MODE_PRIVATE).getString("lastProject", null);
        if (raw == null) return;
        try {
            JSONObject project = new JSONObject(raw);
            String storedVideo = project.optString("videoUri", "");
            if (storedVideo.isEmpty()) return;
            videoUri = Uri.parse(storedVideo);
            sourceName = project.optString("sourceName", "Saved video");
            cues.clear();
            JSONArray array = project.optJSONArray("captions");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item != null) cues.add(CaptionCue.fromJson(item));
                }
            }
            videoName.setText(sourceName + "  •  RESTORED");
            player.setMediaItem(MediaItem.fromUri(videoUri));
            player.prepare();
            selectedIndex = cues.isEmpty() ? -1 : 0;
            refreshCueList();
            updateEditorFields();
        } catch (Exception ignored) { }
    }

    private String readText(Uri uri) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalStateException("File could not be opened");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString("UTF-8");
        }
    }

    private String queryDisplayName(Uri uri) {
        android.database.Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri,
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return "Selected video";
    }

    private long currentPosition() {
        return player == null ? 0 : Math.max(0, player.getCurrentPosition());
    }

    private CaptionCue getSelectedCue() {
        return selectedIndex >= 0 && selectedIndex < cues.size() ? cues.get(selectedIndex) : null;
    }

    private long parseSeconds(String value) {
        return Math.round(Double.parseDouble(value.trim()) * 1000d);
    }

    private String formatTime(long millis) {
        long seconds = Math.max(0, millis / 1000L);
        return String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    private int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
        return 0;
    }

    private EditText timeInput(String hint) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edit.setTextColor(Color.WHITE);
        edit.setHintTextColor(Color.rgb(110, 119, 148));
        edit.setHint(hint);
        edit.setTextSize(14);
        edit.setPadding(dp(11), 0, dp(11), 0);
        edit.setBackground(round(Color.rgb(20, 24, 40), 11));
        return edit;
    }

    private LinearLayout fieldColumn(String title, EditText field) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView label = text(title, 9, Color.rgb(154, 163, 190), true);
        label.setPadding(0, dp(9), 0, dp(5));
        column.addView(label);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(44));
        p.setMargins(0, 0, dp(6), 0);
        column.addView(field, p);
        return column;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, values);
        spinner.setAdapter(adapter);
        spinner.setBackground(round(Color.rgb(20, 24, 40), 11));
        spinner.setPadding(dp(10), 0, dp(8), 0);
        return spinner;
    }

    private LinearLayout labelValue(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(label, 10, Color.rgb(170, 178, 201), true),
                new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text(value, 11, Color.rgb(81, 231, 211), true));
        return row;
    }

    private void styleSeek(SeekBar seek) {
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(119, 91, 246)));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.rgb(76, 230, 208)));
    }

    private LinearLayout section(String title, String subtitle) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(text(title, 13, Color.WHITE, true));
        TextView sub = text(subtitle, 10, Color.rgb(129, 139, 169), false);
        sub.setPadding(0, dp(3), 0, 0);
        block.addView(sub);
        return block;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(10);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(primary ? Color.WHITE : Color.rgb(222, 226, 240));
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setBackground(round(primary ? Color.rgb(105, 79, 231) : Color.rgb(25, 30, 47), 12));
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private void addWeighted(LinearLayout row, View view) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1);
        p.setMargins(0, 0, dp(7), 0);
        row.addView(view, p);
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private void space(LinearLayout parent, int height) {
        View view = new View(this);
        parent.addView(view, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private void hideKeyboard() {
        try {
            InputMethodManager manager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            manager.hideSoftInputFromWindow(captionEditor.getWindowToken(), 0);
        } catch (Exception ignored) { }
    }

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String safeMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()) {
            return "Unknown media error";
        }
        return error.getMessage();
    }

    @Override
    protected void onPause() {
        if (player != null) player.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (activeTransformer != null) {
            try { activeTransformer.cancel(); } catch (Exception ignored) { }
        }
        if (activeOutputFile != null) activeOutputFile.delete();
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}