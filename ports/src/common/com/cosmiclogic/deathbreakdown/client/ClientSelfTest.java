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

    static volatile boolean sawDeathPacket, sawSummary, sawDeathButton, sawRecapScreen, sawReopen;
    private static boolean started, enabled, done, reopened, hurt;
    private static long startAt, hurtAt;

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
        if (mc.player == null || server == null) return;
        if (!hurt) {
            if (hurtAt == 0) { hurtAt = now + 4000; return; } // let the world settle
            if (now < hurtAt) return;
            hurt = true;
            DeathBreakdown.LOG.info("Death Breakdown client selftest: hurting the player");
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                sp.setGameMode(GameType.SURVIVAL);
                sp.setHealth(20);
                for (int i = 0; i < 6 && sp.isAlive(); i++) {
                    sp.invulnerableTime = 0;
                    hit(sp, i);
                }
            });
            return;
        }
        if (sawRecapScreen && !reopened) {
            reopened = true;
            ClientRecap.setScreen(mc, null);
            ClientRecap.open(null); // what the K key does
            sawReopen = ClientRecap.screen(mc) instanceof RecapScreen;
            return;
        }
        if (reopened) {
            String missing = (sawDeathPacket ? "" : " death-packet") + (sawSummary ? "" : " chat-summary")
                    + (sawDeathButton ? "" : " death-screen-button") + (sawRecapScreen ? "" : " auto-open")
                    + (sawReopen ? "" : " reopen");
            finish(missing.isEmpty() ? "PASS: death packet flagged, chat summary, death screen button, recap opened by itself, reopened ("
                    + ClientRecap.history().size() + " in history)" : "FAIL: missing" + missing);
        }
    }

    private static void hit(ServerPlayer sp, int i) {
        //#if MC >= 1.19.4
        DamageSource src = i % 2 == 0 ? sp.damageSources().lava() : sp.damageSources().fall();
        //#else
        DamageSource src = i % 2 == 0 ? DamageSource.LAVA : DamageSource.FALL;
        //#endif
        //#if MC >= 1.21.2
        sp.hurtServer((net.minecraft.server.level.ServerLevel) sp.level(), src, 8);
        //#else
        sp.hurt(src, 8);
        //#endif
    }

    private static void finish(String result) {
        if (done) return;
        done = true;
        if (result.startsWith("PASS")) DeathBreakdown.LOG.info("Death Breakdown client selftest {}", result);
        else DeathBreakdown.LOG.error("Death Breakdown client selftest {}", result);
        Minecraft mc = Minecraft.getInstance();
        if (ClientRecap.screen(mc) instanceof RecapScreen) ClientRecap.setScreen(mc, null);
    }
}
