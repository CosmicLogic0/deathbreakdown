package com.cosmiclogic.deathbreakdown.forge;

import com.cosmiclogic.deathbreakdown.DeathBreakdown;
import com.cosmiclogic.deathbreakdown.RecapCommand;
import com.cosmiclogic.deathbreakdown.ServerTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

import java.nio.file.Path;

@Mod(DeathBreakdown.MOD_ID)
public class DeathBreakdownForge {
    // optional(): players without the mod can still join; they get the chat summary instead.
    static final SimpleChannel CHANNEL = ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(DeathBreakdown.MOD_ID, "main"))
            .networkProtocolVersion(1)
            .optional()
            .simpleChannel();

    static {
        CHANNEL.messageBuilder(RecapMessage.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RecapMessage::encode)
                .decoder(RecapMessage::decode)
                .consumerMainThread((msg, ctx) -> ForgeClient.receive(msg.data()))
                .add();
    }

    public DeathBreakdownForge() {
        DeathBreakdown.init(new DeathBreakdown.Platform() {
            @Override public String loaderName() { return "Forge"; }
            @Override public Path configDir() { return FMLPaths.CONFIGDIR.get(); }
            @Override public boolean canReceiveRecap(ServerPlayer p) { return CHANNEL.isRemotePresent(p.connection.getConnection()); }
            @Override public void sendRecap(ServerPlayer p, String data) { CHANNEL.send(new RecapMessage(data), PacketDistributor.PLAYER.with(p)); }
        });
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, this::onHurt);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, this::onDeath);
        MinecraftForge.EVENT_BUS.addListener(this::onLogout);
        MinecraftForge.EVENT_BUS.addListener(this::onCommands);
        if (FMLEnvironment.dist == Dist.CLIENT) ForgeClient.init(modBus);
    }

    /** Forge gives the damage before armor; the parts are worked out with vanilla's formulas. */
    private void onHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) ServerTracker.recordComputed(p, e.getSource(), e.getAmount());
    }

    private void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) ServerTracker.onDeath(p, e.getSource());
    }

    private void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) ServerTracker.onLogout(p);
    }

    private void onCommands(RegisterCommandsEvent e) {
        RecapCommand.register(e.getDispatcher());
    }
}
