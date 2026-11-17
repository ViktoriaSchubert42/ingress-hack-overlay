package de.goofygecko.hackoverlay;

import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Foreground service that draws the user's timer and counter buttons over Ingress.
 * While Ingress is not in front, the buttons are hidden and the timers are shown as notifications.
 */
public class OverlayService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String ACTION_STOP = "de.goofygecko.hackoverlay.STOP";
    static final String ACTION_TOGGLE_HIDDEN = "de.goofygecko.hackoverlay.TOGGLE_HIDDEN";
    static final String INGRESS_PACKAGE = "com.nianticproject.ingress";

    private static final String CHANNEL_SERVICE = "overlay";
    private static final String CHANNEL_TIMERS = "timers";
    private static final String CHANNEL_ALERTS = "alerts";
    private static final int ID_SERVICE = 1;
    private static final int ID_TIMER_BASE = 100;  // + timer id

    private static final String ALERTED = "_alerted";  // suffix: end time that has already alerted

    private static final long BURNOUT_MS = 4L * 60 * 60 * 1000;
    private static final long LONG_PRESS_MS = 600;
    private static final long TICK_MS = 250;
    private static final long FOREGROUND_POLL_MS = 1000;

    // Text colors, readable on every (dark) button background.
    private static final int TEXT_RUNNING = Color.WHITE;                  // running / counting down
    private static final int TEXT_READY = Color.parseColor("#FFEB3B");    // not started / READY / zero
    // Timer borders, the same on every background: white while running, yellow otherwise.
    private static final int BORDER_RUNNING = Color.WHITE;
    private static final int BORDER_STOPPED = Color.parseColor("#FFEB3B");

    /** The running service, if any, so alarms can reach it directly. */
    static OverlayService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private PowerManager pm;
    private AlarmManager am;
    private NotificationManager nm;
    private UsageStatsManager usm;
    private SharedPreferences prefs;
    private Vibrator vibrator;

    /** One button per timer in the user's list, in the same order. */
    private final List<Bubble> bubbles = new ArrayList<>();

    // All buttons live in one window, in a row or a column, and are dragged together.
    private LinearLayout group;
    private WindowManager.LayoutParams groupLp;
    private boolean groupShown;

    // Foreground app tracking. Without usage access we can't tell, so the buttons stay visible.
    private boolean inIngress = true;
    private String foregroundPackage;
    private long lastEventQuery;
    private long lastForegroundPoll;

    // What is currently posted, so the service notification is only re-posted when something changes.
    private String postedService;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, TICK_MS);
        }
    };

    static boolean hasUsageAccess(Context context) {
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        int mode = Build.VERSION.SDK_INT >= 29
                ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName())
                : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        wm = getSystemService(WindowManager.class);
        pm = getSystemService(PowerManager.class);
        am = getSystemService(AlarmManager.class);
        nm = getSystemService(NotificationManager.class);
        usm = getSystemService(UsageStatsManager.class);
        prefs = Prefs.get(this);
        vibrator = getSystemService(Vibrator.class);
        createChannels();

        group = new LinearLayout(this);
        groupLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        groupLp.gravity = Gravity.TOP | Gravity.START;
        groupLp.x = prefs.getInt("pos_group_x", dp(16));
        groupLp.y = prefs.getInt("pos_group_y", dp(120));

        buildBubbles();

        prefs.registerOnSharedPreferenceChangeListener(this);
        scheduleAlarms();
        handler.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            prefs.edit().putBoolean(Prefs.SERVICE_ON, false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_TOGGLE_HIDDEN.equals(intent.getAction())) {
            prefs.edit().putBoolean(Prefs.HIDDEN, !Prefs.hidden(prefs)).apply();
        }
        prefs.edit().putBoolean(Prefs.SERVICE_ON, true).apply();
        Notification n = buildServiceNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_SERVICE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(ID_SERVICE, n);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (!Prefs.serviceOn(prefs)) {
            // Stopped by the user: no more alerts.
            for (Bubble b : bubbles) am.cancel(alarmIntent(b.timer.id));
        }
        handler.removeCallbacksAndMessages(null);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        setGroupShown(false);
        for (Bubble b : bubbles) nm.cancel(ID_TIMER_BASE + b.timer.id);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (key == null || key.endsWith(ALERTED)) return;
        if (Timers.LIST.equals(key) || Prefs.FACTION.equals(key)) buildBubbles();
        if (Timers.isEndKey(key)) scheduleAlarms();
        refresh();
    }

    /** Called when the user allowed or revoked alarms on time. */
    void onExactAlarmPermissionChanged() {
        scheduleAlarms();
    }

    /** Called when a timer's alarm goes off, possibly while the phone is asleep. */
    void onAlarm() {
        lastForegroundPoll = 0;
        refresh();
    }

    /**
     * Sets a wake-up alarm for the end of every running timer, so the finish alert arrives on time
     * even while the phone sleeps (the regular update loop pauses then).
     */
    private void scheduleAlarms() {
        long now = System.currentTimeMillis();
        for (Bubble b : bubbles) {
            if (Timers.MULTIHACK.equals(b.timer.type)) continue;
            long end = prefs.getLong(Timers.endKey(b.timer.id), 0);
            PendingIntent pi = alarmIntent(b.timer.id);
            if (end <= now) {
                am.cancel(pi);
            } else if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pi);
            }
        }
    }

    private PendingIntent alarmIntent(int timerId) {
        Intent i = new Intent(this, TimerReceiver.class).setAction(TimerReceiver.ACTION_ALARM);
        return PendingIntent.getBroadcast(this, timerId, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** True once per finished timer run; remembered across restarts so each run alerts exactly once. */
    private boolean justFinished(String endKey, long end, long now) {
        if (end == 0 || end > now || prefs.getLong(endKey + ALERTED, 0) == end) return false;
        prefs.edit().putLong(endKey + ALERTED, end).apply();
        return true;
    }

    /** Full run time of a timer (not used for multihack counters). */
    private long duration(Timers.Timer t) {
        switch (t.type) {
            case Timers.BURNOUT: return BURNOUT_MS;
            case Timers.HEATSINK: return Timers.heatsinkSecs(prefs, t.id) * 1000L;
            default: return Timers.droneMins(prefs, t.id) * 60_000L;
        }
    }

    private void startTimer(Timers.Timer t) {
        prefs.edit().putLong(Timers.endKey(t.id), System.currentTimeMillis() + duration(t)).apply();
    }

    /**
     * Matches the buttons to the user's timer list. Buttons of removed timers go away together with
     * their alarm and notification; buttons of timers still in the list are kept.
     */
    private void buildBubbles() {
        List<Timers.Timer> list = Timers.load(prefs);
        List<Bubble> old = new ArrayList<>(bubbles);
        bubbles.clear();
        group.removeAllViews();
        long now = System.currentTimeMillis();
        SharedPreferences.Editor e = prefs.edit();
        for (Timers.Timer t : list) {
            Bubble b = null;
            for (Bubble o : old) {
                if (o.timer.id == t.id) b = o;
            }
            if (b != null) {
                old.remove(b);
                group.addView(b.root);
            } else {
                b = newBubble(t);
                // A timer that already finished earlier shouldn't alert again.
                String endKey = Timers.endKey(t.id);
                long end = prefs.getLong(endKey, 0);
                if (!prefs.contains(endKey + ALERTED) && end != 0 && end <= now) e.putLong(endKey + ALERTED, end);
            }
            b.label = Timers.label(list, t);
            // Kept buttons too: the symbol follows the faction.
            b.icon.setImageResource(Timers.icon(t.type, Prefs.faction(prefs)));
            bubbles.add(b);
        }
        e.apply();
        for (Bubble gone : old) {
            gone.setVisible(false);
            am.cancel(alarmIntent(gone.timer.id));
            nm.cancel(ID_TIMER_BASE + gone.timer.id);
        }
    }

    private Bubble newBubble(Timers.Timer t) {
        boolean counter = Timers.MULTIHACK.equals(t.type);
        Bubble b = new Bubble(t);
        if (counter) {
            b.onTap = () -> {
                int v = Timers.countdownValue(prefs, t.id);
                if (v > 0) prefs.edit().putInt(Timers.countKey(t.id), v - 1).apply();
            };
            b.onLongPress = () ->
                    prefs.edit().putInt(Timers.countKey(t.id), Timers.countdownStart(prefs, t.id)).apply();
        } else {
            // Tapping a running timer does nothing. Long-press puts it back to its full time,
            // and the next tap starts it.
            b.onTap = () -> {
                if (prefs.getLong(Timers.endKey(t.id), 0) <= System.currentTimeMillis()) startTimer(t);
            };
            b.onLongPress = () -> prefs.edit().putLong(Timers.endKey(t.id), 0).apply();
        }
        return b;
    }

    /** Updates {@link #inIngress} from the system's app usage events. */
    private void pollForeground(long now) {
        if (now - lastForegroundPoll < FOREGROUND_POLL_MS) return;
        lastForegroundPoll = now;
        // With the screen off nobody sees the buttons, so use notifications (they show on the lock screen).
        boolean screenOn = pm.isInteractive();
        if (!hasUsageAccess(this)) {
            inIngress = screenOn;
            return;
        }
        // Overlap the previous window a little so late-logged events aren't missed; the last one wins.
        long from = lastEventQuery == 0 ? now - 60 * 60 * 1000 : lastEventQuery - 2000;
        UsageEvents events = usm.queryEvents(from, now);
        UsageEvents.Event e = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                foregroundPackage = e.getPackageName();
            }
        }
        lastEventQuery = now;
        inIngress = screenOn && INGRESS_PACKAGE.equals(foregroundPackage);
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        pollForeground(now);

        int bgColor = Prefs.buttonColor(prefs);
        float scale = Prefs.buttonSize(prefs) / 100f;
        for (Bubble b : bubbles) {
            b.setBackground(bgColor);
            b.setScale(scale);
            b.setVisible(true);
        }
        group.setOrientation(Prefs.vertical(prefs) ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        setGroupShown(inIngress && !Prefs.hidden(prefs) && !bubbles.isEmpty());

        StringBuilder counts = new StringBuilder();
        boolean burnoutDone = false, otherDone = false;
        for (Bubble b : bubbles) {
            Timers.Timer t = b.timer;
            if (Timers.MULTIHACK.equals(t.type)) {
                int left = Timers.countdownValue(prefs, t.id);
                // Like a timer: the full count in yellow until the first hack, then white while counting down.
                int start = Timers.countdownStart(prefs, t.id);
                if (left >= start) {
                    b.show(String.valueOf(left), TEXT_READY, BORDER_STOPPED);
                } else if (left > 0) {
                    b.show(String.valueOf(left), TEXT_RUNNING, BORDER_RUNNING);
                } else {
                    b.show("0", TEXT_READY, BORDER_STOPPED);
                }
                counts.append(counts.length() > 0 ? " · " : "").append(left);
                continue;
            }
            boolean burnout = Timers.BURNOUT.equals(t.type);
            String endKey = Timers.endKey(t.id);
            long end = prefs.getLong(endKey, 0);
            if (end == 0) {
                // Not started yet: the full time.
                long full = duration(t);
                b.show(burnout ? hm(full) : ms(full), TEXT_READY, BORDER_STOPPED);
            } else if (end > now) {
                b.show(burnout ? hm(end - now) : ms(end - now), TEXT_RUNNING, BORDER_RUNNING);
            } else {
                b.show("READY", TEXT_READY, BORDER_STOPPED);
            }
            boolean finished = justFinished(endKey, end, now);
            if (finished) {
                if (burnout) burnoutDone = true; else otherDone = true;
            }
            b.posted = syncTimerNotification(ID_TIMER_BASE + t.id, b.posted, b.label,
                    b.label + (burnout ? " is over" : " is ready"), Timers.icon(t.type, Prefs.faction(prefs)),
                    end, now, finished);
        }

        if (inIngress) {
            // The buttons show everything; the notification alerts would only cover the game.
            if (burnoutDone) vibrate(new long[]{0, 400, 200, 400});
            else if (otherDone) vibrate(new long[]{0, 250});
        }

        String service = counts + "|" + Prefs.hidden(prefs);
        if (!service.equals(postedService)) {
            postedService = service;
            nm.notify(ID_SERVICE, buildServiceNotification());
        }
    }

    /**
     * Shows a live countdown notification for a running timer while Ingress is not in front,
     * and an alerting one when it finishes. Returns what is now posted (null = nothing).
     */
    private String syncTimerNotification(int id, String posted, String title, String doneText,
                                         int iconRes, long end, long now, boolean justFinished) {
        if (inIngress) {
            if (posted != null) nm.cancel(id);
            return null;
        }
        if (justFinished) {
            nm.notify(id, new Notification.Builder(this, CHANNEL_ALERTS)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setLargeIcon(Icon.createWithResource(this, iconRes))
                    .setContentTitle(doneText)
                    .setContentIntent(openIngressIntent())
                    .setAutoCancel(true)
                    .build());
            return "done";
        }
        if (end > now) {
            String key = "run" + end;
            if (!key.equals(posted)) {
                nm.notify(id, new Notification.Builder(this, CHANNEL_TIMERS)
                        .setSmallIcon(R.drawable.ic_notif)
                        .setLargeIcon(Icon.createWithResource(this, iconRes))
                        .setContentTitle(title)
                        .setContentText("Ends at " + DateFormat.getTimeFormat(this).format(end))
                        .setWhen(end)
                        .setShowWhen(true)
                        .setUsesChronometer(true)
                        .setChronometerCountDown(true)
                        .setContentIntent(openIngressIntent())
                        .setOngoing(true)
                        .build());
            }
            return key;
        }
        return posted;
    }

    private PendingIntent openIngressIntent() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(INGRESS_PACKAGE);
        if (launch == null) launch = new Intent(this, MainActivity.class);
        return PendingIntent.getActivity(this, 2, launch, PendingIntent.FLAG_IMMUTABLE);
    }

    /** Hours and minutes, rounded up so it only shows 0:00 once the time is really over. */
    private static String hm(long ms) {
        long m = (ms + 59_999) / 60_000;
        return String.format(Locale.US, "%d:%02d", m / 60, m % 60);
    }

    private static String ms(long ms) {
        long s = (ms + 999) / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private void vibrate(long[] pattern) {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        }
    }

    private void createChannels() {
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_SERVICE, "Overlay running", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_TIMERS, "Running timers", NotificationManager.IMPORTANCE_LOW));
        NotificationChannel alerts = new NotificationChannel(
                CHANNEL_ALERTS, "Timer finished", NotificationManager.IMPORTANCE_HIGH);
        alerts.enableVibration(true);
        nm.createNotificationChannel(alerts);
    }

    private Notification buildServiceNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, OverlayService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent toggle = PendingIntent.getService(this, 3,
                new Intent(this, OverlayService.class).setAction(ACTION_TOGGLE_HIDDEN), PendingIntent.FLAG_IMMUTABLE);
        boolean hidden = Prefs.hidden(prefs);

        Notification.Builder b = new Notification.Builder(this, CHANNEL_SERVICE)
                .setSmallIcon(R.drawable.ic_notif);
        StringBuilder counts = new StringBuilder();
        for (Bubble bubble : bubbles) {
            if (!Timers.MULTIHACK.equals(bubble.timer.type)) continue;
            counts.append(counts.length() > 0 ? " · " : "").append(Timers.countdownValue(prefs, bubble.timer.id));
        }
        if (counts.length() > 0) {
            b.setLargeIcon(Icon.createWithResource(this, R.drawable.sym_multihack))
                    .setContentTitle("Remaining Hacks: " + counts);
        } else {
            b.setLargeIcon(Icon.createWithResource(this, R.drawable.app_icon))
                    .setContentTitle("Hack Timer Overlay for Ingress is running");
        }
        return b.setContentText(hidden ? "Buttons hidden – tap \"Show buttons\" to bring them back" : "Tap for settings")
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, hidden ? "Show buttons" : "Hide buttons", toggle).build())
                .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void setGroupShown(boolean show) {
        if (show == groupShown) return;
        groupShown = show;
        if (show) {
            wm.addView(group, groupLp);
        } else if (group.isAttachedToWindow()) {
            wm.removeView(group);
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /**
     * One floating hexagonal button (symbol on top, value below): tap, long-press and
     * drag-to-move, with its position remembered.
     */
    private class Bubble {
        // Sizes at 100 %; the size setting scales all of them.
        private static final float HEIGHT_DP = 104;
        private static final float WIDTH_DP = 90;  // regular hexagon: height * sqrt(3) / 2
        // Symbol box and text box, placed to fill the hexagon without crossing its edges.
        private static final float ICON_W_DP = 60;
        private static final float ICON_H_DP = 48;
        private static final float ICON_TOP_DP = 10;
        private static final float TEXT_W_DP = 74;
        private static final float TEXT_H_DP = 32;
        private static final float TEXT_TOP_DP = 58;
        private static final float TEXT_MAX_SP = 30;  // shrinks to fit longer texts like "READY"
        private static final float BORDER_DP = 2;
        private static final float GAP_DP = 4;  // space between neighbouring buttons

        Runnable onTap;
        Runnable onLongPress;
        String label;
        String posted;  // what its notification currently shows (null = nothing)

        final Timers.Timer timer;
        private final ImageView icon;
        private final TextView value;
        private final HexDrawable bg = new HexDrawable();
        final FrameLayout root;
        private float scale = -1;
        private int borderColor;

        private float downX, downY;
        private int startX, startY;
        private boolean dragging, longPressed;

        private final Runnable longPress = () -> {
            longPressed = true;
            vibrate(new long[]{0, 40});
            if (onLongPress != null) onLongPress.run();
        };

        Bubble(Timers.Timer timer) {
            this.timer = timer;

            root = new FrameLayout(OverlayService.this);
            root.setBackground(bg);

            icon = new ImageView(OverlayService.this);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            root.addView(icon);

            value = new TextView(OverlayService.this);
            value.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            value.setIncludeFontPadding(false);
            value.setMaxLines(1);
            value.setGravity(Gravity.CENTER);
            root.addView(value);

            int slop = ViewConfiguration.get(OverlayService.this).getScaledTouchSlop();
            root.setOnTouchListener((v, e) -> {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = groupLp.x;
                        startY = groupLp.y;
                        dragging = false;
                        longPressed = false;
                        handler.postDelayed(longPress, LONG_PRESS_MS);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (!dragging && !longPressed && Math.hypot(dx, dy) > slop) {
                            dragging = true;
                            handler.removeCallbacks(longPress);
                        }
                        if (dragging) {
                            groupLp.x = startX + Math.round(dx);
                            groupLp.y = startY + Math.round(dy);
                            if (groupShown) wm.updateViewLayout(group, groupLp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        handler.removeCallbacks(longPress);
                        if (dragging) {
                            prefs.edit()
                                    .putInt("pos_group_x", groupLp.x)
                                    .putInt("pos_group_y", groupLp.y)
                                    .apply();
                        } else if (!longPressed && onTap != null) {
                            onTap.run();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(longPress);
                        return true;
                }
                return false;
            });
            group.addView(root);
        }

        /** Resizes the button; {@code s} is 1 for the largest size. */
        void setScale(float s) {
            if (s == scale) return;
            scale = s;
            LinearLayout.LayoutParams rootLp = new LinearLayout.LayoutParams(dp(WIDTH_DP * s), dp(HEIGHT_DP * s));
            int half = dp(GAP_DP * s / 2);
            rootLp.setMargins(half, half, half, half);
            root.setLayoutParams(rootLp);

            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(
                    dp(ICON_W_DP * s), dp(ICON_H_DP * s), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            iconLp.topMargin = dp(ICON_TOP_DP * s);
            icon.setLayoutParams(iconLp);

            FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(
                    dp(TEXT_W_DP * s), dp(TEXT_H_DP * s), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            textLp.topMargin = dp(TEXT_TOP_DP * s);
            value.setLayoutParams(textLp);
            int maxPx = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                    TEXT_MAX_SP * s, getResources().getDisplayMetrics()));
            value.setAutoSizeTextTypeUniformWithConfiguration(
                    Math.min(4, maxPx), maxPx, 1, TypedValue.COMPLEX_UNIT_PX);
            applyBorder();

        }

        void setVisible(boolean visible) {
            int v = visible ? View.VISIBLE : View.GONE;
            if (root.getVisibility() == v) return;
            if (!visible) handler.removeCallbacks(longPress);
            root.setVisibility(v);
        }

        /** Pass {@link Color#TRANSPARENT} as border for none. */
        void show(String text, int color, int border) {
            value.setText(text);
            value.setTextColor(color);
            borderColor = border;
            applyBorder();
        }

        void setBackground(int color) {
            bg.setFillColor(color);
        }

        private void applyBorder() {
            bg.setStroke(Math.max(1, dp(BORDER_DP * scale)), borderColor);
        }
    }
}
