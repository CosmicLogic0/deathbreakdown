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
 * The recap for 1.12.2 (same layout as every other version): one row per hit, oldest at the top,
 * final blow highlighted at the bottom, totals underneath. Hover a row for every detail. Scroll for more rows.
 */
public class RecapScreen112 extends GuiScreen {
    private static final int ROW_H = 12, PAD = 8, TOP = 50;
    private static final String SKULL = "☠ ";

    private final GuiScreen parent;
    private final Recap recap;
    private final List<Col> cols;
    private final boolean hearts;
    private int[] widths;
    private int tableX, tableW, visibleRows, scroll;

    public RecapScreen112(GuiScreen parent, Recap recap) {
        this.parent = parent;
        this.recap = recap;
        RecapConfig c = DeathBreakdown112.config;
        this.cols = RecapText.columns(c);
        this.hearts = c.hearts;
    }

    @Override
    public void initGui() {
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
        tableX = Math.max(4, (width - tableW) / 2);
        int bottom = height - 64;
        visibleRows = Math.max(1, (bottom - (TOP + ROW_H + 2)) / ROW_H);
        scroll = Math.max(0, recap.hits.size() - visibleRows); // start at the bottom so the final blow is visible
        buttonList.add(new GuiButton(0, width / 2 - 50, height - 26, 100, 20, "Done"));
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
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, DeathBreakdown112.NAME, width / 2, 8, 0xFFAA00);
        drawCenteredString(fontRenderer, recap.deathMessage, width / 2, 20, 0xAAAAAA);
        List<String> summary = fontRenderer.listFormattedStringToWidth(RecapText.summary(recap, hearts), Math.max(200, width - 40));
        for (int i = 0; i < Math.min(2, summary.size()); i++) drawCenteredString(fontRenderer, summary.get(i), width / 2, 32 + i * 10, 0xFFFFFF);

        int y = TOP;
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

        drawCenteredString(fontRenderer, "Armor = armor & toughness   Enchants = Protection-type   Other = Resistance, absorption, shield",
                width / 2, height - 40, 0x777777);

        super.drawScreen(mx, my, pt);

        if (hovered >= 0) drawHoveringText(RecapText.details(recap.hits.get(hovered), recap, hearts), mx, my);
    }

    private void drawRow(int y, String[] text, int[] color, boolean finalBlow) {
        int x = tableX + PAD / 2;
        for (int i = 0; i < cols.size(); i++) {
            Col c = cols.get(i);
            String s = text[i] == null ? "" : text[i];
            if (finalBlow && c == Col.SOURCE) s = SKULL + s;
            if (fontRenderer.getStringWidth(s) > widths[i]) s = fontRenderer.trimStringToWidth(s, widths[i] - fontRenderer.getStringWidth("...")) + "...";
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
        if (b.id == 0) close();
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
