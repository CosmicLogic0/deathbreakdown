package com.cosmiclogic.deathbreakdown.neoforge;

import com.cosmiclogic.deathbreakdown.DeathBreakdown;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The whole recap as one string (see RecapCodec). */
public record RecapPayload(String data) implements CustomPacketPayload {
    public static final Type<RecapPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DeathBreakdown.MOD_ID, "recap"));
    public static final StreamCodec<ByteBuf, RecapPayload> CODEC = ByteBufCodecs.stringUtf8(32767).map(RecapPayload::new, RecapPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
