package com.cosmiclogic.deathbreakdown.client;

import com.cosmiclogic.deathbreakdown.DeathBreakdown;
import com.cosmiclogic.deathbreakdown.Txt;
import com.cosmiclogic.deathbreakdown.core.Hit;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapConfig;
import com.cosmiclogic.deathbreakdown.core.RecapText;
import com.cosmiclogic.deathbreakdown.core.RecapText.Col;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//#if MC >= 1.20
import net.minecraft.client.gui.GuiGraphics;
//#else
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
//#endif
//#if MC >= 1.21.9
import net.minecraft.client.input.KeyEvent;
//#endif

import java.util.ArrayList;
import java.util.List;

/**
 * The recap: one row per hit, oldest at the top, final blow highlighted at the bottom, totals underneath.
 * Hover a row for every detail. Scroll if there are more rows than fit.
 */
public class RecapScreen extends Screen {
    private static final int ROW_H = 12, PAD = 8, TOP = 50;
    private static final String SKULL = "☠ ";

    private final Screen parent;
    private final Recap recap;
    private final List<Col> cols;
    private final boolean hearts;
    private int[] widths;
    private int tableX, tableW, visibleRows, scroll;

    public RecapScreen(Screen parent, Recap recap) {
        super(Txt.lit(DeathBreakdown.NAME));
        this.parent = parent;
        this.recap = recap;
        RecapConfig c = DeathBreakdown.config;
        this.cols = RecapText.columns(c);
        this.hearts = c.hearts;
    }

    // ---- drawing, for whatever this Minecraft version draws screens with ----

    /** The few drawing calls this screen uses. Colors are always full ARGB (newer versions hide alpha 0 text). */
    private static final class G {
        //#if MC >= 1.20
        final GuiGraphics g;
        G(GuiGraphics g) { this.g = g; }
        //#else
        final PoseStack pose;
        final Screen screen;
        G(PoseStack pose, Screen screen) { this.pose = pose; this.screen = screen; }
        //#endif

        void fill(int x0, int y0, int x1, int y1, int argb) {
            //#if MC >= 1.20
            g.fill(x0, y0, x1, y1, argb);
            //#else
            GuiComponent.fill(pose, x0, y0, x1, y1, argb);
            //#endif
        }

        void text(Font f, String s, int x, int y, int rgb) {
            //#if MC >= 1.20
            g.drawString(f, s, x, y, 0xFF000000 | rgb);
            //#else
            f.drawShadow(pose, s, x, y, 0xFF000000 | rgb);
            //#endif
        }

        void centered(Font f, String s, int cx, int y, int rgb) {
            text(f, s, cx - f.width(s) / 2, y, rgb);
        }

        void tooltip(Font f, List<Component> lines, int x, int y) {
            //#if MC >= 1.21.6
            g.setComponentTooltipForNextFrame(f, lines, x, y);
            //#elif MC >= 1.20
            g.renderComponentTooltip(f, lines, x, y);
            //#else
            screen.renderComponentTooltip(pose, lines, x, y);
            //#endif
        }
    }

