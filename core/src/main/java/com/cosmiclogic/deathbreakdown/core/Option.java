package com.cosmiclogic.deathbreakdown.core;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One setting: its key in deathbreakdown.properties, where it shows in the settings screen, what values it takes and
 * a one-line explanation. The settings screen, the config file, /deathbreakdown settings and the self-tests all walk
 * this list, so every setting is handled (and tested) the same way.
 */
public final class Option {
    public enum Kind { BOOL, INT, DECIMAL, CHOICE, COLOR }

    public final String key, page, label, help;
    public final Kind kind;
    public final double min, max, step;
    public final String[] choices;
    /** Only used by the client (screens, HUD, marker, sounds); a dedicated server ignores it. */
    public final boolean clientOnly;
    public final Palette.Slot slot; // COLOR only

    private Option(String key, String page, String label, String help, Kind kind, double min, double max, double step,
                   String[] choices, boolean clientOnly, Palette.Slot slot) {
        this.key = key; this.page = page; this.label = label; this.help = help; this.kind = kind;
        this.min = min; this.max = max; this.step = step; this.choices = choices; this.clientOnly = clientOnly; this.slot = slot;
    }

    public static final String GENERAL = "General", RECAP = "Recap screen", COLORS = "Colors", MARKER = "Death marker",
            HUD = "HUD", SOUND = "Sound";
    public static final String[] PAGES = {GENERAL, RECAP, COLORS, MARKER, HUD, SOUND};

    public static final String[] CORNERS = {"top_left", "top_right", "bottom_left", "bottom_right"};
    public static final String[] MARKER_STYLES = {"off", "particles", "beam"};
    public static final String[] PARTICLES = {"dust", "flame", "end_rod", "happy_villager", "heart", "portal", "crit"};
    public static final String[] SOUNDS = {"page", "bell", "xp"};

    public static final List<Option> ALL;

