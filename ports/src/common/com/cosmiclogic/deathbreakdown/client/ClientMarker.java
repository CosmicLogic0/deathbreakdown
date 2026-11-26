package com.cosmiclogic.deathbreakdown.client;

import com.cosmiclogic.deathbreakdown.DeathBreakdown;
import com.cosmiclogic.deathbreakdown.core.Hit;
import com.cosmiclogic.deathbreakdown.core.Palette;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapConfig;
import com.cosmiclogic.deathbreakdown.core.RecapText;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where you last died, for the death marker (particles or a beam at the spot) and the death HUD. Both are off by
 * default. The spot is remembered when the death recap arrives (that is where you were when you died).
 */
public final class ClientMarker {
    private ClientMarker() {}

    static volatile Spot spot;
    /** The HUD lines, rebuilt every {@code updateTicks} ticks. */
    static volatile List<String> hudLines = new ArrayList<>();
    private static Thread ticker;
    private static long ticks;
    /** For the client selftest. */
    static volatile int particlesSpawned, hudFrames;

    static final class Spot {
        final double x, y, z;
        final String dim;
        final long timeMs;
        final String killer;
        boolean reached;
        Spot(double x, double y, double z, String dim, long timeMs, String killer) {
            this.x = x; this.y = y; this.z = z; this.dim = dim; this.timeMs = timeMs; this.killer = killer;
        }
    }

    static void onDeath(Minecraft mc, Recap r) {
        if (mc.player == null || mc.level == null) return;
        Hit fb = r.finalBlow();
        spot = new Spot(mc.player.getX(), mc.player.getY(), mc.player.getZ(), dimension(mc), System.currentTimeMillis(),
                fb == null ? "" : RecapText.source(fb));
        ensureTicker();
    }

    static String dimension(Minecraft mc) {
        return mc.level == null ? "" : mc.level.dimension().toString();
    }

    static String dimensionName(String dim) {
        if (dim.contains("the_nether")) return "the Nether";
        if (dim.contains("the_end")) return "the End";
        if (dim.contains("overworld")) return "the Overworld";
        return "another dimension";
    }

