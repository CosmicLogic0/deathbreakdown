package com.cosmiclogic.deathbreakdown.legacy;

import com.cosmiclogic.deathbreakdown.core.RecapConfig;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Death Breakdown's settings for 1.12.2: every option, with a one-line explanation when you point at it. */
public class ConfigScreen112 extends GuiScreen {
    private static final int[] WINDOWS = {5, 10, 15, 20, 30, 45, 60};
    private static final int[] HISTORIES = {1, 3, 5, 10, 20};
    private static final int DONE = 100, CANCEL = 101;
    private static final String[] NAMES = {"Time", "Hit by", "Type", "Damage", "Armor", "Enchants", "Other", "Health"};
    private static final String[] COL_HELP = {"Seconds before death.", "Who or what hit you, with the weapon.",
            "The damage type (Melee, Arrow, Lava...).", "Damage before armor.", "What your armor and toughness stopped.",
            "What Protection-type enchantments stopped.", "What Resistance, absorption hearts and shields took.",
            "Your health right after the hit."};

    private final GuiScreen parent;
    private final RecapConfig c = new RecapConfig();
    private final List<String> help = new ArrayList<String>();
    private String status = "";

    public ConfigScreen112(GuiScreen parent) {
        this.parent = parent;
        RecapConfig o = DeathBreakdown112.config;
        c.windowSeconds = o.windowSeconds; c.showSource = o.showSource; c.showType = o.showType; c.showDamage = o.showDamage;
        c.showArmor = o.showArmor; c.showEnchants = o.showEnchants; c.showOther = o.showOther; c.showHealthLeft = o.showHealthLeft;
        c.showTime = o.showTime; c.hearts = o.hearts; c.openOnDeath = o.openOnDeath; c.chatSummary = o.chatSummary;
        c.showTip = o.showTip; c.historySize = o.historySize; c.saveHistory = o.saveHistory;
    }

    private static String onOff(boolean b) { return b ? "ON" : "OFF"; }

    private static int next(int[] options, int now) {
        for (int o : options) if (o > now) return o;
        return options[0];
    }

    private String label(int id) {
        switch (id) {
            case 0: return "Open on death: " + onOff(c.openOnDeath);
            case 1: return "Chat summary: " + onOff(c.chatSummary);
            case 2: return "Amounts: " + (c.hearts ? "hearts" : "health points");
            case 3: return "Tip line: " + onOff(c.showTip);
            case 4: return "Time window: " + c.windowSeconds + " s";
            case 5: return "Deaths kept: " + c.historySize;
            case 6: return "Keep after restart: " + onOff(c.saveHistory);
            default: return NAMES[id - 10] + ": " + onOff(col(id - 10));
        }
    }

    private boolean col(int i) {
        switch (i) {
            case 0: return c.showTime; case 1: return c.showSource; case 2: return c.showType; case 3: return c.showDamage;
            case 4: return c.showArmor; case 5: return c.showEnchants; case 6: return c.showOther; default: return c.showHealthLeft;
        }
    }

    private void toggleCol(int i) {
        switch (i) {
            case 0: c.showTime = !c.showTime; break; case 1: c.showSource = !c.showSource; break;
            case 2: c.showType = !c.showType; break; case 3: c.showDamage = !c.showDamage; break;
            case 4: c.showArmor = !c.showArmor; break; case 5: c.showEnchants = !c.showEnchants; break;
            case 6: c.showOther = !c.showOther; break; default: c.showHealthLeft = !c.showHealthLeft; break;
        }
    }

    @Override
    public void initGui() {
        help.clear();
        int w = 150, gap = 6, left = width / 2 - w - gap / 2, right = width / 2 + gap / 2, y = 36, step = 24;
        int[][] pos = {{left, y}, {right, y}, {left, y + step}, {right, y + step}, {left, y + 2 * step}, {right, y + 2 * step}, {left, y + 3 * step}};
        String[] helps = {"Open the recap by itself when you die (the death screen's button always works).",
                "Post one line in chat after each death.", "Show damage in hearts (1 heart = 2 health) or health points.",
                "Show one short tip, e.g. \"Lava did 89% of the damage.\"",
                "How many seconds before death are recorded. Set on the server in multiplayer.",
                "How many of your last deaths you can browse with < and >.",
                "Save your recent deaths in the world folder so they survive leaving and restarting."};
        for (int i = 0; i < pos.length; i++) buttonList.add(new GuiButton(i, pos[i][0], pos[i][1], w, 20, label(i)));
        for (String h : helps) help.add(h);
        int cy = y + 4 * step + 8, cw = (2 * w + gap - 3 * 4) / 4;
        for (int i = 0; i < NAMES.length; i++) {
            buttonList.add(new GuiButton(10 + i, left + (i % 4) * (cw + 4), cy + (i / 4) * step, cw, 20, label(10 + i)));
        }
        buttonList.add(new GuiButton(DONE, width / 2 - 154, height - 28, 150, 20, "Done"));
        buttonList.add(new GuiButton(CANCEL, width / 2 + 4, height - 28, 150, 20, "Cancel"));
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        switch (b.id) {
            case 0: c.openOnDeath = !c.openOnDeath; break;
            case 1: c.chatSummary = !c.chatSummary; break;
            case 2: c.hearts = !c.hearts; break;
            case 3: c.showTip = !c.showTip; break;
            case 4: c.windowSeconds = next(WINDOWS, c.windowSeconds); break;
            case 5: c.historySize = next(HISTORIES, c.historySize); break;
            case 6: c.saveHistory = !c.saveHistory; break;
            case DONE:
                try {
                    c.save(DeathBreakdown112.configFile());
                    DeathBreakdown112.reloadConfig();
                    mc.displayGuiScreen(parent);
                } catch (IOException e) {
                    status = "Couldn't save: " + e.getMessage();
                }
                return;
            case CANCEL: mc.displayGuiScreen(parent); return;
            default:
                if (b.id >= 10 && b.id < 10 + NAMES.length) toggleCol(b.id - 10);
                break;
        }
        b.displayString = label(b.id);
        status = "";
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, DeathBreakdown112.NAME + " settings", width / 2, 12, 0xFFAA00);
        drawCenteredString(fontRenderer, "Columns in the recap", width / 2, 36 + 4 * 24 - 2, 0xAAAAAA);
        super.drawScreen(mx, my, pt);
        String text = status;
        if (text.isEmpty()) {
            for (GuiButton b : buttonList) {
                if (mx >= b.x && mx < b.x + b.width && my >= b.y && my < b.y + b.height) {
                    if (b.id < help.size()) text = help.get(b.id);
                    else if (b.id >= 10 && b.id < 10 + NAMES.length) text = "Column: " + COL_HELP[b.id - 10];
                }
            }
        }
        if (text.isEmpty()) text = "Point at an option to see what it does. Saved to config/deathbreakdown.properties.";
        List<String> lines = fontRenderer.listFormattedStringToWidth(text, Math.max(160, width - 40));
        for (int i = 0; i < Math.min(2, lines.size()); i++) drawCenteredString(fontRenderer, lines.get(i), width / 2, height - 54 + i * 10, 0xDDDDDD);
    }
}
