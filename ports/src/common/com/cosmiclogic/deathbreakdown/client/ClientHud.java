package com.cosmiclogic.deathbreakdown.client;

import com.cosmiclogic.deathbreakdown.DeathBreakdown;
import com.cosmiclogic.deathbreakdown.core.Palette;
import com.cosmiclogic.deathbreakdown.core.RecapConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//#if MC >= 26.1
import net.minecraft.client.gui.GuiGraphicsExtractor;
//#elif MC >= 1.20
import net.minecraft.client.gui.GuiGraphics;
//#else
import com.mojang.blaze3d.vertex.PoseStack;
//#endif

import java.util.List;

/** The death HUD: a small box in a corner with what killed you, how far away the spot is and how long ago. Off by default. */
public final class ClientHud {
    private ClientHud() {}

    //#if MC >= 26.1
    public static void render(GuiGraphicsExtractor g) { render(new Draw(g)); }
    //#elif MC >= 1.20
    public static void render(GuiGraphics g) { render(new Draw(g)); }
    //#else
    public static void render(PoseStack pose) { render(new Draw(pose, null)); }
    //#endif

    private static void render(Draw g) {
        Minecraft mc = Minecraft.getInstance();
        RecapConfig c = DeathBreakdown.config;
        if (!c.hudEnabled || mc.player == null || ClientRecap.screen(mc) != null || mc.options.hideGui) return;
        List<String> lines = ClientMarker.hudLines;
        if (lines.isEmpty()) return;
        draw(g, mc.font, lines, c, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        ClientMarker.hudFrames++;
    }

    /** Also used by the settings screen's live preview. */
    static void draw(Draw g, Font font, List<String> lines, RecapConfig c, int screenW, int screenH) {
        Palette p = c.palette();
        int w = 0;
        for (String s : lines) w = Math.max(w, font.width(s));
        int pad = 3, lh = 10;
        int boxW = w + pad * 2, boxH = lines.size() * lh + pad * 2 - 2;
        float scale = Math.max(0.5F, Math.min(3F, c.hudScale / 100F));
        int sw = Math.round(boxW * scale), sh = Math.round(boxH * scale);
        int x = c.hudCorner.endsWith("right") ? screenW - sw - c.hudX : c.hudX;
        int y = c.hudCorner.startsWith("bottom") ? screenH - sh - c.hudY : c.hudY;
        x = Math.max(0, Math.min(screenW - sw, x));
        y = Math.max(0, Math.min(screenH - sh, y));
        g.push(x, y, scale);
        if (c.hudBackground && c.hudOpacity > 0) g.fill(0, 0, boxW, boxH, Math.round(c.hudOpacity * 2.55F) << 24);
        for (int i = 0; i < lines.size(); i++) {
            g.text(font, lines.get(i), pad, pad + i * lh, i == 0 ? p.get(Palette.Slot.HUD_ACCENT) : p.get(Palette.Slot.HUD_TEXT));
        }
        g.pop();
    }
}
