package com.cosmiclogic.deathbreakdown.client;

import com.cosmiclogic.deathbreakdown.DeathBreakdown;
import com.cosmiclogic.deathbreakdown.Txt;
import com.cosmiclogic.deathbreakdown.core.RecapConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
//#if MC >= 26.1
import net.minecraft.client.gui.GuiGraphicsExtractor;
//#elif MC >= 1.20
import net.minecraft.client.gui.GuiGraphics;
//#else
import com.mojang.blaze3d.vertex.PoseStack;
//#endif

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Death Breakdown's settings, from the mod list (Forge/NeoForge "Config" button) or the recap screen's Settings button.
 * Every option of deathbreakdown.properties, each with a one-line explanation when you point at it.
 * Done saves the file and applies it at once; Cancel leaves everything as it was.
 */
public class ConfigScreen extends Screen {
    private static final int[] WINDOWS = {5, 10, 15, 20, 30, 45, 60};
    private static final int[] HISTORIES = {1, 3, 5, 10, 20};

    private final Screen parent;
    private final RecapConfig c;
    private final List<Entry> entries = new ArrayList<>();
    private String status = "";

    /** One button and what it means. */
    private static final class Entry {
        final Button button;
        final int x, y, w, h;
        final String help;
        Entry(Button b, int x, int y, int w, int h, String help) { this.button = b; this.x = x; this.y = y; this.w = w; this.h = h; this.help = help; }
    }

    private interface Get { String label(); }
    private interface Change { void run(); }

    public ConfigScreen(Screen parent) {
        super(Txt.lit(DeathBreakdown.NAME + " settings"));
        this.parent = parent;
        this.c = copy(DeathBreakdown.config);
    }

    private static RecapConfig copy(RecapConfig o) {
        RecapConfig c = new RecapConfig();
        c.windowSeconds = o.windowSeconds; c.showSource = o.showSource; c.showType = o.showType; c.showDamage = o.showDamage;
        c.showArmor = o.showArmor; c.showEnchants = o.showEnchants; c.showOther = o.showOther; c.showHealthLeft = o.showHealthLeft;
        c.showTime = o.showTime; c.hearts = o.hearts; c.openOnDeath = o.openOnDeath; c.chatSummary = o.chatSummary;
        c.showTip = o.showTip; c.historySize = o.historySize; c.saveHistory = o.saveHistory;
        return c;
    }

    private static String onOff(boolean b) { return b ? "ON" : "OFF"; }

    private static int next(int[] options, int now) {
        for (int o : options) if (o > now) return o;
        return options[0];
    }

    private void add(int x, int y, int w, Get label, Change change, String help) {
        Button[] self = new Button[1];
        self[0] = ClientRecap.button(label.label(), x, y, w, 20, () -> {
            change.run();
            self[0].setMessage(Txt.lit(label.label()));
            status = "";
        });
        //#if MC >= 1.17
        addRenderableWidget(self[0]);
        //#else
        addButton(self[0]);
        //#endif
        entries.add(new Entry(self[0], x, y, w, 20, help));
    }

