package com.cosmiclogic.deathbreakdown.legacy;

import com.cosmiclogic.deathbreakdown.core.DamageLabels;
import com.cosmiclogic.deathbreakdown.core.Hit;
import com.cosmiclogic.deathbreakdown.core.HitLog;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapCodec;
import com.cosmiclogic.deathbreakdown.core.RecapText;
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
 * /deathbreakdown (alias /db): your last recap again. help, history, show <n>, copy (as text: 1.12 has no clipboard
 * links), player <name> (ops), reload (ops), selftest (console, run by CI on the real 1.12.2 server).
 */
public class RecapCommand112 extends CommandBase {
    @Override public String getName() { return "deathbreakdown"; }
    @Override public List<String> getAliases() { return Collections.singletonList("db"); }
    @Override public String getUsage(ICommandSender s) { return "/deathbreakdown [help|history|show <n>|copy|player <name>|reload]"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return true; }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, net.minecraft.util.math.BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, sender.canUseCommand(2, getName())
                    ? new String[]{"help", "history", "show", "copy", "player", "reload"}
                    : new String[]{"help", "history", "show", "copy"});
        }
        if (args.length == 2 && args[0].equals("player")) return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        return Collections.emptyList();
    }

    private static void say(ICommandSender s, String text, net.minecraft.util.text.TextFormatting f) {
        s.sendMessage(ServerEvents112.color(text, f));
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        String sub = args.length > 0 ? args[0] : "";
        boolean op = sender.canUseCommand(2, getName());
        if (sub.equals("help")) { help(sender, op); return; }
        if (sub.equals("reload") && op) {
            DeathBreakdown112.reloadConfig();
            if (DeathBreakdown112.config.problems.isEmpty()) sender.sendMessage(new TextComponentString("Death Breakdown config reloaded."));
            else {
                sender.sendMessage(new TextComponentString("Death Breakdown config reloaded with " + DeathBreakdown112.config.problems.size() + " fix(es):"));
                for (String p : DeathBreakdown112.config.problems) say(sender, " - " + p, net.minecraft.util.text.TextFormatting.YELLOW);
            }
            return;
        }
        if (sub.equals("selftest") && sender.canUseCommand(4, getName())) {
            selfTest(server, sender);
            return;
        }
        if (sub.equals("player") && op) {
            EntityPlayerMP target = args.length > 1 ? server.getPlayerList().getPlayerByUsername(args[1]) : null;
            if (target == null) { sender.sendMessage(new TextComponentString("That player isn't online.")); return; }
            Recap r = DeathBreakdown112.HITS.last(target.getUniqueID());
            if (r == null) { sender.sendMessage(new TextComponentString(target.getName() + " has no death recap yet.")); return; }
            if (sender.getCommandSenderEntity() instanceof EntityPlayerMP) ServerEvents112.send((EntityPlayerMP) sender.getCommandSenderEntity(), r, false);
            else for (String line : RecapText.plainText(r, DeathBreakdown112.config.hearts).split("\n")) sender.sendMessage(new TextComponentString(line));
            return;
        }
        if (!(sender.getCommandSenderEntity() instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString("Only players have a death recap. Try /deathbreakdown player <name>."));
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) sender.getCommandSenderEntity();
        List<Recap> h = DeathBreakdown112.HITS.history(p.getUniqueID());
        if (h.isEmpty()) {
            p.sendMessage(new TextComponentString("No death recap yet. It appears here after you die (/deathbreakdown help)."));
            return;
        }
        if (sub.equals("history")) {
            long now = System.currentTimeMillis();
            say(p, "Your last " + h.size() + (h.size() == 1 ? " death" : " deaths") + " (click to open):", net.minecraft.util.text.TextFormatting.GOLD);
            for (int i = 0; i < h.size(); i++) {
                Recap r = h.get(h.size() - 1 - i);
                TextComponentString line = ServerEvents112.color((i + 1) + ". " + RecapText.ago(r.timeMs, now) + ": "
                        + RecapText.summary(r, DeathBreakdown112.config.hearts), net.minecraft.util.text.TextFormatting.WHITE);
                line.getStyle().setClickEvent(new net.minecraft.util.text.event.ClickEvent(
                        net.minecraft.util.text.event.ClickEvent.Action.RUN_COMMAND, "/deathbreakdown show " + (i + 1)));
                p.sendMessage(line);
            }
            return;
        }
        if (sub.equals("copy")) {
            say(p, "Your last recap as text (select it in the chat log, or open logs/latest.log):", net.minecraft.util.text.TextFormatting.GRAY);
            for (String line : RecapText.plainText(h.get(h.size() - 1), DeathBreakdown112.config.hearts).split("\n")) {
                p.sendMessage(new TextComponentString(line));
            }
            return;
        }
        int n = 1;
        if (sub.equals("show")) {
            try { n = Integer.parseInt(args.length > 1 ? args[1] : "1"); } catch (NumberFormatException ignored) {}
            if (n < 1 || n > h.size()) {
                p.sendMessage(new TextComponentString("You have " + h.size() + (h.size() == 1 ? " death" : " deaths") + " saved. /deathbreakdown history lists them."));
                return;
            }
        }
        ServerEvents112.send(p, h.get(h.size() - n), false);
    }

    private static void help(ICommandSender s, boolean op) {
        say(s, "Death Breakdown: see exactly what killed you.", net.minecraft.util.text.TextFormatting.GOLD);
        say(s, "/deathbreakdown (or /db) - your last death recap (with the mod: press K)", net.minecraft.util.text.TextFormatting.GRAY);
        say(s, "/db history - your last " + DeathBreakdown112.config.historySize + " deaths, click one to open it", net.minecraft.util.text.TextFormatting.GRAY);
        say(s, "/db show <number> - open one from the history (1 = newest)", net.minecraft.util.text.TextFormatting.GRAY);
        say(s, "/db copy - your last recap as plain text", net.minecraft.util.text.TextFormatting.GRAY);
        if (op) {
            say(s, "/db player <name> - another player's last recap", net.minecraft.util.text.TextFormatting.GRAY);
            say(s, "/db reload - reload config/deathbreakdown.properties", net.minecraft.util.text.TextFormatting.GRAY);
        }
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

            // 1.1.0: death/replay flag, history, saving in the world folder, capped hover, broken config, Nether
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
            java.io.File dir = new java.io.File(server.getWorld(0).getSaveHandler().getWorldDirectory(), DeathBreakdown112.MOD_ID);
            UUID tmp = UUID.randomUUID();
            check(com.cosmiclogic.deathbreakdown.core.RecapStore.save(dir, tmp, h), "history saved in " + dir);
            check(com.cosmiclogic.deathbreakdown.core.RecapStore.load(dir, tmp).size() == 3, "history loaded back");
            check(com.cosmiclogic.deathbreakdown.core.RecapStore.fileFor(dir, tmp).delete(), "test file removed");
            report.append("history + world save ok; ");

            List<Hit> many = new java.util.ArrayList<Hit>();
            for (int i = 0; i < 30; i++) many.add(new Hit(now - 3000 + i * 100, "Bee", "", Hit.Kind.MOB, "sting", 1, 0, 0, 0, 0, 0, 1, 20 - i * 0.5F, i == 29));
            Recap swarm = new Recap("Tester", "Tester was stung to death", now, 10_000, many);
            int lines = ServerEvents112.hover(swarm, true).getUnformattedText().split("\n").length;
            check(lines < 60, "hover fits on screen (" + lines + " lines)");
            report.append("hover capped (").append(lines).append(" lines); ");

            java.io.File bad = new java.io.File(dir, "selftest-broken.properties");
            check(bad.getParentFile().isDirectory() || bad.getParentFile().mkdirs(), "test dir");
            java.nio.file.Files.write(bad.toPath(), "windowSeconds=banana\nhearts=maybe\nhistorySize=999\n".getBytes("UTF-8"));
            com.cosmiclogic.deathbreakdown.core.RecapConfig bc = com.cosmiclogic.deathbreakdown.core.RecapConfig.load(bad);
            check(bc.windowSeconds == 10 && bc.hearts && bc.historySize == 20 && bc.problems.size() >= 3, "broken config falls back: " + bc.problems);
            check(bad.delete(), "test config removed");
            report.append("broken config ok; ");

            check(server.getWorld(-1) != null, "nether loaded");
            EntityZombie netherZombie = new EntityZombie(server.getWorld(-1));
            check("Zombie".equals(ServerEvents112.sourceName(DamageSource.causeMobDamage(netherZombie))), "nether mob name");
            report.append("nether ok; ");

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