    static {
        List<Option> o = new ArrayList<Option>();
        // General (server side too)
        o.add(integer("windowSeconds", GENERAL, "Time window", "How many seconds before death to include (1-60).", 1, 60, 1, false));
        o.add(bool("hearts", GENERAL, "Amounts", "Show damage in hearts (on) or health points (off).", false));
        o.add(integer("historySize", GENERAL, "Deaths kept", "How many of your last deaths to keep (1-20).", 1, 20, 1, false));
        o.add(bool("saveHistory", GENERAL, "Keep after restart", "Keep your last deaths in the world folder so they survive leaving and restarting.", false));
        o.add(bool("chatSummary", GENERAL, "Chat summary", "Post a one-line summary in chat when you die.", false));
        o.add(bool("showTip", GENERAL, "Tip line", "Show one short tip, e.g. \"Lava did 89% of the damage.\"", false));
        o.add(integer("updateTicks", GENERAL, "Update speed", "How often the HUD and death marker update, in ticks (1-40; 20 ticks = 1 second).", 1, 40, 1, true));
        // Recap screen
        o.add(bool("openOnDeath", RECAP, "Open on death", "Open the recap by itself when you die.", true));
        o.add(bool("deathButton", RECAP, "Death screen button", "Add the \"Death Breakdown\" button to the death screen.", true));
        o.add(choice("deathButtonCorner", RECAP, "Button corner", "Which corner of the death screen the button goes in.", CORNERS, true));
        o.add(integer("deathButtonX", RECAP, "Button offset X", "Pixels from the side of the screen (0-200).", 0, 200, 2, true));
        o.add(integer("deathButtonY", RECAP, "Button offset Y", "Pixels from the top or bottom of the screen (0-200).", 0, 200, 2, true));
        o.add(bool("showDeathMessage", RECAP, "Death message", "Show the death message at the top.", true));
        o.add(bool("showSummary", RECAP, "Summary line", "Show the one-line summary above the table.", true));
        o.add(bool("showLegend", RECAP, "Legend", "Explain the Armor, Enchants and Other columns under the table.", true));
        o.add(bool("compact", RECAP, "Compact mode", "Tighter rows, no death message, tip or legend: more hits fit.", true));
        o.add(bool("stripes", RECAP, "Row stripes", "Shade every other row.", true));
        o.add(integer("panelOpacity", RECAP, "Panel darkness", "A dark panel behind the table, 0 (none) to 100 (solid).", 0, 100, 10, true));
        o.add(bool("showTime", RECAP, "Column: Time", "Seconds before death.", false));
        o.add(bool("showSource", RECAP, "Column: Hit by", "Who or what hit you, with the weapon.", false));
        o.add(bool("showType", RECAP, "Column: Type", "The damage type (fall, fire, melee...).", false));
        o.add(bool("showDamage", RECAP, "Column: Damage", "Damage before armor.", false));
        o.add(bool("showArmor", RECAP, "Column: Armor", "What armor and toughness stopped.", false));
        o.add(bool("showEnchants", RECAP, "Column: Enchants", "What Protection-type enchantments stopped.", false));
        o.add(bool("showOther", RECAP, "Column: Other", "What Resistance, absorption hearts and shields stopped.", false));
        o.add(bool("showHealthLeft", RECAP, "Column: Health left", "Your health after each hit.", false));
        // Colors
        o.add(choice("colorPreset", COLORS, "Preset", "A ready-made look. \"colorblind\" uses colors told apart with color blindness.", Palette.PRESETS, false));
        for (Palette.Slot s : Palette.Slot.values()) {
            o.add(new Option(s.key(), COLORS, s.label, "A hex code like #FF8800, or blank to use the preset's color.",
                    Kind.COLOR, 0, 0, 0, null, false, s));
        }
        // Death marker
        o.add(choice("markerStyle", MARKER, "Marker", "Mark the spot where you died: particles, a beam (a line straight up), or off.", MARKER_STYLES, true));
        o.add(choice("markerParticle", MARKER, "Particle", "Which particle the marker uses. Size and color only change \"dust\".", PARTICLES, true));
        o.add(decimal("markerSize", MARKER, "Particle size", "Size of dust particles (0.5-4).", 0.5, 4, 0.25, true));
        o.add(decimal("markerSpacing", MARKER, "Spacing", "Blocks between particles (0.25-4). Smaller = denser.", 0.25, 4, 0.25, true));
        o.add(integer("markerMinutes", MARKER, "How long", "Minutes the marker stays after you die (1-120).", 1, 120, 1, true));
        o.add(integer("markerRange", MARKER, "Range", "Show the marker from this many blocks away (8-256).", 8, 256, 8, true));
        o.add(bool("markerUntilReached", MARKER, "Hide when reached", "Remove the marker once you get back to the spot.", true));
        // HUD
        o.add(bool("hudEnabled", HUD, "Death HUD", "A small box showing your last death: what killed you, how far and how long ago.", true));
        o.add(choice("hudCorner", HUD, "Corner", "Which corner of the screen the HUD goes in.", CORNERS, true));
        o.add(integer("hudX", HUD, "Offset X", "Pixels from the side of the screen (0-500).", 0, 500, 2, true));
        o.add(integer("hudY", HUD, "Offset Y", "Pixels from the top or bottom of the screen (0-500).", 0, 500, 2, true));
        o.add(integer("hudScale", HUD, "Size", "HUD size in percent (50-300).", 50, 300, 10, true));
        o.add(integer("hudOpacity", HUD, "Background darkness", "How dark the HUD background is (0-100).", 0, 100, 5, true));
        o.add(bool("hudBackground", HUD, "Background", "Draw a background behind the HUD text.", true));
        o.add(bool("hudShowKiller", HUD, "Line: killed by", "Show what killed you.", true));
        o.add(bool("hudShowDistance", HUD, "Line: distance", "Show how far away and which way the spot is.", true));
        o.add(bool("hudShowTime", HUD, "Line: time", "Show how long ago you died.", true));
        o.add(bool("hudShowCoords", HUD, "Line: coordinates", "Show the coordinates of the spot.", true));
        o.add(bool("hudCompact", HUD, "Compact mode", "Everything on one short line.", true));
        o.add(integer("hudMinutes", HUD, "How long", "Minutes the HUD stays after you die (1-120).", 1, 120, 1, true));
        // Sound
        o.add(bool("soundOnOpen", SOUND, "Sound", "Play a sound when the recap opens.", true));
        o.add(choice("soundKind", SOUND, "Which sound", "page (a book page), bell or xp (the experience pickup).", SOUNDS, true));
        o.add(integer("soundVolume", SOUND, "Volume", "Sound volume in percent (0-100).", 0, 100, 5, true));
        ALL = Collections.unmodifiableList(o);
    }