    @Override
    protected void init() {
        entries.clear();
        int w = 150, gap = 6, left = width / 2 - w - gap / 2, right = width / 2 + gap / 2, y = 36, step = 24;
        add(left, y, w, () -> "Open on death: " + onOff(c.openOnDeath), () -> c.openOnDeath = !c.openOnDeath,
                "Open the recap by itself when you die (the death screen's button always works).");
        add(right, y, w, () -> "Chat summary: " + onOff(c.chatSummary), () -> c.chatSummary = !c.chatSummary,
                "Post one line in chat after each death: what killed you and how much your gear stopped.");
        y += step;
        add(left, y, w, () -> "Amounts: " + (c.hearts ? "hearts" : "health points"), () -> c.hearts = !c.hearts,
                "Show damage in hearts (1 heart = 2 health) or in health points.");
        add(right, y, w, () -> "Tip line: " + onOff(c.showTip), () -> c.showTip = !c.showTip,
                "Show one short tip, e.g. \"Lava did 89% of the damage.\"");
        y += step;
        add(left, y, w, () -> "Time window: " + c.windowSeconds + " s", () -> c.windowSeconds = next(WINDOWS, c.windowSeconds),
                "How many seconds before death are recorded. Set on the server in multiplayer.");
        add(right, y, w, () -> "Deaths kept: " + c.historySize, () -> c.historySize = next(HISTORIES, c.historySize),
                "How many of your last deaths you can browse with < and >.");
        y += step;
        add(left, y, w, () -> "Keep after restart: " + onOff(c.saveHistory), () -> c.saveHistory = !c.saveHistory,
                "Save your recent deaths in the world folder so they survive leaving and restarting.");
        y += step + 14;
        int cw = (2 * w + gap - 3 * 4) / 4;
        String[] names = {"Time", "Hit by", "Type", "Damage", "Armor", "Enchants", "Other", "Health"};
        String[] helps = {"Seconds before death.", "Who or what hit you, with the weapon.", "The damage type (Melee, Arrow, Lava...).",
                "Damage before armor.", "What your armor and toughness stopped.", "What Protection-type enchantments stopped.",
                "What Resistance, absorption hearts and shields took.", "Your health right after the hit."};
        for (int i = 0; i < names.length; i++) {
            final int col = i;
            int x = left + (i % 4) * (cw + 4), yy = y + (i / 4) * step;
            add(x, yy, cw, () -> names[col] + ": " + onOff(get(col)), () -> set(col, !get(col)), "Column: " + helps[col]);
        }
        int by = height - 28;
        Button done = ClientRecap.button("Done", width / 2 - 154, by, 150, 20, this::save);
        Button cancel = ClientRecap.button("Cancel", width / 2 + 4, by, 150, 20, this::onClose);
        //#if MC >= 1.17
        addRenderableWidget(done);
        addRenderableWidget(cancel);
        //#else
        addButton(done);
        addButton(cancel);
        //#endif
    }

    private boolean get(int col) {
        switch (col) {
            case 0: return c.showTime;
            case 1: return c.showSource;
            case 2: return c.showType;
            case 3: return c.showDamage;
            case 4: return c.showArmor;
            case 5: return c.showEnchants;
            case 6: return c.showOther;
            default: return c.showHealthLeft;
        }
    }

    private void set(int col, boolean v) {
        switch (col) {
            case 0: c.showTime = v; break;
            case 1: c.showSource = v; break;
            case 2: c.showType = v; break;
            case 3: c.showDamage = v; break;
            case 4: c.showArmor = v; break;
            case 5: c.showEnchants = v; break;
            case 6: c.showOther = v; break;
            default: c.showHealthLeft = v; break;
        }
    }

    private void save() {
        File f = DeathBreakdown.configFile();
        try {
            c.save(f);
            DeathBreakdown.reloadConfig();
            onClose();
        } catch (IOException e) {
            status = "Couldn't save: " + e.getMessage();
        }
    }

    //#if MC >= 26.1
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        draw(new Draw(g), mx, my);
    }
    //#elif MC >= 1.20
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        //#if MC < 1.20.2
        renderBackground(g);
        //#endif
        super.render(g, mx, my, pt);
        draw(new Draw(g), mx, my);
    }
    //#else
    @Override
    public void render(PoseStack pose, int mx, int my, float pt) {
        renderBackground(pose);
        super.render(pose, mx, my, pt);
        draw(new Draw(pose, this), mx, my);
    }
    //#endif

    private void draw(Draw g, int mx, int my) {
        g.centered(font, title.getString(), width / 2, 12, 0xFFAA00);
        g.centered(font, "Columns in the recap", width / 2, 36 + 4 * 24 + 3, 0xAAAAAA);
        String help = status;
        if (help.isEmpty()) {
            for (Entry e : entries) {
                if (mx >= e.x && mx < e.x + e.w && my >= e.y && my < e.y + e.h) { help = e.help; break; }
            }
        }
        if (help.isEmpty()) help = "Point at an option to see what it does. Saved to config/deathbreakdown.properties.";
        List<String> lines = Draw.wrap(font, help, Math.max(160, width - 40));
        for (int i = 0; i < Math.min(2, lines.size()); i++) g.centered(font, lines.get(i), width / 2, height - 54 + i * 10, 0xDDDDDD);
    }

    @Override
    public void onClose() {
        ClientRecap.setScreen(minecraft, parent);
    }
}
