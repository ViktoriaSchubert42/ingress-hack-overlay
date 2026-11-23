package de.goofygecko.hackoverlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Settings page of a Remaining Hacks Countdown (multihack) or a Hacking Speed Timer (heat sink): the portal's mods, and the number
 * of hacks or the cooldown they result in.
 */
public class ModsActivity extends Activity {
    static final String EXTRA_TIMER_ID = "timer_id";
    static final String EXTRA_TYPE = "type";
    static final String EXTRA_LABEL = "label";

    private SharedPreferences prefs;
    private int id;
    private boolean heatsink;  // false = multihack
    private String[] modNames;
    private int[] modIcons;
    private int max;
    private final FrameLayout[] slots = new FrameLayout[Prefs.MOD_SLOTS];
    private TextView result;
    private EditText valueInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        id = getIntent().getIntExtra(EXTRA_TIMER_ID, -1);
        heatsink = Timers.HEATSINK.equals(getIntent().getStringExtra(EXTRA_TYPE));
        String name = getIntent().getStringExtra(EXTRA_LABEL);
        if (id < 0) {
            finish();
            return;
        }
        modNames = heatsink ? Prefs.HS_NAMES : Prefs.MH_NAMES;
        modIcons = heatsink ? Prefs.HS_ICONS : Prefs.MH_ICONS;
        max = heatsink ? Prefs.MAX_COOLDOWN_SECS : Prefs.MAX_COUNTDOWN;

