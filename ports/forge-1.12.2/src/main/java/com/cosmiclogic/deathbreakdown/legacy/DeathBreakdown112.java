package com.cosmiclogic.deathbreakdown.legacy;

import com.cosmiclogic.deathbreakdown.core.HitLog;
import com.cosmiclogic.deathbreakdown.core.RecapConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * Death Breakdown for Forge 1.12.2. Players without the mod can join (acceptableRemoteVersions = "*"):
 * they get the chat recap; players with it get the recap screen.
 */
@Mod(modid = DeathBreakdown112.MOD_ID, name = DeathBreakdown112.NAME, version = "1.0.0",
        acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[1.12.2]")
public class DeathBreakdown112 {
    public static final String MOD_ID = "deathbreakdown";
    public static final String NAME = "Death Breakdown";
    public static final Logger LOG = LogManager.getLogger(NAME);
    public static final HitLog HITS = new HitLog(10_000L);
    public static volatile RecapConfig config = new RecapConfig();
    static SimpleNetworkWrapper NET;
    private static File configFile;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        configFile = new File(e.getModConfigurationDirectory(), MOD_ID + ".properties");
        reloadConfig();
        NET = NetworkRegistry.INSTANCE.newSimpleChannel(MOD_ID);
        NET.registerMessage(RecapMessage.Handler.class, RecapMessage.class, 0, Side.CLIENT);
        MinecraftForge.EVENT_BUS.register(new ServerEvents112());
        if (e.getSide().isClient()) ClientRecap112.preInit();
        LOG.info("{} loaded on Forge", NAME);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        if (e.getSide().isClient()) ClientRecap112.init();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent e) {
        e.registerServerCommand(new RecapCommand112());
    }

    static void reloadConfig() {
        config = RecapConfig.load(configFile);
        HITS.setWindowMs(config.windowMs());
    }

    /** True when this player's client has Death Breakdown (so it can show the recap screen). */
    static boolean hasMod(EntityPlayerMP p) {
        try {
            NetworkDispatcher d = NetworkDispatcher.get(p.connection.getNetworkManager());
            return d != null && d.getModList() != null && d.getModList().containsKey(MOD_ID);
        } catch (Throwable t) {
            return false;
        }
    }

    static void sendRecap(EntityPlayerMP p, String data) {
        NET.sendTo(new RecapMessage(data), p);
    }

    /** The whole recap as one string (see RecapCodec). */
    public static class RecapMessage implements IMessage {
        String data = "";

        public RecapMessage() {}

        RecapMessage(String data) { this.data = data; }

        @Override public void fromBytes(ByteBuf buf) { data = ByteBufUtils.readUTF8String(buf); }

        @Override public void toBytes(ByteBuf buf) { ByteBufUtils.writeUTF8String(buf, data); }

        public static class Handler implements IMessageHandler<RecapMessage, IMessage> {
            @Override
            public IMessage onMessage(RecapMessage msg, MessageContext ctx) {
                ClientRecap112.schedule(msg.data);
                return null;
            }
        }
    }
}
