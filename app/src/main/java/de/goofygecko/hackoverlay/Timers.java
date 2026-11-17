package de.goofygecko.hackoverlay;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * The timers and counters the user added, in button order, and the settings and state of each.
 * Every timer has its own id, so several of the same kind keep separate settings and run separately.
 */
final class Timers {
    static final String BURNOUT = "burnout";
    static final String HEATSINK = "heatsink";
    static final String MULTIHACK = "multihack";
    static final String DRONE = "drone";
    /** The kinds a user can add, in the order offered. */
    static final String[] TYPES = {BURNOUT, HEATSINK, MULTIHACK, DRONE};

    static final String LIST = "timers";        // "type:id,type:id,…" in button order
    private static final String NEXT_ID = "timers_next_id";

    static final class Timer {
        final String type;
        final int id;

        Timer(String type, int id) {
            this.type = type;
            this.id = id;
        }
    }

    private Timers() {}

    static List<Timer> load(SharedPreferences p) {
        if (!p.contains(LIST)) migrate(p);
        List<Timer> list = new ArrayList<>();
        for (String item : p.getString(LIST, "").split(",")) {
            int colon = item.indexOf(':');
            if (colon < 0) continue;
            try {
                list.add(new Timer(item.substring(0, colon), Integer.parseInt(item.substring(colon + 1))));
            } catch (NumberFormatException ignored) {
                // Skip a damaged entry rather than losing the whole list.
            }
        }
        return list;
    }

    private static void save(SharedPreferences.Editor e, List<Timer> list) {
        StringBuilder sb = new StringBuilder();
        for (Timer t : list) {
            if (sb.length() > 0) sb.append(',');
            sb.append(t.type).append(':').append(t.id);
        }
        e.putString(LIST, sb.toString());
    }

    static void add(SharedPreferences p, String type) {
        List<Timer> list = load(p);
        int id = p.getInt(NEXT_ID, 1);
        list.add(new Timer(type, id));
        SharedPreferences.Editor e = p.edit().putInt(NEXT_ID, id + 1);
        save(e, list);
        e.apply();
    }

    static void remove(SharedPreferences p, int id) {
        List<Timer> list = load(p);
        list.removeIf(t -> t.id == id);
        SharedPreferences.Editor e = p.edit();
        save(e, list);
        String prefix = "t" + id + "_";
        for (String key : p.getAll().keySet()) {
            if (key.startsWith(prefix)) e.remove(key);
        }
        e.apply();
    }

    static boolean has(List<Timer> list, String type) {
        for (Timer t : list) if (t.type.equals(type)) return true;
        return false;
    }

    /** Only one drone can be out at a time; everything else can be added more than once. */
    static boolean canAdd(List<Timer> list, String type) {
        return !DRONE.equals(type) || !has(list, DRONE);
    }

    static String name(String type) {
        switch (type) {
            case BURNOUT: return "Portal Burnout Refresh";
            case HEATSINK: return "Hacking Speed Timer";
            case MULTIHACK: return "Remaining Hacks Countdown";
            default: return "Drone Timer Cooldown";
        }
    }

    static int icon(String type) {
        switch (type) {
            case BURNOUT: return R.drawable.sym_burnout;
            case HEATSINK: return R.drawable.sym_heatsink;
            case MULTIHACK: return R.drawable.sym_multihack;
            default: return R.drawable.sym_drone;
        }
    }

    /**
     * Symbol for an overlay button: a blue version for the Resistance and a green one otherwise
     * (Enlightened or no faction picked yet).
     */
    static int icon(String type, int faction) {
        boolean res = faction == 1;
        switch (type) {
            case BURNOUT: return res ? R.drawable.sym_burnout_res : R.drawable.sym_burnout_enl;
            case HEATSINK: return res ? R.drawable.sym_heatsink_res : R.drawable.sym_heatsink_enl;
            case MULTIHACK: return res ? R.drawable.sym_multihack_res : R.drawable.sym_multihack_enl;
            default: return res ? R.drawable.sym_drone_res : R.drawable.sym_drone_enl;
        }
    }

