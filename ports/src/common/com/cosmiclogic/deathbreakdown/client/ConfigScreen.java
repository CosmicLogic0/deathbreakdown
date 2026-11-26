package com.cosmiclogic.deathbreakdown.client;

import com.cosmiclogic.deathbreakdown.DeathBreakdown;
import com.cosmiclogic.deathbreakdown.Txt;
import com.cosmiclogic.deathbreakdown.core.Hit;
import com.cosmiclogic.deathbreakdown.core.Option;
import com.cosmiclogic.deathbreakdown.core.Palette;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapConfig;
import com.cosmiclogic.deathbreakdown.core.RecapText;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
//#if MC >= 26.1
import net.minecraft.client.gui.GuiGraphicsExtractor;
//#elif MC >= 1.20
import net.minecraft.client.gui.GuiGraphics;
//#else
import com.mojang.blaze3d.vertex.PoseStack;
//#endif

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Death Breakdown's settings (Mods list → Config, or Settings in the recap screen). Pages along the top, the settings
 * of that page on the left, a live preview on the right that changes as you click. Save keeps them (and writes
 * config/deathbreakdown.properties), Cancel forgets them, Reset to defaults puts every setting back.
 */
public class ConfigScreen extends Screen {
    private static final int ROW = 22;
    /** Quick colors on the Colors page. */
    static final int[] SWATCHES = {0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xFF55FF, 0xFFFFFF,
            0xE69F00, 0x56B4E9, 0x009E73, 0xF0E442, 0x0072B2, 0xD55E00, 0xCC79A7, 0xAAAAAA};

    private final Screen parent;
    final RecapConfig c;
    String page = Option.GENERAL;
    private int scroll;
    private Palette.Slot selected = Palette.Slot.DAMAGE;
    private EditBox hexBox;
    private String status = "";
    private final List<Row> rows = new ArrayList<>();
    private int listX, listW, top, bottom, previewX, previewW;

    /** An option row on screen (for the help line and the self-test). */
    static final class Row {
        final Option option;
        final Button button;
        final int x, y, w;
        Row(Option o, Button b, int x, int y, int w) { this.option = o; this.button = b; this.x = x; this.y = y; this.w = w; }
    }

    public ConfigScreen(Screen parent) {
        super(Txt.lit(DeathBreakdown.NAME + " settings"));
        this.parent = parent;
        this.c = DeathBreakdown.config.copy();
        ClientSelfTest.sawConfigScreen = true;
    }

    private <T extends net.minecraft.client.gui.components.AbstractWidget> T add(T w) {
        //#if MC >= 1.17
        addRenderableWidget(w);
        //#else
        addButton(w);
        //#endif
        return w;
    }

    private void rebuild() {
        //#if MC >= 1.17
        rebuildWidgets();
        //#else
        init(minecraft, width, height);
        //#endif
    }

    List<Row> rows() { return rows; }

    void showPage(String p) {
        page = p;
        scroll = 0;
        status = "";
        rebuild();
    }

