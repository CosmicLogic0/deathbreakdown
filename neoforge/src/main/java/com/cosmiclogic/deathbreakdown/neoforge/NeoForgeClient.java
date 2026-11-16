package com.cosmiclogic.deathbreakdown.neoforge;

import com.cosmiclogic.deathbreakdown.client.ClientRecap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only hooks. Only loaded on the client, so client classes never touch a dedicated server. */
final class NeoForgeClient {
    private NeoForgeClient() {}

    static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(ClientRecap.OPEN_KEY));
        NeoForge.EVENT_BUS.addListener((InputEvent.Key e) -> ClientRecap.onKey());
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post e) -> ClientRecap.onScreenInit(e.getScreen(), e::addListener));
    }

    static void receive(String data) {
        ClientRecap.receive(data);
    }
}
