package com.cosmiclogic.deathbreakdown.legacy;

import com.cosmiclogic.deathbreakdown.core.Hit;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapConfig;
import com.cosmiclogic.deathbreakdown.core.RecapText;
import com.cosmiclogic.deathbreakdown.core.RecapText.Col;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The recap for 1.12.2 (same layout as every other version): one row per hit, oldest at the top, final blow
 * highlighted at the bottom, totals underneath. Hover a row for every detail; scroll for more rows; &lt; &gt; browse
 * your last few deaths; Copy puts it on the clipboard; Settings opens the options.
 */
public class RecapScreen112 extends GuiScreen {
    private static final int ROW_H = 12, PAD = 8;
    private static final int COPY = 1, SETTINGS = 2, DONE = 0, PREV = 3, NEXT = 4;
    private static final String SKULL = "☠ ";

    private final GuiScreen parent;
    private final List<Recap> history;
    private final int index;
    private final Recap recap;
    private final List<Col> cols;
    private final boolean hearts;
    private int[] widths;
    private int tableX, tableW, visibleRows, scroll, top, legendY;
    private List<String> summary, legend;
    private String tip;
    private GuiButton copyButton;
    private long copiedAt;

    public RecapScreen112(GuiScreen parent, List<Recap> history, int index) {
        this.parent = parent;
        this.history = history;
        this.index = Math.max(0, Math.min(history.size() - 1, index));
        this.recap = history.get(this.index);
        RecapConfig c = DeathBreakdown112.config;
        this.cols = RecapText.columns(c);
        this.hearts = c.hearts;
    }

    private List<String> wrap(String s, int max) {
        return fontRenderer.listFormattedStringToWidth(s == null ? "" : s, max);
    }

