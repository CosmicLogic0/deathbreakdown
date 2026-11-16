package com.cosmiclogic.deathbreakdown.client;

import com.cosmiclogic.deathbreakdown.DeathBreakdown;
import com.cosmiclogic.deathbreakdown.Txt;
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
//#if MC < 26.1
import org.lwjgl.glfw.GLFW;
//#endif

import java.util.function.Consumer;

/** Client side: keeps the last recap, the reopen key, and the button on the death screen. */
public final class ClientRecap {
    private ClientRecap() {}

    //#if MC >= 1.21.11
    //#if MC >= 26.1
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.deathbreakdown.open", InputConstants.Type.values()[0],
            InputConstants.KEY_K, KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath(DeathBreakdown.MOD_ID, "main")));
    //#else
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.deathbreakdown.open", InputConstants.Type.values()[0],
            GLFW.GLFW_KEY_K, KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath(DeathBreakdown.MOD_ID, "main")));
    //#endif
    //#elif MC >= 1.21.9
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.deathbreakdown.open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, KeyMapping.Category.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(DeathBreakdown.MOD_ID, "main")));
    //#else
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.deathbreakdown.open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.deathbreakdown");
    //#endif

    private static Recap last;
    private static long autoOpened = Long.MIN_VALUE;

    public static Recap last() { return last; }

    /** A chat line (overlay = above the hotbar). displayClientMessage was split in 26.1. */
    static void say(Minecraft mc, net.minecraft.network.chat.Component c, boolean overlay) {
        if (mc.player == null) return;
        //#if MC >= 26.1
        if (overlay) mc.player.sendOverlayMessage(c); else mc.player.sendSystemMessage(c);
        //#else
        mc.player.displayClientMessage(c, overlay);
        //#endif
    }

    /** The open screen (moved to mc.gui on 26.2+). */
    static Screen screen(Minecraft mc) {
        //#if MC >= 26.2
        return mc.gui.screen();
        //#else
        return mc.screen;
        //#endif
    }

    static void setScreen(Minecraft mc, Screen s) {
        //#if MC >= 26.2
        mc.gui.setScreen(s);
        //#else
        mc.setScreen(s);
        //#endif
    }

    /** A recap arrived from the server. */
    public static void receive(String data) {
        Recap r = RecapCodec.decode(data);
        if (r == null) return;
        last = r;
        Minecraft mc = Minecraft.getInstance();
        boolean dead = mc.player == null || mc.player.isDeadOrDying() || screen(mc) instanceof DeathScreen;
        if (dead) {
            if (DeathBreakdown.config.chatSummary && mc.player != null) {
                say(mc, Txt.lit("[" + DeathBreakdown.NAME + "] ").withStyle(ChatFormatting.GOLD)
                        .append(Txt.lit(RecapText.summary(r, DeathBreakdown.config.hearts)).withStyle(ChatFormatting.WHITE))
                        .append(Txt.lit(" Press ").withStyle(ChatFormatting.GRAY))
                        .append(OPEN_KEY.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.AQUA))
                        .append(Txt.lit(" to see it again.").withStyle(ChatFormatting.GRAY)), false);
            }
            if (DeathBreakdown.config.openOnDeath && screen(mc) instanceof DeathScreen) autoOpen(screen(mc));
        } else {
            open(screen(mc)); // came from /deathbreakdown
        }
    }

    /** Called after any screen is built. Adds the "Death Breakdown" button to the death screen. */
    public static void onScreenInit(Screen screen, Consumer<Button> addButton) {
        if (!(screen instanceof DeathScreen)) return;
        addButton.accept(button("☠ " + DeathBreakdown.NAME, 4, 4, 110, 20, () -> open(screen)));
        if (DeathBreakdown.config.openOnDeath) autoOpen(screen);
    }

    static Button button(String text, int x, int y, int w, int h, Runnable onPress) {
        //#if MC >= 1.19.3
        return Button.builder(Txt.lit(text), b -> onPress.run()).bounds(x, y, w, h).build();
        //#else
        return new Button(x, y, w, h, Txt.lit(text), b -> onPress.run());
        //#endif
    }

    private static void autoOpen(Screen parent) {
        if (last == null || autoOpened == last.timeMs) return;
        long age = Math.abs(System.currentTimeMillis() - last.timeMs);
        if (age > 60_000L) return; // an old recap, e.g. died again on a server without the mod
        autoOpened = last.timeMs;
        Minecraft.getInstance().execute(() -> open(parent));
    }

    /** The reopen key, checked on key presses while no screen is open. */
    public static void onKey() {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_KEY.consumeClick()) {
            if (screen(mc) == null) open(null);
        }
    }

    public static void open(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (last == null) {
            say(mc, Txt.lit("No death recap yet.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        setScreen(mc, new RecapScreen(parent, last));
    }
}