    /** A light timer thread: every tick it may ask the game thread to update the marker and HUD. Nothing runs without a death spot. */
    static synchronized void ensureTicker() {
        if (ticker != null) return;
        ticker = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    return;
                }
                ticks++;
                RecapConfig c = DeathBreakdown.config;
                if (spot == null || ticks % Math.max(1, c.updateTicks) != 0) continue;
                if (c.markerStyle.equals("off") && !c.hudEnabled) continue;
                Minecraft.getInstance().execute(ClientMarker::tick);
            }
        }, "Death Breakdown marker");
        ticker.setDaemon(true);
        ticker.start();
    }

    /** On the game thread. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Spot s = spot;
        if (s == null || mc.player == null || mc.level == null) return;
        RecapConfig c = DeathBreakdown.config;
        hudLines = hudText(mc, s, c);
        if (mc.isPaused() || c.markerStyle.equals("off")) return;
        if (System.currentTimeMillis() - s.timeMs > c.markerMinutes * 60_000L) return;
        if (!s.dim.equals(dimension(mc))) return;
        double dx = s.x - mc.player.getX(), dy = s.y - mc.player.getY(), dz = s.z - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 3 && System.currentTimeMillis() - s.timeMs > 5000) s.reached = true;
        if (s.reached && c.markerUntilReached) return;
        if (dist > c.markerRange) return;
        spawnMarker(mc, s, c);
    }

    private static void spawnMarker(Minecraft mc, Spot s, RecapConfig c) {
        ParticleOptions p = particle(c.markerParticle, c.palette().get(Palette.Slot.MARKER), (float) c.markerSize);
        double step = Math.max(0.25, c.markerSpacing);
        if (c.markerStyle.equals("beam")) {
            for (double h = 0; h <= 32; h += step) add(mc, p, s.x, s.y + h, s.z);
        } else {
            double r = 0.8;
            int n = Math.max(6, (int) Math.ceil(2 * Math.PI * r / step));
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n;
                add(mc, p, s.x + Math.cos(a) * r, s.y + 0.15, s.z + Math.sin(a) * r);
            }
            for (double h = 0.5; h <= 2.5; h += step) add(mc, p, s.x, s.y + h, s.z);
        }
    }

    private static void add(Minecraft mc, ParticleOptions p, double x, double y, double z) {
        mc.level.addParticle(p, x, y, z, 0, 0, 0);
        particlesSpawned++;
    }

    /** The particle for a setting value. Only dust takes the marker color and size. */
    static ParticleOptions particle(String name, int rgb, float size) {
        switch (name) {
            case "flame": return ParticleTypes.FLAME;
            case "end_rod": return ParticleTypes.END_ROD;
            case "happy_villager": return ParticleTypes.HAPPY_VILLAGER;
            case "heart": return ParticleTypes.HEART;
            case "portal": return ParticleTypes.PORTAL;
            case "crit": return ParticleTypes.CRIT;
            default: return dust(rgb, size);
        }
    }

    static ParticleOptions dust(int rgb, float size) {
        float r = ((rgb >> 16) & 255) / 255F, g = ((rgb >> 8) & 255) / 255F, b = (rgb & 255) / 255F;
        float sz = Math.max(0.5F, Math.min(4F, size));
        //#if MC >= 1.21.2
        return new DustParticleOptions(rgb & 0xFFFFFF, sz);
        //#elif MC >= 1.19.3
        return new DustParticleOptions(new org.joml.Vector3f(r, g, b), sz);
        //#elif MC >= 1.17
        return new DustParticleOptions(new com.mojang.math.Vector3f(r, g, b), sz);
        //#else
        return new DustParticleOptions(r, g, b, sz);
        //#endif
    }

    private static float yaw(Minecraft mc) {
        //#if MC >= 1.17
        return mc.player.getYRot();
        //#else
        return mc.player.yRot;
        //#endif
    }

    /** The HUD lines for this spot ("" lines are left out), or nothing once it's too old. */
    static List<String> hudText(Minecraft mc, Spot s, RecapConfig c) {
        if (!s.dim.equals(dimension(mc))) return hudText(s, c, -1, "");
        double dx = s.x - mc.player.getX(), dz = s.z - mc.player.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        return hudText(s, c, dist, arrow(Math.toDegrees(Math.atan2(-dx, dz)) - yaw(mc)));
    }

    /** {@code dist} -1 = the spot is in another dimension. */
    static List<String> hudText(Spot s, RecapConfig c, int dist, String arrow) {
        List<String> out = new ArrayList<>();
        long age = System.currentTimeMillis() - s.timeMs;
        if (age > c.hudMinutes * 60_000L) return out;
        String killer = s.killer.isEmpty() ? "Last death" : s.killer;
        String where = dist < 0 ? "in " + dimensionName(s.dim) : dist + " blocks " + arrow;
        String when = RecapText.ago(s.timeMs, System.currentTimeMillis());
        String coords = String.format(Locale.ROOT, "%d, %d, %d", (int) Math.floor(s.x), (int) Math.floor(s.y), (int) Math.floor(s.z));
        if (c.hudCompact) {
            StringBuilder b = new StringBuilder("☠");
            if (c.hudShowKiller) b.append(' ').append(killer);
            if (c.hudShowDistance) b.append(" · ").append(where);
            if (c.hudShowTime) b.append(" · ").append(when);
            if (c.hudShowCoords) b.append(" · ").append(coords);
            out.add(b.toString());
            return out;
        }
        out.add("☠ " + (c.hudShowKiller ? killer : "Last death"));
        if (c.hudShowDistance) out.add(where);
        if (c.hudShowTime) out.add(when);
        if (c.hudShowCoords) out.add(coords);
        return out;
    }

    /** An arrow pointing the way to go, from the angle between where you look and where the spot is. */
    static String arrow(double deg) {
        double a = ((deg % 360) + 360) % 360;
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        return arrows[(int) Math.round(a / 45) % 8];
    }

    /** Leaving a world: its death spot belongs to it. */
    static void clear() {
        spot = null;
        hudLines = new ArrayList<>();
    }
}
