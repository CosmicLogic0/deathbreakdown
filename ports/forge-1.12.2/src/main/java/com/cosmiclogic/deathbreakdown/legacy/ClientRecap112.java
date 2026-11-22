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
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/** Client side for 1.12.2: your recent recaps, the reopen key (K), and the button on the death screen. */
public class ClientRecap112 {
    static final int BUTTON_ID = 9001;
    static KeyBinding OPEN_KEY;
    /** Recaps this client received, oldest first; capped at historySize. */
    private static final List<Recap> HISTORY = new ArrayList<Recap>();
    private static long autoOpened = Long.MIN_VALUE;

    static void preInit() {
        OPEN_KEY = new KeyBinding("key.deathbreakdown.open", Keyboard.KEY_K, "key.categories.deathbreakdown");
        ClientRegistry.registerKeyBinding(OPEN_KEY);
        MinecraftForge.EVENT_BUS.register(new ClientRecap112());
    }

    static void init() {}

    static Recap last() { return HISTORY.isEmpty() ? null : HISTORY.get(HISTORY.size() - 1); }

    static List<Recap> history() { return new ArrayList<Recap>(HISTORY); }

    static int remember(Recap r) {
        for (int i = 0; i < HISTORY.size(); i++) {
            Recap x = HISTORY.get(i);
            if (x.timeMs == r.timeMs && x.victim.equals(r.victim)) return i;
        }
        int at = HISTORY.size();
        while (at > 0 && HISTORY.get(at - 1).timeMs > r.timeMs) at--;
        HISTORY.add(at, r);
        int max = Math.max(1, Math.min(20, DeathBreakdown112.config.historySize));
        while (HISTORY.size() > max) { HISTORY.remove(0); at--; }
        return Math.max(0, at);
    }

    /** Packets arrive on the network thread; handle them on the game thread. */
    static void schedule(final String data) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override public void run() { receive(data); }
        });
    }

    /** 1.1.0+ servers flag deaths vs replays; from a 1.0.0 server a recap made in the last 3 s counts as a death. */
    static void receive(String data) {
        Recap r = RecapCodec.decode(data);
        if (r == null) return;
        Boolean flag = RecapCodec.isDeath(data);
        boolean death = flag != null ? flag : Math.abs(System.currentTimeMillis() - r.timeMs) < 3000L;
        int at = remember(r);
        Minecraft mc = Minecraft.getMinecraft();
        if (death) {
            ClientSelfTest112.sawDeathPacket = true;
            if (DeathBreakdown112.config.chatSummary && mc.player != null) {
                ClientSelfTest112.sawSummary = true;
                mc.ingameGUI.getChatGUI().printChatMessage(ServerEvents112.color("[" + DeathBreakdown112.NAME + "] ", TextFormatting.GOLD)
                        .appendSibling(ServerEvents112.color(RecapText.summary(r, DeathBreakdown112.config.hearts), TextFormatting.WHITE))
                        .appendSibling(ServerEvents112.color(" Press ", TextFormatting.GRAY))
                        .appendSibling(ServerEvents112.color(OPEN_KEY.getDisplayName(), TextFormatting.AQUA))
                        .appendSibling(ServerEvents112.color(" to see it again.", TextFormatting.GRAY)));
            }
            if (DeathBreakdown112.config.openOnDeath && mc.currentScreen instanceof GuiGameOver) autoOpen(mc.currentScreen);
        } else {
            openAt(mc.currentScreen, at);
        }
    }

    /** Adds the "Death Breakdown" button to the death screen. */
    @SubscribeEvent
    public void onScreenInit(GuiScreenEvent.InitGuiEvent.Post e) {
        ClientSelfTest112.maybeStart();
        if (!(e.getGui() instanceof GuiGameOver)) return;
        ClientSelfTest112.sawDeathButton = true;
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

    /** Leaving a world or server: its recaps belong to it. */
    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override public void run() {
                HISTORY.clear();
                autoOpened = Long.MIN_VALUE;
            }
        });
    }

    private static void autoOpen(final GuiScreen parent) {
        Recap last = last();
        if (last == null || autoOpened == last.timeMs) return;
        long age = Math.abs(System.currentTimeMillis() - last.timeMs);
        if (age > 60_000L) return;
        autoOpened = last.timeMs;
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override public void run() { open(parent); }
        });
    }

    /** Does the server we're on have Death Breakdown? (Then an empty history asks it for the saved recap.) */
    private static boolean serverHasMod(Minecraft mc) {
        try {
            if (mc.getConnection() == null) return false;
            NetworkDispatcher d = NetworkDispatcher.get(mc.getConnection().getNetworkManager());
            return d != null && d.getModList() != null && d.getModList().containsKey(DeathBreakdown112.MOD_ID);
        } catch (Throwable t) {
            return mc.isIntegratedServerRunning();
        }
    }

    static void open(GuiScreen parent) {
        openAt(parent, HISTORY.size() - 1);
    }

    static void openAt(GuiScreen parent, int index) {
        Minecraft mc = Minecraft.getMinecraft();
        if (HISTORY.isEmpty()) {
            if (mc.player != null && serverHasMod(mc)) { mc.player.sendChatMessage("/deathbreakdown"); return; }
            mc.ingameGUI.setOverlayMessage(TextFormatting.GRAY + "No death recap yet. It shows up here after you die.", false);
            return;
        }
        mc.displayGuiScreen(new RecapScreen112(parent, history(), index));
    }
}
