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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Client side: keeps the last recap, the reopen key, and the button on the death screen. */
public final class ClientRecap {
    private ClientRecap() {}

    //#if MC >= 1.21.11
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath(DeathBreakdown.MOD_ID, "main"));
    //#elif MC >= 1.21.9
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(DeathBreakdown.MOD_ID, "main"));
    //#endif

    /** A key in Controls → Death Breakdown. {@code code} -1 = not bound until the player picks one. */
    private static KeyMapping key(String name, int code) {
        //#if MC >= 1.21.9
        return new KeyMapping("key.deathbreakdown." + name, InputConstants.Type.values()[0], code, CATEGORY);
        //#else
        return new KeyMapping("key.deathbreakdown." + name, InputConstants.Type.KEYSYM, code, "key.categories.deathbreakdown");
        //#endif
    }

    //#if MC >= 26.1
    public static final KeyMapping OPEN_KEY = key("open", InputConstants.KEY_K);
    //#else
    public static final KeyMapping OPEN_KEY = key("open", GLFW.GLFW_KEY_K);
    //#endif
    /** Turns the death HUD on or off (not bound by default). */
    public static final KeyMapping HUD_KEY = key("hud", -1);
    /** Turns the death marker on or off (not bound by default). */
    public static final KeyMapping MARKER_KEY = key("marker", -1);
    /** Every key, for the loader to register in Controls. */
    public static final KeyMapping[] KEYS = {OPEN_KEY, HUD_KEY, MARKER_KEY};

    /** Recaps this client received, oldest first (newest = the last death), capped at historySize. */
    private static final List<Recap> HISTORY = new ArrayList<>();
    private static long autoOpened = Long.MIN_VALUE;

    public static Recap last() { return HISTORY.isEmpty() ? null : HISTORY.get(HISTORY.size() - 1); }

    public static List<Recap> history() { return new ArrayList<>(HISTORY); }

    /** Adds a recap (or finds it again); returns its index. */
    static int remember(Recap r) {
        for (int i = 0; i < HISTORY.size(); i++) {
            Recap x = HISTORY.get(i);
            if (x.timeMs == r.timeMs && x.victim.equals(r.victim)) return i;
        }
        int at = HISTORY.size();
        while (at > 0 && HISTORY.get(at - 1).timeMs > r.timeMs) at--; // keep time order (replays of old deaths)
        HISTORY.add(at, r);
        int max = Math.max(1, Math.min(20, DeathBreakdown.config.historySize));
        while (HISTORY.size() > max) { HISTORY.remove(0); at--; }
        return Math.max(0, at);
    }

    /** Set by the loader: does the server we're on have Death Breakdown (its network channel)? */
    public static java.util.function.BooleanSupplier serverHasMod = () -> false;

    /** Asks the server for the saved recap (after a relog or restart the client has none of its own). */
    static void requestFromServer(Minecraft mc) {
        if (mc.player == null) return;
        //#if MC >= 1.19.3
        mc.player.connection.sendCommand("deathbreakdown");
        //#elif MC >= 1.19
        mc.player.commandUnsigned("deathbreakdown");
        //#else
        mc.player.chat("/deathbreakdown");
        //#endif
    }

    static void copyToClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }


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

    /**
     * A recap arrived from the server. 1.1.0+ servers say whether it's a death that just happened or a replay
     * (/deathbreakdown, history). The death packet arrives before the client knows its health is 0, so 1.0.0 asked
     * the wrong question here and opened the recap at once (and never printed the chat summary); now the flag decides.
     * From a 1.0.0 server (no flag) a recap made in the last 3 seconds counts as a fresh death.
     */
    public static void receive(String data) {
        Recap r = RecapCodec.decode(data);
        if (r == null) return;
        Boolean flag = RecapCodec.isDeath(data);
        boolean death = flag != null ? flag : Math.abs(System.currentTimeMillis() - r.timeMs) < 3000L;
        int at = remember(r);
        Minecraft mc = Minecraft.getInstance();
        if (death) {
            ClientSelfTest.sawDeathPacket = true;
            ClientMarker.onDeath(mc, r);
            if (DeathBreakdown.config.chatSummary && mc.player != null) {
                ClientSelfTest.sawSummary = true;
                say(mc, Txt.lit("[" + DeathBreakdown.NAME + "] ").withStyle(ChatFormatting.GOLD)
                        .append(Txt.lit(RecapText.summary(r, DeathBreakdown.config.hearts)).withStyle(ChatFormatting.WHITE))
                        .append(Txt.lit(" Press ").withStyle(ChatFormatting.GRAY))
                        .append(OPEN_KEY.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.AQUA))
                        .append(Txt.lit(" to see it again.").withStyle(ChatFormatting.GRAY)), false);
            }
            // The death screen opens a moment later; onScreenInit opens the recap then (if openOnDeath).
            if (DeathBreakdown.config.openOnDeath && screen(mc) instanceof DeathScreen) autoOpen(screen(mc));
        } else {
            openAt(screen(mc), at); // asked for with /deathbreakdown (show, player)
        }
    }

    /** Called after any screen is built. Adds the "Death Breakdown" button to the death screen. */
    public static void onScreenInit(Screen screen, Consumer<Button> addButton) {
        ClientSelfTest.maybeStart();
        if (!(screen instanceof DeathScreen)) return;
        com.cosmiclogic.deathbreakdown.core.RecapConfig c = DeathBreakdown.config;
        if (c.deathButton) {
            ClientSelfTest.sawDeathButton = true;
            int w = 110, h = 20;
            int x = c.deathButtonCorner.endsWith("right") ? screen.width - w - c.deathButtonX : c.deathButtonX;
            int y = c.deathButtonCorner.startsWith("bottom") ? screen.height - h - c.deathButtonY : c.deathButtonY;
            x = Math.max(0, Math.min(screen.width - w, x));
            y = Math.max(0, Math.min(screen.height - h, y));
            addButton.accept(button("☠ " + DeathBreakdown.NAME, x, y, w, h, () -> open(screen)));
        }
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
        Recap last = last();
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
        while (HUD_KEY.consumeClick()) {
            toggle("hudEnabled", DeathBreakdown.config.hudEnabled ? "false" : "true",
                    DeathBreakdown.config.hudEnabled ? "Death HUD off" : "Death HUD on");
        }
        while (MARKER_KEY.consumeClick()) {
            boolean on = !DeathBreakdown.config.markerStyle.equals("off");
            toggle("markerStyle", on ? "off" : "particles", on ? "Death marker off" : "Death marker on");
        }
    }

    /** A key flips a setting: change it, save the file, say so above the hotbar. */
    private static void toggle(String key, String value, String message) {
        com.cosmiclogic.deathbreakdown.core.RecapConfig c = DeathBreakdown.config.copy();
        com.cosmiclogic.deathbreakdown.core.Option.get(key).set(c, value);
        DeathBreakdown.applyConfig(c);
        say(Minecraft.getInstance(), Txt.lit(message).withStyle(ChatFormatting.GRAY), true);
    }

    /** The sound when the recap opens (off by default). */
    static void playOpenSound() {
        com.cosmiclogic.deathbreakdown.core.RecapConfig c = DeathBreakdown.config;
        if (!c.soundOnOpen || c.soundVolume <= 0) return;
        float vol = Math.min(1F, c.soundVolume / 100F);
        net.minecraft.sounds.SoundEvent ev;
        if (c.soundKind.equals("bell")) {
            //#if MC >= 1.19.3
            ev = net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value();
            //#else
            ev = net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL;
            //#endif
        } else if (c.soundKind.equals("xp")) {
            ev = net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP;
        } else {
            ev = net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN;
        }
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(ev, 1.0F, vol));
        ClientSelfTest.soundsPlayed++;
    }

    public static void open(Screen parent) {
        openAt(parent, HISTORY.size() - 1);
    }

    static void openAt(Screen parent, int index) {
        Minecraft mc = Minecraft.getInstance();
        if (HISTORY.isEmpty()) {
            if (serverHasMod.getAsBoolean()) { requestFromServer(mc); return; }
            say(mc, Txt.lit("No death recap yet. It shows up here after you die.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        playOpenSound();
        setScreen(mc, new RecapScreen(parent, history(), index));
    }

    /** Leaving a world or server: its recaps belong to it. */
    public static void clear() {
        HISTORY.clear();
        ClientMarker.clear();
        autoOpened = Long.MIN_VALUE;
    }
}
