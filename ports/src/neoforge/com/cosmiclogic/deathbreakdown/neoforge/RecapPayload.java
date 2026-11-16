package com.cosmiclogic.deathbreakdown.neoforge;

import com.cosmiclogic.deathbreakdown.DeathBreakdown;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#if MC >= 1.21.11
import net.minecraft.resources.Identifier;
//#else
import net.minecraft.resources.ResourceLocation;
//#endif

/** The whole recap as one string (see RecapCodec). */
public record RecapPayload(String data) implements CustomPacketPayload {
    //#if MC >= 1.21.11
    public static final Type<RecapPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(DeathBreakdown.MOD_ID, "recap"));
    //#else
    public static final Type<RecapPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DeathBreakdown.MOD_ID, "recap"));
    //#endif
    public static final StreamCodec<ByteBuf, RecapPayload> CODEC = ByteBufCodecs.stringUtf8(32767).map(RecapPayload::new, RecapPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