    public static Option get(String key) {
        for (Option o : ALL) if (o.key.equalsIgnoreCase(key)) return o;
        return null;
    }

    public static List<Option> page(String page) {
        List<Option> out = new ArrayList<Option>();
        for (Option o : ALL) if (o.page.equals(page)) out.add(o);
        return out;
    }

    private static Option bool(String k, String p, String l, String h, boolean c) { return new Option(k, p, l, h, Kind.BOOL, 0, 1, 1, null, c, null); }
    private static Option integer(String k, String p, String l, String h, int lo, int hi, int step, boolean c) { return new Option(k, p, l, h, Kind.INT, lo, hi, step, null, c, null); }
    private static Option decimal(String k, String p, String l, String h, double lo, double hi, double step, boolean c) { return new Option(k, p, l, h, Kind.DECIMAL, lo, hi, step, null, c, null); }
    private static Option choice(String k, String p, String l, String h, String[] ch, boolean c) { return new Option(k, p, l, h, Kind.CHOICE, 0, 0, 0, ch, c, null); }

    private Field field() {
        try { return RecapConfig.class.getField(key); } catch (NoSuchFieldException e) { throw new IllegalStateException("no field " + key); }
    }

    /** The value as it's written in the file ("true", "10", "1.5", "dust", "#FF8800" or "" for a color). */
    public String get(RecapConfig c) {
        if (kind == Kind.COLOR) return c.colorOverride(slot);
        try {
            Object v = field().get(c);
            if (kind == Kind.DECIMAL) return fmt(((Number) v).doubleValue());
            return String.valueOf(v);
        } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    public String getDefault() { return get(new RecapConfig()); }

    /**
     * Sets the value from text. Returns null if it was fine, otherwise what was wrong (the setting then keeps or
     * gets the closest good value: out-of-range numbers are clamped, anything else falls back to the default).
     */
    public String set(RecapConfig c, String raw) {
        String v = raw == null ? "" : raw.trim();
        String def = getDefault();
        try {
            switch (kind) {
                case BOOL: {
                    String w = v.toLowerCase(Locale.ROOT);
                    if (w.equals("true") || w.equals("yes") || w.equals("on") || w.equals("1")) { field().setBoolean(c, true); return null; }
                    if (w.equals("false") || w.equals("no") || w.equals("off") || w.equals("0")) { field().setBoolean(c, false); return null; }
                    field().setBoolean(c, Boolean.parseBoolean(def));
                    return key + "=" + v + " isn't true or false: using " + def;
                }
                case INT: {
                    int i;
                    try { i = Integer.parseInt(v); } catch (NumberFormatException e) {
                        field().setInt(c, Integer.parseInt(def));
                        return key + "=" + v + " isn't a whole number: using " + def;
                    }
                    int out = (int) Math.max(min, Math.min(max, i));
                    field().setInt(c, out);
                    return out == i ? null : key + "=" + i + " is outside " + (int) min + "-" + (int) max + ": using " + out;
                }
                case DECIMAL: {
                    double d;
                    try { d = Double.parseDouble(v.replace(',', '.')); } catch (NumberFormatException e) { d = Double.NaN; }
                    if (Double.isNaN(d) || Double.isInfinite(d)) {
                        field().setDouble(c, Double.parseDouble(def));
                        return key + "=" + v + " isn't a number: using " + def;
                    }
                    double out = Math.max(min, Math.min(max, d));
                    field().setDouble(c, out);
                    return out == d ? null : key + "=" + fmt(d) + " is outside " + fmt(min) + "-" + fmt(max) + ": using " + fmt(out);
                }
                case CHOICE: {
                    for (String ch : choices) if (ch.equalsIgnoreCase(v.replace(' ', '_').replace('-', '_'))) { field().set(c, ch); return null; }
                    field().set(c, def);
                    return key + "=" + v + " isn't one of " + join(choices) + ": using " + def;
                }
                case COLOR: {
                    if (v.isEmpty()) { c.setColorOverride(slot, ""); return null; }
                    Integer rgb = Palette.parseHex(v);
                    if (rgb == null) {
                        c.setColorOverride(slot, "");
                        return key + "=" + v + " isn't a color like #FF8800: using the preset's color";
                    }
                    c.setColorOverride(slot, Palette.hex(rgb));
                    return null;
                }
                default: return null;
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public void reset(RecapConfig c) { set(c, getDefault()); }

    /** The next value for a click in the settings screen (wraps around; back = the one before). */
    public String next(RecapConfig c, boolean back) {
        String cur = get(c);
        switch (kind) {
            case BOOL: return String.valueOf(!Boolean.parseBoolean(cur));
            case INT: case DECIMAL: {
                double v = Double.parseDouble(cur) + (back ? -step : step);
                if (v > max + 1e-9) v = min;
                if (v < min - 1e-9) v = max;
                v = Math.round(v / step) * step;
                return kind == Kind.INT ? String.valueOf((int) Math.round(v)) : fmt(v);
            }
            case CHOICE: {
                int i = 0;
                for (int k = 0; k < choices.length; k++) if (choices[k].equals(cur)) i = k;
                return choices[(i + (back ? choices.length - 1 : 1)) % choices.length];
            }
            default: return cur;
        }
    }

    /** What the settings screen shows on the button, e.g. "Time window: 10 s". */
    public String display(RecapConfig c) {
        String v = get(c);
        switch (kind) {
            case BOOL:
                if (key.equals("hearts")) return label + ": " + (Boolean.parseBoolean(v) ? "hearts" : "health points");
                return label + ": " + (Boolean.parseBoolean(v) ? "ON" : "OFF");
            case INT:
                if (key.equals("windowSeconds")) return label + ": " + v + " s";
                if (key.endsWith("Minutes")) return label + ": " + v + " min";
                if (key.equals("hudScale") || key.equals("soundVolume") || key.endsWith("Opacity")) return label + ": " + v + "%";
                if (key.equals("markerRange")) return label + ": " + v + " blocks";
                if (key.equals("updateTicks")) return label + ": every " + v + (v.equals("1") ? " tick" : " ticks");
                return label + ": " + v;
            case DECIMAL: return label + ": " + v;
            case CHOICE: return label + ": " + v.replace('_', ' ');
            case COLOR: return label + ": " + Palette.hex(Palette.of(c).get(slot)) + (v.isEmpty() ? "" : " *");
            default: return label;
        }
    }

    /** A value that is not allowed, for tests ("banana" for most, a number far out of range for numbers). */
    public String badValue() {
        switch (kind) {
            case INT: case DECIMAL: return "banana";
            case COLOR: return "#GGHHII";
            default: return "banana";
        }
    }

    /** A good value that differs from the default, for tests. */
    public String otherValue() {
        String def = getDefault();
        switch (kind) {
            case BOOL: return String.valueOf(!Boolean.parseBoolean(def));
            case INT: case DECIMAL: {
                double d = Double.parseDouble(def);
                double v = d + step <= max ? d + step : d - step;
                return kind == Kind.INT ? String.valueOf((int) Math.round(v)) : fmt(v);
            }
            case CHOICE: return choices[choices[0].equals(def) ? 1 : 0];
            case COLOR: return "#12AB34";
            default: return def;
        }
    }

    static String fmt(double d) {
        String s = String.format(Locale.ROOT, "%.2f", d);
        while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String join(String[] a) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < a.length; i++) b.append(i == 0 ? "" : ", ").append(a[i]);
        return b.toString();
    }
}
