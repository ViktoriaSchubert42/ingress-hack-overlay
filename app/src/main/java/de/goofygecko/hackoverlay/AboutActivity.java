package de.goofygecko.hackoverlay;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** About page: app picture, name, version and author on top, copyright notice at the bottom. */
public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = dp(20);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(pad, pad * 2, pad, pad);

        ImageView picture = new ImageView(this);
        picture.setImageResource(R.drawable.app_icon);
        picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
        picture.setContentDescription(getString(R.string.app_name));
        col.addView(picture, new LinearLayout.LayoutParams(dp(180), dp(180)));

        TextView title = centered(getString(R.string.app_name), 26);
        title.setPadding(0, dp(16), 0, 0);
        col.addView(title);
        col.addView(centered("Version " + versionName(), 16));
        col.addView(centered("by GoofyGecko, Enlightened Germany", 16));
        TextView source = centered("Source: https://github.com/ViktoriaSchubert42/ingress-hack-overlay", 14);
        source.setPadding(0, dp(12), 0, 0);
        Linkify.addLinks(source, Linkify.WEB_URLS);
        col.addView(source);

        // Empty space that pushes the notice to the bottom of the screen.
        col.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1));

        TextView notice = centered("Copyright Viktoria Schubert. All rights reserved. "
                + "\"Ingress\" and the Enlightened and the Resistance logos are the property of Google Inc. "
                + "This application and GoofyGecko are NOT affiliated in any way with Google Inc, "
                + "Niantic Labs or Ingress Project", 12);
        notice.setPadding(0, dp(32), 0, 0);
        col.addView(notice);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);  // lets the empty space fill the screen
        scroll.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        MainActivity.keepClearOfSystemBars(scroll);
        setContentView(scroll);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private TextView centered(String text, int sp) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(0, dp(4), 0, 0);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
