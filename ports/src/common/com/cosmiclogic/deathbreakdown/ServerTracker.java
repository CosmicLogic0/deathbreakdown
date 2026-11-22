package com.cosmiclogic.deathbreakdown;

import com.cosmiclogic.deathbreakdown.core.DamageLabels;
import com.cosmiclogic.deathbreakdown.core.DamageMath;
import com.cosmiclogic.deathbreakdown.core.Hit;
import com.cosmiclogic.deathbreakdown.core.Recap;
import com.cosmiclogic.deathbreakdown.core.RecapCodec;
import com.cosmiclogic.deathbreakdown.core.RecapStore;
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

import java.io.File;
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
        send(p, r, true);
        saveHistory(p);
    }

    /** A death that just happened. */
    public static void send(ServerPlayer p, Recap r) { send(p, r, true); }

    /**
     * Sends a recap: the full screen for players with the mod, a chat summary with hover details for everyone else.
     * {@code death} = it just happened (the client shows the death summary); false = asked for again.
     */
    public static void send(ServerPlayer p, Recap r, boolean death) {
        if (DeathBreakdown.platform.canReceiveRecap(p)) {
            DeathBreakdown.platform.sendRecap(p, death ? RecapCodec.encodeDeath(r) : RecapCodec.encodeReplay(r));
        } else if (!death || DeathBreakdown.config.chatSummary) {
            Txt.send(p, chatSummary(r, DeathBreakdown.config.hearts));
        }
    }

    public static void onLogin(ServerPlayer p) {
        if (!DeathBreakdown.config.saveHistory) return;
        File dir = storeDir(p);
        if (dir != null) DeathBreakdown.HITS.restore(p.getUUID(), RecapStore.load(dir, p.getUUID()));
    }

    public static void onLogout(ServerPlayer p) {
        DeathBreakdown.HITS.forget(p.getUUID());
    }

    /** Keeps the player's recent recaps in the world folder (deathbreakdown/<uuid>.txt) so they survive restarts. */
    static void saveHistory(ServerPlayer p) {
        if (!DeathBreakdown.config.saveHistory) return;
        File dir = storeDir(p);
        if (dir != null && !RecapStore.save(dir, p.getUUID(), DeathBreakdown.HITS.history(p.getUUID()))) {
            DeathBreakdown.LOG.warn("Couldn't save death history to {}", dir);
        }
    }

    static net.minecraft.server.MinecraftServer server(ServerPlayer p) {
        //#if MC >= 1.20
        return ((net.minecraft.server.level.ServerLevel) p.level()).getServer();
        //#else
        return p.getLevel().getServer();
        //#endif
    }

    static File storeDir(ServerPlayer p) {
        try {
            return server(p).getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve(DeathBreakdown.MOD_ID).toFile();
        } catch (RuntimeException e) {
            DeathBreakdown.LOG.warn("No world folder for death history: {}", e.toString());
            return null;
        }
    }

    // ---- chat (vanilla clients) ----

    /** How many hits the chat hover shows (the rest are in /deathbreakdown history and the copy). */
    public static final int HOVER_HITS = 6;

    public static MutableComponent hover(Recap r, boolean hearts) {
        MutableComponent hover = Txt.lit(DeathBreakdown.NAME).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        hover.append(Txt.lit("\n" + r.deathMessage).withStyle(ChatFormatting.GRAY));
        String tip = DeathBreakdown.config.showTip ? RecapText.tip(r) : "";
        if (!tip.isEmpty()) hover.append(Txt.lit("\n" + tip).withStyle(ChatFormatting.YELLOW));
        boolean header = true;
        for (String line : RecapText.hoverLines(r, hearts, HOVER_HITS)) {
            if (line.isEmpty()) { header = true; continue; }
            if (line.startsWith("(")) { hover.append(Txt.lit("\n\n" + line).withStyle(ChatFormatting.DARK_GRAY)); header = true; continue; }
            if (header) {
                hover.append(Txt.lit("\n\n" + line).withStyle(line.startsWith("FINAL BLOW") ? ChatFormatting.GOLD : ChatFormatting.WHITE));
                header = false;
            } else {
                hover.append(Txt.lit("\n " + line).withStyle(ChatFormatting.GRAY));
            }
        }
        return hover;
    }

    public static MutableComponent chatSummary(Recap r, boolean hearts) {
        return Txt.lit("[" + DeathBreakdown.NAME + "] ").withStyle(ChatFormatting.GOLD)
                .append(Txt.lit(RecapText.summary(r, hearts)).withStyle(ChatFormatting.WHITE))
                .append(Txt.hoverClick(" [details]", RecapText.BLUE, hover(r, hearts), "/deathbreakdown"))
                .append(Txt.copyClick(" [copy]", RecapText.GRAY, Txt.lit("Copy this recap as text (for Discord or a bug report)"),
                        RecapText.plainText(r, hearts)));
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
