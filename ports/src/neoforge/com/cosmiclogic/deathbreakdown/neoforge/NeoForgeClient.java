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

    static void init(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        // "Config" button in the mod list opens Death Breakdown's settings.
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (c, parent) -> new com.cosmiclogic.deathbreakdown.client.ConfigScreen(parent));
        modBus.addListener((RegisterKeyMappingsEvent e) -> { for (net.minecraft.client.KeyMapping k : ClientRecap.KEYS) e.register(k); });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderGuiEvent.Post e) ->
                com.cosmiclogic.deathbreakdown.client.ClientHud.render(e.getGuiGraphics()));
        NeoForge.EVENT_BUS.addListener((InputEvent.Key e) -> ClientRecap.onKey());
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post e) -> ClientRecap.onScreenInit(e.getScreen(), e::addListener));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> ClientRecap.clear());
        ClientRecap.serverHasMod = () -> {
            net.minecraft.client.multiplayer.ClientPacketListener c = net.minecraft.client.Minecraft.getInstance().getConnection();
            return c != null && c.hasChannel(RecapPayload.TYPE);
        };
    }

    static void receive(String data) {
        ClientRecap.receive(data);
    }
}