    @Override
    public void initGui() {
        ClientSelfTest112.sawRecapScreen = true;
        summary = wrap(RecapText.summary(recap, hearts), Math.max(200, width - 40));
        if (summary.size() > 2) summary = summary.subList(0, 2);
        tip = DeathBreakdown112.config.showTip ? RecapText.tip(recap) : "";
        legend = new ArrayList<String>();
        final int legendMax = Math.max(120, width - 20);
        for (String l : RecapText.legendLines(new RecapText.Width() {
            @Override public int of(String s) { return fontRenderer.getStringWidth(s); }
        }, legendMax)) legend.addAll(wrap(l, legendMax));
        int y0 = history.size() > 1 ? 42 : 32;
        top = y0 + summary.size() * 10 + (tip.isEmpty() ? 0 : 10) + 8;

        widths = new int[cols.size()];
        for (int i = 0; i < cols.size(); i++) {
            Col c = cols.get(i);
            int w = fontRenderer.getStringWidth(c.title);
            for (Hit h : recap.hits) w = Math.max(w, fontRenderer.getStringWidth(RecapText.cell(c, h, recap, hearts)));
            w = Math.max(w, fontRenderer.getStringWidth(total(c)));
            if (c == Col.SOURCE) w += fontRenderer.getStringWidth(SKULL);
            widths[i] = Math.min(w, c == Col.SOURCE ? 160 : 90);
        }
        tableW = PAD;
        for (int w : widths) tableW += w + PAD;
        int over = tableW - (width - 8);
        int src = cols.indexOf(Col.SOURCE);
        if (over > 0 && src >= 0) {
            int cut = Math.min(over, widths[src] - 40);
            widths[src] -= cut;
            tableW -= cut;
        }
        tableX = Math.max(4, (width - tableW) / 2);
        legendY = height - 32 - legend.size() * 10;
        int bottom = legendY - 6 - ROW_H;
        visibleRows = Math.max(1, (bottom - (top + ROW_H + 2)) / ROW_H - 1);
        scroll = Math.max(0, recap.hits.size() - visibleRows);

        int bw = 74, gap = 4, bx = width / 2 - (bw * 3 + gap * 2) / 2, by = height - 26;
        copyButton = new GuiButton(COPY, bx, by, bw, 20, "Copy");
        buttonList.add(copyButton);
        buttonList.add(new GuiButton(SETTINGS, bx + bw + gap, by, bw, 20, "Settings"));
        buttonList.add(new GuiButton(DONE, bx + (bw + gap) * 2, by, bw, 20, "Done"));
        if (history.size() > 1) {
            if (index > 0) buttonList.add(new GuiButton(PREV, 6, 4, 20, 20, "<"));
            if (index < history.size() - 1) buttonList.add(new GuiButton(NEXT, width - 26, 4, 20, 20, ">"));
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

    @Override
    public void updateScreen() {
        if (copiedAt != 0 && System.currentTimeMillis() - copiedAt > 2000) {
            copyButton.displayString = "Copy";
            copiedAt = 0;
        }
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, DeathBreakdown112.NAME, width / 2, 8, 0xFFAA00);
        if (history.size() > 1) {
            drawCenteredString(fontRenderer, "Death " + (history.size() - index) + " of " + history.size() + " - "
                    + RecapText.ago(recap.timeMs, System.currentTimeMillis()), width / 2, 20, 0x777777);
            drawCenteredString(fontRenderer, recap.deathMessage, width / 2, 30, 0xAAAAAA);
        } else {
            drawCenteredString(fontRenderer, recap.deathMessage, width / 2, 20, 0xAAAAAA);
        }
        int y0 = history.size() > 1 ? 42 : 32;
        for (int i = 0; i < summary.size(); i++) drawCenteredString(fontRenderer, summary.get(i), width / 2, y0 + i * 10, 0xFFFFFF);
        if (!tip.isEmpty()) drawCenteredString(fontRenderer, tip, width / 2, y0 + summary.size() * 10, 0xFFDD55);

        int y = top;
        drawRect(tableX - 2, y - 2, tableX + tableW + 2, y + ROW_H - 2, 0x60000000);
        String[] head = new String[cols.size()];
        int[] grey = new int[cols.size()];
        for (int i = 0; i < cols.size(); i++) { head[i] = cols.get(i).title; grey[i] = 0xAAAAAA; }
        drawRow(y, head, grey, false);
        y += ROW_H + 2;

        int hovered = -1;
        int end = Math.min(recap.hits.size(), scroll + visibleRows);
        for (int k = scroll; k < end; k++) {
            Hit h = recap.hits.get(k);
            int bg = h.finalBlow ? 0x55AA6600 : ((k & 1) == 0 ? 0x30000000 : 0x18FFFFFF);
            drawRect(tableX - 2, y - 2, tableX + tableW + 2, y + ROW_H - 2, bg);
            if (mx >= tableX && mx <= tableX + tableW && my >= y - 2 && my < y + ROW_H - 2) {
                hovered = k;
                drawRect(tableX - 2, y - 2, tableX + tableW + 2, y + ROW_H - 2, 0x22FFFFFF);
            }
            String[] cells = new String[cols.size()];
            int[] colors = new int[cols.size()];
            for (int i = 0; i < cols.size(); i++) {
                cells[i] = RecapText.cell(cols.get(i), h, recap, hearts);
                colors[i] = RecapText.color(cols.get(i), h);
            }
            drawRow(y, cells, colors, h.finalBlow);
            y += ROW_H;
        }
        if (recap.hits.size() > visibleRows) {
            drawString(fontRenderer, (scroll + 1) + "-" + end + " of " + recap.hits.size() + " (scroll)", tableX, y + 1, 0x777777);
            y += ROW_H;
        }
        drawRect(tableX - 2, y, tableX + tableW + 2, y + 1, 0x80FFFFFF);
        y += 3;
        String[] totals = new String[cols.size()];
        int[] white = new int[cols.size()];
        for (int i = 0; i < cols.size(); i++) { totals[i] = total(cols.get(i)); white[i] = 0xFFFFFF; }
        drawRow(y, totals, white, false);

        for (int i = 0; i < legend.size(); i++) drawCenteredString(fontRenderer, legend.get(i), width / 2, legendY + i * 10, 0x777777);

        super.drawScreen(mx, my, pt);

        if (hovered >= 0) drawHoveringText(RecapText.details(recap.hits.get(hovered), recap, hearts), mx, my);
    }

    private void drawRow(int y, String[] text, int[] color, boolean finalBlow) {
        int x = tableX + PAD / 2;
        for (int i = 0; i < cols.size(); i++) {
            Col c = cols.get(i);
            String s = text[i] == null ? "" : text[i];
            if (finalBlow && c == Col.SOURCE) s = SKULL + s;
            if (fontRenderer.getStringWidth(s) > widths[i]) s = fontRenderer.trimStringToWidth(s, Math.max(0, widths[i] - fontRenderer.getStringWidth("..."))) + "...";
            boolean right = c != Col.SOURCE && c != Col.TYPE;
            int dx = right ? x + widths[i] - fontRenderer.getStringWidth(s) : x;
            drawString(fontRenderer, s, dx, y, color[i]);
            x += widths[i] + PAD;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) {
            int max = Math.max(0, recap.hits.size() - visibleRows);
            scroll = Math.max(0, Math.min(max, scroll - Integer.signum(d)));
        }
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        switch (b.id) {
            case DONE: close(); break;
            case COPY:
                setClipboardString(RecapText.plainText(recap, hearts));
                copyButton.displayString = "Copied!";
                copiedAt = System.currentTimeMillis();
                break;
            case SETTINGS: mc.displayGuiScreen(new ConfigScreen112(this)); break;
            case PREV: mc.displayGuiScreen(new RecapScreen112(parent, history, index - 1)); break;
            case NEXT: mc.displayGuiScreen(new RecapScreen112(parent, history, index + 1)); break;
            default: break;
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE || (ClientRecap112.OPEN_KEY != null && key == ClientRecap112.OPEN_KEY.getKeyCode())) {
            close();
            return;
        }
        super.keyTyped(c, key);
    }

    private void close() {
        mc.displayGuiScreen(parent);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
