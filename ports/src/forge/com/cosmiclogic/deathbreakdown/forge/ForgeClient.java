package com.cosmiclogic.deathbreakdown.forge;

import com.cosmiclogic.deathbreakdown.client.ClientRecap;
import com.cosmiclogic.deathbreakdown.client.ClientHud;
import com.cosmiclogic.deathbreakdown.client.ConfigScreen;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//#if MC < 1.21.6
import net.minecraftforge.common.MinecraftForge;
//#endif
//#if MC >= 1.19.1
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
//#else
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//#endif
//#if MC >= 1.19
import net.minecraftforge.client.event.ScreenEvent;
//#elif MC >= 1.18
import net.minecraftforge.client.event.ScreenEvent;
//#else
import net.minecraftforge.client.event.GuiScreenEvent;
//#endif

/** Client-only hooks. Only loaded on the client, so client classes never touch a dedicated server. */
final class ForgeClient {
    private ForgeClient() {}

    static void init() {
        registerKey();
        registerConfigScreen();
        registerHud();
        //#if MC >= 1.20.2
        ClientRecap.serverHasMod = () -> {
            net.minecraft.client.multiplayer.ClientPacketListener c = net.minecraft.client.Minecraft.getInstance().getConnection();
            return c != null && DeathBreakdownForge.CHANNEL.isRemotePresent(c.getConnection());
        };
        //#else
        ClientRecap.serverHasMod = () -> {
            net.minecraft.client.multiplayer.ClientPacketListener c = net.minecraft.client.Minecraft.getInstance().getConnection();
            return c != null && DeathBreakdownForge.CHANNEL.isRemotePresent(c.getConnection());
        };
        //#endif
        //#if MC >= 1.21.6
        net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(e -> ClientRecap.clear());
        //#elif MC >= 1.19
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> ClientRecap.clear());
        //#else
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggedOutEvent e) -> ClientRecap.clear());
        //#endif
        //#if MC >= 1.21.6
        InputEvent.Key.BUS.addListener(e -> ClientRecap.onKey());
        ScreenEvent.Init.Post.BUS.addListener(e -> ClientRecap.onScreenInit(e.getScreen(), e::addListener));
        //#elif MC >= 1.19
        MinecraftForge.EVENT_BUS.addListener((InputEvent.Key e) -> ClientRecap.onKey());
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post e) -> ClientRecap.onScreenInit(e.getScreen(), e::addListener));
        //#elif MC >= 1.18
        MinecraftForge.EVENT_BUS.addListener((InputEvent.KeyInputEvent e) -> ClientRecap.onKey());
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.InitScreenEvent.Post e) -> ClientRecap.onScreenInit(e.getScreen(), e::addListener));
        //#else
        MinecraftForge.EVENT_BUS.addListener((InputEvent.KeyInputEvent e) -> ClientRecap.onKey());
        MinecraftForge.EVENT_BUS.addListener((GuiScreenEvent.InitGuiEvent.Post e) -> ClientRecap.onScreenInit(e.getGui(), e::addWidget));
        //#endif
    }

    /** "Config" button in the mod list opens Death Breakdown's settings. */
    private static void registerConfigScreen() {
        //#if MC >= 1.19
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new ConfigScreen(parent)));
        //#elif MC >= 1.18
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(net.minecraftforge.client.ConfigGuiHandler.ConfigGuiFactory.class,
                () -> new net.minecraftforge.client.ConfigGuiHandler.ConfigGuiFactory((mc, parent) -> new ConfigScreen(parent)));
        //#else
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(net.minecraftforge.fml.ExtensionPoint.CONFIGGUIFACTORY,
                () -> (mc, parent) -> new ConfigScreen(parent));
        //#endif
    }

    private static void registerKey() {
        //#if MC >= 26.1
        RegisterKeyMappingsEvent.BUS.addListener(e -> { for (net.minecraft.client.KeyMapping k : ClientRecap.KEYS) e.register(k); });
        //#elif MC >= 1.21.6
        RegisterKeyMappingsEvent.getBus(FMLJavaModLoadingContext.get().getModBusGroup()).addListener(e -> { for (net.minecraft.client.KeyMapping k : ClientRecap.KEYS) e.register(k); });
        //#elif MC >= 1.19.1
        FMLJavaModLoadingContext.get().getModEventBus().addListener((RegisterKeyMappingsEvent e) -> { for (net.minecraft.client.KeyMapping k : ClientRecap.KEYS) e.register(k); });
        //#else
        FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> {
            for (net.minecraft.client.KeyMapping k : ClientRecap.KEYS) {
                //#if MC >= 1.18
                net.minecraftforge.client.ClientRegistry.registerKeyBinding(k);
                //#else
                net.minecraftforge.fml.client.registry.ClientRegistry.registerKeyBinding(k);
                //#endif
            }
        }));
        //#endif
    }

    /** The death HUD is drawn after the rest of the in-game HUD. */
    private static void registerHud() {
        //#if MC >= 1.21.6
        net.minecraftforge.client.event.RenderGuiEvent.Post.BUS.addListener(e -> ClientHud.render(e.getGuiGraphics()));
        //#elif MC >= 1.20
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderGuiEvent.Post e) -> ClientHud.render(e.getGuiGraphics()));
        //#elif MC >= 1.19
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderGuiEvent.Post e) -> ClientHud.render(e.getPoseStack()));
        //#else
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderGameOverlayEvent.Post e) -> {
            if (e.getType() == net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL) ClientHud.render(e.getMatrixStack());
        });
        //#endif
    }

    static void receive(String data) {
        ClientRecap.receive(data);
    }
}
