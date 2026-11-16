package com.cosmiclogic.deathbreakdown.forge;

import com.cosmiclogic.deathbreakdown.DeathBreakdown;
import com.cosmiclogic.deathbreakdown.RecapCommand;
import com.cosmiclogic.deathbreakdown.ServerTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
//#if MC < 1.21.6
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
//#endif
//#if MC >= 1.21.11
import net.minecraft.resources.Identifier;
//#else
import net.minecraft.resources.ResourceLocation;
//#endif
//#if MC >= 1.20.2
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;
//#elif MC >= 1.18
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
//#else
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;
//#endif

import java.nio.file.Path;

@Mod(DeathBreakdown.MOD_ID)
public class DeathBreakdownForge {
    // Players without the mod can still join; they get the chat summary instead.
    //#if MC >= 1.21.11
    static final SimpleChannel CHANNEL = ChannelBuilder.named(Identifier.fromNamespaceAndPath(DeathBreakdown.MOD_ID, "main"))
            .networkProtocolVersion(1).optional().simpleChannel();
    //#elif MC >= 1.21
    static final SimpleChannel CHANNEL = ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(DeathBreakdown.MOD_ID, "main"))
            .networkProtocolVersion(1).optional().simpleChannel();
    //#elif MC >= 1.20.2
    static final SimpleChannel CHANNEL = ChannelBuilder.named(new ResourceLocation(DeathBreakdown.MOD_ID, "main"))
            .networkProtocolVersion(1).optional().simpleChannel();
    //#else
    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(DeathBreakdown.MOD_ID, "main"),
            () -> "1", v -> true, v -> true);
    //#endif

    static {
        //#if MC >= 1.20.2
        CHANNEL.messageBuilder(RecapMessage.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RecapMessage::encode)
                .decoder(RecapMessage::decode)
                .consumerMainThread((msg, ctx) -> ForgeClient.receive(msg.data))
                .add();
        //#else
        CHANNEL.registerMessage(0, RecapMessage.class, RecapMessage::encode, RecapMessage::decode, (msg, ctx) -> {
            ctx.get().enqueueWork(() -> ForgeClient.receive(msg.data));
            ctx.get().setPacketHandled(true);
        });
        //#endif
    }

    public DeathBreakdownForge() {
        DeathBreakdown.init(new DeathBreakdown.Platform() {
            @Override public String loaderName() { return "Forge"; }
            @Override public Path configDir() { return FMLPaths.CONFIGDIR.get(); }
            @Override public boolean canReceiveRecap(ServerPlayer p) {
                //#if MC >= 1.20.2
                return CHANNEL.isRemotePresent(p.connection.getConnection());
                //#else
                return CHANNEL.isRemotePresent(p.connection.connection);
                //#endif
            }
            @Override public void sendRecap(ServerPlayer p, String data) {
                //#if MC >= 1.20.2
                CHANNEL.send(new RecapMessage(data), PacketDistributor.PLAYER.with(p));
                //#else
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new RecapMessage(data));
                //#endif
            }
        });
        //#if MC >= 1.21.6
        LivingHurtEvent.BUS.addListener(this::onHurt);
        LivingDeathEvent.BUS.addListener(this::onDeath);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(this::onLogout);
        RegisterCommandsEvent.BUS.addListener(this::onCommands);
        //#else
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, this::onHurt);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, this::onDeath);
        MinecraftForge.EVENT_BUS.addListener(this::onLogout);
        MinecraftForge.EVENT_BUS.addListener(this::onCommands);
        //#endif
        if (FMLEnvironment.dist == Dist.CLIENT) ForgeClient.init();
    }

    private static Entity living(LivingHurtEvent e) {
        //#if MC >= 1.19
        return e.getEntity();
        //#else
        return e.getEntityLiving();
        //#endif
    }

    /** Forge gives the damage before armor; the parts are worked out with vanilla's formulas. */
    private void onHurt(LivingHurtEvent e) {
        Entity en = living(e);
        if (en instanceof ServerPlayer) ServerTracker.recordComputed((ServerPlayer) en, e.getSource(), e.getAmount());
    }

    private void onDeath(LivingDeathEvent e) {
        //#if MC >= 1.19
        Entity en = e.getEntity();
        //#else
        Entity en = e.getEntityLiving();
        //#endif
        if (en instanceof ServerPlayer) ServerTracker.onDeath((ServerPlayer) en, e.getSource());
    }

    private void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        //#if MC >= 1.19
        Entity en = e.getEntity();
        //#else
        Entity en = e.getPlayer();
        //#endif
        if (en instanceof ServerPlayer) ServerTracker.onLogout((ServerPlayer) en);
    }

    private void onCommands(RegisterCommandsEvent e) {
        RecapCommand.register(e.getDispatcher());
    }
}
