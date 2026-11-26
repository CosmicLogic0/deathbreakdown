package com.cosmiclogic.deathbreakdown.client;

import com.cosmiclogic.deathbreakdown.DeathBreakdown;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;

import java.io.File;

/**
 * End-to-end test of a real death on the real client, run by CI in every Forge/NeoForge client test.
 * Inert unless config/deathbreakdown-ci-selftest.txt exists (only the CI writes it).
 * In the single-player world it hurts the player with real game damage until they die, then checks what a player
 * would see: the death packet (flagged as a death), the chat summary, the button on the death screen, the recap
 * opening by itself, and opening it again (the K key's path). Logs "Death Breakdown client selftest PASS/FAIL".
 */
public final class ClientSelfTest {
    private ClientSelfTest() {}

    static volatile boolean sawDeathPacket, sawSummary, sawDeathButton, sawRecapScreen, sawReopen, sawConfigScreen;
    static volatile int soundsPlayed;
    private static int phase, pageAt, waitTicks, hudBefore, partBefore;
    private static ConfigScreen cs;
    private static final java.util.List<String> settingsFail = new java.util.ArrayList<>();
    private static String savedText;
    private static boolean started, enabled, done, reopened, hurt, finalHit, holding, helpAsked;
    private static volatile boolean helpDone, helpOk;
    private static long startAt, hurtAt, finalAt, lastLog;
    private static int tries;

    /** Invisible, doesn't pause: keeps the client test from ending before this test is done. */
    private static final class Hold extends net.minecraft.client.gui.screens.Screen {
        Hold() { super(com.cosmiclogic.deathbreakdown.Txt.lit("")); }
        @Override public boolean isPauseScreen() { return false; }
    }

    static void maybeStart() {
        if (started) return;
        started = true;
        enabled = new File(DeathBreakdown.platform.configDir().toFile(), "deathbreakdown-ci-selftest.txt").isFile();
        if (!enabled) return;
        DeathBreakdown.LOG.info("Death Breakdown client selftest: waiting for the world");
        startAt = System.currentTimeMillis();
        loop();
    }

    /** A daemon thread queues one step on the client thread every 50 ms (no per-version tick event needed). */
    private static void loop() {
        Thread t = new Thread(() -> {
            while (!done) {
                try { Thread.sleep(50); } catch (InterruptedException e) { return; }
                Minecraft.getInstance().execute(() -> {
                    try {
                        if (!done) step();
                    } catch (Throwable e) {
                        finish("FAIL: " + e);
                    }
                });
            }
        }, "Death Breakdown client selftest");
        t.setDaemon(true);
        t.start();
    }

