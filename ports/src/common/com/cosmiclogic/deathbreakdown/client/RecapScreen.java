package com.cosmiclogic.deathbreakdown.client;

import com.cosmiclogic.deathbreakdown.DeathBreakdown;
import com.cosmiclogic.deathbreakdown.Txt;
import com.cosmiclogic.deathbreakdown.core.Hit;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapConfig;
import com.cosmiclogic.deathbreakdown.core.RecapText;
import com.cosmiclogic.deathbreakdown.core.RecapText.Col;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//#if MC >= 26.1
import net.minecraft.client.gui.GuiGraphicsExtractor;
//#elif MC >= 1.20
import net.minecraft.client.gui.GuiGraphics;
//#else
import com.mojang.blaze3d.vertex.PoseStack;
//#endif
//#if MC >= 1.21.9
import net.minecraft.client.input.KeyEvent;
//#endif

import java.util.ArrayList;
import java.util.List;

/**
 * The recap: one row per hit, oldest at the top, final blow highlighted at the bottom, totals underneath.
 * Hover a row for every detail. Scroll if there are more rows than fit. &lt; and &gt; browse your last few deaths;
 * Copy puts the recap on the clipboard as text; Settings opens the options.
 */
public class RecapScreen extends Screen {
    private static final int ROW_H = 12, PAD = 8;
    private static final String SKULL = "☠ ";

    private final Screen parent;
    private final List<Recap> history;
    private final int index; // into history, newest = size - 1
    private final Recap recap;
    private final List<Col> cols;
    private final boolean hearts;
    private int[] widths;
    private int tableX, tableW, visibleRows, scroll, top, legendY;
    private List<String> summary, legend;
    private String tip;
    private Button copyButton;
    private long copiedAt;

    public RecapScreen(Screen parent, List<Recap> history, int index) {
        super(Txt.lit(DeathBreakdown.NAME));
        this.parent = parent;
        this.history = history;
        this.index = Math.max(0, Math.min(history.size() - 1, index));
        this.recap = history.get(this.index);
        RecapConfig c = DeathBreakdown.config;
        this.cols = RecapText.columns(c);
        this.hearts = c.hearts;
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

    private void add(Button b) {
        //#if MC >= 1.17
        addRenderableWidget(b);
        //#else
        addButton(b);
        //#endif
    }

    @Override
    protected void init() {
        ClientSelfTest.sawRecapScreen = true;
        summary = Draw.wrap(font, RecapText.summary(recap, hearts), Math.max(200, width - 40));
        if (summary.size() > 2) summary = summary.subList(0, 2);
        tip = DeathBreakdown.config.showTip ? RecapText.tip(recap) : "";
        legend = Draw.wrap(font, RecapText.LEGEND, Math.max(120, width - 20));
        int y0 = history.size() > 1 ? 42 : 32;
        top = y0 + summary.size() * 10 + (tip.isEmpty() ? 0 : 10) + 8;

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
        // Small window or big GUI scale: shrink the "Hit by" column (long names get "...") so the table fits.
        int over = tableW - (width - 8);
        int src = cols.indexOf(Col.SOURCE);
        if (over > 0 && src >= 0) {
            int cut = Math.min(over, widths[src] - 40);
            widths[src] -= cut;
            tableW -= cut;
        }
        tableX = Math.max(4, (width - tableW) / 2);
        legendY = height - 32 - legend.size() * 10;
        int bottom = legendY - 6 - ROW_H; // room for the totals row
        visibleRows = Math.max(1, (bottom - (top + ROW_H + 2)) / ROW_H - 1);
        scroll = Math.max(0, recap.hits.size() - visibleRows); // start at the bottom so the final blow is visible

        int bw = 74, gap = 4, bx = width / 2 - (bw * 3 + gap * 2) / 2, by = height - 26;
        copyButton = ClientRecap.button("Copy", bx, by, bw, 20, this::copy);
        add(copyButton);
        add(ClientRecap.button("Settings", bx + bw + gap, by, bw, 20,
                () -> ClientRecap.setScreen(minecraft, new ConfigScreen(this))));
        add(ClientRecap.button("Done", bx + (bw + gap) * 2, by, bw, 20, this::onClose));
        if (history.size() > 1) {
            if (index > 0) add(ClientRecap.button("<", 6, 4, 20, 20, () -> go(index - 1)));
            if (index < history.size() - 1) add(ClientRecap.button(">", width - 26, 4, 20, 20, () -> go(index + 1)));
        }
    }

    private void go(int i) {
        ClientRecap.setScreen(minecraft, new RecapScreen(parent, history, i));
    }

    private void copy() {
        ClientRecap.copyToClipboard(RecapText.plainText(recap, hearts));
        copyButton.setMessage(Txt.lit("Copied!"));
        copiedAt = System.currentTimeMillis();
    }

    @Override
    public void tick() {
        super.tick();
        if (copiedAt != 0 && System.currentTimeMillis() - copiedAt > 2000) {
            copyButton.setMessage(Txt.lit("Copy"));
            copiedAt = 0;
        }
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

    private void draw(Draw g, int mx, int my) {
        g.centered(font, title.getString(), width / 2, 8, 0xFFAA00);
        if (history.size() > 1) {
            String which = "Death " + (history.size() - index) + " of " + history.size() + " · "
                    + RecapText.ago(recap.timeMs, System.currentTimeMillis());
            g.centered(font, which, width / 2, 20, 0x777777);
            g.centered(font, recap.deathMessage, width / 2, 30, 0xAAAAAA);
        } else {
            g.centered(font, recap.deathMessage, width / 2, 20, 0xAAAAAA);
        }
        int y0 = history.size() > 1 ? 42 : 32;
        for (int i = 0; i < summary.size(); i++) g.centered(font, summary.get(i), width / 2, y0 + i * 10, 0xFFFFFF);
        if (!tip.isEmpty()) g.centered(font, tip, width / 2, y0 + summary.size() * 10, 0xFFDD55);

        int y = top;
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

        for (int i = 0; i < legend.size(); i++) g.centered(font, legend.get(i), width / 2, legendY + i * 10, 0x777777);

        if (hovered >= 0) {
            Hit h = recap.hits.get(hovered);
            List<Component> lines = new ArrayList<>();
            for (String s : RecapText.details(h, recap, hearts)) lines.add(Txt.lit(s));
            g.tooltip(font, lines, mx, my);
        }
    }

    private interface IntStr { String get(int i); }
    private interface IntInt { int get(int i); }

    private void drawRow(Draw g, int y, IntStr text, IntInt color, boolean finalBlow) {
        int x = tableX + PAD / 2;
        for (int i = 0; i < cols.size(); i++) {
            Col c = cols.get(i);
            String s = text.get(i);
            if (s == null) s = "";
            if (finalBlow && c == Col.SOURCE) s = SKULL + s;
            if (font.width(s) > widths[i]) s = font.plainSubstrByWidth(s, Math.max(0, widths[i] - font.width("..."))) + "...";
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
        ClientRecap.setScreen(minecraft, parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
