package com.cosmiclogic.deathbreakdown.legacy;

import com.cosmiclogic.deathbreakdown.core.DamageLabels;
import com.cosmiclogic.deathbreakdown.core.DamageMath;
import com.cosmiclogic.deathbreakdown.core.Hit;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapCodec;
import com.cosmiclogic.deathbreakdown.core.RecapStore;
import com.cosmiclogic.deathbreakdown.core.RecapText;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.io.File;
import java.util.List;

/** Server side for 1.12.2: records hits, builds the recap on death and sends it (screen or chat). */
public class ServerEvents112 {

    /** LivingHurtEvent gives the damage before armor (after a shield); the parts come from vanilla's formulas. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHurt(LivingHurtEvent e) {
        if (!(e.getEntityLiving() instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) e.getEntityLiving();
        DamageSource src = e.getSource();
        float raw = e.getAmount();
        if (raw <= 0) return;
        float protection = EnchantmentHelper.getEnchantmentModifierDamage(p.getArmorInventoryList(), src);
        PotionEffect res = p.getActivePotionEffect(MobEffects.RESISTANCE);
        boolean bypassArmor = src.isUnblockable();
        boolean bypassMagic = src.isDamageAbsolute();
        DamageMath.Split s = DamageMath.split(raw, p.getTotalArmorValue(),
                (float) p.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue(),
                protection, res == null ? -1 : res.getAmplifier(), p.getAbsorptionAmount(),
                bypassArmor, bypassMagic, bypassMagic || src == DamageSource.OUT_OF_WORLD);
        float healthAfter = Math.max(0, p.getHealth() - s.dealt);
        DeathBreakdown112.HITS.add(p.getUniqueID(), new Hit(System.currentTimeMillis(), sourceName(src), weapon(src), kind(src),
                typeId(src), raw, s.armor, s.enchants, s.effects, s.absorbed, 0, s.dealt, healthAfter, false));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent e) {
        if (e.isCanceled() || !(e.getEntityLiving() instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) e.getEntityLiving();
        DamageSource src = e.getSource();
        long now = System.currentTimeMillis();
        Hit fallback = new Hit(now, sourceName(src), weapon(src), kind(src), typeId(src), 0, 0, 0, 0, 0, 0, 0, 0, false);
        String msg = p.getCombatTracker().getDeathMessage().getUnformattedText();
        Recap r = DeathBreakdown112.HITS.onDeath(p.getUniqueID(), p.getName(), msg, now, fallback);
        send(p, r, true);
        saveHistory(p);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.player != null) DeathBreakdown112.HITS.forget(e.player.getUniqueID()); // live hits only; history stays
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.player instanceof EntityPlayerMP) || !DeathBreakdown112.config.saveHistory) return;
        File dir = storeDir((EntityPlayerMP) e.player);
        if (dir != null) DeathBreakdown112.HITS.restore(e.player.getUniqueID(), RecapStore.load(dir, e.player.getUniqueID()));
    }

    /** Keeps the player's recent recaps in the world folder (deathbreakdown/<uuid>.txt). */
    static void saveHistory(EntityPlayerMP p) {
        if (!DeathBreakdown112.config.saveHistory) return;
        File dir = storeDir(p);
        if (dir != null && !RecapStore.save(dir, p.getUniqueID(), DeathBreakdown112.HITS.history(p.getUniqueID()))) {
            DeathBreakdown112.LOG.warn("Couldn't save death history to {}", dir);
        }
    }

    static File storeDir(EntityPlayerMP p) {
        try {
            return new File(p.getServerWorld().getSaveHandler().getWorldDirectory(), DeathBreakdown112.MOD_ID);
        } catch (RuntimeException e) {
            DeathBreakdown112.LOG.warn("No world folder for death history: {}", e.toString());
            return null;
        }
    }

    /** A death that just happened. */
    static void send(EntityPlayerMP p, Recap r) { send(p, r, true); }

