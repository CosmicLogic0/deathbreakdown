package com.cosmiclogic.deathbreakdown.legacy;

import com.cosmiclogic.deathbreakdown.core.DamageLabels;
import com.cosmiclogic.deathbreakdown.core.Hit;
import com.cosmiclogic.deathbreakdown.core.HitLog;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapCodec;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * /deathbreakdown (alias /db): show your last recap again. /deathbreakdown reload (ops): re-read the config.
 * /deathbreakdown selftest (console): automatic feature test, run by CI on the real 1.12.2 server.
 */
public class RecapCommand112 extends CommandBase {
    @Override public String getName() { return "deathbreakdown"; }
    @Override public List<String> getAliases() { return Collections.singletonList("db"); }
    @Override public String getUsage(ICommandSender s) { return "/deathbreakdown [reload]"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return true; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        String sub = args.length > 0 ? args[0] : "";
        if (sub.equals("reload") && sender.canUseCommand(2, getName())) {
            DeathBreakdown112.reloadConfig();
            sender.sendMessage(new TextComponentString("Death Breakdown config reloaded."));
            return;
        }
        if (sub.equals("selftest") && sender.canUseCommand(4, getName())) {
            selfTest(server, sender);
            return;
        }
        if (!(sender.getCommandSenderEntity() instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString("Only players have a death recap."));
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) sender.getCommandSenderEntity();
        Recap r = DeathBreakdown112.HITS.last(p.getUniqueID());
        if (r == null) {
            p.sendMessage(new TextComponentString("No death recap yet. You haven't died since joining."));
            return;
        }
        if (DeathBreakdown112.hasMod(p)) DeathBreakdown112.sendRecap(p, RecapCodec.encode(r));
        else p.sendMessage(ServerEvents112.chatSummary(r, DeathBreakdown112.config.hearts));
    }

    /** Checks the 1.12.2-specific parts against real Minecraft objects (same checks as every other version). */
    private static void selfTest(MinecraftServer server, ICommandSender out) {
        StringBuilder report = new StringBuilder();
        try {
            World world = server.getWorld(0);
            EntityZombie zombie = new EntityZombie(world);
            zombie.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            DamageSource fall = DamageSource.FALL, lava = DamageSource.LAVA, hit = DamageSource.causeMobDamage(zombie);

            check("Fall".equals(DamageLabels.label(ServerEvents112.typeId(fall))), "fall id = " + ServerEvents112.typeId(fall));
            check("Fall".equals(ServerEvents112.sourceName(fall)), "fall name = " + ServerEvents112.sourceName(fall));
            check(ServerEvents112.kind(fall) == Hit.Kind.ENVIRONMENT, "fall kind");
            check("Lava".equals(ServerEvents112.sourceName(lava)), "lava name = " + ServerEvents112.sourceName(lava));
            report.append("environment ok (").append(ServerEvents112.typeId(fall)).append("); ");

            check("Zombie".equals(ServerEvents112.sourceName(hit)), "mob name = " + ServerEvents112.sourceName(hit));
            check("Iron Sword".equals(ServerEvents112.weapon(hit)), "weapon = " + ServerEvents112.weapon(hit));
            check(ServerEvents112.kind(hit) == Hit.Kind.MOB, "mob kind = " + ServerEvents112.kind(hit));
            check("Melee".equals(DamageLabels.label(ServerEvents112.typeId(hit))), "mob id = " + ServerEvents112.typeId(hit));
            report.append("mob + weapon ok; ");

            HitLog log = new HitLog(DeathBreakdown112.config.windowMs());
            UUID id = UUID.randomUUID();
            long now = System.currentTimeMillis();
            log.add(id, new Hit(now - 4000, "Lava", "", Hit.Kind.ENVIRONMENT, ServerEvents112.typeId(lava), 8, 0, 1.5F, 0, 0, 0, 6.5F, 13.5F, false));
            log.add(id, new Hit(now, ServerEvents112.sourceName(hit), ServerEvents112.weapon(hit), ServerEvents112.kind(hit),
                    ServerEvents112.typeId(hit), 20, 9, 2, 0, 0, 0, 9, 0, false));
            Recap r = log.onDeath(id, "Tester", "Tester was slain by Zombie", now, null);
            check(r.hits.size() == 2 && r.finalBlow().finalBlow, "recap built");
            String chat = ServerEvents112.chatSummary(r, true).getUnformattedText();
            check(chat.startsWith("[" + DeathBreakdown112.NAME + "] Killed by Zombie (Iron Sword)"), "chat = " + chat);
            report.append("chat ok; ");
            Recap back = RecapCodec.decode(RecapCodec.encode(r));
            check(back != null && back.hits.size() == 2 && "Iron Sword".equals(back.hits.get(1).weapon), "packet round trip");
            report.append("packet ok; config ok (window ").append(DeathBreakdown112.config.windowSeconds).append("s)");

            DeathBreakdown112.LOG.info("Death Breakdown selftest PASS: {}", report);
            out.sendMessage(new TextComponentString("Death Breakdown selftest PASS"));
        } catch (Throwable t) {
            DeathBreakdown112.LOG.error("Death Breakdown selftest FAIL: " + t, t);
            out.sendMessage(new TextComponentString("Death Breakdown selftest FAIL: " + t));
        }
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("check failed: " + what);
    }
}
