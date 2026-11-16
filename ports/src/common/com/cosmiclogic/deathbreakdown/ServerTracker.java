package com.cosmiclogic.deathbreakdown;

import com.cosmiclogic.deathbreakdown.core.DamageLabels;
import com.cosmiclogic.deathbreakdown.core.DamageMath;
import com.cosmiclogic.deathbreakdown.core.Hit;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapCodec;
import com.cosmiclogic.deathbreakdown.core.RecapText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
//#if MC >= 1.19.4
import net.minecraft.tags.DamageTypeTags;
//#endif
//#if MC >= 1.21
import net.minecraft.server.level.ServerLevel;
//#endif

import java.util.List;

/** Server side: records hits, builds the recap on death and sends it. Loader code only forwards events here. */
public final class ServerTracker {
    private ServerTracker() {}

    /** Loader reports the exact parts (NeoForge's DamageContainer). healthAfter = health once the hit landed. */
    public static void recordExact(ServerPlayer p, DamageSource src, float raw, float armor, float enchants,
                                   float effects, float absorbed, float blocked, float dealt, float healthAfter) {
        if (raw <= 0 && dealt <= 0) return;
        DeathBreakdown.HITS.add(p.getUUID(), new Hit(System.currentTimeMillis(), sourceName(src), weapon(src),
                kind(src), typeId(src), raw, armor, enchants, effects, absorbed, blocked, dealt, healthAfter, false));
    }

    /**
     * Loader only gives the damage before armor (Forge's LivingHurtEvent): work out the parts with vanilla's formulas
     * using the player's current armor, toughness, enchantments, Resistance and absorption hearts.
     */
    public static void recordComputed(ServerPlayer p, DamageSource src, float raw) {
        if (raw <= 0) return;
        float protection = 0;
        //#if MC >= 1.21
        if (p.level() instanceof ServerLevel) protection = EnchantmentHelper.getDamageProtection((ServerLevel) p.level(), p, src);
        //#else
        protection = EnchantmentHelper.getDamageProtection(p.getArmorSlots(), src);
        //#endif
        //#if MC >= 1.21.5
        MobEffectInstance res = p.getEffect(MobEffects.RESISTANCE);
        //#else
        MobEffectInstance res = p.getEffect(MobEffects.DAMAGE_RESISTANCE);
        //#endif
        //#if MC >= 1.19.4
        boolean bypassArmor = src.is(DamageTypeTags.BYPASSES_ARMOR);
        boolean bypassEffects = src.is(DamageTypeTags.BYPASSES_EFFECTS);
        boolean bypassEnchants = bypassEffects || src.is(DamageTypeTags.BYPASSES_ENCHANTMENTS);
        boolean bypassResistance = bypassEffects || src.is(DamageTypeTags.BYPASSES_RESISTANCE);
        //#else
        boolean bypassArmor = src.isBypassArmor();
        boolean bypassEnchants = src.isBypassMagic();
        boolean bypassResistance = src.isBypassMagic();
        //#endif
        DamageMath.Split s = DamageMath.split(raw, p.getArmorValue(), (float) p.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
                protection, res == null ? -1 : res.getAmplifier(), p.getAbsorptionAmount(),
                bypassArmor, bypassEnchants, bypassResistance);
        float healthAfter = Math.max(0, p.getHealth() - s.dealt);
        recordExact(p, src, raw, s.armor, s.enchants, s.effects, s.absorbed, 0, s.dealt, healthAfter);
    }

    public static void onDeath(ServerPlayer p, DamageSource src) {
        long now = System.currentTimeMillis();
        Hit fallback = new Hit(now, sourceName(src), weapon(src), kind(src), typeId(src), 0, 0, 0, 0, 0, 0, 0, 0, false);
        String msg = p.getCombatTracker().getDeathMessage().getString();
        Recap r = DeathBreakdown.HITS.onDeath(p.getUUID(), p.getName().getString(), msg, now, fallback);
        send(p, r);
    }

    /** Sends a recap: the full screen for players with the mod, a chat summary with hover details for everyone else. */
    public static void send(ServerPlayer p, Recap r) {
        if (DeathBreakdown.platform.canReceiveRecap(p)) {
            DeathBreakdown.platform.sendRecap(p, RecapCodec.encode(r));
        } else if (DeathBreakdown.config.chatSummary) {
            Txt.send(p, chatSummary(r, DeathBreakdown.config.hearts));
        }
    }

    public static void onLogout(ServerPlayer p) {
        DeathBreakdown.HITS.forget(p.getUUID());
    }

    // ---- chat (vanilla clients) ----

    public static MutableComponent chatSummary(Recap r, boolean hearts) {
        MutableComponent hover = Txt.lit(DeathBreakdown.NAME).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        hover.append(Txt.lit("\n" + r.deathMessage).withStyle(ChatFormatting.GRAY));
        for (Hit h : r.hits) {
            List<String> lines = RecapText.details(h, r, hearts);
            hover.append(Txt.lit("\n\n" + lines.get(0)).withStyle(h.finalBlow ? ChatFormatting.GOLD : ChatFormatting.WHITE));
            for (int i = 1; i < lines.size(); i++) hover.append(Txt.lit("\n " + lines.get(i)).withStyle(ChatFormatting.GRAY));
        }
        return Txt.lit("[" + DeathBreakdown.NAME + "] ").withStyle(ChatFormatting.GOLD)
                .append(Txt.lit(RecapText.summary(r, hearts)).withStyle(ChatFormatting.WHITE))
                .append(Txt.hoverClick(" [details]", RecapText.BLUE, hover, "/deathbreakdown"));
    }

    // ---- describing a damage source ----

    public static String typeId(DamageSource src) {
        //#if MC >= 1.20.5
        return src.typeHolder().getRegisteredName();
        //#elif MC >= 1.19.4
        return src.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse(src.getMsgId());
        //#else
        return src.getMsgId();
        //#endif
    }

    public static String sourceName(DamageSource src) {
        Entity e = src.getEntity();
        if (e != null) return e.getDisplayName().getString();
        Entity d = src.getDirectEntity();
        if (d != null) return d.getDisplayName().getString();
        return DamageLabels.label(typeId(src));
    }

    public static String weapon(DamageSource src) {
        Entity e = src.getEntity();
        Entity d = src.getDirectEntity();
        if (e instanceof LivingEntity) {
            ItemStack held = ((LivingEntity) e).getMainHandItem();
            if (!held.isEmpty()) return held.getHoverName().getString();
        }
        if (d != null && e != null && d != e) return d.getDisplayName().getString();
        return "";
    }

    public static Hit.Kind kind(DamageSource src) {
        Entity e = src.getEntity();
        if (e instanceof Player) return Hit.Kind.PLAYER;
        if (src.getDirectEntity() instanceof Projectile) return Hit.Kind.PROJECTILE;
        //#if MC >= 1.19.4
        if (src.is(DamageTypeTags.IS_EXPLOSION)) return Hit.Kind.EXPLOSION;
        //#else
        if (src.isExplosion()) return Hit.Kind.EXPLOSION;
        //#endif
        if (e != null) return Hit.Kind.MOB;
        return DamageLabels.kind(typeId(src));
    }
}
