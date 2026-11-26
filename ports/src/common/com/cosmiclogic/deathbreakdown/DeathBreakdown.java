package com.cosmiclogic.deathbreakdown;

import com.cosmiclogic.deathbreakdown.core.HitLog;
import com.cosmiclogic.deathbreakdown.core.RecapConfig;
import net.minecraft.server.level.ServerPlayer;
//#if MC >= 1.18
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//#else
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//#endif

import java.nio.file.Path;

/** Shared entry point. Each loader calls {@link #init} from its mod constructor. */
public final class DeathBreakdown {
    public static final String MOD_ID = "deathbreakdown";
    public static final String NAME = "Death Breakdown";
    //#if MC >= 1.18
    public static final Logger LOG = LoggerFactory.getLogger(NAME);
    //#else
    public static final Logger LOG = LogManager.getLogger(NAME);
    //#endif

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
        config = RecapConfig.load(configFile());
        HITS.setWindowMs(config.windowMs());
        HITS.setHistorySize(config.historySize);
        for (String problem : config.problems) LOG.warn("deathbreakdown.properties: {}", problem);
    }

    /** Uses new settings right away and saves them (settings screen, the HUD/marker keys). */
    public static void applyConfig(RecapConfig c) {
        config = c;
        HITS.setWindowMs(c.windowMs());
        HITS.setHistorySize(c.historySize);
        try {
            c.save(configFile());
        } catch (java.io.IOException e) {
            LOG.warn("Couldn't save {}: {}", configFile(), e.toString());
        }
    }

    public static java.io.File configFile() {
        return platform.configDir().resolve(MOD_ID + ".properties").toFile();
    }
}
