package com.cosmiclogic.deathbreakdown.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.GameType;

import java.io.File;

/**
 * End-to-end test of a real death on the real 1.12.2 client, run by CI. Inert unless
 * config/deathbreakdown-ci-selftest.txt exists (only the CI writes it). Same checks as every other version.
 */
public final class ClientSelfTest112 {
    private ClientSelfTest112() {}

    static volatile boolean sawDeathPacket, sawSummary, sawDeathButton, sawRecapScreen, sawReopen;
    private static boolean started, done, reopened, hurt, holding;
    private static int tries;
    private static long startAt, hurtAt, lastLog;

    /** Invisible, doesn't pause: keeps the client test from ending before this test is done. */
    private static final class Hold extends net.minecraft.client.gui.GuiScreen {
        @Override public boolean doesGuiPauseGame() { return false; }
    }

    static void maybeStart() {
        if (started) return;
        started = true;
        if (!new File(DeathBreakdown112.configDir(), "deathbreakdown-ci-selftest.txt").isFile()) return;
        DeathBreakdown112.LOG.info("Death Breakdown client selftest: waiting for the world");
        startAt = System.currentTimeMillis();
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                while (!done) {
                    try { Thread.sleep(50); } catch (InterruptedException e) { return; }
                    Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                        @Override public void run() {
                            try { if (!done) step(); } catch (Throwable e) { finish("FAIL: " + e); }
                        }
                    });
                }
            }
        }, "Death Breakdown client selftest");
        t.setDaemon(true);
        t.start();
    }

    private static void step() {
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.currentTimeMillis();
        if (now - startAt > 240_000L) { finish("FAIL: timed out"); return; }
        final IntegratedServer server = mc.getIntegratedServer();
        if (now - lastLog > 2000) {
            lastLog = now;
            DeathBreakdown112.LOG.info("Death Breakdown client selftest state: player={} server={} screen={} holding={} hurt={} packet={} summary={} button={} recap={}",
                    mc.player != null, server != null, mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName(),
                    holding, hurt, sawDeathPacket, sawSummary, sawDeathButton, sawRecapScreen);
        }
        if (mc.player == null || server == null) return;
        if (!holding) {
            if (mc.currentScreen != null) return; // wait until the world is really shown (no loading screen)
            holding = true;
            mc.displayGuiScreen(new Hold());
            hurtAt = now + 1500;
            return;
        }
        if (!hurt) {
            if (now < hurtAt) return;
            hurt = true;
            DeathBreakdown112.LOG.info("Death Breakdown client selftest: hurting the player");
            server.addScheduledTask(new Runnable() {
                @Override public void run() {
                    EntityPlayerMP sp = server.getPlayerList().getPlayers().get(0);
                    sp.setGameType(GameType.SURVIVAL);
                    sp.setHealth(20);
                    for (int i = 0; i < 6 && sp.isEntityAlive(); i++) {
                        sp.hurtResistantTime = 0;
                        sp.attackEntityFrom(i % 2 == 0 ? DamageSource.LAVA : DamageSource.FALL, 8);
                    }
                    DeathBreakdown112.LOG.info("Death Breakdown client selftest: health after the hits {}", sp.getHealth());
                }
            });
            hurtAt = now;
            return;
        }
        // Fresh players can't be hurt for their first seconds (spawn protection): try again a few times.
        if (!sawDeathPacket && mc.player.getHealth() > 0 && now - hurtAt > 3000 && ++tries <= 6) {
            hurt = false;
            hurtAt = now;
            return;
        }
        if (sawRecapScreen && !reopened) {
            reopened = true;
            mc.displayGuiScreen(null);
            ClientRecap112.open(null);
            sawReopen = mc.currentScreen instanceof RecapScreen112;
            return;
        }
        if (reopened) {
            String missing = (sawDeathPacket ? "" : " death-packet") + (sawSummary ? "" : " chat-summary")
                    + (sawDeathButton ? "" : " death-screen-button") + (sawRecapScreen ? "" : " auto-open") + (sawReopen ? "" : " reopen");
            finish(missing.isEmpty() ? "PASS: death packet flagged, chat summary, death screen button, recap opened by itself, reopened ("
                    + ClientRecap112.history().size() + " in history)" : "FAIL: missing" + missing);
        }
    }

    private static void finish(String result) {
        if (done) return;
        done = true;
        if (result.startsWith("PASS")) DeathBreakdown112.LOG.info("Death Breakdown client selftest {}", result);
        else DeathBreakdown112.LOG.error("Death Breakdown client selftest {}", result);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof RecapScreen112 || mc.currentScreen instanceof Hold) mc.displayGuiScreen(null);
    }
}
