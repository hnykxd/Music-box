package dev.musicbox.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenPayload(BlockPos pos, int instrument, int note) implements CustomPacketPayload {
    public static final Type<OpenPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("musicbox", "open_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeBlockPos(p.pos); buf.writeVarInt(p.instrument); buf.writeVarInt(p.note); },
        buf -> new OpenPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
