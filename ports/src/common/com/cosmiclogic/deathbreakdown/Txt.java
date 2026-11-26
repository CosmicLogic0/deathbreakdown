package com.cosmiclogic.deathbreakdown;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
//#if MC < 1.19
import net.minecraft.Util;
import net.minecraft.network.chat.TextComponent;
//#endif

/** Chat text helpers that hide the differences between Minecraft versions. */
public final class Txt {
    private Txt() {}

    public static MutableComponent lit(String s) {
        //#if MC >= 1.19
        return Component.literal(s);
        //#else
        return new TextComponent(s);
        //#endif
    }

    /** A colored piece of text that shows {@code hover} when hovered and runs {@code command} when clicked. */
    public static MutableComponent hoverClick(String text, int rgb, Component hover, String command) {
        Style style = Style.EMPTY.withColor(TextColor.fromRgb(rgb))
                //#if MC >= 1.21.5
                .withHoverEvent(new HoverEvent.ShowText(hover))
                .withClickEvent(new ClickEvent.RunCommand(command));
                //#else
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
                //#endif
        return lit(text).withStyle(style);
    }

    /** A colored piece of text that shows {@code hover} and copies {@code copy} to the clipboard when clicked. */
    public static MutableComponent copyClick(String text, int rgb, Component hover, String copy) {
        Style style = Style.EMPTY.withColor(TextColor.fromRgb(rgb))
                //#if MC >= 1.21.5
                .withHoverEvent(new HoverEvent.ShowText(hover))
                .withClickEvent(new ClickEvent.CopyToClipboard(copy));
                //#else
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, copy));
                //#endif
        return lit(text).withStyle(style);
    }

    /**
     * Text in a palette color. If the player kept that color at its 1.1.0 default, the old chat color is used, so
     * the default look doesn't change at all; any other color is drawn exactly (hex colors work in chat on 1.16+).
     */
    public static MutableComponent col(String s, com.cosmiclogic.deathbreakdown.core.Palette p,
                                       com.cosmiclogic.deathbreakdown.core.Palette.Slot slot, net.minecraft.ChatFormatting whenDefault) {
        int rgb = p.get(slot);
        if (rgb == slot.def) return lit(s).withStyle(whenDefault);
        return lit(s).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    public static void send(ServerPlayer p, Component c) {
        //#if MC >= 1.19
        p.sendSystemMessage(c);
        //#else
        p.sendMessage(c, Util.NIL_UUID);
        //#endif
    }
}
