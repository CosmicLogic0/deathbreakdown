package com.cosmiclogic.deathbreakdown.neoforge;

import com.cosmiclogic.deathbreakdown.DeathBreakdown;
import com.cosmiclogic.deathbreakdown.RecapCommand;
import com.cosmiclogic.deathbreakdown.ServerTracker;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.nio.file.Path;

@Mod(DeathBreakdown.MOD_ID)
public class DeathBreakdownNeoForge {

    public DeathBreakdownNeoForge(IEventBus modBus, Dist dist) {
        DeathBreakdown.init(new DeathBreakdown.Platform() {
            @Override public String loaderName() { return "NeoForge"; }
            @Override public Path configDir() { return FMLPaths.CONFIGDIR.get(); }
            @Override public boolean canReceiveRecap(ServerPlayer p) { return p.connection.hasChannel(RecapPayload.TYPE); }
            @Override public void sendRecap(ServerPlayer p, String data) { PacketDistributor.sendToPlayer(p, new RecapPayload(data)); }
        });
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onDeath);
        NeoForge.EVENT_BUS.addListener(this::onLogout);
        NeoForge.EVENT_BUS.addListener(this::onCommands);
        if (dist.isClient()) NeoForgeClient.init(modBus);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        // optional(): players without the mod can still join; they get the chat summary instead.
        event.registrar("1").optional().playToClient(RecapPayload.TYPE, RecapPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> NeoForgeClient.receive(payload.data())));
    }

    /** NeoForge reports exactly how much each layer stopped. */
    private void onDamage(LivingDamageEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        float invul = e.getReduction(DamageContainer.Reduction.INVULNERABILITY);
        float raw = e.getOriginalDamage() - invul;
        ServerTracker.recordExact(p, e.getSource(), raw,
                e.getReduction(DamageContainer.Reduction.ARMOR),
                e.getReduction(DamageContainer.Reduction.ENCHANTMENTS),
                e.getReduction(DamageContainer.Reduction.MOB_EFFECTS),
                e.getReduction(DamageContainer.Reduction.ABSORPTION),
                e.getBlockedDamage(),
                e.getNewDamage(),
                p.getHealth());
    }

    private void onDeath(LivingDeathEvent e) {
        if (e.isCanceled()) return;
        if (e.getEntity() instanceof ServerPlayer p) ServerTracker.onDeath(p, e.getSource());
    }

    private void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) ServerTracker.onLogout(p);
    }

    private void onCommands(RegisterCommandsEvent e) {
        RecapCommand.register(e.getDispatcher());
    }
}
