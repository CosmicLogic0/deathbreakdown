package com.cosmiclogic.dbshots;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** CI only. Turned on by config/dbshots.txt, which only the gallery screenshot jobs write. */
@Mod(value = "dbshots", dist = Dist.CLIENT)
public final class DbShots {
    public DbShots(IEventBus modBus) {
        if (!Shots.init(FMLPaths.CONFIGDIR.get())) return;
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Opening e) -> Shots.onScreenOpening(e));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> Shots.tick());
    }
}
