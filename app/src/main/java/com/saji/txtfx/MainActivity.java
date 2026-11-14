package com.saji.txtfx;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private final String[] styles = {
            "Soft Fade", "Rise Up", "Slide Left", "Zoom In", "Pop", "Bounce",
            "Typewriter", "Wide Tracking", "Tilt", "Soft Glow", "Neon Pulse",
            "Glitch", "Wave Text", "Flip", "Word Reveal", "Pulse", "Wipe",
            "Float", "Shake", "Elastic", "Shimmer", "Cinematic"
    };
    private final int[] palette = {
            Color.WHITE, Color.CYAN, Color.YELLOW, Color.MAGENTA,
            Color.rgb(255, 100, 125), Color.rgb(150, 255, 180),
            Color.rgb(160, 150, 255), Color.rgb(255, 170, 80)
    };

    private int selectedStyle = 0;
    private int selectedColor = Color.WHITE;
    private float selectedSize = 42f;
    private int selectedRatio = 0;
    private Typeface selectedTypeface = Typeface.create("sans-serif", Typeface.BOLD);
    private TextFxView preview;
    private FrameLayout previewFrame;
    private EditText lyricInput;
    private TextView styleStatus;
    private Button exportButton;
    private Button gifButton;
    private Spinner fontSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(8, 10, 19));
        getWindow().setNavigationBarColor(Color.rgb(8, 10, 19));
        buildScreen();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(8, 10, 19));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(18), dp(18), dp(26));
        scroll.addView(page);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = new TextView(this);
        logo.setText("FX");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(19);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(round(Color.rgb(112, 83, 242), 16));
        header.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(12), 0, 0, 0);
        TextView title = text("SAJI TXTFX", 20, Color.WHITE, true);
        TextView subtitle = text("MOTION TEXT STUDIO", 10, Color.rgb(156, 161, 190), true);
        brand.addView(title);
        brand.addView(subtitle);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        TextView version = text("MOBILE  •  1.0", 10, Color.rgb(153, 160, 188), true);
        header.addView(version);
        page.addView(header);
        addSpace(page, 20);

        page.addView(sectionTitle("LIVE PREVIEW", "Your animation updates instantly"));
        previewFrame = new FrameLayout(this);
        previewFrame.setBackground(round(Color.rgb(16, 19, 34), 22));
        preview = new TextFxView(this);
        previewFrame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(310));
        previewParams.topMargin = dp(12);
        page.addView(previewFrame, previewParams);
        addSpace(page, 16);

        TextView ratioLabel = text("CANVAS RATIO", 11, Color.rgb(172, 178, 204), true);
        page.addView(ratioLabel);
        LinearLayout ratios = new LinearLayout(this);
        ratios.setOrientation(LinearLayout.HORIZONTAL);
        ratios.setGravity(Gravity.CENTER_VERTICAL);
        String[] ratioNames = {"9:16 STORY", "1:1 SQUARE", "16:9 VIDEO"};
        for (int i = 0; i < ratioNames.length; i++) {
            final int index = i;
            Button button = smallButton(ratioNames[i], i == selectedRatio);
            button.setOnClickListener(v -> selectRatio(index, ratios));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(42), 1);
            p.setMargins(0, dp(8), dp(7), 0);
            ratios.addView(button, p);
        }
        page.addView(ratios);
        addSpace(page, 20);

        page.addView(sectionTitle("YOUR TEXT", "Write a line, hook, title or lyric"));
        lyricInput = new EditText(this);
        lyricInput.setText("YOUR LYRICS\nSTART HERE");
        lyricInput.setTextColor(Color.WHITE);
        lyricInput.setHintTextColor(Color.rgb(111, 118, 148));
        lyricInput.setTextSize(16);
        lyricInput.setGravity(Gravity.TOP | Gravity.START);
        lyricInput.setMinLines(2);
        lyricInput.setMaxLines(5);
        lyricInput.setPadding(dp(15), dp(14), dp(15), dp(14));
        lyricInput.setBackground(round(Color.rgb(20, 23, 40), 15));
        page.addView(lyricInput, new LinearLayout.LayoutParams(-1, dp(112)));
        lyricInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) { preview.setTextValue(s.toString()); }
            @Override public void afterTextChanged(Editable e) { }
        });
        addSpace(page, 20);

        LinearLayout styleHead = new LinearLayout(this);
        styleHead.setGravity(Gravity.CENTER_VERTICAL);
        styleHead.addView(sectionTitle("22 TEXT ANIMATIONS", "Tap a style to preview"));
        styleStatus = text("01 / 22", 10, Color.rgb(70, 229, 210), true);
        styleHead.addView(styleStatus);
        page.addView(styleHead);
        HorizontalScrollView styleScroll = new HorizontalScrollView(this);
        styleScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout styleRow = new LinearLayout(this);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < styles.length; i++) {
            final int index = i;
            Button chip = smallButton(styles[i], i == selectedStyle);
            chip.setOnClickListener(v -> {
                selectedStyle = index;
                preview.setStyleIndex(index);
                styleStatus.setText(String.format("%02d / 22", index + 1));
                refreshStyleChips(styleRow);
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(42));
            p.setMargins(0, dp(10), dp(8), dp(4));
            styleRow.addView(chip, p);
        }
        styleScroll.addView(styleRow);
        page.addView(styleScroll);
        addSpace(page, 18);

        page.addView(sectionTitle("FONT & SIZE", "Choose a type style and adjust scale"));
        fontSpinner = new Spinner(this);
        String[] fonts = {"Bold Sans", "Classic Serif", "Mono Type", "Light Sans"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fonts);
        fontSpinner.setAdapter(adapter);
        fontSpinner.setBackground(round(Color.rgb(20, 23, 40), 12));
        fontSpinner.setPadding(dp(8), 0, dp(8), 0);
        fontSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                Typeface tf;
                if (position == 1) tf = Typeface.create("serif", Typeface.BOLD);
                else if (position == 2) tf = Typeface.create("monospace", Typeface.BOLD);
                else if (position == 3) tf = Typeface.create("sans-serif-light", Typeface.NORMAL);
                else tf = Typeface.create("sans-serif", Typeface.BOLD);
                selectedTypeface = tf;
                preview.setTypefaceValue(tf);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        page.addView(fontSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        addSpace(page, 10);

        LinearLayout sizeHeader = new LinearLayout(this);
        sizeHeader.setGravity(Gravity.CENTER_VERTICAL);
        sizeHeader.addView(text("TEXT SIZE", 11, Color.rgb(172, 178, 204), true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView sizeValue = text("42", 12, Color.rgb(70, 229, 210), true);
        sizeHeader.addView(sizeValue);
        page.addView(sizeHeader);
        SeekBar sizeSeek = new SeekBar(this);
        sizeSeek.setMax(96);
        sizeSeek.setProgress(26);
        sizeSeek.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(139, 108, 255)));
        sizeSeek.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.rgb(70, 229, 210)));
        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selectedSize = 16 + progress;
                sizeValue.setText(String.valueOf((int)selectedSize));
                preview.setTextSizeSp(selectedSize);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        page.addView(sizeSeek);
        addSpace(page, 18);

        page.addView(sectionTitle("TEXT COLOR", "Tap a color swatch"));
        LinearLayout colors = new LinearLayout(this);
        colors.setGravity(Gravity.CENTER_VERTICAL);
        for (int c : palette) {
            View swatch = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(c);
            shape.setShape(GradientDrawable.OVAL);
            shape.setStroke(dp(c == selectedColor ? 3 : 1), c == selectedColor ? Color.rgb(70, 229, 210) : Color.rgb(100, 105, 130));
            swatch.setBackground(shape);
            swatch.setOnClickListener(v -> {
                selectedColor = c;
                preview.setTextColorValue(c);
                refreshColors(colors);
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(30), dp(30));
            p.setMargins(0, dp(11), dp(13), dp(4));
            colors.addView(swatch, p);
        }
        page.addView(colors);
        addSpace(page, 22);

        TextView note = text("EXPORT  •  MP4 GREEN SCREEN  +  TRANSPARENT GIF", 11, Color.rgb(70, 229, 210), true);
        page.addView(note);
        TextView noteBody = text("MP4 saves to Movies/SAJI TXTFX for InShot Chroma Key. Transparent GIF saves to Pictures/SAJI TXTFX.", 12, Color.rgb(158, 164, 190), false);
        noteBody.setPadding(0, dp(6), 0, dp(12));
        page.addView(noteBody);

        LinearLayout exportRow = new LinearLayout(this);
        exportRow.setOrientation(LinearLayout.HORIZONTAL);
        exportButton = new Button(this);
        exportButton.setText("EXPORT MP4");
        exportButton.setTextColor(Color.WHITE);
        exportButton.setTextSize(13);
        exportButton.setTypeface(Typeface.DEFAULT_BOLD);
        exportButton.setAllCaps(false);
        exportButton.setBackground(round(Color.rgb(112, 83, 242), 16));
        gifButton = new Button(this);
        gifButton.setText("TRANSPARENT GIF");
        gifButton.setTextColor(Color.rgb(8, 20, 25));
        gifButton.setTextSize(12);
        gifButton.setTypeface(Typeface.DEFAULT_BOLD);
        gifButton.setAllCaps(false);
        gifButton.setBackground(round(Color.rgb(70, 229, 210), 16));
        LinearLayout.LayoutParams exportParams = new LinearLayout.LayoutParams(0, dp(54), 1);
        exportParams.setMargins(0, 0, dp(7), 0);
        exportRow.addView(exportButton, exportParams);
        LinearLayout.LayoutParams gifParams = new LinearLayout.LayoutParams(0, dp(54), 1);
        gifParams.setMargins(dp(7), 0, 0, 0);
        exportRow.addView(gifButton, gifParams);
        page.addView(exportRow);
        exportButton.setOnClickListener(v -> startExport());
        gifButton.setOnClickListener(v -> startGifExport());

        TextView footer = text("MADE FOR YOUR LYRICS  •  SAJI TXTFX", 10, Color.rgb(104, 110, 139), true);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(20), 0, dp(4));
        page.addView(footer);

        setContentView(scroll);
    }

    private void selectRatio(int ratio, LinearLayout row) {
        selectedRatio = ratio;
        for (int i = 0; i < row.getChildCount(); i++) {
            ((Button)row.getChildAt(i)).setBackground(round(i == selectedRatio ? Color.rgb(71, 62, 130) : Color.rgb(25, 28, 46), 12));
        }
        int w = getResources().getDisplayMetrics().widthPixels - dp(36);
        int h;
        if (ratio == 0) h = Math.min(dp(380), (int)(w * 16f / 9f));
        else if (ratio == 1) h = Math.min(dp(360), w);
        else h = Math.max(dp(190), (int)(w * 9f / 16f));
        ViewGroup.LayoutParams params = previewFrame.getLayoutParams();
        params.height = h;
        previewFrame.setLayoutParams(params);
    }

    private void refreshStyleChips(LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            Button b = (Button) row.getChildAt(i);
            b.setBackground(round(i == selectedStyle ? Color.rgb(71, 62, 130) : Color.rgb(25, 28, 46), 12));
            b.setTextColor(i == selectedStyle ? Color.rgb(70, 229, 210) : Color.rgb(221, 224, 238));
        }
    }

    private void refreshColors(LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            int c = palette[i];
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(c);
            shape.setShape(GradientDrawable.OVAL);
            shape.setStroke(dp(c == selectedColor ? 3 : 1), c == selectedColor ? Color.rgb(70, 229, 210) : Color.rgb(100, 105, 130));
            row.getChildAt(i).setBackground(shape);
        }
    }

    private void startGifExport() {
        String textValue = lyricInput.getText().toString().trim();
        if (textValue.isEmpty()) {
            Toast.makeText(this, "Pehle apna text likho", Toast.LENGTH_SHORT).show();
            return;
        }
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(lyricInput.getWindowToken(), 0);
        exportButton.setEnabled(false);
        gifButton.setEnabled(false);
        gifButton.setText("RENDERING…");
        GifExporter.export(this, textValue, selectedStyle, selectedColor, selectedSize,
                selectedRatio, selectedTypeface, new Mp4Exporter.ResultCallback() {
                    @Override public void onComplete(String message, boolean success) {
                        exportButton.setEnabled(true);
                        gifButton.setEnabled(true);
                        gifButton.setText("TRANSPARENT GIF");
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void startExport() {
        String textValue = lyricInput.getText().toString().trim();
        if (textValue.isEmpty()) {
            Toast.makeText(this, "Pehle apna text likho", Toast.LENGTH_SHORT).show();
            return;
        }
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(lyricInput.getWindowToken(), 0);
        exportButton.setEnabled(false);
        gifButton.setEnabled(false);
        exportButton.setText("RENDERING…");
        Mp4Exporter.export(this, textValue, selectedStyle, selectedColor, selectedSize, selectedRatio, selectedTypeface,
                new Mp4Exporter.ResultCallback() {
                    @Override public void onComplete(String message, boolean success) {
                        exportButton.setEnabled(true);
                        gifButton.setEnabled(true);
                        exportButton.setText("EXPORT MP4");
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private LinearLayout sectionTitle(String title, String subtitle) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        TextView first = text(title, 13, Color.WHITE, true);
        TextView second = text(subtitle, 11, Color.rgb(125, 132, 163), false);
        block.addView(first);
        block.addView(second);
        return block;
    }

    private Button smallButton(String label, boolean selected) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(10);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(selected ? Color.rgb(70, 229, 210) : Color.rgb(221, 224, 238));
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setBackground(round(selected ? Color.rgb(71, 62, 130) : Color.rgb(25, 28, 46), 12));
        return b;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private void addSpace(LinearLayout parent, int heightDp) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
