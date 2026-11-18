package com.cosmiclogic.dbshots;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Takes the real in-game screenshots for the CurseForge galleries (same method as Range Sight's PageShots).
 * "mod": the client test's new world is made Classic Flat; Death Breakdown (the released jar) runs in single player.
 * "plugin host:port": this client has no Death Breakdown mod and joins a Superflat Paper server with the plugin.
 * Every death is real game damage (a fall, a zombie with a sword, skeleton arrows, lava, a creeper).
 * An empty see-through screen keeps the client test from quitting until every shot is saved.
 */
final class Shots {
    static final Logger LOG = LoggerFactory.getLogger("dbshots");
    private static String mode;
    private static String address;
    private static boolean holding, connecting;
    private static int wait, step, polls;
    private static final List<Step> STEPS = new ArrayList<>();
    private static BlockPos base;
    private static Style detailsStyle, openStyle;

    private interface Step {
        /** Returns ticks to wait before the next step, or REPEAT to run this step again next tick. */
        int run(Minecraft mc) throws Exception;
    }

    private static final int REPEAT = -1;

    private Shots() {}

    static boolean init(Path configDir) {
        try {
            Path marker = configDir.resolve("dbshots.txt");
            if (!Files.exists(marker)) return false;
            String[] parts = new String(Files.readAllBytes(marker)).trim().split("\\s+");
            mode = parts[0];
            address = parts.length > 1 ? parts[1] : null;
            LOG.info("dbshots: mode {}", mode);
            return true;
        } catch (Exception e) {
            LOG.error("dbshots: bad marker", e);
            return false;
        }
    }

    static boolean plugin() {
        return "plugin".equals(mode);
    }

    /** The client test opens "Create World": make it Classic Flat, or (plugin mode) join the Paper server instead. */
    static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof CreateWorldScreen screen)) return;
        if (plugin()) {
            event.setCanceled(true);
            connecting = true;
            return;
        }
        WorldCreationUiState ui = screen.getUiState();
        List<WorldCreationUiState.WorldTypeEntry> types = new ArrayList<>(ui.getNormalPresetList());
        types.addAll(ui.getAltPresetList());
        for (WorldCreationUiState.WorldTypeEntry type : types) {
            if (type.preset() != null && type.preset().is(WorldPresets.FLAT)) {
                ui.setWorldType(type);
                LOG.info("dbshots: new world is Superflat (Classic Flat)");
            }
        }
        ui.setGenerateStructures(false);
        ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
        ui.setDifficulty(Difficulty.PEACEFUL);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (connecting) {
            connecting = false;
            ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
                    new ServerData("Death Breakdown shots", address, ServerData.Type.OTHER), false, null);
            LOG.info("dbshots: joining {}", address);
            return;
        }
        if (mc.player == null || mc.level == null) return;
        if (!holding) {
            if (mc.screen != null) return;
            hold(mc);
            holding = true;
            setup(mc);
            LOG.info("dbshots: world shown, holding the client open");
            return;
        }
        if (step >= STEPS.size()) return;
        if (wait > 0) {
            wait--;
            return;
        }
        try {
            int r = STEPS.get(step).run(mc);
            if (r == REPEAT) {
                if (++polls > 1200) { // 60 s
                    LOG.error("dbshots: step {} timed out", step);
                    polls = 0;
                    step++;
                }
                return;
            }
            polls = 0;
            step++;
            wait = r;
        } catch (Throwable t) {
            LOG.error("dbshots: step " + step + " failed", t);
            step++;
            wait = 2;
        }
    }

    // ------------------------------------------------------------------ the shots

    private static void setup(Minecraft mc) {
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1920, 1080);
        mc.options.graphicsMode().set(GraphicsStatus.FANCY);
        mc.options.renderDistance().set(12);
        mc.options.hideGui = true;
        base = mc.player.blockPosition();
        String p = plugin() ? "plugin-" : "mod-";
        BlockPos a = base.offset(4, 0, 4);      // zombie + skeleton fight
        BlockPos l = base.offset(34, 0, 4);     // lava
        BlockPos c = base.offset(64, 0, 4);     // creeper

        cmds(List.of(
                "gamerule sendCommandFeedback false", "gamerule logAdminCommands false", "gamerule doDaylightCycle false",
                "gamerule doWeatherCycle false", "gamerule doMobSpawning false", "gamerule keepInventory true",
                "gamerule doImmediateRespawn false", "time set 6000", "weather clear", "difficulty normal",
                "kill @e[type=!player]",
                fill(l.offset(-2, -1, -2), l.offset(2, -1, 2), "stone_bricks"),
                fill(l.offset(-1, -1, -1), l.offset(1, -1, 1), "lava")), 40);

        // ---- 1: zombie with a sword + skeleton, after a short fall (full iron, Protection III, Feather Falling IV)
        cmds(List.of("gamemode survival @s",
                "item replace entity @s armor.head with iron_helmet",
                "item replace entity @s armor.chest with iron_chestplate[enchantments={levels:{\"minecraft:protection\":3}}]",
                "item replace entity @s armor.legs with iron_leggings",
                "item replace entity @s armor.feet with iron_boots[enchantments={levels:{\"minecraft:feather_falling\":4}}]",
                "item replace entity @s weapon.mainhand with iron_sword",
                "effect give @s instant_health 1 10 true", "effect give @s saturation 1 10 true",
                tp(a, 0, 8),
                summon("zombie", a.offset(0, 0, 3), 180, "HandItems:[{id:\"minecraft:iron_sword\",count:1},{}],ArmorItems:[{},{},{},{id:\"minecraft:iron_helmet\",count:1}]"),
                summon("skeleton", a.offset(3, 0, 9), 160, "HandItems:[{id:\"minecraft:bow\",count:1},{}],ArmorItems:[{},{},{},{id:\"minecraft:chainmail_helmet\",count:1}]")), 60);
        STEPS.add(m -> {
            m.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            m.options.hideGui = true;
            return 30;
        });
        STEPS.add(m -> save(m, p + "fight"));
        STEPS.add(m -> {
            m.options.setCameraType(CameraType.FIRST_PERSON);
            cmd(m, tpUp(a, 7));
            return 40; // the fall
        });
        cmds(List.of("data merge entity @e[type=zombie,limit=1,sort=nearest] {NoAI:0b}",
                "data merge entity @e[type=skeleton,limit=1,sort=nearest] {NoAI:0b}"), 1);
        untilDead();
        if (plugin()) {
            STEPS.add(m -> {
                m.options.hideGui = false;
                guiScale(m, 3);
                return 45;
            });
            STEPS.add(m -> save(m, p + "death-chat"));
            respawn();
            chatHoverAndBook(p, "mobs", true);
        } else {
            STEPS.add(m -> {
                m.options.hideGui = false;
                guiScale(m, 3);
                return 45;
            });
            STEPS.add(m -> save(m, p + "death-screen"));
            STEPS.add(m -> {
                pressDeathBreakdownButton(m);
                return 15;
            });
            STEPS.add(m -> save(m, p + "recap-mobs"));
            STEPS.add(m -> {
                hoverFinalBlow(m);
                return 6;
            });
            STEPS.add(m -> save(m, p + "recap-hover"));
            respawn();
        }

        // ---- 2: fall into lava (Fire Protection IV)
        cmds(List.of("kill @e[type=!player]",
                "item replace entity @s armor.chest with iron_chestplate[enchantments={levels:{\"minecraft:fire_protection\":4}}]",
                "item replace entity @s armor.feet with iron_boots",
                "effect give @s instant_health 1 10 true", tpUp(l, 6)), 1);
        untilDead();
        if (plugin()) {
            respawn();
            STEPS.add(m -> {
                cmd(m, "deathbreakdown book");
                return 30;
            });
            STEPS.add(m -> {
                guiScale(m, 4);
                m.options.hideGui = false;
                return 10;
            });
            STEPS.add(m -> m.screen instanceof BookViewScreen ? save(m, p + "book-lava") : REPEAT);
            STEPS.add(m -> {
                hold(m);
                return 10;
            });
        } else {
            STEPS.add(m -> {
                pressDeathBreakdownButton(m);
                return 15;
            });
            STEPS.add(m -> save(m, p + "recap-lava"));
            respawn();
        }

        // ---- 3: a fall, then a creeper (iron chestplate with Blast Protection IV only)
        cmds(List.of("kill @e[type=!player]",
                "item replace entity @s armor.head with air", "item replace entity @s armor.legs with air",
                "item replace entity @s armor.feet with air",
                "item replace entity @s armor.chest with iron_chestplate[enchantments={levels:{\"minecraft:blast_protection\":4}}]",
                "effect give @s instant_health 1 10 true", tpUp(c, 10)), 40);
        cmds(List.of(summon("creeper", c.offset(0, 0, 2), 180, "")), 1);
        untilDead();
        if (plugin()) {
            respawn();
            // /deathbreakdown again: the summary line comes back in chat (close-up)
            STEPS.add(m -> {
                cmd(m, "deathbreakdown");
                m.options.hideGui = false;
                guiScale(m, 4);
                m.setScreen(new ChatScreen(""));
                return 30;
            });
            STEPS.add(m -> save(m, p + "chat-command"));
        } else {
            STEPS.add(m -> {
                pressDeathBreakdownButton(m);
                return 15;
            });
            STEPS.add(m -> save(m, p + "recap-creeper"));
            respawn();
            // chat close-up: the summary line after each death
            STEPS.add(m -> {
                m.options.hideGui = false;
                guiScale(m, 4);
                m.setScreen(new ChatScreen(""));
                return 20;
            });
            STEPS.add(m -> save(m, p + "chat-summary"));
            // reopen with /deathbreakdown, over the world
            STEPS.add(m -> {
                hold(m);
                guiScale(m, 3);
                cmd(m, tp(base.offset(4, 0, -6), 20, 4));
                return 40;
            });
            STEPS.add(m -> {
                cmd(m, "deathbreakdown");
                return 30;
            });
            STEPS.add(m -> save(m, p + "reopen"));
            // the key bind in Controls
            STEPS.add(m -> {
                m.setScreen(keyBindsScreen(m));
                return 10;
            });
            STEPS.add(m -> {
                scrollKeyBinds(m);
                return 10;
            });
            STEPS.add(m -> save(m, p + "key-binds"));
        }

        STEPS.add(m -> {
            m.options.hideGui = false;
            m.setScreen(null);
            LOG.info("dbshots: done");
            return 1;
        });
    }

    /** Plugin: the chat summary (hover on [details]) and the book from [Open recap]. */
    private static void chatHoverAndBook(String p, String name, boolean pages) {
        STEPS.add(m -> {
            m.options.hideGui = false;
            guiScale(m, 3);
            m.setScreen(new ChatScreen(""));
            return 10;
        });
        STEPS.add(m -> {
            findChatStyles(m);
            if (detailsStyle == null) return REPEAT;
            return 2;
        });
        STEPS.add(m -> save(m, p + "chat-hover"));
        STEPS.add(m -> {
            if (openStyle != null && m.screen != null) m.screen.handleComponentClicked(openStyle);
            else cmd(m, "deathbreakdown book");
            return 20;
        });
        STEPS.add(m -> {
            guiScale(m, 4);
            return 10;
        });
        STEPS.add(m -> m.screen instanceof BookViewScreen ? save(m, p + "book-" + name) : REPEAT);
        if (pages) {
            STEPS.add(m -> {
                m.screen.keyPressed(GLFW.GLFW_KEY_PAGE_DOWN, 0, 0);
                return 10;
            });
            STEPS.add(m -> save(m, p + "book-" + name + "-page2"));
        }
        STEPS.add(m -> {
            hold(m);
            return 10;
        });
    }

    // ------------------------------------------------------------------ helpers

    private static void untilDead() {
        STEPS.add(m -> m.screen instanceof DeathScreen ? 1 : REPEAT);
    }

    private static void respawn() {
        STEPS.add(m -> {
            if (m.player != null) m.player.respawn();
            return 20;
        });
        STEPS.add(m -> {
            hold(m);
            m.options.hideGui = true;
            guiScale(m, 3);
            return 30;
        });
    }

    private static void pressDeathBreakdownButton(Minecraft mc) {
        Screen s = mc.screen;
        if (s == null) return;
        for (var child : s.children()) {
            if (child instanceof Button b && b.getMessage().getString().contains("Death Breakdown")) {
                b.onPress();
                LOG.info("dbshots: pressed the Death Breakdown button");
                return;
            }
        }
        LOG.warn("dbshots: no Death Breakdown button on {}", s.getClass().getName());
    }

    /** Moves the mouse over the last (final blow) row of the recap table. */
    private static void hoverFinalBlow(Minecraft mc) throws Exception {
        Screen s = mc.screen;
        Object recap = field(s, "recap");
        List<?> hits = (List<?>) field(recap, "hits");
        int tableX = (int) field(s, "tableX"), tableW = (int) field(s, "tableW");
        int scroll = (int) field(s, "scroll"), visible = (int) field(s, "visibleRows");
        int row = Math.min(hits.size(), scroll + visible) - 1 - scroll;
        int y = 50 + 12 + 2 + 12 * row + 4;
        mouse(mc, tableX + tableW / 3.0, y);
    }

    private static void findChatStyles(Minecraft mc) {
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        detailsStyle = null;
        openStyle = null;
        double dx = 0, dy = 0;
        for (int y = h - 16; y > h - 220 && y > 0; y -= 2) {
            for (int x = 2; x < Math.min(w, 340); x += 2) {
                Style st = mc.gui.getChat().getClickedComponentStyleAt(x, y);
                if (st == null) continue;
                if (st.getClickEvent() != null && st.getClickEvent().getAction() == ClickEvent.Action.RUN_COMMAND
                        && st.getClickEvent().getValue().contains("book") && openStyle == null) openStyle = st;
                if (st.getHoverEvent() != null && st.getClickEvent() == null && detailsStyle == null) {
                    detailsStyle = st;
                    dx = x + 4;
                    dy = y;
                }
            }
            if (detailsStyle != null && openStyle != null) break;
        }
        if (detailsStyle != null) {
            mouse(mc, dx, dy);
            LOG.info("dbshots: hovering [details] at {},{}", dx, dy);
        }
    }

    /** Puts the mouse at a GUI position (both the real cursor and Minecraft's own copy). */
    private static void mouse(Minecraft mc, double gx, double gy) {
        double scale = mc.getWindow().getGuiScale();
        double px = gx * scale, py = gy * scale;
        long win = mc.getWindow().getWindow();
        GLFW.glfwSetCursorPos(win, px, py);
        try {
            Method onMove = mc.mouseHandler.getClass().getDeclaredMethod("onMove", long.class, double.class, double.class);
            onMove.setAccessible(true);
            onMove.invoke(mc.mouseHandler, win, px, py);
            onMove.invoke(mc.mouseHandler, win, px, py);
        } catch (Exception e) {
            LOG.info("dbshots: onMove not found ({}), setting the fields", e.toString());
        }
        try {
            Field fx = mc.mouseHandler.getClass().getDeclaredField("xpos"), fy = mc.mouseHandler.getClass().getDeclaredField("ypos");
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setDouble(mc.mouseHandler, px);
            fy.setDouble(mc.mouseHandler, py);
        } catch (Exception e) {
            LOG.warn("dbshots: could not move the mouse: {}", e.toString());
        }
    }

    private static Object field(Object o, String name) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void guiScale(Minecraft mc, int s) {
        if (mc.options.guiScale().get() != s) {
            mc.options.guiScale().set(s);
            mc.resizeDisplay();
        }
    }

    private static void hold(Minecraft mc) {
        mc.setScreen(new HoldScreen());
    }

    private static void cmds(List<String> list, int after) {
        STEPS.add(m -> {
            for (String c : list) cmd(m, c);
            return after;
        });
    }

    /** Runs a command as the player (op on the Paper server; permission 4 in single player). */
    private static void cmd(Minecraft mc, String c) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4).withSuppressedOutput(), c);
            });
        } else {
            mc.player.connection.sendCommand(c);
        }
    }

    private static String tp(BlockPos p, float yaw, float pitch) {
        return String.format(Locale.ROOT, "tp @s %.1f %d %.1f %.1f %.1f", p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, pitch);
    }

    private static String tpUp(BlockPos p, int up) {
        return tp(p.above(up), 0, 10);
    }

    private static String summon(String type, BlockPos p, float yaw, String extra) {
        String nbt = "{NoAI:1b,PersistenceRequired:1b,Rotation:[" + yaw + "f,0f]" + (extra.isEmpty() ? "" : "," + extra)
                + ",HandDropChances:[0f,0f],ArmorDropChances:[0f,0f,0f,0f]}";
        if ("creeper".equals(type)) nbt = "{PersistenceRequired:1b,Rotation:[" + yaw + "f,0f]}";
        return String.format(Locale.ROOT, "summon %s %.1f %d %.1f %s", type, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, nbt);
    }

    private static String fill(BlockPos a, BlockPos b, String block) {
        return "fill " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " " + block;
    }

    private static int save(Minecraft mc, String name) {
        int w = mc.getMainRenderTarget().width, h = mc.getMainRenderTarget().height;
        Screenshot.grab(mc.gameDirectory, "deathbreakdown-" + name + ".png", mc.getMainRenderTarget(),
                msg -> LOG.info("dbshots: saved {} ({}x{}) {}", name, w, h, msg.getString()));
        return 6;
    }

    private static Screen keyBindsScreen(Minecraft mc) {
        for (String cls : new String[]{"net.minecraft.client.gui.screens.options.controls.KeyBindsScreen",
                "net.minecraft.client.gui.screens.controls.KeyBindsScreen"}) {
            try {
                return (Screen) Class.forName(cls).getConstructor(Screen.class, net.minecraft.client.Options.class)
                        .newInstance(mc.screen, mc.options);
            } catch (ReflectiveOperationException ignored) {
                // try the next name
            }
        }
        throw new IllegalStateException("KeyBindsScreen not found");
    }

    /** Scrolls the key binds list so the Death Breakdown category is near the top. */
    private static void scrollKeyBinds(Minecraft mc) {
        Screen screen = mc.screen;
        if (screen == null) return;
        for (Object child : screen.children()) {
            if (child instanceof AbstractSelectionList<?> list) {
                int index = 0;
                for (Object entry : list.children()) {
                    StringBuilder text = new StringBuilder(entry.toString());
                    try {
                        for (Field f : entry.getClass().getDeclaredFields()) {
                            if (Component.class.isAssignableFrom(f.getType())) {
                                f.setAccessible(true);
                                Object v = f.get(entry);
                                if (v != null) text.append(' ').append(((Component) v).getString());
                            }
                        }
                    } catch (Exception ignored) {
                        // keep toString
                    }
                    if (text.toString().contains("Death Breakdown")) {
                        list.setScrollAmount(Math.max(0, index - 1) * 20.0);
                        LOG.info("dbshots: key binds scrolled to entry {}", index);
                        return;
                    }
                    index++;
                }
                LOG.info("dbshots: Death Breakdown category not found in {} entries", index);
            }
        }
    }

    /** Draws nothing, so the world shows through; doesn't pause the game. */
    private static final class HoldScreen extends Screen {
        HoldScreen() {
            super(Component.empty());
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }
    }
}
