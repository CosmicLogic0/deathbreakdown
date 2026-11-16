package com.cosmiclogic.deathbreakdown;

import com.cosmiclogic.deathbreakdown.core.Recap;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /deathbreakdown (alias /db): show your last recap again.
 * /deathbreakdown reload (ops): re-read deathbreakdown.properties.
 */
public final class RecapCommand {
    private RecapCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("deathbreakdown")
                .executes(c -> show(c.getSource()))
                .then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(c -> {
                    DeathBreakdown.reloadConfig();
                    c.getSource().sendSuccess(() -> Component.literal("Death Breakdown config reloaded."), true);
                    return 1;
                })));
        d.register(Commands.literal("db").executes(c -> show(c.getSource())));
    }

    private static int show(CommandSourceStack s) {
        ServerPlayer p = s.getPlayer();
        if (p == null) {
            s.sendFailure(Component.literal("Only players have a death recap."));
            return 0;
        }
        Recap r = DeathBreakdown.HITS.last(p.getUUID());
        if (r == null) {
            s.sendFailure(Component.literal("No death recap yet. You haven't died since joining."));
            return 0;
        }
        ServerTracker.send(p, r);
        if (!DeathBreakdown.platform.canReceiveRecap(p) && !DeathBreakdown.config.chatSummary) {
            p.sendSystemMessage(ServerTracker.chatSummary(r, DeathBreakdown.config.hearts));
        }
        return 1;
    }
}
