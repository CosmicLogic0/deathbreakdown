package com.cosmiclogic.deathbreakdown.forge;

import com.cosmiclogic.deathbreakdown.client.ClientRecap;
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

    private static void registerKey() {
        //#if MC >= 26.1
        RegisterKeyMappingsEvent.BUS.addListener(e -> e.register(ClientRecap.OPEN_KEY));
        //#elif MC >= 1.21.6
        RegisterKeyMappingsEvent.getBus(FMLJavaModLoadingContext.get().getModBusGroup()).addListener(e -> e.register(ClientRecap.OPEN_KEY));
        //#elif MC >= 1.19.1
        FMLJavaModLoadingContext.get().getModEventBus().addListener((RegisterKeyMappingsEvent e) -> e.register(ClientRecap.OPEN_KEY));
        //#else
        FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> {
            //#if MC >= 1.18
            net.minecraftforge.client.ClientRegistry.registerKeyBinding(ClientRecap.OPEN_KEY);
            //#else
            net.minecraftforge.fml.client.registry.ClientRegistry.registerKeyBinding(ClientRecap.OPEN_KEY);
            //#endif
        }));
        //#endif
    }

    static void receive(String data) {
        ClientRecap.receive(data);
    }
}
