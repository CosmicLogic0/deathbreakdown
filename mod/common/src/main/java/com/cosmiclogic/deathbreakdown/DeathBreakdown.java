package com.cosmiclogic.deathbreakdown;

import com.cosmiclogic.deathbreakdown.core.HitLog;
import com.cosmiclogic.deathbreakdown.core.RecapConfig;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/** Shared entry point. Each loader calls {@link #init} from its mod constructor. */
public final class DeathBreakdown {
    public static final String MOD_ID = "deathbreakdown";
    public static final String NAME = "Death Breakdown";
    public static final Logger LOG = LoggerFactory.getLogger(NAME);

    /** The few things that differ per loader: config folder and the one network packet. */
    public interface Platform {
        String loaderName();
        Path configDir();
        /** True when this player's client has Death Breakdown installed (so it can show the recap screen). */
        boolean canReceiveRecap(ServerPlayer player);
        void sendRecap(ServerPlayer player, String data);
    }

    public static Platform platform;
    public static volatile RecapConfig config = new RecapConfig();
    public static final HitLog HITS = new HitLog(10_000L);

    private DeathBreakdown() {}

    public static void init(Platform p) {
        platform = p;
        reloadConfig();
        LOG.info("{} loaded on {}", NAME, p.loaderName());
    }

    public static void reloadConfig() {
        config = RecapConfig.load(platform.configDir().resolve(MOD_ID + ".properties").toFile());
        HITS.setWindowMs(config.windowMs());
    }
}
