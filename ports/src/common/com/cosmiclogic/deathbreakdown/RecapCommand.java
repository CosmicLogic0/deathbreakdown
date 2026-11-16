package com.cosmiclogic.deathbreakdown;

import com.cosmiclogic.deathbreakdown.core.Recap;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * /deathbreakdown (alias /db): show your last recap again.
 * /deathbreakdown reload (ops): re-read deathbreakdown.properties.
 * /deathbreakdown selftest (console): automatic feature test, run by CI.
 */
public final class RecapCommand {
    private RecapCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("deathbreakdown")
                .executes(c -> show(c.getSource()))
                .then(Commands.literal("selftest").requires(s -> op(s, 4)).executes(c -> SelfTest.run(c.getSource())))
                .then(Commands.literal("reload").requires(s -> op(s, 2)).executes(c -> {
                    DeathBreakdown.reloadConfig();
                    ok(c.getSource(), "Death Breakdown config reloaded.");
                    return 1;
                })));
        d.register(Commands.literal("db").executes(c -> show(c.getSource())));
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
        Component c = Txt.lit(text);
        //#if MC >= 1.20
        s.sendSuccess(() -> c, false);
        //#else
        s.sendSuccess(c, false);
        //#endif
    }

    private static int show(CommandSourceStack s) {
        Entity e = s.getEntity();
        if (!(e instanceof ServerPlayer)) {
            s.sendFailure(Txt.lit("Only players have a death recap."));
            return 0;
        }
        ServerPlayer p = (ServerPlayer) e;
        Recap r = DeathBreakdown.HITS.last(p.getUUID());
        if (r == null) {
            s.sendFailure(Txt.lit("No death recap yet. You haven't died since joining."));
            return 0;
        }
        ServerTracker.send(p, r);
        if (!DeathBreakdown.platform.canReceiveRecap(p) && !DeathBreakdown.config.chatSummary) {
            Txt.send(p, ServerTracker.chatSummary(r, DeathBreakdown.config.hearts));
        }
        return 1;
    }
}
