package com.cosmiclogic.deathbreakdown.forge;

import net.minecraft.network.FriendlyByteBuf;

/** The whole recap as one string (see RecapCodec). */
public final class RecapMessage {
    public final String data;

    public RecapMessage(String data) {
        this.data = data;
    }

    public static void encode(RecapMessage m, FriendlyByteBuf buf) {
        buf.writeUtf(m.data, 32767);
    }

    public static RecapMessage decode(FriendlyByteBuf buf) {
        return new RecapMessage(buf.readUtf(32767));
    }
}
