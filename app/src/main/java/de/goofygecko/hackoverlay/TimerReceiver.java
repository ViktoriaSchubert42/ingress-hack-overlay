package de.goofygecko.hackoverlay;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/**
 * Keeps the overlay service alive for the timers: handles timer alarms, re-sets them when the user
 * allows alarms on time, and brings the service back after a reboot or an app update if the user
 * had it running.
 */
public class TimerReceiver extends BroadcastReceiver {
    static final String ACTION_ALARM = "de.goofygecko.hackoverlay.TIMER_ALARM";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Prefs.serviceOn(Prefs.get(context)) || !Settings.canDrawOverlays(context)) return;

        OverlayService running = OverlayService.instance;
        if (running != null && AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(intent.getAction())) {
            running.onExactAlarmPermissionChanged();
            return;
        }
        if (running != null) {
            running.onAlarm();
            return;
        }
        try {
            // The new service checks the timers on start and alerts for any that finished.
            context.startForegroundService(new Intent(context, OverlayService.class));
        } catch (RuntimeException e) {
            // Android refused a background start; the next alarm or opening the app retries.
        }
    }
}
