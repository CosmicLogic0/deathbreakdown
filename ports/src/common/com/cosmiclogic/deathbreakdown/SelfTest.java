package com.cosmiclogic.deathbreakdown;

import com.cosmiclogic.deathbreakdown.core.DamageLabels;
import com.cosmiclogic.deathbreakdown.core.Hit;
import com.cosmiclogic.deathbreakdown.core.HitLog;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapCodec;
import com.cosmiclogic.deathbreakdown.core.RecapConfig;
import com.cosmiclogic.deathbreakdown.core.RecapStore;
import com.cosmiclogic.deathbreakdown.core.RecapText;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//#if MC >= 1.19.4
import net.minecraft.world.damagesource.DamageSources;
//#endif
//#if MC >= 1.21.2
import net.minecraft.world.entity.EntitySpawnReason;
//#endif

import java.io.File;
import java.util.List;
import java.util.UUID;

/**
 * "/deathbreakdown selftest" (console / op 4). CI runs it on the real Forge and NeoForge servers of every version.
 * It checks the version-specific parts against real Minecraft objects: damage type ids, labels, who/what hit you,
 * the weapon, the chat summary and the packet string.
 */
public final class SelfTest {
    private SelfTest() {}

    public static int run(CommandSourceStack out) {
        StringBuilder report = new StringBuilder();
        try {
            ServerLevel level = out.getServer().overworld();
            //#if MC >= 26.2
            LivingEntity zombie = (LivingEntity) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getValue(net.minecraft.resources.Identifier.withDefaultNamespace("zombie")).create(level, EntitySpawnReason.TRIGGERED);
            //#elif MC >= 1.21.2
            LivingEntity zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
            //#else
            LivingEntity zombie = EntityType.ZOMBIE.create(level);
            //#endif
            check(zombie != null, "zombie created");
            zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));

            //#if MC >= 1.19.4
            DamageSources ds = level.damageSources();
            DamageSource fall = ds.fall();
            DamageSource lava = ds.lava();
            DamageSource hit = ds.mobAttack(zombie);
            //#else
            DamageSource fall = DamageSource.FALL;
            DamageSource lava = DamageSource.LAVA;
            DamageSource hit = DamageSource.mobAttack(zombie);
            //#endif

            check("Fall".equals(DamageLabels.label(ServerTracker.typeId(fall))), "fall id = " + ServerTracker.typeId(fall));
            check("Fall".equals(ServerTracker.sourceName(fall)), "fall name = " + ServerTracker.sourceName(fall));
            check(ServerTracker.kind(fall) == Hit.Kind.ENVIRONMENT, "fall kind = " + ServerTracker.kind(fall));
            check("Lava".equals(ServerTracker.sourceName(lava)), "lava name = " + ServerTracker.sourceName(lava));
            report.append("environment ok (").append(ServerTracker.typeId(fall)).append("); ");

            check("Zombie".equals(ServerTracker.sourceName(hit)), "mob name = " + ServerTracker.sourceName(hit));
            check("Iron Sword".equals(ServerTracker.weapon(hit)), "weapon = " + ServerTracker.weapon(hit));
            check(ServerTracker.kind(hit) == Hit.Kind.MOB, "mob kind = " + ServerTracker.kind(hit));
            check("Melee".equals(DamageLabels.label(ServerTracker.typeId(hit))), "mob id = " + ServerTracker.typeId(hit));
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

            // 1.1.0: death/replay flag, history, saving in the world folder, capped hover, tip, broken config, Nether
            check(Boolean.TRUE.equals(RecapCodec.isDeath(RecapCodec.encodeDeath(r))), "death flag");
            check(Boolean.FALSE.equals(RecapCodec.isDeath(RecapCodec.encodeReplay(r))), "replay flag");
            HitLog hist = new HitLog(10_000);
            hist.setHistorySize(3);
            for (int i = 0; i < 5; i++) {
                hist.add(id, new Hit(now + i, "Lava", "", Hit.Kind.ENVIRONMENT, "lava", 4, 0, 0, 0, 0, 0, 4, 0, false));
                hist.onDeath(id, "Tester", "death " + i, now + i, null);
            }
            List<Recap> h = hist.history(id);
            check(h.size() == 3 && "death 4".equals(h.get(2).deathMessage), "history keeps the newest 3");
            File dir = out.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve(DeathBreakdown.MOD_ID).toFile();
            UUID tmp = UUID.randomUUID();
            check(RecapStore.save(dir, tmp, h), "history saved in " + dir);
            List<Recap> loaded = RecapStore.load(dir, tmp);
            check(loaded.size() == 3 && "death 2".equals(loaded.get(0).deathMessage), "history loaded back");
            check(RecapStore.fileFor(dir, tmp).delete(), "test file removed");
            report.append("history + world save ok; ");

            List<Hit> many = new java.util.ArrayList<>();
            for (int i = 0; i < 30; i++) many.add(new Hit(now - 3000 + i * 100, "Bee", "", Hit.Kind.MOB, "sting", 1, 0, 0, 0, 0, 0, 1, 20 - i * 0.5F, i == 29));
            Recap swarm = new Recap("Tester", "Tester was stung to death", now, 10_000, many);
            String hover = ServerTracker.hover(swarm, true).getString();
            int lines = hover.split("\n").length;
            check(lines < 60, "hover fits on screen (" + lines + " lines)");
            check(ServerTracker.chatSummary(swarm, true).getString().contains("[copy]"), "copy link");
            report.append("hover capped (").append(lines).append(" lines); ");

            File bad = new File(dir, "selftest-broken.properties");
            check(bad.getParentFile().isDirectory() || bad.getParentFile().mkdirs(), "test dir");
            java.nio.file.Files.write(bad.toPath(), "windowSeconds=banana\nhearts=maybe\nhistorySize=999\n".getBytes("UTF-8"));
            RecapConfig bc = RecapConfig.load(bad);
            check(bc.windowSeconds == 10 && bc.hearts && bc.historySize == 20 && bc.problems.size() >= 3, "broken config falls back: " + bc.problems);
            check(bad.delete(), "test config removed");
            report.append("broken config ok; ");

            ServerLevel nether = out.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            check(nether != null, "nether loaded");
            //#if MC >= 1.19.4
            DamageSource netherLava = nether.damageSources().lava();
            //#else
            DamageSource netherLava = DamageSource.LAVA;
            //#endif
            check("Lava".equals(ServerTracker.sourceName(netherLava)), "nether lava name = " + ServerTracker.sourceName(netherLava));
            report.append("nether ok; ");

            check(DeathBreakdown.config.windowSeconds >= 1, "config loaded");
            check(!RecapText.tip(r).isEmpty() || r.hits.size() < 2, "tip");
            report.append("config ok (window ").append(DeathBreakdown.config.windowSeconds).append("s, history ")
                    .append(DeathBreakdown.config.historySize).append(")");

            DeathBreakdown.LOG.info("Death Breakdown selftest PASS: {}", report);
            RecapCommand.ok(out, "Death Breakdown selftest PASS");
            return 1;
        } catch (Throwable t) {
            DeathBreakdown.LOG.error("Death Breakdown selftest FAIL: " + t, t);
            out.sendFailure(Txt.lit("Death Breakdown selftest FAIL: " + t));
            return 0;
        }
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("check failed: " + what);
    }
}
