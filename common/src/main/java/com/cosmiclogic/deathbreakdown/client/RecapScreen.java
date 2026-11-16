package com.cosmiclogic.deathbreakdown.client;

import com.cosmiclogic.deathbreakdown.DeathBreakdown;
import com.cosmiclogic.deathbreakdown.core.Hit;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapConfig;
import com.cosmiclogic.deathbreakdown.core.RecapText;
import com.cosmiclogic.deathbreakdown.core.RecapText.Col;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The recap: one row per hit, oldest at the top, final blow highlighted at the bottom, totals underneath.
 * Hover a row for every detail. Scroll if there are more rows than fit.
 */
public class RecapScreen extends Screen {
    private static final int ROW_H = 12, PAD = 8, TOP = 50;

    private final Screen parent;
    private final Recap recap;
    private final List<Col> cols;
    private final boolean hearts;
    private int[] widths;
    private int tableX, tableW, visibleRows, scroll;

    public RecapScreen(Screen parent, Recap recap) {
        super(Component.literal(DeathBreakdown.NAME));
        this.parent = parent;
        this.recap = recap;
        RecapConfig c = DeathBreakdown.config;
        this.cols = RecapText.columns(c);
        this.hearts = c.hearts;
    }

    @Override
    protected void init() {
        widths = new int[cols.size()];
        for (int i = 0; i < cols.size(); i++) {
            Col c = cols.get(i);
            int w = font.width(c.title);
            for (Hit h : recap.hits) w = Math.max(w, font.width(RecapText.cell(c, h, recap, hearts)));
            w = Math.max(w, font.width(total(c)));
            if (c == Col.SOURCE) w += font.width("\u2620 ");
            widths[i] = Math.min(w, c == Col.SOURCE ? 160 : 90);
        }
        tableW = PAD;
        for (int w : widths) tableW += w + PAD;
        tableX = Math.max(4, (width - tableW) / 2);
        int bottom = height - 64;
        visibleRows = Math.max(1, (bottom - (TOP + ROW_H + 2)) / ROW_H);
        scroll = Math.max(0, recap.hits.size() - visibleRows); // start at the bottom so the final blow is visible
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 50, height - 26, 100, 20).build());
    }

    private String total(Col c) {
        switch (c) {
            case SOURCE: return "Total";
            case DAMAGE: return RecapText.amount(recap.totalRaw(), hearts);
            case ARMOR: return recap.totalArmor() > 0.005F ? "-" + RecapText.amount(recap.totalArmor(), hearts) : "";
            case ENCHANTS: return recap.totalEnchants() > 0.005F ? "-" + RecapText.amount(recap.totalEnchants(), hearts) : "";
            case OTHER: return recap.totalOther() > 0.005F ? "-" + RecapText.amount(recap.totalOther(), hearts) : "";
            default: return "";
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFAA00);
        g.drawCenteredString(font, recap.deathMessage, width / 2, 20, 0xAAAAAA);
        List<FormattedCharSequence> summary = font.split(Component.literal(RecapText.summary(recap, hearts)), Math.max(200, width - 40));
        for (int i = 0; i < Math.min(2, summary.size()); i++) g.drawCenteredString(font, summary.get(i), width / 2, 32 + i * 10, 0xFFFFFF);

        int y = TOP;
        g.fill(tableX - 2, y - 2, tableX + tableW + 2, y + ROW_H - 2, 0x60000000);
        drawRow(g, y, i -> cols.get(i).title, i -> 0xAAAAAA, false);
        y += ROW_H + 2;

        int hovered = -1;
        int end = Math.min(recap.hits.size(), scroll + visibleRows);
        for (int k = scroll; k < end; k++) {
            Hit h = recap.hits.get(k);
            int bg = h.finalBlow ? 0x55AA6600 : ((k & 1) == 0 ? 0x30000000 : 0x18FFFFFF);
            g.fill(tableX - 2, y - 2, tableX + tableW + 2, y + ROW_H - 2, bg);
            if (mx >= tableX && mx <= tableX + tableW && my >= y - 2 && my < y + ROW_H - 2) {
                hovered = k;
                g.fill(tableX - 2, y - 2, tableX + tableW + 2, y + ROW_H - 2, 0x22FFFFFF);
            }
            drawRow(g, y, i -> RecapText.cell(cols.get(i), h, recap, hearts), i -> RecapText.color(cols.get(i), h), h.finalBlow);
            y += ROW_H;
        }
        if (recap.hits.size() > visibleRows) {
            g.drawString(font, (scroll + 1) + "-" + end + " of " + recap.hits.size() + " (scroll)", tableX, y + 1, 0x777777);
            y += ROW_H;
        }
        g.fill(tableX - 2, y, tableX + tableW + 2, y + 1, 0x80FFFFFF);
        y += 3;
        drawRow(g, y, i -> total(cols.get(i)), i -> 0xFFFFFF, false);

        String legend = "Armor = armor & toughness   Enchants = Protection-type   Other = Resistance, absorption, shield";
        g.drawCenteredString(font, legend, width / 2, height - 40, 0x777777);

        if (hovered >= 0) {
            Hit h = recap.hits.get(hovered);
            List<Component> lines = new ArrayList<>();
            for (String s : RecapText.details(h, recap, hearts)) lines.add(Component.literal(s));
            g.renderComponentTooltip(font, lines, mx, my);
        }
    }

    private interface IntStr { String get(int i); }
    private interface IntInt { int get(int i); }

    private void drawRow(GuiGraphics g, int y, IntStr text, IntInt color, boolean finalBlow) {
        int x = tableX + PAD / 2;
        for (int i = 0; i < cols.size(); i++) {
            String s = text.get(i);
            if (s == null) s = "";
            if (finalBlow && cols.get(i) == Col.SOURCE) s = "\u2620 " + s;
            if (font.width(s) > widths[i]) s = font.plainSubstrByWidth(s, widths[i] - font.width("...")) + "...";
            Col c = cols.get(i);
            boolean right = c != Col.SOURCE && c != Col.TYPE;
            int dx = right ? x + widths[i] - font.width(s) : x;
            if (finalBlow && c == Col.SOURCE && i == 0) s = "☠ " + s;
            g.drawString(font, s, dx, y, 0xFF000000 | color.get(i));
            x += widths[i] + PAD;
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, recap.hits.size() - visibleRows);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(sy)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientRecap.OPEN_KEY.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
