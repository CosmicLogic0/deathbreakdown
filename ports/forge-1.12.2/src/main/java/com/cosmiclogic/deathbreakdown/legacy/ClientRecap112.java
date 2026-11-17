package com.cosmiclogic.deathbreakdown.legacy;

import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapCodec;
import com.cosmiclogic.deathbreakdown.core.RecapText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

/** Client side for 1.12.2: keeps the last recap, the reopen key (K), and the button on the death screen. */
public class ClientRecap112 {
    static final int BUTTON_ID = 9001;
    static KeyBinding OPEN_KEY;
    private static Recap last;
    private static long autoOpened = Long.MIN_VALUE;

    static void preInit() {
        OPEN_KEY = new KeyBinding("key.deathbreakdown.open", Keyboard.KEY_K, "key.categories.deathbreakdown");
        ClientRegistry.registerKeyBinding(OPEN_KEY);
        MinecraftForge.EVENT_BUS.register(new ClientRecap112());
    }

    static void init() {}

    /** Packets arrive on the network thread; handle them on the game thread. */
    static void schedule(final String data) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override public void run() { receive(data); }
        });
    }

    static void receive(String data) {
        Recap r = RecapCodec.decode(data);
        if (r == null) return;
        last = r;
        Minecraft mc = Minecraft.getMinecraft();
        boolean dead = mc.player == null || mc.player.getHealth() <= 0 || mc.currentScreen instanceof GuiGameOver;
        if (dead) {
            if (DeathBreakdown112.config.chatSummary && mc.player != null) {
                mc.ingameGUI.getChatGUI().printChatMessage(ServerEvents112.color("[" + DeathBreakdown112.NAME + "] ", TextFormatting.GOLD)
                        .appendSibling(ServerEvents112.color(RecapText.summary(r, DeathBreakdown112.config.hearts), TextFormatting.WHITE))
                        .appendSibling(ServerEvents112.color(" Press ", TextFormatting.GRAY))
                        .appendSibling(ServerEvents112.color(OPEN_KEY.getDisplayName(), TextFormatting.AQUA))
                        .appendSibling(ServerEvents112.color(" to see it again.", TextFormatting.GRAY)));
            }
            if (DeathBreakdown112.config.openOnDeath && mc.currentScreen instanceof GuiGameOver) autoOpen(mc.currentScreen);
        } else {
            open(mc.currentScreen); // came from /deathbreakdown
        }
    }

    /** Adds the "Death Breakdown" button to the death screen. */
    @SubscribeEvent
    public void onScreenInit(GuiScreenEvent.InitGuiEvent.Post e) {
        if (!(e.getGui() instanceof GuiGameOver)) return;
        e.getButtonList().add(new GuiButton(BUTTON_ID, 4, 4, 110, 20, "☠ " + DeathBreakdown112.NAME));
        if (DeathBreakdown112.config.openOnDeath) autoOpen(e.getGui());
    }

    @SubscribeEvent
    public void onButton(GuiScreenEvent.ActionPerformedEvent.Pre e) {
        if (e.getGui() instanceof GuiGameOver && e.getButton().id == BUTTON_ID) {
            open(e.getGui());
            e.setCanceled(true);
        }
    }

    /** The reopen key, while no screen is open. */
    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        while (OPEN_KEY.isPressed()) {
            if (Minecraft.getMinecraft().currentScreen == null) open(null);
        }
    }

    private static void autoOpen(final GuiScreen parent) {
        if (last == null || autoOpened == last.timeMs) return;
        long age = Math.abs(System.currentTimeMillis() - last.timeMs);
        if (age > 60_000L) return; // an old recap, e.g. died again on a server without the mod
        autoOpened = last.timeMs;
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override public void run() { open(parent); }
        });
    }

    static void open(GuiScreen parent) {
        Minecraft mc = Minecraft.getMinecraft();
        if (last == null) {
            mc.ingameGUI.setOverlayMessage(TextFormatting.GRAY + "No death recap yet.", false);
            return;
        }
        mc.displayGuiScreen(new RecapScreen112(parent, last));
    }
}
