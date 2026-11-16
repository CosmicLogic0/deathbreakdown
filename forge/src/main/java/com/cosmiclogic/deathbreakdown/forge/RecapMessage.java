package com.cosmiclogic.deathbreakdown.forge;

import net.minecraft.network.FriendlyByteBuf;

/** The whole recap as one string (see RecapCodec). */
public record RecapMessage(String data) {
    public static void encode(RecapMessage m, FriendlyByteBuf buf) {
        buf.writeUtf(m.data, 32767);
    }

    public static RecapMessage decode(FriendlyByteBuf buf) {
        return new RecapMessage(buf.readUtf(32767));
    }
}
