package com.cosmiclogic.deathbreakdown;

import com.cosmiclogic.deathbreakdown.core.Hit;
import com.cosmiclogic.deathbreakdown.core.HitLog;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapCodec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/**
 * "/deathbreakdown selftest" (console / op 4). CI runs it on the real Forge and NeoForge servers after they start.
 * It checks the version-specific parts against real Minecraft objects: damage type ids, labels, who/what hit you,
 * the weapon, the chat summary and the packet string.
 */
public final class SelfTest {
    private SelfTest() {}

    public static int run(CommandSourceStack out) {
        StringBuilder report = new StringBuilder();
        try {
            ServerLevel level = out.getServer().overworld();
            DamageSources ds = level.damageSources();

            DamageSource fall = ds.fall();
            check("minecraft:fall".equals(ServerTracker.typeId(fall)), "fall id = " + ServerTracker.typeId(fall));
            check("Fall".equals(ServerTracker.sourceName(fall)), "fall name = " + ServerTracker.sourceName(fall));
            check(ServerTracker.kind(fall) == Hit.Kind.ENVIRONMENT, "fall kind = " + ServerTracker.kind(fall));

            DamageSource lava = ds.lava();
            check("Lava".equals(ServerTracker.sourceName(lava)), "lava name = " + ServerTracker.sourceName(lava));
            report.append("environment ok; ");

            Zombie zombie = EntityType.ZOMBIE.create(level);
            check(zombie != null, "zombie created");
            zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            DamageSource hit = ds.mobAttack(zombie);
            check("Zombie".equals(ServerTracker.sourceName(hit)), "mob name = " + ServerTracker.sourceName(hit));
            check("Iron Sword".equals(ServerTracker.weapon(hit)), "weapon = " + ServerTracker.weapon(hit));
            check(ServerTracker.kind(hit) == Hit.Kind.MOB, "mob kind = " + ServerTracker.kind(hit));
            check("minecraft:mob_attack".equals(ServerTracker.typeId(hit)), "mob id = " + ServerTracker.typeId(hit));
            report.append("mob + weapon ok; ");

            HitLog log = new HitLog(DeathBreakdown.config.windowMs());
            UUID id = UUID.randomUUID();
            long now = System.currentTimeMillis();
            log.add(id, new Hit(now - 4000, ServerTracker.sourceName(lava), "", ServerTracker.kind(lava),
                    ServerTracker.typeId(lava), 8, 0, 1.5F, 0, 0, 0, 6.5F, 13.5F, false));
            log.add(id, new Hit(now, ServerTracker.sourceName(hit), ServerTracker.weapon(hit), ServerTracker.kind(hit),
                    ServerTracker.typeId(hit), 20, 9, 2, 0, 0, 0, 9, 0, false));
            Recap r = log.onDeath(id, "Tester", "Tester was slain by Zombie", now, null);
            check(r.hits.size() == 2 && r.finalBlow().finalBlow, "recap built");

            String chat = ServerTracker.chatSummary(r, true).getString();
            check(chat.startsWith("[" + DeathBreakdown.NAME + "] Killed by Zombie (Iron Sword)"), "chat = " + chat);
            report.append("chat ok; ");

            Recap back = RecapCodec.decode(RecapCodec.encode(r));
            check(back != null && back.hits.size() == 2 && "Iron Sword".equals(back.hits.get(1).weapon), "packet round trip");
            report.append("packet ok; ");

            check(DeathBreakdown.config.windowSeconds >= 1, "config loaded");
            report.append("config ok (window ").append(DeathBreakdown.config.windowSeconds).append("s)");

            DeathBreakdown.LOG.info("Death Breakdown selftest PASS: {}", report);
            out.sendSuccess(() -> Component.literal("Death Breakdown selftest PASS"), false);
            return 1;
        } catch (Throwable t) {
            DeathBreakdown.LOG.error("Death Breakdown selftest FAIL: {}", t.toString(), t);
            out.sendFailure(Component.literal("Death Breakdown selftest FAIL: " + t));
            return 0;
        }
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("check failed: " + what);
    }
}
