package com.cosmiclogic.deathbreakdown.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Every color Death Breakdown draws, by name. A preset gives all of them; any single color can be replaced with a
 * hex code ({@code #FF8800}) in the settings. {@link #DEFAULT} is exactly the look of 1.1.0.
 */
public final class Palette {

    /** A color the mod draws. The default values are the 1.1.0 colors. */
    public enum Slot {
        TITLE("Title", 0xFFAA00),
        INFO("Death message", 0xAAAAAA),
        SUMMARY("Summary", 0xFFFFFF),
        TIP("Tip line", 0xFFDD55),
        MUTED("Small print", 0x777777),
        HEADER("Column titles", 0xAAAAAA),
        TIME("Time", 0xAAAAAA),
        SOURCE("Hit by", 0xFFFFFF),
        FINAL_BLOW("Final blow", 0xFFAA00),
        FINAL_ROW("Final blow row", 0xAA6600),
        DAMAGE("Damage", 0xFF5555),
        ARMOR("Armor", 0xAAAAAA),
        ENCHANTS("Enchants", 0xC08CFF),
        OTHER("Other", 0x55AAFF),
        HEALTH("Health left", 0x55FF55),
        HEALTH_ZERO("Health at death", 0xAA0000),
        TOTAL("Totals", 0xFFFFFF),
        KIND_PLAYER("Type: player", 0x55FFFF),
        KIND_MOB("Type: mob", 0xFF7777),
        KIND_PROJECTILE("Type: projectile", 0xFFDD55),
        KIND_EXPLOSION("Type: explosion", 0xFF9944),
        KIND_ENVIRONMENT("Type: environment", 0x88CC66),
        KIND_MAGIC("Type: magic", 0xC08CFF),
        KIND_OTHER("Type: other", 0xAAAAAA),
        MARKER("Death marker", 0xFF3333),
        HUD_TEXT("HUD text", 0xFFFFFF),
        HUD_ACCENT("HUD accent", 0xFFAA00);

        public final String label;
        public final int def;
        Slot(String label, int def) { this.label = label; this.def = def; }

        /** The settings key, e.g. "colorFinalBlow". */
        public String key() {
            StringBuilder b = new StringBuilder("color");
            for (String part : name().toLowerCase(Locale.ROOT).split("_")) {
                b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            return b.toString();
        }
    }

    /** Preset names, in the order the settings show them. */
    public static final String[] PRESETS = {"default", "colorblind", "high_contrast", "soft", "mono"};

    public static final Palette DEFAULT = preset("default");

    private final int[] rgb = new int[Slot.values().length];

    private Palette() {}

    public int get(Slot s) { return rgb[s.ordinal()]; }

    /** A preset by name; unknown names give the default look. */
    public static Palette preset(String name) {
        Palette p = new Palette();
        for (Slot s : Slot.values()) p.rgb[s.ordinal()] = s.def;
        Map<Slot, Integer> m = presetColors(name == null ? "" : name.toLowerCase(Locale.ROOT));
        for (Map.Entry<Slot, Integer> e : m.entrySet()) p.rgb[e.getKey().ordinal()] = e.getValue();
        return p;
    }

    /** The preset, then each color the player typed in (a blank or broken one keeps the preset's color). */
    public static Palette of(RecapConfig c) {
        Palette p = preset(c.colorPreset);
        for (Slot s : Slot.values()) {
            Integer v = parseHex(c.colorOverride(s));
            if (v != null) p.rgb[s.ordinal()] = v;
        }
        return p;
    }

    private static Map<Slot, Integer> presetColors(String name) {
        Map<Slot, Integer> m = new LinkedHashMap<Slot, Integer>();
        if (name.equals("colorblind")) {
            // Okabe-Ito colors: told apart with red-green and blue-yellow color blindness.
            m.put(Slot.TITLE, 0xE69F00);
            m.put(Slot.TIP, 0xF0E442);
            m.put(Slot.FINAL_BLOW, 0xF0E442);
            m.put(Slot.FINAL_ROW, 0x8A6A00);
            m.put(Slot.DAMAGE, 0xE69F00);
            m.put(Slot.ARMOR, 0xBBBBBB);
            m.put(Slot.ENCHANTS, 0xCC79A7);
            m.put(Slot.OTHER, 0x56B4E9);
            m.put(Slot.HEALTH, 0x009E73);
            m.put(Slot.HEALTH_ZERO, 0xD55E00);
            m.put(Slot.KIND_PLAYER, 0x56B4E9);
            m.put(Slot.KIND_MOB, 0xD55E00);
            m.put(Slot.KIND_PROJECTILE, 0xF0E442);
            m.put(Slot.KIND_EXPLOSION, 0xE69F00);
            m.put(Slot.KIND_ENVIRONMENT, 0x009E73);
            m.put(Slot.KIND_MAGIC, 0xCC79A7);
            m.put(Slot.MARKER, 0x56B4E9);
            m.put(Slot.HUD_ACCENT, 0xE69F00);
        } else if (name.equals("high_contrast")) {
            m.put(Slot.TITLE, 0xFFFF00);
            m.put(Slot.INFO, 0xFFFFFF);
            m.put(Slot.TIP, 0xFFFF00);
            m.put(Slot.MUTED, 0xCCCCCC);
            m.put(Slot.HEADER, 0xFFFFFF);
            m.put(Slot.TIME, 0xFFFFFF);
            m.put(Slot.FINAL_BLOW, 0xFFFF00);
            m.put(Slot.FINAL_ROW, 0x606000);
            m.put(Slot.DAMAGE, 0xFF4444);
            m.put(Slot.ARMOR, 0xFFFFFF);
            m.put(Slot.ENCHANTS, 0xFF66FF);
            m.put(Slot.OTHER, 0x55FFFF);
            m.put(Slot.HEALTH, 0x55FF55);
            m.put(Slot.HEALTH_ZERO, 0xFF4444);
            m.put(Slot.KIND_PLAYER, 0x55FFFF);
            m.put(Slot.KIND_MOB, 0xFF6666);
            m.put(Slot.KIND_PROJECTILE, 0xFFFF55);
            m.put(Slot.KIND_EXPLOSION, 0xFFAA00);
            m.put(Slot.KIND_ENVIRONMENT, 0x55FF55);
            m.put(Slot.KIND_MAGIC, 0xFF66FF);
            m.put(Slot.KIND_OTHER, 0xFFFFFF);
            m.put(Slot.MARKER, 0xFFFF00);
            m.put(Slot.HUD_ACCENT, 0xFFFF00);
        } else if (name.equals("soft")) {
            m.put(Slot.TITLE, 0xF2C48D);
            m.put(Slot.INFO, 0xB8B8C8);
            m.put(Slot.SUMMARY, 0xEDEDED);
            m.put(Slot.TIP, 0xF5E1A4);
            m.put(Slot.MUTED, 0x8A8A9A);
            m.put(Slot.HEADER, 0xB8B8C8);
            m.put(Slot.TIME, 0xB8B8C8);
            m.put(Slot.SOURCE, 0xEDEDED);
            m.put(Slot.FINAL_BLOW, 0xF2C48D);
            m.put(Slot.FINAL_ROW, 0x6E5A3C);
            m.put(Slot.DAMAGE, 0xF08A8A);
            m.put(Slot.ARMOR, 0xB8B8C8);
            m.put(Slot.ENCHANTS, 0xC9A8F0);
            m.put(Slot.OTHER, 0x9CC8F0);
            m.put(Slot.HEALTH, 0xA8E0A8);
            m.put(Slot.HEALTH_ZERO, 0xC86464);
            m.put(Slot.TOTAL, 0xEDEDED);
            m.put(Slot.KIND_PLAYER, 0xA0E0E0);
            m.put(Slot.KIND_MOB, 0xF0A0A0);
            m.put(Slot.KIND_PROJECTILE, 0xF5E1A4);
            m.put(Slot.KIND_EXPLOSION, 0xF2C48D);
            m.put(Slot.KIND_ENVIRONMENT, 0xB4D89C);
            m.put(Slot.KIND_MAGIC, 0xC9A8F0);
            m.put(Slot.KIND_OTHER, 0xB8B8C8);
            m.put(Slot.MARKER, 0xF08A8A);
            m.put(Slot.HUD_TEXT, 0xEDEDED);
            m.put(Slot.HUD_ACCENT, 0xF2C48D);
        } else if (name.equals("mono")) {
            for (Slot s : Slot.values()) m.put(s, 0xDDDDDD);
            m.put(Slot.TITLE, 0xFFFFFF);
            m.put(Slot.MUTED, 0x888888);
            m.put(Slot.HEADER, 0xAAAAAA);
            m.put(Slot.TIME, 0xAAAAAA);
            m.put(Slot.FINAL_BLOW, 0xFFFFFF);
            m.put(Slot.FINAL_ROW, 0x555555);
            m.put(Slot.ARMOR, 0xAAAAAA);
            m.put(Slot.HEALTH_ZERO, 0x888888);
            m.put(Slot.HUD_ACCENT, 0xFFFFFF);
        }
        return m;
    }

    /** "#FF8800", "FF8800", "#f80" or "0xFF8800" → 0xFF8800; anything else (and blank) → null. */
    public static Integer parseHex(String s) {
        if (s == null) return null;
        String v = s.trim();
        if (v.startsWith("#")) v = v.substring(1);
        else if (v.startsWith("0x") || v.startsWith("0X")) v = v.substring(2);
        if (v.length() == 3) {
            StringBuilder b = new StringBuilder();
            for (char ch : v.toCharArray()) b.append(ch).append(ch);
            v = b.toString();
        }
        if (v.length() != 6) return null;
        for (char ch : v.toCharArray()) if (Character.digit(ch, 16) < 0) return null;
        return Integer.parseInt(v, 16);
    }

    /** 0xFF8800 → "#FF8800". */
    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** Minecraft's 16 chat colors (0-9, a-f), for versions and places that only have those. */
    public static final int[] LEGACY = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    /** The closest of the 16 chat colors, as its code character ('0'-'9', 'a'-'f'). */
    public static char nearestLegacy(int rgb) {
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < LEGACY.length; i++) {
            int c = LEGACY[i];
            long dr = ((rgb >> 16) & 255) - ((c >> 16) & 255), dg = ((rgb >> 8) & 255) - ((c >> 8) & 255), db = (rgb & 255) - (c & 255);
            long d = 3 * dr * dr + 4 * dg * dg + 2 * db * db;
            if (d < bestD) { bestD = d; best = i; }
        }
        return "0123456789abcdef".charAt(best);
    }
}