    private static void step() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (now - startAt > 240_000L) { finish("FAIL: timed out"); return; }
        IntegratedServer server = mc.getSingleplayerServer();
        if (now - lastLog > 2000) {
            lastLog = now;
            DeathBreakdown.LOG.info("Death Breakdown client selftest state: player={} server={} screen={} holding={} hurt={} final={} packet={} summary={} button={} recap={}",
                    mc.player != null, server != null, ClientRecap.screen(mc) == null ? "none" : ClientRecap.screen(mc).getClass().getSimpleName(),
                    holding, hurt, finalHit, sawDeathPacket, sawSummary, sawDeathButton, sawRecapScreen);
        }
        if (mc.player == null || server == null) return;
        if (!holding) {
            if (ClientRecap.screen(mc) != null) return; // wait until the world is really shown (no loading screen)
            holding = true;
            ClientRecap.setScreen(mc, new Hold());
            hurtAt = now + 1500; // let the world settle
            return;
        }
        if (!hurt) {
            if (now < hurtAt) return;
            hurt = true;
            DeathBreakdown.LOG.info("Death Breakdown client selftest: hurting the player");
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                sp.setGameMode(GameType.SURVIVAL);
                sp.setHealth(20);
                hit(sp, 0, 8); // lava
            });
            finalAt = now + 1200; // after the hurt cooldown (10 ticks)
            return;
        }
        if (!finalHit) {
            if (now < finalAt) return;
            finalHit = true;
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                hit(sp, 1, 40); // a deadly fall
                DeathBreakdown.LOG.info("Death Breakdown client selftest: health after the hits {}", sp.getHealth());
            });
            return;
        }
        // Fresh players can't be hurt for their first seconds (spawn protection): try again a few times.
        if (!sawDeathPacket && mc.player.getHealth() > 0 && now - finalAt > 3000 && ++tries <= 6) {
            hurt = false;
            finalHit = false;
            hurtAt = now;
            return;
        }
        if (sawRecapScreen && !reopened) {
            reopened = true;
            ClientRecap.setScreen(mc, null);
            ClientRecap.open(null); // what the K key does
            sawReopen = ClientRecap.screen(mc) instanceof RecapScreen;
            return;
        }
        if (reopened && !helpAsked) {
            helpAsked = true;
            // /deathbreakdown help typed by the player reaches them even when sendCommandFeedback is off
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                int before = com.cosmiclogic.deathbreakdown.RecapCommand.directReplies;
                try {
                    server.getCommands().getDispatcher().execute("deathbreakdown help", sp.createCommandSourceStack());
                } catch (Exception e) {
                    DeathBreakdown.LOG.error("Death Breakdown client selftest: /deathbreakdown help failed", e);
                }
                helpOk = com.cosmiclogic.deathbreakdown.RecapCommand.directReplies > before;
                helpDone = true;
            });
            return;
        }
        if (reopened && helpDone && phase < 99) {
            settingsStep(mc);
            return;
        }
        if (reopened && helpDone) {
            String missing = (sawDeathPacket ? "" : " death-packet") + (sawSummary ? "" : " chat-summary")
                    + (sawDeathButton ? "" : " death-screen-button") + (sawRecapScreen ? "" : " auto-open")
                    + (sawReopen ? "" : " reopen") + (helpOk ? "" : " help-reply")
                    + (settingsFail.isEmpty() ? "" : " settings" + settingsFail);
            finish(missing.isEmpty() ? "PASS: death packet flagged, chat summary, death screen button, recap opened by itself, reopened, help reply, "
                    + "settings screen (every page and setting, reset, cancel, save), sound, death marker, HUD ("
                    + ClientRecap.history().size() + " in history, " + ClientMarker.particlesSpawned + " particles, "
                    + ClientMarker.hudFrames + " HUD frames)" : "FAIL: missing" + missing);
        }
    }

    /**
     * The 1.2.0 settings, one step per tick so every page gets drawn: each setting changes when clicked, Reset to
     * defaults puts everything back, Cancel keeps the old settings, Save applies and writes them; then the sound,
     * the death marker (particles and beam) and the HUD.
     */
    private static void settingsStep(Minecraft mc) {
        com.cosmiclogic.deathbreakdown.core.RecapConfig live = DeathBreakdown.config;
        switch (phase) {
            case 0: {
                savedText = live.text();
                cs = new ConfigScreen(new Hold());
                ClientRecap.setScreen(mc, cs);
                pageAt = 0;
                phase = 1;
                return;
            }
            case 1: { // one page per tick: click every setting on it (the preview draws in between)
                if (pageAt >= com.cosmiclogic.deathbreakdown.core.Option.PAGES.length) { phase = 2; return; }
                String page = com.cosmiclogic.deathbreakdown.core.Option.PAGES[pageAt++];
                cs.showPage(page);
                for (com.cosmiclogic.deathbreakdown.core.Option o : com.cosmiclogic.deathbreakdown.core.Option.page(page)) {
                    String before = o.get(cs.c);
                    if (o.kind == com.cosmiclogic.deathbreakdown.core.Option.Kind.COLOR) {
                        cs.change(o, "#12AB34");
                        if (cs.c.palette().get(o.slot) != 0x12AB34) settingsFail.add(o.key + "-color");
                    } else {
                        cs.change(o, o.next(cs.c, false));
                        if (o.get(cs.c).equals(before)) settingsFail.add(o.key + "-click");
                    }
                    String bad = o.set(cs.c, o.badValue());
                    if (bad == null || !o.get(cs.c).equalsIgnoreCase(o.getDefault())) settingsFail.add(o.key + "-bad");
                }
                return;
            }
            case 2: {
                cs.resetAll();
                for (com.cosmiclogic.deathbreakdown.core.Option o : com.cosmiclogic.deathbreakdown.core.Option.ALL) {
                    if (!o.get(cs.c).equalsIgnoreCase(o.getDefault())) settingsFail.add(o.key + "-reset");
                }
                cs.change(com.cosmiclogic.deathbreakdown.core.Option.get("hudEnabled"), "true");
                cs.onClose(); // Cancel
                if (!DeathBreakdown.config.text().equals(savedText)) settingsFail.add("cancel-kept-changes");
                phase = 3;
                return;
            }
            case 3: {
                cs = new ConfigScreen(new Hold());
                ClientRecap.setScreen(mc, cs);
                cs.change(com.cosmiclogic.deathbreakdown.core.Option.get("markerStyle"), "particles");
                cs.change(com.cosmiclogic.deathbreakdown.core.Option.get("markerUntilReached"), "false");
                cs.change(com.cosmiclogic.deathbreakdown.core.Option.get("updateTicks"), "2");
                cs.change(com.cosmiclogic.deathbreakdown.core.Option.get("hudEnabled"), "true");
                cs.change(com.cosmiclogic.deathbreakdown.core.Option.get("soundOnOpen"), "true");
                cs.change(com.cosmiclogic.deathbreakdown.core.Option.get("colorDamage"), "#12AB34");
                cs.save();
                if (!DeathBreakdown.config.markerStyle.equals("particles") || !DeathBreakdown.config.hudEnabled
                        || DeathBreakdown.config.palette().get(com.cosmiclogic.deathbreakdown.core.Palette.Slot.DAMAGE) != 0x12AB34) {
                    settingsFail.add("save-not-applied");
                }
                try {
                    String file = new String(java.nio.file.Files.readAllBytes(DeathBreakdown.configFile().toPath()), "UTF-8");
                    if (!file.contains("markerStyle=particles") || !file.contains("colorDamage=#12AB34")) settingsFail.add("save-not-written");
                } catch (java.io.IOException e) {
                    settingsFail.add("save-read:" + e);
                }
                int sounds = soundsPlayed;
                ClientRecap.open(new Hold()); // opening the recap plays the sound now
                if (soundsPlayed <= sounds) settingsFail.add("sound");
                if (ClientMarker.spot == null) settingsFail.add("no-death-spot");
                partBefore = ClientMarker.particlesSpawned;
                waitTicks = 0;
                phase = 4;
                return;
            }
            case 4: { // the marker spawns particles around the spot
                if (ClientMarker.particlesSpawned > partBefore) {
                    com.cosmiclogic.deathbreakdown.core.RecapConfig b = DeathBreakdown.config.copy();
                    b.markerStyle = "beam";
                    DeathBreakdown.config = b;
                    partBefore = ClientMarker.particlesSpawned;
                    waitTicks = 0;
                    phase = 5;
                } else if (++waitTicks > 60) {
                    settingsFail.add("marker-particles");
                    phase = 5;
                }
                return;
            }
            case 5: {
                if (ClientMarker.particlesSpawned > partBefore + 20 || ++waitTicks > 60) {
                    if (ClientMarker.particlesSpawned <= partBefore) settingsFail.add("marker-beam");
                    hudBefore = ClientMarker.hudFrames;
                    ClientRecap.setScreen(mc, null); // the HUD only shows in game, with no screen open
                    waitTicks = 0;
                    phase = 6;
                }
                return;
            }
            case 6: {
                if (ClientMarker.hudFrames > hudBefore || ++waitTicks > 8) {
                    if (ClientMarker.hudFrames <= hudBefore) settingsFail.add("hud");
                    ClientRecap.setScreen(mc, new Hold());
                    phase = 99;
                }
                return;
            }
            default:
        }
    }

    private static void hit(ServerPlayer sp, int i, float amount) {
        //#if MC >= 1.19.4
        DamageSource src = i % 2 == 0 ? sp.damageSources().lava() : sp.damageSources().fall();
        //#else
        DamageSource src = i % 2 == 0 ? DamageSource.LAVA : DamageSource.FALL;
        //#endif
        //#if MC >= 1.21.2
        sp.hurtServer((net.minecraft.server.level.ServerLevel) sp.level(), src, amount);
        //#else
        sp.hurt(src, amount);
        //#endif
    }

    private static void finish(String result) {
        if (done) return;
        done = true;
        if (result.startsWith("PASS")) DeathBreakdown.LOG.info("Death Breakdown client selftest {}", result);
        else DeathBreakdown.LOG.error("Death Breakdown client selftest {}", result);
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen open = ClientRecap.screen(mc);
        if (open instanceof RecapScreen || open instanceof Hold) ClientRecap.setScreen(mc, null);
    }
}