    @Override
    protected void init() {
        rows.clear();
        hexBox = null;
        // Page tabs
        String[] short_ = {"General", "Recap", "Colors", "Marker", "HUD", "Sound"};
        int tw = Math.min(70, (width - 20 - 5 * 3) / 6), tx = width / 2 - (tw * 6 + 5 * 3) / 2;
        for (int i = 0; i < Option.PAGES.length; i++) {
            String p = Option.PAGES[i];
            Button b = add(ClientRecap.button(p.equals(page) ? "[" + short_[i] + "]" : short_[i], tx + i * (tw + 3), 18, tw, 20, () -> showPage(p)));
            b.active = !p.equals(page);
        }
        top = 44;
        bottom = height - 58;
        boolean preview = width >= 400;
        listW = preview ? Math.min(230, (int) (width * 0.5)) : Math.min(300, width - 16);
        listX = preview ? 8 : width / 2 - listW / 2;
        previewX = listX + listW + 12;
        previewW = width - previewX - 8;

        List<Option> opts = Option.page(page);
        int visible = Math.max(1, (bottom - top) / ROW);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, opts.size() - visible)));
        for (int i = scroll; i < Math.min(opts.size(), scroll + visible); i++) {
            Option o = opts.get(i);
            int y = top + (i - scroll) * ROW;
            boolean number = o.kind == Option.Kind.INT || o.kind == Option.Kind.DECIMAL;
            if (number) {
                add(ClientRecap.button("-", listX, y, 20, 20, () -> change(o, o.next(c, true))));
                Button mid = add(ClientRecap.button(o.display(c), listX + 22, y, listW - 44, 20, () -> change(o, o.next(c, false))));
                add(ClientRecap.button("+", listX + listW - 20, y, 20, 20, () -> change(o, o.next(c, false))));
                rows.add(new Row(o, mid, listX, y, listW));
            } else if (o.kind == Option.Kind.COLOR) {
                Button b = add(ClientRecap.button((o.slot == selected ? "> " : "") + o.display(c), listX, y, listW - 14, 20, () -> {
                    selected = o.slot;
                    rebuild();
                }));
                rows.add(new Row(o, b, listX, y, listW));
            } else {
                Button b = add(ClientRecap.button(o.display(c), listX, y, listW, 20, () -> change(o, o.next(c, false))));
                rows.add(new Row(o, b, listX, y, listW));
            }
        }

        if (page.equals(Option.COLORS) && preview) {
            int ey = top + 96;
            hexBox = add(new EditBox(font, previewX, ey + 12, 70, 18, Txt.lit("Hex color")));
            hexBox.setMaxLength(9);
            hexBox.setValue(Palette.hex(c.palette().get(selected)));
            hexBox.setResponder(this::typedHex);
            add(ClientRecap.button("Use preset", previewX + 76, ey + 11, Math.max(60, Math.min(90, previewW - 76)), 20, () -> {
                Option.get(selected.key()).set(c, "");
                rebuild();
            }));
            int sw = 14, per = Math.max(4, Math.min(8, previewW / (sw + 3)));
            for (int i = 0; i < SWATCHES.length; i++) {
                int col = SWATCHES[i];
                int sx = previewX + (i % per) * (sw + 3), sy = ey + 38 + (i / per) * (sw + 3);
                add(ClientRecap.button("", sx, sy, sw, sw, () -> {
                    Option.get(selected.key()).set(c, Palette.hex(col));
                    rebuild();
                }));
            }
        }
        if (page.equals(Option.SOUND)) {
            add(ClientRecap.button("Play sound", preview ? previewX : listX, preview ? top + 90 : bottom - 22, 100, 20, this::testSound));
        }

        int by = height - 26, bw = Math.min(120, (width - 24) / 3);
        add(ClientRecap.button("Reset to defaults", width / 2 - bw * 3 / 2 - 4, by, bw, 20, this::resetAll));
        add(ClientRecap.button("Cancel", width / 2 - bw / 2, by, bw, 20, this::onClose));
        add(ClientRecap.button("Save", width / 2 + bw / 2 + 4, by, bw, 20, this::save));
    }

    void change(Option o, String value) {
        String problem = o.set(c, value);
        status = problem == null ? "" : problem;
        rebuild();
    }

    private void typedHex(String s) {
        Integer v = Palette.parseHex(s);
        if (v == null) { status = "Type a color like #FF8800"; return; }
        Option.get(selected.key()).set(c, Palette.hex(v));
        status = "";
        for (Row r : rows) if (r.option.slot == selected) r.button.setMessage(Txt.lit("> " + r.option.display(c)));
    }

    void resetAll() {
        c.resetAll();
        status = "Every setting is back to its default. Save to keep it.";
        rebuild();
    }

    void save() {
        DeathBreakdown.applyConfig(c.copy());
        onClose();
    }

    private void testSound() {
        RecapConfig saved = DeathBreakdown.config;
        RecapConfig t = c.copy();
        t.soundOnOpen = true;
        DeathBreakdown.config = t;
        try {
            ClientRecap.playOpenSound();
        } finally {
            DeathBreakdown.config = saved;
        }
    }

    //#if MC >= 1.20.2
    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return scrollList(mx, sy);
    }
    //#else
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        return scrollList(mx, delta);
    }
    //#endif

    private boolean scrollList(double mx, double amount) {
        if (mx > listX + listW) return false;
        int before = scroll;
        scroll = Math.max(0, scroll - (int) Math.signum(amount) * 2);
        if (scroll != before) rebuild();
        return true;
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
        g.centered(font, title.getString(), width / 2, 6, 0xFFAA00);
        Palette pal = c.palette();
        for (Row r : rows) {
            if (r.option.kind == Option.Kind.COLOR) g.fill(r.x + r.w - 11, r.y + 4, r.x + r.w, r.y + 16, 0xFF000000 | pal.get(r.option.slot));
        }
        if (page.equals(Option.COLORS) && hexBox != null) {
            int ey = top + 96;
            g.text(font, "Selected: " + selected.label, previewX, ey, 0xFFFFFF);
            int sw = 14, per = Math.max(4, Math.min(8, previewW / (sw + 3)));
            for (int i = 0; i < SWATCHES.length; i++) {
                int sx = previewX + (i % per) * (sw + 3), sy = ey + 38 + (i / per) * (sw + 3);
                g.fill(sx + 2, sy + 2, sx + sw - 2, sy + sw - 2, 0xFF000000 | SWATCHES[i]);
            }
        }
        if (width >= 400) drawPreview(g, pal);
        int total = Option.page(page).size(), visible = Math.max(1, (bottom - top) / ROW);
        if (total > visible) g.text(font, "Scroll for more (" + (scroll + 1) + "-" + Math.min(total, scroll + visible) + " of " + total + ")",
                listX, bottom + 2, 0x888888);

        String help = status;
        if (help.isEmpty()) {
            for (Row r : rows) if (mx >= r.x && mx < r.x + r.w && my >= r.y && my < r.y + 20) { help = r.option.help; break; }
        }
        if (help.isEmpty()) help = "Point at a setting to see what it does. Saved to config/deathbreakdown.properties.";
        List<String> lines = Draw.wrap(font, help, Math.max(160, width - 40));
        for (int i = 0; i < Math.min(2, lines.size()); i++) g.centered(font, lines.get(i), width / 2, height - 50 + i * 10, 0xDDDDDD);
    }

    /** A small sample with the current settings, so every click shows what it does. */
    private void drawPreview(Draw g, Palette pal) {
        int x = previewX, y = top, w = previewW;
        g.text(font, "Preview", x, y - 10, 0x888888);
        if (page.equals(Option.HUD)) {
            int h = Math.max(60, Math.min(120, bottom - top - 10));
            g.fill(x, y, x + w, y + h, 0xFF3A5A2A);
            g.fill(x, y, x + w, y + h / 2, 0xFF7FA8D8);
            List<String> lines = ClientMarker.hudText(SAMPLE_SPOT, c, 12, "↗");
            if (!c.hudEnabled) {
                g.centered(font, "Death HUD is off", x + w / 2, y + h / 2 - 4, 0xFFFFFF);
            } else if (!lines.isEmpty()) {
                g.push(x, y, 1F);
                ClientHud.draw(g, font, lines, c, w, h);
                g.pop();
            }
            return;
        }
        if (page.equals(Option.MARKER)) {
            int h = Math.max(60, Math.min(120, bottom - top - 10));
            g.fill(x, y, x + w, y + h, 0xFF7FA8D8);
            g.fill(x, y + h * 3 / 4, x + w, y + h, 0xFF3A5A2A);
            int cx = x + w / 2, gy = y + h * 3 / 4;
            if (c.markerStyle.equals("off")) {
                g.centered(font, "Death marker is off", cx, y + h / 2 - 4, 0xFFFFFF);
                return;
            }
            boolean dust = c.markerParticle.equals("dust");
            int col = dust ? pal.get(Palette.Slot.MARKER) : particleColor(c.markerParticle);
            int dot = dust ? Math.max(1, (int) Math.round(c.markerSize * 1.5)) : 2;
            int px = Math.max(2, (int) Math.round(c.markerSpacing * 8));
            if (c.markerStyle.equals("beam")) {
                for (int yy = gy; yy > y + 2; yy -= px) g.fill(cx - dot, yy - dot, cx + dot, yy + dot, 0xFF000000 | col);
            } else {
                int n = Math.max(6, (int) Math.ceil(2 * Math.PI * 0.8 / Math.max(0.25, c.markerSpacing)));
                for (int i = 0; i < n; i++) {
                    double a = 2 * Math.PI * i / n;
                    int ox = cx + (int) Math.round(Math.cos(a) * 16), oy = gy + (int) Math.round(Math.sin(a) * 5);
                    g.fill(ox - dot, oy - dot, ox + dot, oy + dot, 0xFF000000 | col);
                }
                for (int yy = gy - 4; yy > gy - 20; yy -= px) g.fill(cx - dot, yy - dot, cx + dot, yy + dot, 0xFF000000 | col);
            }
            g.text(font, c.markerParticle.replace('_', ' ') + (dust ? "" : " (color and size: dust only)"), x + 2, y + 2, 0xFFFFFF);
            return;
        }
        // A tiny recap
        int h = page.equals(Option.COLORS) ? 86 : Math.max(60, Math.min(140, bottom - top - 10));
        g.fill(x, y, x + w, y + h, 0xC0101010);
        int ty = y + 3;
        g.centered(font, DeathBreakdown.NAME, x + w / 2, ty, pal.get(Palette.Slot.TITLE));
        ty += 10;
        if (c.showDeathMessage && !c.compact) { g.centered(font, cut(SAMPLE.deathMessage, w - 4), x + w / 2, ty, pal.get(Palette.Slot.INFO)); ty += 10; }
        if (c.showSummary) { g.centered(font, cut(RecapText.summary(SAMPLE, c.hearts), w - 4), x + w / 2, ty, pal.get(Palette.Slot.SUMMARY)); ty += 10; }
        if (c.showTip && !c.compact) { g.centered(font, cut(RecapText.tip(SAMPLE), w - 4), x + w / 2, ty, pal.get(Palette.Slot.TIP)); ty += 10; }
        List<RecapText.Col> cols = new ArrayList<>(RecapText.columns(c));
        int rh = c.compact ? 10 : 12;
        int[] cw = new int[cols.size()];
        int sum = 0;
        for (int i = 0; i < cols.size(); i++) {
            int m = font.width(cols.get(i).title);
            for (Hit hit : SAMPLE.hits) m = Math.max(m, font.width(RecapText.cell(cols.get(i), hit, SAMPLE, c.hearts)));
            cw[i] = m;
            sum += m + 6;
        }
        while (sum > w - 6 && cols.size() > 1) { // drop columns from the right until it fits
            sum -= cw[cols.size() - 1] + 6;
            cols.remove(cols.size() - 1);
        }
        if (c.panelOpacity > 0) g.fill(x + 1, ty - 2, x + w - 1, ty + rh * 4 + 2, Math.round(c.panelOpacity * 2.55F) << 24);
        int cx = x + 4;
        for (int i = 0; i < cols.size(); i++) { g.text(font, cols.get(i).title, cx, ty, pal.get(Palette.Slot.HEADER)); cx += cw[i] + 6; }
        ty += rh;
        for (int k = 0; k < SAMPLE.hits.size(); k++) {
            Hit hit = SAMPLE.hits.get(k);
            int bg = hit.finalBlow ? 0x55000000 | pal.get(Palette.Slot.FINAL_ROW) : !c.stripes ? 0 : ((k & 1) == 0 ? 0x30000000 : 0x18FFFFFF);
            if (bg != 0) g.fill(x + 2, ty - 2, x + w - 2, ty + rh - 2, bg);
            cx = x + 4;
            for (int i = 0; i < cols.size(); i++) {
                g.text(font, RecapText.cell(cols.get(i), hit, SAMPLE, c.hearts), cx, ty, RecapText.color(cols.get(i), hit, pal));
                cx += cw[i] + 6;
            }
            ty += rh;
        }
    }

    private String cut(String s, int max) {
        return font.width(s) <= max ? s : font.plainSubstrByWidth(s, Math.max(0, max - font.width("..."))) + "...";
    }

    private static int particleColor(String p) {
        switch (p) {
            case "flame": return 0xFFA030;
            case "end_rod": return 0xFFFFE0;
            case "happy_villager": return 0x60E060;
            case "heart": return 0xFF4060;
            case "portal": return 0xA040FF;
            default: return 0xFFFFFF;
        }
    }

    static final Recap SAMPLE = new Recap("You", "You were slain by Zombie", 4000, 10_000, Arrays.asList(
            new Hit(1000, "Fall", "", Hit.Kind.ENVIRONMENT, "fall", 3, 0, 0, 0, 0, 0, 3, 17, false),
            new Hit(2500, "Skeleton", "Bow", Hit.Kind.PROJECTILE, "arrow", 4, 1.5F, 0.4F, 0, 0, 0, 2.1F, 14.9F, false),
            new Hit(4000, "Zombie", "Iron Sword", Hit.Kind.MOB, "mob", 9, 3.2F, 1.1F, 0, 0, 0, 14.9F, 0, true)));

    static final ClientMarker.Spot SAMPLE_SPOT = new ClientMarker.Spot(120, 64, -40, "overworld", System.currentTimeMillis() - 95_000, "Zombie (Iron Sword)");

    @Override
    public void onClose() {
        ClientRecap.setScreen(minecraft, parent);
    }
}
