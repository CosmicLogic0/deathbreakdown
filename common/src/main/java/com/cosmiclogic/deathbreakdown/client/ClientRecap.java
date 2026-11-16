package com.cosmiclogic.deathbreakdown.client;

import com.cosmiclogic.deathbreakdown.DeathBreakdown;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapCodec;
import com.cosmiclogic.deathbreakdown.core.RecapText;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** Client side: keeps the last recap, the reopen key, and the button on the death screen. */
public final class ClientRecap {
    private ClientRecap() {}

    public static final KeyMapping OPEN_KEY = new KeyMapping("key.deathbreakdown.open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.deathbreakdown");

    private static Recap last;
    private static long autoOpened = Long.MIN_VALUE;

    public static Recap last() { return last; }

    /** A recap arrived from the server. */
    public static void receive(String data) {
        Recap r = RecapCodec.decode(data);
        if (r == null) return;
        last = r;
        Minecraft mc = Minecraft.getInstance();
        boolean dead = mc.player == null || mc.player.isDeadOrDying() || mc.screen instanceof DeathScreen;
        if (dead) {
            if (DeathBreakdown.config.chatSummary && mc.player != null) {
                mc.player.displayClientMessage(Component.literal("[" + DeathBreakdown.NAME + "] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(RecapText.summary(r, DeathBreakdown.config.hearts)).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" Press ").withStyle(ChatFormatting.GRAY))
                        .append(OPEN_KEY.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" to see it again.").withStyle(ChatFormatting.GRAY)), false);
            }
            if (DeathBreakdown.config.openOnDeath && mc.screen instanceof DeathScreen) autoOpen(mc.screen);
        } else {
            open(mc.screen); // came from /deathbreakdown
        }
    }

    /** Called after any screen is built. Adds the "Death Breakdown" button to the death screen. */
    public static void onScreenInit(Screen screen, Consumer<Button> addButton) {
        if (!(screen instanceof DeathScreen)) return;
        addButton.accept(Button.builder(Component.literal("☠ " + DeathBreakdown.NAME), b -> open(screen))
                .bounds(4, 4, 110, 20).build());
        if (DeathBreakdown.config.openOnDeath) autoOpen(screen);
    }

    private static void autoOpen(Screen parent) {
        if (last == null || autoOpened == last.timeMs) return;
        long age = Math.abs(System.currentTimeMillis() - last.timeMs);
        if (age > 60_000L) return; // an old recap, e.g. died again on a server without the mod
        autoOpened = last.timeMs;
        Minecraft.getInstance().tell(() -> open(parent));
    }

    /** The reopen key, checked on key presses while no screen is open. */
    public static void onKey() {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_KEY.consumeClick()) {
            if (mc.screen == null) open(null);
        }
    }

    public static void open(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (last == null) {
            if (mc.player != null) mc.player.displayClientMessage(
                    Component.literal("No death recap yet.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        mc.setScreen(new RecapScreen(parent, last));
    }
}
