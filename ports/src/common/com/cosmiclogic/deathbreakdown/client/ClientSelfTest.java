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
    private static boolean started, enabled, done, reopened, hurt, finalHit, holding;
    private static long startAt, hurtAt, finalAt, lastLog;

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
            server.execute(() -> hit(server.getPlayerList().getPlayers().get(0), 1, 40)); // a deadly fall
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