    /** death = it just happened; false = asked for again (/deathbreakdown, history). */
    static void send(EntityPlayerMP p, Recap r, boolean death) {
        if (DeathBreakdown112.hasMod(p)) DeathBreakdown112.sendRecap(p, death ? RecapCodec.encodeDeath(r) : RecapCodec.encodeReplay(r));
        else if (!death || DeathBreakdown112.config.chatSummary) p.sendMessage(chatSummary(r, DeathBreakdown112.config.hearts));
    }

    // ---- chat (vanilla clients). 1.12 chat has 16 colors, no RGB. ----

    /** How many hits the chat hover shows; older ones are summed up so it fits on screen. */
    static final int HOVER_HITS = 6;

    static TextComponentString hover(Recap r, boolean hearts) {
        TextComponentString hover = color(DeathBreakdown112.NAME, TextFormatting.GOLD);
        hover.appendSibling(color("\n" + r.deathMessage, TextFormatting.GRAY));
        String tip = DeathBreakdown112.config.showTip ? RecapText.tip(r) : "";
        if (!tip.isEmpty()) hover.appendSibling(color("\n" + tip, TextFormatting.YELLOW));
        boolean header = true;
        for (String line : RecapText.hoverLines(r, hearts, HOVER_HITS)) {
            if (line.isEmpty()) { header = true; continue; }
            if (line.startsWith("(")) { hover.appendSibling(color("\n\n" + line, TextFormatting.DARK_GRAY)); header = true; continue; }
            if (header) {
                hover.appendSibling(color("\n\n" + line, line.startsWith("FINAL BLOW") ? TextFormatting.GOLD : TextFormatting.WHITE));
                header = false;
            } else {
                hover.appendSibling(color("\n " + line, TextFormatting.GRAY));
            }
        }
        return hover;
    }

    static ITextComponent chatSummary(Recap r, boolean hearts) {
        TextComponentString hover = hover(r, hearts);
        TextComponentString details = new TextComponentString(" [details]");
        details.setStyle(new Style().setColor(TextFormatting.AQUA)
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
                .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/deathbreakdown")));
        TextComponentString out = color("[" + DeathBreakdown112.NAME + "] ", TextFormatting.GOLD);
        out.appendSibling(color(RecapText.summary(r, hearts), TextFormatting.WHITE));
        out.appendSibling(details);
        return out;
    }

    static TextComponentString color(String text, TextFormatting f) {
        TextComponentString c = new TextComponentString(text);
        c.setStyle(new Style().setColor(f));
        return c;
    }

    // ---- describing a damage source ----

    static String typeId(DamageSource src) {
        return src.getDamageType();
    }

    static String sourceName(DamageSource src) {
        Entity e = src.getTrueSource();
        if (e != null) return e.getDisplayName().getUnformattedText();
        Entity d = src.getImmediateSource();
        if (d != null) return d.getDisplayName().getUnformattedText();
        return DamageLabels.label(typeId(src));
    }

    static String weapon(DamageSource src) {
        Entity e = src.getTrueSource();
        Entity d = src.getImmediateSource();
        if (e instanceof EntityLivingBase) {
            ItemStack held = ((EntityLivingBase) e).getHeldItemMainhand();
            if (!held.isEmpty()) return held.getDisplayName();
        }
        if (d != null && e != null && d != e) return d.getDisplayName().getUnformattedText();
        return "";
    }

    static Hit.Kind kind(DamageSource src) {
        Entity e = src.getTrueSource();
        if (e instanceof EntityPlayer) return Hit.Kind.PLAYER;
        if (src.getImmediateSource() instanceof IProjectile) return Hit.Kind.PROJECTILE;
        if (src.isExplosion()) return Hit.Kind.EXPLOSION;
        if (e != null) return Hit.Kind.MOB;
        return DamageLabels.kind(typeId(src));
    }
}
