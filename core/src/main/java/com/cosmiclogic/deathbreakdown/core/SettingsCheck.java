package com.cosmiclogic.deathbreakdown.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks every setting the same way: a good value takes effect, a bad value falls back with a warning, reset gives
 * the default, and the file keeps it all. Run by the unit tests and by /deathbreakdown selftest on every version,
 * so each setting is tested on every loader and Minecraft version we ship.
 */
public final class SettingsCheck {
    private SettingsCheck() {}

    /** Returns what failed (empty = all good). {@code dir} is a scratch folder for the file round trip. */
    public static List<String> run(File dir) {
        List<String> fail = new ArrayList<String>();
        RecapConfig def = new RecapConfig();

        // 1.1.0 look and behavior are the defaults
        if (!def.markerStyle.equals("off") || def.hudEnabled || def.soundOnOpen || !def.colorPreset.equals("default")
                || def.compact || def.panelOpacity != 0 || !def.deathButton || !def.openOnDeath || def.windowSeconds != 10) {
            fail.add("a default changed from 1.1.0");
        }
        for (Palette.Slot s : Palette.Slot.values()) {
            if (Palette.of(def).get(s) != s.def) fail.add("default color " + s + " isn't the 1.1.0 color");
        }

        for (Option o : Option.ALL) {
            RecapConfig c = new RecapConfig();
            String d = o.get(c);
            String other = o.otherValue();
            String p = o.set(c, other);
            if (p != null) fail.add(o.key + ": good value " + other + " was refused: " + p);
            if (!same(o.get(c), other)) fail.add(o.key + ": set " + other + " but got " + o.get(c));
            if (same(o.get(c), d)) fail.add(o.key + ": changing it did nothing");
            if (o.kind == Option.Kind.COLOR && Palette.of(c).get(o.slot) != 0x12AB34) fail.add(o.key + ": the color isn't used");

            String bad = o.badValue();
            p = o.set(c, bad);
            if (p == null) fail.add(o.key + ": bad value " + bad + " gave no warning");
            if (!same(o.get(c), d)) fail.add(o.key + ": bad value " + bad + " didn't fall back to " + d + " (got " + o.get(c) + ")");

            if (o.kind == Option.Kind.INT || o.kind == Option.Kind.DECIMAL) {
                boolean whole = o.kind == Option.Kind.INT;
                p = o.set(c, whole ? String.valueOf((int) o.max + 1000) : Option.fmt(o.max + 1000));
                if (p == null || Double.parseDouble(o.get(c)) != o.max) fail.add(o.key + ": too big wasn't kept at " + o.max);
                p = o.set(c, whole ? String.valueOf((int) o.min - 1000) : Option.fmt(o.min - 1000));
                if (p == null || Double.parseDouble(o.get(c)) != o.min) fail.add(o.key + ": too small wasn't kept at " + o.min);
            }

            o.set(c, other);
            o.reset(c);
            if (!same(o.get(c), d)) fail.add(o.key + ": reset gave " + o.get(c) + ", not " + d);
        }

        // Reset to defaults (all at once) after changing everything
        RecapConfig all = new RecapConfig();
        for (Option o : Option.ALL) o.set(all, o.otherValue());
        all.resetAll();
        for (Option o : Option.ALL) if (!same(o.get(all), o.getDefault())) fail.add(o.key + ": Reset to defaults missed it");

        // Every setting survives the file, and a file full of junk loads (with warnings) instead of crashing
        try {
            File f = new File(dir, "deathbreakdown-settingscheck.properties");
            RecapConfig a = new RecapConfig();
            for (Option o : Option.ALL) o.set(a, o.otherValue());
            if (!a.showSource) a.showSource = true;
            a.save(f);
            RecapConfig b = RecapConfig.load(f);
            if (!b.problems.isEmpty()) fail.add("file round trip had problems: " + b.problems);
            for (Option o : Option.ALL) if (!same(o.get(a), o.get(b))) fail.add(o.key + ": not kept by the file");

            Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            try {
                for (Option o : Option.ALL) w.write(o.key + "=" + o.badValue() + "\n");
            } finally {
                w.close();
            }
            RecapConfig junk = RecapConfig.load(f);
            if (junk.problems.size() < Option.ALL.size() - 1) fail.add("junk file: only " + junk.problems.size() + " warnings");
            for (Option o : Option.ALL) if (!same(o.get(junk), o.getDefault())) fail.add(o.key + ": junk didn't fall back");
            f.delete();
        } catch (IOException e) {
            fail.add("file round trip: " + e);
        }
        return fail;
    }

    private static boolean same(String a, String b) {
        return a.equalsIgnoreCase(b);
    }
}