    //#if MC >= 1.20
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        //#if MC < 1.20.2
        renderBackground(g);
        //#endif
        super.render(g, mx, my, pt);
        draw(new G(g), mx, my);
    }
    //#else
    @Override
    public void render(PoseStack pose, int mx, int my, float pt) {
        renderBackground(pose);
        super.render(pose, mx, my, pt);
        draw(new G(pose, this), mx, my);
    }
    //#endif

    @Override
    protected void init() {
        widths = new int[cols.size()];
        for (int i = 0; i < cols.size(); i++) {
            Col c = cols.get(i);
            int w = font.width(c.title);
            for (Hit h : recap.hits) w = Math.max(w, font.width(RecapText.cell(c, h, recap, hearts)));
            w = Math.max(w, font.width(total(c)));
            if (c == Col.SOURCE) w += font.width(SKULL);
            widths[i] = Math.min(w, c == Col.SOURCE ? 160 : 90);
        }
        tableW = PAD;
        for (int w : widths) tableW += w + PAD;
        tableX = Math.max(4, (width - tableW) / 2);
        int bottom = height - 64;
        visibleRows = Math.max(1, (bottom - (TOP + ROW_H + 2)) / ROW_H);
        scroll = Math.max(0, recap.hits.size() - visibleRows); // start at the bottom so the final blow is visible
        //#if MC >= 1.17
        addRenderableWidget(ClientRecap.button("Done", width / 2 - 50, height - 26, 100, 20, this::onClose));
        //#else
        addButton(ClientRecap.button("Done", width / 2 - 50, height - 26, 100, 20, this::onClose));
        //#endif
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

    /** Splits text into lines that fit {@code max} pixels (plain strings work on every version). */
    private List<String> wrap(String s, int max) {
        List<String> out = new ArrayList<>();
        String rest = s;
        while (!rest.isEmpty()) {
            String part = font.plainSubstrByWidth(rest, max);
            if (part.length() < rest.length()) {
                int sp = part.lastIndexOf(' ');
                if (sp > 0) part = part.substring(0, sp);
            }
            if (part.isEmpty()) part = rest.substring(0, 1);
            out.add(part);
            rest = rest.substring(part.length()).trim();
        }
        return out;
    }

    private void draw(G g, int mx, int my) {
        g.centered(font, title.getString(), width / 2, 8, 0xFFAA00);
        g.centered(font, recap.deathMessage, width / 2, 20, 0xAAAAAA);
        List<String> summary = wrap(RecapText.summary(recap, hearts), Math.max(200, width - 40));
        for (int i = 0; i < Math.min(2, summary.size()); i++) g.centered(font, summary.get(i), width / 2, 32 + i * 10, 0xFFFFFF);

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
            g.text(font, (scroll + 1) + "-" + end + " of " + recap.hits.size() + " (scroll)", tableX, y + 1, 0x777777);
            y += ROW_H;
        }
        g.fill(tableX - 2, y, tableX + tableW + 2, y + 1, 0x80FFFFFF);
        y += 3;
        drawRow(g, y, i -> total(cols.get(i)), i -> 0xFFFFFF, false);

        g.centered(font, "Armor = armor & toughness   Enchants = Protection-type   Other = Resistance, absorption, shield",
                width / 2, height - 40, 0x777777);

        if (hovered >= 0) {
            Hit h = recap.hits.get(hovered);
            List<Component> lines = new ArrayList<>();
            for (String s : RecapText.details(h, recap, hearts)) lines.add(Txt.lit(s));
            g.tooltip(font, lines, mx, my);
        }
    }

    private interface IntStr { String get(int i); }
    private interface IntInt { int get(int i); }

    private void drawRow(G g, int y, IntStr text, IntInt color, boolean finalBlow) {
        int x = tableX + PAD / 2;
        for (int i = 0; i < cols.size(); i++) {
            Col c = cols.get(i);
            String s = text.get(i);
            if (s == null) s = "";
            if (finalBlow && c == Col.SOURCE) s = SKULL + s;
            if (font.width(s) > widths[i]) s = font.plainSubstrByWidth(s, widths[i] - font.width("...")) + "...";
            boolean right = c != Col.SOURCE && c != Col.TYPE;
            int dx = right ? x + widths[i] - font.width(s) : x;
            g.text(font, s, dx, y, color.get(i));
            x += widths[i] + PAD;
        }
    }

    //#if MC >= 1.20.2
    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scrollBy(sy);
        return true;
    }
    //#else
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scrollBy(delta);
        return true;
    }
    //#endif

    private void scrollBy(double amount) {
        int max = Math.max(0, recap.hits.size() - visibleRows);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(amount)));
    }

    //#if MC >= 1.21.9
    @Override
    public boolean keyPressed(KeyEvent e) {
        if (ClientRecap.OPEN_KEY.matches(e)) { onClose(); return true; }
        return super.keyPressed(e);
    }
    //#else
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientRecap.OPEN_KEY.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
    //#endif

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