    /**
     * The kind's name, with its number in the timer list when there is more than one of that kind,
     * e.g. "3 · Hacking Speed Timer".
     */
    static String label(List<Timer> list, Timer timer) {
        int count = 0, number = 0;
        for (int i = 0; i < list.size(); i++) {
            Timer t = list.get(i);
            if (t.type.equals(timer.type)) count++;
            if (t.id == timer.id) number = i + 1;
        }
        return count > 1 ? number + " · " + name(timer.type) : name(timer.type);
    }

    // Per-timer preference keys. Everything of timer <id> starts with "t<id>_".
    static String endKey(int id) { return "t" + id + "_end"; }          // wall-clock ms, 0 = not started
    static String secsKey(int id) { return "t" + id + "_secs"; }
    static String minsKey(int id) { return "t" + id + "_mins"; }
    static String startKey(int id) { return "t" + id + "_start"; }
    static String countKey(int id) { return "t" + id + "_count"; }
    static String modKey(int id, int slot) { return "t" + id + "_mod_" + slot; }
    static String friendlyKey(int id) { return "t" + id + "_friendly"; }  // heat sink: own faction's portal

    static boolean isEndKey(String key) {
        return key.startsWith("t") && key.endsWith("_end");
    }

    static int heatsinkSecs(SharedPreferences p, int id) {
        return p.getInt(secsKey(id), Prefs.DEFAULT_COOLDOWN_SECS);
    }

    static boolean friendly(SharedPreferences p, int id) {
        return p.getBoolean(friendlyKey(id), false);
    }

    static int droneMins(SharedPreferences p, int id) {
        return p.getInt(minsKey(id), Prefs.DEFAULT_DRONE_MINS);
    }

    static int countdownStart(SharedPreferences p, int id) {
        return p.getInt(startKey(id), Prefs.DEFAULT_COUNTDOWN_START);
    }

    static int countdownValue(SharedPreferences p, int id) {
        return p.getInt(countKey(id), countdownStart(p, id));
    }

    static int[] mods(SharedPreferences p, int id) {
        int[] mods = new int[Prefs.MOD_SLOTS];
        for (int i = 0; i < Prefs.MOD_SLOTS; i++) {
            mods[i] = Prefs.clamp(p.getInt(modKey(id, i), 0), 0, Prefs.MH_NAMES.length - 1);
        }
        return mods;
    }

    /**
     * Turns the fixed four buttons of earlier versions into list entries, keeping their settings
     * and running timers. A button switched off there isn't added. A new install gets all four.
     */
    private static void migrate(SharedPreferences p) {
        SharedPreferences.Editor e = p.edit();
        List<Timer> list = new ArrayList<>();
        if (p.getBoolean("enabled_burnout", true)) {
            list.add(new Timer(BURNOUT, 1));
            copyLong(p, e, "burnout_end", endKey(1));
            copyLong(p, e, "burnout_end_alerted", endKey(1) + "_alerted");
        }
        if (p.getBoolean("enabled_cooldown", true)) {
            list.add(new Timer(HEATSINK, 2));
            copyLong(p, e, "cooldown_end", endKey(2));
            copyLong(p, e, "cooldown_end_alerted", endKey(2) + "_alerted");
            copyInt(p, e, "cooldown_secs", secsKey(2));
        }
        if (p.getBoolean("enabled_countdown", true)) {
            list.add(new Timer(MULTIHACK, 3));
            copyInt(p, e, "countdown_start", startKey(3));
            copyInt(p, e, "countdown_value", countKey(3));
            for (int i = 0; i < Prefs.MOD_SLOTS; i++) copyInt(p, e, "mod_slot_" + i, modKey(3, i));
        }
        if (p.getBoolean("enabled_drone", true)) {
            list.add(new Timer(DRONE, 4));
            copyLong(p, e, "drone_end", endKey(4));
            copyLong(p, e, "drone_end_alerted", endKey(4) + "_alerted");
            copyInt(p, e, "drone_mins", minsKey(4));
        }
        e.putInt(NEXT_ID, 5);
        save(e, list);
        e.commit();
    }

    private static void copyLong(SharedPreferences p, SharedPreferences.Editor e, String from, String to) {
        if (p.contains(from)) e.putLong(to, p.getLong(from, 0));
    }

    private static void copyInt(SharedPreferences p, SharedPreferences.Editor e, String from, String to) {
        if (p.contains(from)) e.putInt(to, p.getInt(from, 0));
    }
}
