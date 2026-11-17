package com.cosmiclogic.deathbreakdown.forge;

import com.cosmiclogic.deathbreakdown.client.ClientRecap;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/** Client-only hooks. Only loaded on the client, so client classes never touch a dedicated server. */
final class ForgeClient {
    private ForgeClient() {}

    static void init(IEventBus modBus) {
        ClientRecap.startup(net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get());
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(ClientRecap.OPEN_KEY));
        MinecraftForge.EVENT_BUS.addListener((InputEvent.Key e) -> ClientRecap.onKey());
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post e) -> ClientRecap.onScreenInit(e.getScreen(), e::addListener));
    }

    static void receive(String data) {
        ClientRecap.receive(data);
    }
}
