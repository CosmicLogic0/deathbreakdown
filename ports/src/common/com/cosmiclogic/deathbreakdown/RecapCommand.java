package com.cosmiclogic.deathbreakdown;

import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapText;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * /deathbreakdown (alias /db), for everyone:
 *   (nothing)       your last recap again        history          your last few deaths, click one to open it
 *   show <n>        open one from the history    copy             copy your last recap as text
 *   help            what all this does
 * For ops: player <name> (someone else's last recap, level 2), reload (level 2), selftest (CI, level 4).
 */
public final class RecapCommand {
    private RecapCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(build("deathbreakdown"));
        d.register(build("db")); // a full copy: brigadier redirects don't keep the bare "/db" working
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name)
                .executes(c -> show(c.getSource(), 1))
                .then(Commands.literal("help").executes(c -> help(c.getSource())))
                .then(Commands.literal("history").executes(c -> history(c.getSource())))
                .then(Commands.literal("show").then(Commands.argument("number", IntegerArgumentType.integer(1, 20))
                        .executes(c -> show(c.getSource(), IntegerArgumentType.getInteger(c, "number")))))
                .then(Commands.literal("copy").executes(c -> copy(c.getSource())))
                .then(Commands.literal("player").requires(s -> op(s, 2)).then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> other(c.getSource(), EntityArgument.getPlayer(c, "target")))))
                .then(Commands.literal("reload").requires(s -> op(s, 2)).executes(c -> reload(c.getSource())))
                .then(Commands.literal("selftest").requires(s -> op(s, 4)).executes(c -> SelfTest.run(c.getSource())));
    }

    static boolean op(CommandSourceStack s, int level) {
        //#if MC >= 1.21.11
        return s.permissions().hasPermission(new net.minecraft.server.permissions.Permission.HasCommandLevel(
                net.minecraft.server.permissions.PermissionLevel.byId(level)));
        //#else
        return s.hasPermission(level);
        //#endif
    }

    static void ok(CommandSourceStack s, String text) {
        send(s, Txt.lit(text));
    }

    static void send(CommandSourceStack s, Component c) {
        //#if MC >= 1.20
        s.sendSuccess(() -> c, false);
        //#else
        s.sendSuccess(c, false);
        //#endif
    }

    private static ServerPlayer player(CommandSourceStack s) {
        Entity e = s.getEntity();
        if (e instanceof ServerPlayer) return (ServerPlayer) e;
        s.sendFailure(Txt.lit("Only players have a death recap. Try /deathbreakdown player <name>."));
        return null;
    }

    /** n = 1 is the newest. */
    private static int show(CommandSourceStack s, int n) {
        ServerPlayer p = player(s);
        if (p == null) return 0;
        List<Recap> h = DeathBreakdown.HITS.history(p.getUUID());
        if (h.isEmpty()) {
            s.sendFailure(Txt.lit("No death recap yet. It appears here after you die (see /deathbreakdown help)."));
            return 0;
        }
        if (n > h.size()) {
            s.sendFailure(Txt.lit("You only have " + h.size() + (h.size() == 1 ? " death" : " deaths") + " saved. See /deathbreakdown history."));
            return 0;
        }
        ServerTracker.send(p, h.get(h.size() - n), false);
        return 1;
    }

    private static int history(CommandSourceStack s) {
        ServerPlayer p = player(s);
        if (p == null) return 0;
        List<Recap> h = DeathBreakdown.HITS.history(p.getUUID());
        if (h.isEmpty()) {
            s.sendFailure(Txt.lit("No deaths saved yet."));
            return 0;
        }
        long now = System.currentTimeMillis();
        send(s, Txt.lit("Your last " + h.size() + (h.size() == 1 ? " death" : " deaths") + " (click to open):").withStyle(ChatFormatting.GOLD));
        for (int i = 0; i < h.size(); i++) {
            Recap r = h.get(h.size() - 1 - i);
            String line = (i + 1) + ". " + RecapText.ago(r.timeMs, now) + ": " + RecapText.summary(r, DeathBreakdown.config.hearts);
            send(s, Txt.hoverClick(line, RecapText.WHITE, Txt.lit("Open this recap"), "/deathbreakdown show " + (i + 1)));
        }
        return h.size();
    }

    private static int copy(CommandSourceStack s) {
        ServerPlayer p = player(s);
        if (p == null) return 0;
        Recap r = DeathBreakdown.HITS.last(p.getUUID());
        if (r == null) {
            s.sendFailure(Txt.lit("No death recap yet."));
            return 0;
        }
        send(s, Txt.copyClick("[Click here to copy your last recap]", RecapText.BLUE,
                Txt.lit("Copies it as text, ready to paste into Discord or a bug report"), RecapText.plainText(r, DeathBreakdown.config.hearts)));
        return 1;
    }

    private static int other(CommandSourceStack s, ServerPlayer target) {
        Recap r = DeathBreakdown.HITS.last(target.getUUID());
        if (r == null) {
            s.sendFailure(Txt.lit(target.getName().getString() + " has no death recap yet."));
            return 0;
        }
        Entity e = s.getEntity();
        if (e instanceof ServerPlayer) {
            ServerTracker.send((ServerPlayer) e, r, false);
        } else {
            ok(s, target.getName().getString() + ": " + RecapText.summary(r, DeathBreakdown.config.hearts));
            for (String line : RecapText.plainText(r, DeathBreakdown.config.hearts).split("\n")) ok(s, line);
        }
        return 1;
    }

    private static int reload(CommandSourceStack s) {
        DeathBreakdown.reloadConfig();
        if (DeathBreakdown.config.problems.isEmpty()) {
            ok(s, "Death Breakdown config reloaded.");
        } else {
            ok(s, "Death Breakdown config reloaded with " + DeathBreakdown.config.problems.size() + " fix(es):");
            for (String p : DeathBreakdown.config.problems) send(s, Txt.lit(" - " + p).withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    private static int help(CommandSourceStack s) {
        boolean op = op(s, 2);
        send(s, Txt.lit(DeathBreakdown.NAME + ": see exactly what killed you.").withStyle(ChatFormatting.GOLD));
        line(s, "/deathbreakdown", "or /db: your last death recap again (with the mod: press K)");
        line(s, "/db history", "your last " + DeathBreakdown.config.historySize + " deaths, click one to open it");
        line(s, "/db show <number>", "open one from the history (1 = newest)");
        line(s, "/db copy", "copy your last recap as text");
        if (op) {
            line(s, "/db player <name>", "see another player's last recap");
            line(s, "/db reload", "reload config/deathbreakdown.properties");
        }
        return 1;
    }

    private static void line(CommandSourceStack s, String cmd, String what) {
        MutableComponent c = Txt.lit(cmd).withStyle(ChatFormatting.AQUA).append(Txt.lit(" - " + what).withStyle(ChatFormatting.GRAY));
        send(s, c);
    }
}
