package com.cosmiclogic.deathbreakdown.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * deathbreakdown.properties: one small, commented file that works the same on every loader and version.
 * Missing or broken values fall back to the defaults, and new keys are written back so the file stays complete.
 */
public final class RecapConfig {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    // Tracking (server side)
    public int windowSeconds = 10;
    // What's shown
    public boolean showSource = true;
    public boolean showType = true;
    public boolean showDamage = true;
    public boolean showArmor = true;
    public boolean showEnchants = true;
    public boolean showOther = true;      // Resistance, absorption hearts and shields
    public boolean showHealthLeft = true;
    public boolean showTime = true;
    public boolean hearts = true;         // show damage in hearts (true) or health points (false)
    // When
    public boolean openOnDeath = true;    // open the recap screen automatically on the death screen
    public boolean chatSummary = true;    // one-line summary in chat after death
    // Added in 1.1.0 (old files get these defaults written in)
    public boolean showTip = true;        // one short tip under the summary ("Lava did 89% of the damage.")
    public int historySize = 5;           // recaps kept per player (1-20), browse with < > in the screen
    public boolean saveHistory = true;    // keep them in the world folder so they survive restarts
    // Added in 1.2.0 ("make it yours"). Every default keeps the 1.1.0 look and behavior.
    public int updateTicks = 5;
    public boolean deathButton = true;
    public String deathButtonCorner = "top_left";
    public int deathButtonX = 4, deathButtonY = 4;
    public boolean showDeathMessage = true, showSummary = true, showLegend = true, compact = false, stripes = true;
    public int panelOpacity = 0;
    public String colorPreset = "default";
    private final java.util.EnumMap<Palette.Slot, String> colors = new java.util.EnumMap<Palette.Slot, String>(Palette.Slot.class);
    public String markerStyle = "off", markerParticle = "dust";
    public double markerSize = 1.0, markerSpacing = 0.5;
    public int markerMinutes = 5, markerRange = 64;
    public boolean markerUntilReached = true;
    public boolean hudEnabled = false;
    public String hudCorner = "top_right";
    public int hudX = 4, hudY = 4, hudScale = 100, hudOpacity = 50;
    public boolean hudBackground = true, hudShowKiller = true, hudShowDistance = true, hudShowTime = true,
            hudShowCoords = false, hudCompact = false;
    public int hudMinutes = 10;
    public boolean soundOnOpen = false;
    public String soundKind = "page";
    public int soundVolume = 100;

    /** What was wrong with the file when it was loaded (empty if nothing). Shown in the log and by /deathbreakdown reload. */
    public final List<String> problems = new ArrayList<String>();

    public long windowMs() { return windowSeconds * 1000L; }

    /** The hex code typed for this color ("" = use the preset). */
    public String colorOverride(Palette.Slot s) {
        String v = colors.get(s);
        return v == null ? "" : v;
    }

    public void setColorOverride(Palette.Slot s, String hex) {
        if (hex == null || hex.isEmpty()) colors.remove(s); else colors.put(s, hex);
    }

    /** The colors to draw with right now (preset + the player's own). */
    public Palette palette() { return Palette.of(this); }

    /** Every setting back to its default ("Reset to defaults"). */
    public void resetAll() {
        for (Option o : Option.ALL) o.reset(this);
    }

    /** A separate copy (the settings screen edits one and only keeps it on Save). */
    public RecapConfig copy() {
        RecapConfig c = new RecapConfig();
        for (Option o : Option.ALL) o.set(c, o.get(this));
        return c;
    }

    /** Reads the settings from text in the file's format (missing keys keep their defaults). */
    public static RecapConfig fromProperties(Properties p) {
        RecapConfig c = new RecapConfig();
        for (Option o : Option.ALL) {
            String v = p.getProperty(o.key);
            if (v == null) continue; // new key: keeps its default and gets written in
            String problem = o.set(c, v);
            if (problem != null) c.problems.add(problem);
        }
        if (!c.showTime && !c.showSource && !c.showType && !c.showDamage && !c.showArmor && !c.showEnchants
                && !c.showOther && !c.showHealthLeft) {
            c.problems.add("every column was turned off: showing \"Hit by\" so the recap isn't empty");
            c.showSource = true;
        }
        return c;
    }

    public static RecapConfig load(File file) {
        Properties p = new Properties();
        List<String> early = new ArrayList<String>();
        if (file.isDirectory()) {
            RecapConfig c = new RecapConfig();
            c.problems.add(file.getName() + " is a folder, not a file: using the defaults");
            return c;
        }
        if (file.isFile()) {
            Reader r = null;
            try {
                r = new InputStreamReader(new FileInputStream(file), UTF8);
                p.load(r);
            } catch (IOException e) {
                early.add("couldn't read " + file.getName() + " (" + e.getMessage() + "): using the defaults");
            } catch (IllegalArgumentException e) {
                early.add(file.getName() + " has a broken line (" + e.getMessage() + "): using the defaults for it");
            } finally {
                if (r != null) try { r.close(); } catch (IOException ignored) {}
            }
        }
        RecapConfig c = fromProperties(p);
        c.problems.addAll(0, early);
        try { c.save(file); } catch (IOException e) { c.problems.add("couldn't save " + file.getName() + ": " + e.getMessage()); }
        return c;
    }

    /** Writes every setting with a one-line explanation, grouped like the settings screen. */
    public void save(File file) throws IOException {
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("Can't create " + dir);
        File tmp = new File(file.getPath() + ".tmp");
        Writer w = new OutputStreamWriter(new FileOutputStream(tmp), UTF8);
        try {
            w.write(text());
        } finally {
            w.close();
        }
        if (!tmp.renameTo(file)) {
            if (!file.delete() || !tmp.renameTo(file)) throw new IOException("Can't replace " + file);
        }
    }

    /** The file's text. */
    public String text() {
        StringBuilder b = new StringBuilder();
        b.append("# Death Breakdown settings. Change them in game: Mods list > Death Breakdown > Config.\n");
        b.append("# A value that can't be read is replaced by its default (the log says which).\n");
        for (String page : Option.PAGES) {
            b.append("\n# ===== ").append(page).append(" =====\n");
            if (page.equals(Option.COLORS)) b.append("# Presets: default, colorblind, high_contrast, soft, mono. Each color below: a hex code like #FF8800, or blank for the preset's.\n");
            for (Option o : Option.page(page)) {
                if (o.kind != Option.Kind.COLOR) b.append("# ").append(o.label).append(": ").append(o.help)
                        .append(o.kind == Option.Kind.CHOICE ? " (" + String.join(", ", o.choices) + ")" : "").append('\n');
                b.append(o.key).append('=').append(o.get(this)).append('\n');
            }
        }
        return b.toString();
    }

    static boolean bool(String v, boolean def) {
        if (v == null) return def;
        v = v.trim();
        if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes") || v.equals("1") || v.equalsIgnoreCase("on")) return true;
        if (v.equalsIgnoreCase("false") || v.equalsIgnoreCase("no") || v.equals("0") || v.equalsIgnoreCase("off")) return false;
        return def;
    }

    static int clampInt(String v, int def, int lo, int hi) {
        if (v == null) return def;
        try {
            int i = Integer.parseInt(v.trim());
            return Math.max(lo, Math.min(hi, i));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