        int pad = dp(20);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad * 2, pad, pad);

        TextView title = new TextView(this);
        title.setText(name != null ? name : Timers.name(heatsink ? Timers.HEATSINK : Timers.MULTIHACK));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        col.addView(title);

        TextView help = new TextView(this);
        if (heatsink) {
            help.setText("Choose whose portal it is, then tap a mod slot to choose the heat sink installed in it. "
                    + "The cooldown is " + Prefs.FRIENDLY_COOLDOWN_SECS + " seconds on a portal of your own faction and "
                    + Prefs.DEFAULT_COOLDOWN_SECS + " seconds otherwise. The rarest heat sink takes off its full "
                    + "percentage, every other heat sink half of its percentage:\n"
                    + "• Common −20 % (−10 % as additional mod)\n"
                    + "• Rare −50 % (−25 % as additional mod)\n"
                    + "• Very rare −70 % (−35 % as additional mod)\n\n"
                    + "Changing the portal or a slot sets the cooldown to the new time.");
        } else {
            help.setText("Tap a mod slot to choose the multihack installed in the portal. "
                    + "A portal allows " + Prefs.BASE_HACKS + " hacks, plus the full bonus of its rarest multihack "
                    + "and half the bonus of every other multihack:\n"
                    + "• Common +4 (+2 as additional mod)\n"
                    + "• Rare +8 (+4 as additional mod)\n"
                    + "• Very rare +12 (+6 as additional mod)\n\n"
                    + "Changing a slot sets the countdown to the new number of hacks.");
        }
        help.setPadding(0, dp(12), 0, dp(16));
        col.addView(help);

        if (heatsink) {
            col.addView(label("Portal"));
            RadioGroup owner = new RadioGroup(this);
            RadioButton own = new RadioButton(this);
            own.setId(1);
            own.setText("Portal of own faction (" + Prefs.FRIENDLY_COOLDOWN_SECS + " s)");
            owner.addView(own);
            RadioButton other = new RadioButton(this);
            other.setId(2);
            other.setText("Portal neutral or enemy faction (" + Prefs.DEFAULT_COOLDOWN_SECS + " s)");
            owner.addView(other);
            owner.check(Timers.friendly(prefs, id) ? 1 : 2);
            owner.setOnCheckedChangeListener((g, checked) -> {
                prefs.edit().putBoolean(Timers.friendlyKey(id), checked == 1).apply();
                modsChanged();
            });
            col.addView(owner);
        }

        addLinkChoice(col);

        col.addView(label("Portal mods"));
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_HORIZONTAL);
            for (int c = 0; c < 2; c++) {
                int slot = row * 2 + c;
                slots[slot] = slotView(slot);
                line.addView(slots[slot]);
            }
            col.addView(line);
        }

        result = new TextView(this);
        result.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        result.setPadding(0, dp(12), 0, dp(4));
        col.addView(result);

        Button clear = new Button(this);
        clear.setText("Remove all mods");
        clear.setOnClickListener(v -> {
            SharedPreferences.Editor e = prefs.edit();
            for (int i = 0; i < Prefs.MOD_SLOTS; i++) e.putInt(Timers.modKey(id, i), 0);
            e.apply();
            modsChanged();
        });
        col.addView(clear);

        col.addView(label((heatsink ? "Cooldown in seconds (0–" : "Countdown start value (0–") + max
                + "). Filled in from the " + (heatsink ? "portal and mods" : "mods") + "; you can also type a number."));
        valueInput = new EditText(this);
        valueInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        valueInput.setText(String.valueOf(savedValue()));
        valueInput.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(valueInput);

        Button save = new Button(this);
        save.setText("Save");
        save.setOnClickListener(v -> {
            saveValue();
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
            finish();
        });
        col.addView(save);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(col);
        MainActivity.keepClearOfSystemBars(scroll);
        setContentView(scroll);
        showMods();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (valueInput != null) saveValue();
    }

    /**
     * "Linked with": pairs a Hacking Speed Timer with a Remaining Hacks Countdown for the same
     * portal, so one tap on either button counts a hack on both.
     */
    private void addLinkChoice(LinearLayout col) {
        List<Timers.Timer> list = Timers.load(prefs);
        Timers.Timer self = Timers.find(list, id);
        if (self == null) return;
        String otherType = heatsink ? Timers.MULTIHACK : Timers.HEATSINK;

        col.addView(label("Linked with"));
        TextView help = new TextView(this);
        help.setText("Link it with a " + Timers.name(otherType) + " when both are about hacks on the same "
                + "portal. Then one tap on either button counts a hack: it starts the timer and counts "
                + "one hack down. Long-press still resets only the button you press.");
        help.setPadding(0, dp(4), 0, dp(4));
        col.addView(help);

        RadioGroup choice = new RadioGroup(this);
        RadioButton none = new RadioButton(this);
        none.setId(View.generateViewId());
        none.setText("Not linked");
        choice.addView(none);
        int checked = none.getId();
        Timers.Timer current = Timers.linked(prefs, list, self);
        boolean any = false;
        for (int i = 0; i < list.size(); i++) {
            Timers.Timer t = list.get(i);
            if (!otherType.equals(t.type)) continue;
            any = true;
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            Timers.Timer partner = Timers.linked(prefs, list, t);
            String taken = partner != null && partner.id != id ? "  (now linked with " + number(list, partner) + ")" : "";
            rb.setText(number(list, t) + " · " + Timers.name(t.type) + taken);
            rb.setTag(t.id);
            choice.addView(rb);
            if (current != null && current.id == t.id) checked = rb.getId();
        }
        if (!any) {
            TextView missing = new TextView(this);
            missing.setText("Add a " + Timers.name(otherType) + " on the settings page to link it with this one.");
            missing.setPadding(0, 0, 0, dp(8));
            col.addView(missing);
            return;
        }
        choice.check(checked);
        choice.setOnCheckedChangeListener((g, checkedId) -> {
            View rb = g.findViewById(checkedId);
            int other = rb != null && rb.getTag() instanceof Integer ? (Integer) rb.getTag() : -1;
            if (heatsink) {
                Timers.setLink(prefs, id, other);
            } else {
                Timers.Timer old = Timers.linked(prefs, Timers.load(prefs), self);
                if (old != null) Timers.setLink(prefs, old.id, -1);
                if (other >= 0) Timers.setLink(prefs, other, id);
            }
        });
        col.addView(choice);
    }

    /** The timer's number in the settings list, starting at 1. */
    private static int number(List<Timers.Timer> list, Timers.Timer t) {
        return list.indexOf(t) + 1;
    }

    private int savedValue() {
        return heatsink ? Timers.heatsinkSecs(prefs, id) : Timers.countdownStart(prefs, id);
    }

    /** What the portal and mods work out to: seconds of cooldown, or hacks. */
    private int calculated() {
        int[] mods = Timers.mods(prefs, id);
        return heatsink ? Prefs.heatsinkCooldown(mods, Timers.friendly(prefs, id)) : Prefs.portalHacks(mods);
    }

    /** Saves a typed value. */
    private void saveValue() {
        int value;
        try {
            value = Integer.parseInt(valueInput.getText().toString().trim());
        } catch (NumberFormatException ex) {
            value = savedValue();
        }
        setValue(Prefs.clamp(value, 0, max));
    }

    /** A changed multihack start value also resets the countdown to it. */
    private void setValue(int value) {
        valueInput.setText(String.valueOf(value));
        if (value == savedValue()) return;
        if (heatsink) {
            prefs.edit().putInt(Timers.secsKey(id), value).apply();
        } else {
            prefs.edit().putInt(Timers.startKey(id), value).putInt(Timers.countKey(id), value).apply();
        }
    }

    private void modsChanged() {
        showMods();
        setValue(Prefs.clamp(calculated(), 0, max));
    }

    private void showMods() {
        int[] mods = Timers.mods(prefs, id);
        for (int i = 0; i < Prefs.MOD_SLOTS; i++) showSlot(slots[i], mods[i]);
        result.setText(heatsink
                ? "Cooldown: " + calculated() + " seconds\n" + heatsinkBreakdown(mods)
                : "Hacks per portal: " + calculated() + "\n" + multihackBreakdown(mods));
    }

    /** E.g. "4 base + 12 very rare + 4 rare (half) = 20". */
    private static String multihackBreakdown(int[] mods) {
        int[] sorted = mods.clone();
        Arrays.sort(sorted);
        StringBuilder sb = new StringBuilder().append(Prefs.BASE_HACKS).append(" base");
        boolean first = true;
        for (int i = sorted.length - 1; i >= 0; i--) {
            int m = sorted[i];
            if (m == 0) break;
            int bonus = Prefs.MH_HACK_BONUS[m];
            sb.append(" + ").append(first ? bonus : bonus / 2).append(' ').append(rarity(Prefs.MH_NAMES[m]));
            if (!first) sb.append(" (half)");
            first = false;
        }
        return sb.append(" = ").append(Prefs.portalHacks(mods)).toString();
    }

    /** E.g. "300 s × 0.30 very rare × 0.75 rare (half) = 68 s". */
    private String heatsinkBreakdown(int[] mods) {
        int[] sorted = mods.clone();
        Arrays.sort(sorted);
        boolean friendly = Timers.friendly(prefs, id);
        StringBuilder sb = new StringBuilder()
                .append(friendly ? Prefs.FRIENDLY_COOLDOWN_SECS : Prefs.DEFAULT_COOLDOWN_SECS).append(" s");
        boolean first = true;
        for (int i = sorted.length - 1; i >= 0; i--) {
            int m = sorted[i];
            if (m == 0) break;
            int pct = Prefs.HS_PERCENT[m];
            double factor = first ? 1 - pct / 100.0 : 1 - pct / 200.0;
            sb.append(String.format(Locale.US, " × %.2f ", factor)).append(rarity(Prefs.HS_NAMES[m]));
            if (!first) sb.append(" (half)");
            first = false;
        }
        return sb.append(" = ").append(Prefs.heatsinkCooldown(mods, friendly)).append(" s").toString();
    }

    /** "Very rare heat sink" → "very rare". */
    private static String rarity(String modName) {
        return modName.replace(" multihack", "").replace(" heat sink", "").toLowerCase(Locale.US);
    }

    private FrameLayout slotView(int slot) {
        FrameLayout f = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(110), dp(110));
        lp.setMargins(dp(8), dp(8), dp(8), dp(8));
        f.setLayoutParams(lp);
        GradientDrawable border = new GradientDrawable();
        border.setCornerRadius(dp(10));
        border.setStroke(dp(2), Color.GRAY, dp(6), dp(4));
        f.setBackground(border);
        f.setOnClickListener(v -> pickMod(slot));

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(80), dp(90), Gravity.CENTER);
        f.addView(icon, ilp);

        TextView empty = new TextView(this);
        empty.setGravity(Gravity.CENTER);
        f.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        f.setContentDescription("Mod slot " + (slot + 1));
        return f;
    }

    private void showSlot(FrameLayout f, int mod) {
        ImageView icon = (ImageView) f.getChildAt(0);
        TextView empty = (TextView) f.getChildAt(1);
        if (mod == 0) {
            icon.setImageDrawable(null);
            empty.setText("Empty\n(tap to add)");
        } else {
            icon.setImageDrawable(modIcon(mod));
            empty.setText("");
        }
    }

    private void pickMod(int slot) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(8), dp(16), dp(8));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Mod slot " + (slot + 1))
                .setView(list)
                .setNegativeButton("Cancel", null)
                .create();
        for (int m = 0; m < modNames.length; m++) {
            int mod = m;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));
            ImageView icon = new ImageView(this);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImageDrawable(modIcon(m));
            row.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(56)));
            TextView text = new TextView(this);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            text.setPadding(dp(16), 0, 0, 0);
            String effect = heatsink ? "−" + Prefs.HS_PERCENT[m] + " %" : "+" + Prefs.MH_HACK_BONUS[m];
            text.setText(m == 0 ? "Empty slot (clear slot)" : modNames[m] + "  (" + effect + ")");
            row.addView(text);
            row.setOnClickListener(v -> {
                prefs.edit().putInt(Timers.modKey(id, slot), mod).apply();
                modsChanged();
                dialog.dismiss();
            });
            list.addView(row);
        }
        dialog.show();
    }

    /** The small pixel-art mod pictures, scaled up without blurring. */
    private Drawable modIcon(int mod) {
        Drawable d = getDrawable(mod == 0 ? R.drawable.mod_empty : modIcons[mod]);
        if (d instanceof BitmapDrawable) ((BitmapDrawable) d).setFilterBitmap(false);
        return d;
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
