package de.goofygecko.hackoverlay;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Settings screen: configure the timers and start/stop the overlay. */
public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout timerList;
    private TextView status;
    private TextView sizeLabel;
    private Button battery;
    private Button exactAlarms;
    private ImageView logo;
    private RadioGroup colors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);

        int pad = dp(20);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad * 2, pad, pad);

        logo = new ImageView(this);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(200));
        logoLp.bottomMargin = dp(12);
        col.addView(logo, logoLp);
        showFaction(Prefs.faction(prefs));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(title);

        col.addView(label("Faction"));
        RadioGroup factions = new RadioGroup(this);
        for (int i = 0; i < Prefs.FACTION_NAMES.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(i + 1);
            rb.setText(Prefs.FACTION_NAMES[i]);
            rb.setTextColor(Prefs.FACTION_COLORS[i]);
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            factions.addView(rb);
        }
        if (Prefs.faction(prefs) >= 0) factions.check(Prefs.faction(prefs) + 1);
        factions.setOnCheckedChangeListener((g, id) -> {
            int faction = id - 1;
            prefs.edit().putInt(Prefs.FACTION, faction).apply();
            showFaction(faction);
        });
        col.addView(factions);

        TextView help = new TextView(this);
        help.setText("The floating buttons appear on top of Ingress while it is open. "
                + "In other apps the buttons are hidden and running timers are shown as notifications. "
                + "The buttons sit in one row or column; drag any of them to move them all.\n"
                + "To keep them out of a screenshot, tap \"Hide buttons\" in the notification or use the "
                + "\"Hack buttons\" Quick Settings tile (add it via the pencil icon in the quick settings panel).");
        help.setPadding(0, dp(12), 0, dp(12));
        col.addView(help);

        col.addView(paragraph("Add the timers and counters you want below. Each one is its own button:"));
        col.addView(buttonHelp(Timers.BURNOUT,
                "Tap to start 4:00 (hours:minutes). Long-press to reset to 4:00, tap again to start."));
        col.addView(buttonHelp(Timers.HEATSINK,
                "Tap to start, long-press to reset to the full time, tap again to start. Vibrates when ready. "
                        + "Set the portal's faction and heat sinks in its setup."));
        col.addView(buttonHelp(Timers.MULTIHACK,
                "Tap to count down by 1, long-press to reset. Set the portal's multihack mods in its setup."));
        col.addView(buttonHelp(Timers.DRONE,
                "Only one. Tap to start, long-press to reset to the full time, tap again to start. "
                        + "Vibrates when ready."));

        TextView colorHelp = paragraph("Yellow full time or count = not started yet, white = running or counting down, "
                + "yellow READY = finished, can be started again, yellow 0 = no hacks left.\n"
                + "Borders: white while a timer runs or a countdown is under way, yellow otherwise.");
        colorHelp.setPadding(0, dp(12), 0, dp(20));
        col.addView(colorHelp);

        // Applied immediately; a running overlay picks the changes up straight away.
        col.addView(label("Timers (buttons in this order)"));
        timerList = new LinearLayout(this);
        timerList.setOrientation(LinearLayout.VERTICAL);
        col.addView(timerList);
        Button add = new Button(this);
        add.setText("+ Add timer");
        add.setOnClickListener(v -> pickTimerToAdd());
        col.addView(add);

        sizeLabel = label("");
        col.addView(sizeLabel);
        SeekBar size = new SeekBar(this);
        size.setMax(Prefs.MAX_BUTTON_SIZE - Prefs.MIN_BUTTON_SIZE);
        size.setProgress(Prefs.buttonSize(prefs) - Prefs.MIN_BUTTON_SIZE);
        showSize(Prefs.buttonSize(prefs));
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int pct = progress + Prefs.MIN_BUTTON_SIZE;
                showSize(pct);
                prefs.edit().putInt(Prefs.BUTTON_SIZE, pct).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        col.addView(size);

        col.addView(label("Button arrangement"));
        RadioGroup layout = new RadioGroup(this);
        RadioButton row = new RadioButton(this);
        row.setId(1);
        row.setText("Horizontal (side by side)");
        layout.addView(row);
        RadioButton column = new RadioButton(this);
        column.setId(2);
        column.setText("Vertical (stacked)");
        layout.addView(column);
        layout.check(Prefs.vertical(prefs) ? 2 : 1);
        // Applied immediately; a running overlay picks it up straight away.
        layout.setOnCheckedChangeListener((g, id) ->
                prefs.edit().putBoolean(Prefs.VERTICAL, id == 2).apply());
        col.addView(layout);

        col.addView(label("Button color"));
        colors = new RadioGroup(this);
        colors.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < Prefs.COLOR_NAMES.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(i + 1);
            rb.setText(Prefs.COLOR_NAMES[i]);
            colors.addView(rb);
        }
        colors.check(Prefs.buttonColorIndex(prefs) + 1);
        // Applied immediately; a running overlay picks it up straight away.
        colors.setOnCheckedChangeListener((g, id) ->
                prefs.edit().putInt(Prefs.BUTTON_COLOR, id - 1).apply());
        col.addView(colors);

        status = new TextView(this);
        status.setPadding(0, dp(20), 0, dp(8));
        col.addView(status);

        Button start = new Button(this);
        start.setText("Start overlay");
        start.setOnClickListener(v -> startOverlay());
        col.addView(start);

        Button stop = new Button(this);
        stop.setText("Stop overlay");
        stop.setOnClickListener(v -> {
            prefs.edit().putBoolean(Prefs.SERVICE_ON, false).apply();
            stopService(new Intent(this, OverlayService.class));
        });
        col.addView(stop);

        // Without this, timers that end while the phone sleeps may alert a few minutes late.
        exactAlarms = new Button(this);
        exactAlarms.setText("Allow alarms on time");
        exactAlarms.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 31) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        col.addView(exactAlarms);

        // Some phones stop background apps to save battery, which would stop the timer alerts.
        // The user switches this off in the phone's list of battery-optimized apps.
        battery = new Button(this);
        battery.setText("Keep timers running in background");
        battery.setOnClickListener(v -> {
            Toast.makeText(this, "Choose \"All apps\", then " + getString(R.string.app_name)
                    + " and \"Don't optimize\"", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (ActivityNotFoundException e) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        col.addView(battery);

        Button about = new Button(this);
        about.setText("About the app");
        about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        about.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        LinearLayout.LayoutParams aboutLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        aboutLp.topMargin = dp(24);
        col.addView(about, aboutLp);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(col);
        keepClearOfSystemBars(scroll);
        setContentView(scroll);
    }

    /** Android draws apps edge to edge, so keep the content clear of the status and navigation bars. */
    static void keepClearOfSystemBars(View view) {
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(i.left, i.top, i.right, i.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        showTimers();
        status.setText((Settings.canDrawOverlays(this)
                ? "Display over other apps: allowed"
                : "Display over other apps: missing – \"Start overlay\" will open the settings page.")
                + "\n"
                + (OverlayService.hasUsageAccess(this)
                ? "Usage access: allowed"
                : "Usage access: missing – needed to show the buttons only in Ingress.")
                + "\n"
                + (exactAlarmsAllowed()
                ? "Alarms on time: allowed"
                : "Alarms on time: not allowed – timers ending while the phone sleeps may alert late.")
                + "\n"
                + (ignoresBatteryLimits()
                ? "Battery optimization: off for this app"
                : "Battery optimization: on – the phone may stop the timers in the background."));
        battery.setVisibility(ignoresBatteryLimits() ? View.GONE : View.VISIBLE);
        exactAlarms.setVisibility(exactAlarmsAllowed() ? View.GONE : View.VISIBLE);
    }

    private boolean exactAlarmsAllowed() {
        return Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    private boolean ignoresBatteryLimits() {
        return getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
    }

    /**
     * Sends the user to a special-access settings page. Since Android 13 the phone blocks these
     * pages ("restricted setting") for apps installed from a downloaded APK file until the user
     * unblocks them in the app's info page, so explain how.
     */
    private void askForAccess(String setting, Intent settingsPage) {
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(this, "Allow \"" + setting + "\", then come back and tap Start again",
                    Toast.LENGTH_LONG).show();
            startActivity(settingsPage);
            return;
        }
        String app = getString(R.string.app_name);
        new AlertDialog.Builder(this)
                .setTitle("Allow \"" + setting + "\"")
                .setMessage("Turn on \"" + setting + "\" for " + app + ", then come back and tap "
                        + "\"Start overlay\" again.\n\n"
                        + "If the phone says \"App was denied access\" or \"Restricted setting\": Android "
                        + "blocks this for apps installed from a downloaded file. To unblock it once:\n"
                        + "1. Tap \"Open app info\".\n"
                        + "2. Tap the ⋮ menu at the top right and choose \"Allow restricted settings\". "
                        + "Confirm with your PIN or fingerprint.\n"
                        + "3. Come back and tap \"Start overlay\" again.\n\n"
                        + "If the menu doesn't show that option, first try once to turn on the setting, "
                        + "then open app info again.")
                .setPositiveButton("Open setting", (d, w) -> startActivity(settingsPage))
                .setNeutralButton("Open app info", (d, w) -> startActivity(new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            askForAccess("Display over other apps", new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        if (!OverlayService.hasUsageAccess(this)) {
            askForAccess("Usage access", new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // Only needed to see the "running / stop" notification; the overlay works without it.
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        startForegroundService(new Intent(this, OverlayService.class));
        moveTaskToBack(true);
    }

    /** One row per timer: symbol, name and setting, with setup and remove buttons. */
    private void showTimers() {
        timerList.removeAllViews();
        List<Timers.Timer> list = Timers.load(prefs);
        if (list.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("No timers – tap \"Add timer\".");
            none.setPadding(0, dp(8), 0, dp(8));
            timerList.addView(none);
        }
        for (int i = 0; i < list.size(); i++) {
            Timers.Timer t = list.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, dp(4));

            TextView number = new TextView(this);
            number.setText(String.valueOf(i + 1));
            number.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            number.setGravity(Gravity.END);
            number.setPadding(0, 0, dp(8), 0);
            row.addView(number, new LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.WRAP_CONTENT));

            ImageView icon = new ImageView(this);
            icon.setImageResource(Timers.icon(t.type));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));

            TextView name = new TextView(this);
            name.setText(Timers.name(t.type) + "\n" + summary(t));
            name.setPadding(dp(12), 0, dp(8), 0);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            if (!Timers.BURNOUT.equals(t.type)) {
                Button setup = new Button(this);
                setup.setText("Setup");
                setup.setOnClickListener(v -> setUp(list, t));
                row.addView(setup);
            }
            Button remove = new Button(this);
            remove.setText("✕");
            String numbered = (i + 1) + " · " + Timers.name(t.type);
            remove.setContentDescription("Remove " + numbered);
            remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage("Remove " + numbered + "?")
                    .setPositiveButton("Remove", (d, w) -> {
                        Timers.remove(prefs, t.id);
                        showTimers();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
            row.addView(remove);
            timerList.addView(row);
        }
    }

    /** A line of the button guide: the button's symbol, its name and how to use it. */
    private LinearLayout buttonHelp(String type, String text) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(6), 0, dp(6));
        ImageView icon = new ImageView(this);
        icon.setImageResource(Timers.icon(type));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        iconLp.rightMargin = dp(12);
        row.addView(icon, iconLp);
        TextView t = new TextView(this);
        SpannableStringBuilder sb = new SpannableStringBuilder(Timers.name(type));
        sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("\n").append(text);
        t.setText(sb);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private TextView paragraph(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setPadding(0, dp(8), 0, dp(4));
        return t;
    }

    private String summary(Timers.Timer t) {
        switch (t.type) {
            case Timers.BURNOUT: return "4 hours";
            case Timers.HEATSINK: return Timers.heatsinkSecs(prefs, t.id) + " seconds · "
                    + (Timers.friendly(prefs, t.id) ? "own faction portal" : "neutral or enemy portal");
            case Timers.MULTIHACK: return Timers.countdownStart(prefs, t.id) + " hacks";
            default: return Timers.droneMins(prefs, t.id) + " minutes";
        }
    }

    private void pickTimerToAdd() {
        List<Timers.Timer> list = Timers.load(prefs);
        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setPadding(dp(16), dp(8), dp(16), dp(8));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add timer")
                .setView(options)
                .setNegativeButton("Cancel", null)
                .create();
        for (String type : Timers.TYPES) {
            if (!Timers.canAdd(list, type)) continue;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            ImageView icon = new ImageView(this);
            icon.setImageResource(Timers.icon(type));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
            TextView name = new TextView(this);
            name.setText(Timers.name(type));
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            name.setPadding(dp(16), 0, 0, 0);
            row.addView(name);
            row.setOnClickListener(v -> {
                Timers.add(prefs, type);
                showTimers();
                dialog.dismiss();
            });
            options.addView(row);
        }
        dialog.show();
    }

    private void setUp(List<Timers.Timer> list, Timers.Timer t) {
        switch (t.type) {
            case Timers.MULTIHACK:
            case Timers.HEATSINK:
                startActivity(new Intent(this, ModsActivity.class)
                        .putExtra(ModsActivity.EXTRA_TIMER_ID, t.id)
                        .putExtra(ModsActivity.EXTRA_TYPE, t.type)
                        .putExtra(ModsActivity.EXTRA_LABEL, Timers.label(list, t)));
                break;
            case Timers.DRONE:
                askNumber(Timers.label(list, t), "Timer in minutes (0–" + Prefs.MAX_DRONE_MINS + ")",
                        Timers.droneMins(prefs, t.id), Prefs.MAX_DRONE_MINS, Timers.minsKey(t.id));
                break;
        }
    }

    private void askNumber(String title, String message, int value, int max, String key) {
        EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setText(String.valueOf(value));
        field.setSelectAllOnFocus(true);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), 0, dp(20), 0);
        box.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    int v;
                    try {
                        v = Integer.parseInt(field.getText().toString().trim());
                    } catch (NumberFormatException ex) {
                        v = value;
                    }
                    prefs.edit().putInt(key, Prefs.clamp(v, 0, max)).apply();
                    showTimers();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Shows the faction's logo at the top, or nothing until a faction is chosen. */
    private void showFaction(int faction) {
        if (faction < 0) {
            logo.setVisibility(View.GONE);
        } else {
            logo.setImageResource(Prefs.FACTION_LOGOS[faction]);
            logo.setContentDescription(Prefs.FACTION_NAMES[faction]);
            logo.setVisibility(View.VISIBLE);
        }
    }

    private void showSize(int pct) {
        sizeLabel.setText("Button size: " + pct + " %");
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setPadding(0, dp(8), 0, 0);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
