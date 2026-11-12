package com.araztools.sumer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.graphics.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.Random;
import java.util.Arrays;

public class MainActivity extends Activity {
    private final Handler handler = new Handler();
    private final Random random = new Random();
    private final int rows = 16, cols = 16, blockSize = rows * cols;
    private final int[] block = new int[blockSize];
    private int blockCount = 0;
    private int minValue = Integer.MAX_VALUE, maxValue = Integer.MIN_VALUE;
    private int latestValue = 0;
    private long blocksRendered = 0;
    private boolean running = false;
    private Bitmap latestBitmap;
    private ScanView scanView;
    private TextView status, latestText, minText, maxText, countText;
    private Button startButton;

    private final Runnable demoFeed = new Runnable() {
        @Override public void run() {
            if (!running) return;
            int value = 1700 + (int)(950 * Math.sin(blocksRendered * 0.21 + blockCount * 0.12))
                    + random.nextInt(701) - 350;
            acceptValue(Math.max(0, Math.min(5000, value)));
            handler.postDelayed(this, 55);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        renderEmptyBlock();
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView label(String text, int size, int color) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(5), dp(5), dp(5), dp(5));
        return t;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(20));
        root.setBackgroundColor(Color.rgb(12, 19, 29));
        scroll.addView(root);
        setContentView(scroll);

        root.addView(label("SUMER", 30, Color.WHITE));
        root.addView(label("LIVE SCAN  •  نمونه تغییرات جدید", 13, Color.rgb(120, 190, 220)));
        status = label("● آماده — ورودی آزمایشی", 14, Color.rgb(100, 220, 160));
        root.addView(status);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        latestText = statBox(stats, "آخرین داده", "—");
        minText = statBox(stats, "MIN", "—");
        maxText = statBox(stats, "MAX", "—");
        root.addView(stats);

        root.addView(label("تصویر زنده — فقط آخرین بلوک", 16, Color.WHITE));
        scanView = new ScanView();
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, dp(300));
        imageParams.setMargins(0, dp(8), 0, dp(8));
        root.addView(scanView, imageParams);

        countText = label("بلوک‌های تولیدشده: 0", 13, Color.LTGRAY);
        root.addView(countText);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        startButton = new Button(this);
        startButton.setText("شروع Live");
        startButton.setOnClickListener(v -> startDemo());
        Button stop = new Button(this);
        stop.setText("توقف");
        stop.setOnClickListener(v -> stopDemo());
        Button reset = new Button(this);
        reset.setText("بازنشانی");
        reset.setOnClickListener(v -> resetScan());
        buttons.addView(startButton, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(stop, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(reset, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(buttons);

        root.addView(label("حافظه: یک بلوک موقت برای ساخت تصویر؛ پس از تولید، داده‌های عددی آن پاک می‌شوند. فقط MIN و MAX جاری در طول اسکن باقی می‌مانند.", 12, Color.rgb(190, 202, 215)));
        root.addView(label("توجه: این نسخه برای بررسی رابط و رفتار تصویر از داده آزمایشی استفاده می‌کند؛ اتصال HC-05 و داده واقعی حسگرها هنوز در این نمونه فعال نیست.", 12, Color.rgb(255, 195, 100)));
    }

    private TextView statBox(LinearLayout parent, String title, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(3), dp(8), dp(3), dp(8));
        box.setBackgroundColor(Color.rgb(28, 40, 55));
        box.addView(label(title, 12, Color.LTGRAY));
        TextView valueView = label(value, 19, Color.WHITE);
        box.addView(valueView);
        parent.addView(box, new LinearLayout.LayoutParams(0, dp(66), 1));
        return valueView;
    }

    private void startDemo() {
        if (running) return;
        running = true;
        status.setText("● Live فعال — داده آزمایشی");
        startButton.setEnabled(false);
        handler.post(demoFeed);
    }

    private void stopDemo() {
        running = false;
        handler.removeCallbacks(demoFeed);
        if (startButton != null) startButton.setEnabled(true);
        if (status != null) status.setText("● متوقف شد");
    }

    private void resetScan() {
        stopDemo();
        minValue = Integer.MAX_VALUE; maxValue = Integer.MIN_VALUE;
        latestValue = 0; blockCount = 0; blocksRendered = 0;
        Arrays.fill(block, 0);
        if (latestBitmap != null && !latestBitmap.isRecycled()) latestBitmap.recycle();
        latestBitmap = null;
        latestText.setText("—"); minText.setText("—"); maxText.setText("—");
        countText.setText("بلوک‌های تولیدشده: 0");
        renderEmptyBlock();
        status.setText("● بازنشانی شد");
    }

    private void acceptValue(int value) {
        latestValue = value;
        if (value < minValue) minValue = value;
        if (value > maxValue) maxValue = value;
        block[blockCount++] = value;

        latestText.setText(String.valueOf(latestValue));
        minText.setText(String.valueOf(minValue));
        maxText.setText(String.valueOf(maxValue));

        if (blockCount == blockSize) {
            makeLatestImage();
            Arrays.fill(block, 0);
            blockCount = 0;
            blocksRendered++;
            countText.setText("بلوک‌های تولیدشده: " + blocksRendered);
        }
    }

    private int colorFor(int value) {
        if (maxValue == minValue) return Color.rgb(30, 140, 210);
        float p = (value - minValue) / (float)(maxValue - minValue);
        p = Math.max(0f, Math.min(1f, p));
        return Color.HSVToColor(new float[]{240f - 240f * p, 0.95f, 0.98f});
    }

    private void makeLatestImage() {
        Bitmap next = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888);
        for (int i = 0; i < blockSize; i++) next.setPixel(i % cols, i / cols, colorFor(block[i]));
        Bitmap previous = latestBitmap;
        latestBitmap = next;
        if (previous != null && !previous.isRecycled()) previous.recycle();
        scanView.invalidate();
    }

    private void renderEmptyBlock() {
        latestBitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888);
        latestBitmap.eraseColor(Color.rgb(25, 40, 58));
        if (scanView != null) scanView.invalidate();
    }

    private class ScanView extends View {
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        ScanView() { super(MainActivity.this); setBackgroundColor(Color.rgb(18, 28, 42)); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (latestBitmap == null || latestBitmap.isRecycled()) return;
            float side = Math.min(getWidth(), getHeight()) - dp(12);
            float left = (getWidth() - side) / 2f;
            float top = (getHeight() - side) / 2f;
            canvas.drawBitmap(latestBitmap, null, new RectF(left, top, left + side, top + side), paint);
        }
    }

    @Override protected void onDestroy() {
        stopDemo();
        if (latestBitmap != null && !latestBitmap.isRecycled()) latestBitmap.recycle();
        super.onDestroy();
    }
}
