package de.goofygecko.hackoverlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/** Shared preference keys and defaults used by the settings screen and the overlay. */
final class Prefs {
    static final String FILE = "hackoverlay";

    static final String BUTTON_COLOR = "button_color";
    static final String FACTION = "faction";          // index into FACTION_NAMES, -1 = not chosen yet
    static final String VERTICAL = "vertical";        // false = buttons in a row, true = in a column
    static final String BUTTON_SIZE = "button_size";
    static final String HIDDEN = "hidden";           // buttons hidden by the user, e.g. for a screenshot
    static final String SERVICE_ON = "service_on";   // overlay started and not stopped by the user

    /**
     * Portal mod slots 0–3 (top left, top right, bottom left, bottom right). Each holds an index into
     * the mod lists below: 0 = empty, then common, rare, very rare.
     */
    static final int MOD_SLOTS = 4;
    static final String[] MH_NAMES = {"Empty", "Common multihack", "Rare multihack", "Very rare multihack"};
    static final int[] MH_HACK_BONUS = {0, 4, 8, 12};
    static final int[] MH_ICONS = {0, R.drawable.mod_mh_common, R.drawable.mod_mh_rare, R.drawable.mod_mh_veryrare};
    static final int BASE_HACKS = 4;

    static final String[] HS_NAMES = {"Empty", "Common heat sink", "Rare heat sink", "Very rare heat sink"};
    static final int[] HS_PERCENT = {0, 20, 50, 70};  // cooldown decrease
    static final int[] HS_ICONS = {0, R.drawable.mod_hs_common, R.drawable.mod_hs_rare, R.drawable.mod_hs_veryrare};
    static final int FRIENDLY_COOLDOWN_SECS = 180;    // portal of the player's own faction

    static final int DEFAULT_COOLDOWN_SECS = 300;
    static final int DEFAULT_COUNTDOWN_START = 4;
    static final int MAX_COOLDOWN_SECS = 300;
    static final int MAX_COUNTDOWN = 50;
    static final int DEFAULT_DRONE_MINS = 60;
    static final int MAX_DRONE_MINS = 60;
    static final int MIN_BUTTON_SIZE = 33;
    static final int MAX_BUTTON_SIZE = 100;

    /** Button background choices, in the order shown on the settings screen. */
    static final String[] COLOR_NAMES = {"Black", "Green", "Red", "Blue"};
    static final int[] COLOR_VALUES = {
            Color.argb(200, 0, 0, 0),
            Color.argb(220, 9, 237, 70),
            Color.argb(220, 0xC6, 0x28, 0x28),
            Color.argb(220, 0x15, 0x65, 0xC0),
    };

    static final String[] FACTION_NAMES = {"Enlightenment", "Resistance"};
    static final int[] FACTION_COLORS = {Color.rgb(0x00, 0xD0, 0x00), Color.rgb(0x1E, 0x90, 0xFF)};
    static final int[] FACTION_LOGOS = {R.drawable.logo_enl, R.drawable.logo_res};
    /** Button color each faction starts with: green for Enlightenment, blue for Resistance. */
    static final int[] FACTION_BUTTON_COLORS = {1, 3};

    private Prefs() {}

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static int buttonSize(SharedPreferences p) {
        return clamp(p.getInt(BUTTON_SIZE, MAX_BUTTON_SIZE), MIN_BUTTON_SIZE, MAX_BUTTON_SIZE);
    }

    static boolean hidden(SharedPreferences p) {
        return p.getBoolean(HIDDEN, false);
    }

    static boolean serviceOn(SharedPreferences p) {
        return p.getBoolean(SERVICE_ON, false);
    }

    static boolean vertical(SharedPreferences p) {
        return p.getBoolean(VERTICAL, true);
    }

    /**
     * Hacks a portal allows with these mods: the base hacks plus the full bonus of the rarest
     * multihack, plus half the bonus of every other multihack.
     */
    static int portalHacks(int[] mods) {
        int best = 0, sum = 0;
        for (int m : mods) {
            int bonus = MH_HACK_BONUS[m];
            best = Math.max(best, bonus);
            sum += bonus;
        }
        return BASE_HACKS + best + (sum - best) / 2;
    }

    /**
     * Hack cooldown in seconds with these heat sinks: the base cooldown (180 s on a portal of the
     * player's own faction, 300 s otherwise), times (1 - h) for the rarest heat sink and
     * (1 - x / 2) for each further one, rounded to whole seconds (.5 and above rounds up).
     */
    static int heatsinkCooldown(int[] mods, boolean friendly) {
        int[] pct = new int[mods.length];
        for (int i = 0; i < mods.length; i++) pct[i] = HS_PERCENT[mods[i]];
        java.util.Arrays.sort(pct);
        // Whole-number maths: (100 - h) / 100 for the rarest, (200 - x) / 200 for the others.
        long num = friendly ? FRIENDLY_COOLDOWN_SECS : DEFAULT_COOLDOWN_SECS;
        long den = 1;
        for (int i = pct.length - 1; i >= 0; i--) {
            boolean rarest = i == pct.length - 1;
            num *= (rarest ? 100 : 200) - pct[i];
            den *= rarest ? 100 : 200;
        }
        return (int) ((2 * num + den) / (2 * den));
    }

    static int faction(SharedPreferences p) {
        return clamp(p.getInt(FACTION, -1), -1, FACTION_NAMES.length - 1);
    }

    static int buttonColorIndex(SharedPreferences p) {
        int faction = faction(p);
        int fallback = faction < 0 ? 0 : FACTION_BUTTON_COLORS[faction];
        return clamp(p.getInt(BUTTON_COLOR, fallback), 0, COLOR_VALUES.length - 1);
    }

    /** True for backgrounds bright enough to need dark text (the green one). */
    static boolean lightButtonColor(SharedPreferences p) {
        return buttonColorIndex(p) == 1;
    }

    static int buttonColor(SharedPreferences p) {
        return COLOR_VALUES[buttonColorIndex(p)];
    }

    static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
