package com.cosmiclogic.deathbreakdown.legacy;

import com.cosmiclogic.deathbreakdown.core.DamageLabels;
import com.cosmiclogic.deathbreakdown.core.DamageMath;
import com.cosmiclogic.deathbreakdown.core.Hit;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapCodec;
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
        send(p, r);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.player != null) DeathBreakdown112.HITS.forget(e.player.getUniqueID());
    }

    static void send(EntityPlayerMP p, Recap r) {
        if (DeathBreakdown112.hasMod(p)) DeathBreakdown112.sendRecap(p, RecapCodec.encode(r));
        else if (DeathBreakdown112.config.chatSummary) p.sendMessage(chatSummary(r, DeathBreakdown112.config.hearts));
    }

    // ---- chat (vanilla clients). 1.12 chat has 16 colors, no RGB. ----

    static ITextComponent chatSummary(Recap r, boolean hearts) {
        TextComponentString hover = color(DeathBreakdown112.NAME, TextFormatting.GOLD);
        hover.appendSibling(color("\n" + r.deathMessage, TextFormatting.GRAY));
        for (Hit h : r.hits) {
            List<String> lines = RecapText.details(h, r, hearts);
            hover.appendSibling(color("\n\n" + lines.get(0), h.finalBlow ? TextFormatting.GOLD : TextFormatting.WHITE));
            for (int i = 1; i < lines.size(); i++) hover.appendSibling(color("\n " + lines.get(i), TextFormatting.GRAY));
        }
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
