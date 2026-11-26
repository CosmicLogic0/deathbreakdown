package com.cosmiclogic.deathbreakdown.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//#if MC >= 26.1
import net.minecraft.client.gui.GuiGraphicsExtractor;
//#elif MC >= 1.20
import net.minecraft.client.gui.GuiGraphics;
//#else
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
//#endif

import java.util.ArrayList;
import java.util.List;

/**
 * The few drawing calls Death Breakdown's screens use, for whatever this Minecraft version draws screens with.
 * Colors are always full ARGB (newer versions hide alpha 0 text).
 */
final class Draw {
    //#if MC >= 26.1
    final GuiGraphicsExtractor g;
    Draw(GuiGraphicsExtractor g) { this.g = g; }
    //#elif MC >= 1.20
    final GuiGraphics g;
    Draw(GuiGraphics g) { this.g = g; }
    //#else
    final PoseStack pose;
    final Screen screen;
    Draw(PoseStack pose, Screen screen) { this.pose = pose; this.screen = screen; }
    //#endif

    void fill(int x0, int y0, int x1, int y1, int argb) {
        //#if MC >= 1.20
        g.fill(x0, y0, x1, y1, argb);
        //#else
        GuiComponent.fill(pose, x0, y0, x1, y1, argb);
        //#endif
    }

    void text(Font f, String s, int x, int y, int rgb) {
        //#if MC >= 26.1
        g.text(f, s, x, y, 0xFF000000 | rgb, true);
        //#elif MC >= 1.20
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

    /** Moves the origin to x, y and scales by s until pop(). */
    void push(float x, float y, float s) {
        //#if MC >= 1.21.6
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
        //#elif MC >= 1.20
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(s, s, 1);
        //#else
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(s, s, 1);
        //#endif
    }

    void pop() {
        //#if MC >= 1.21.6
        g.pose().popMatrix();
        //#elif MC >= 1.20
        g.pose().popPose();
        //#else
        pose.popPose();
        //#endif
    }

    /** Splits text into lines that fit {@code max} pixels (plain strings work on every version). */
    static List<String> wrap(Font font, String s, int max) {
        List<String> out = new ArrayList<>();
        String rest = s == null ? "" : s.trim();
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
}
