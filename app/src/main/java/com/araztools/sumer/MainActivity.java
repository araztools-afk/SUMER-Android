package com.araztools.sumer;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(Color.rgb(244, 247, 250));

        TextView title = new TextView(this);
        title.setText("SUMER");
        title.setTextSize(32);
        title.setTextColor(Color.rgb(20, 55, 85));
        title.setGravity(Gravity.CENTER);

        TextView message = new TextView(this);
        message.setText("INSTALLATION TEST\n\nIf you can read this screen,\nthe APK installed successfully.\n\nNext: restore Bluetooth and scanner features.");
        message.setTextSize(18);
        message.setTextColor(Color.DKGRAY);
        message.setGravity(Gravity.CENTER);
        message.setPadding(12, 28, 12, 12);

        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        root.addView(message, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }
}
