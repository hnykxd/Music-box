package dev.musicbox.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SavedPayload(BlockPos pos, boolean success) implements CustomPacketPayload {
    public static final Type<SavedPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("musicbox", "saved_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SavedPayload> CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeBlockPos(p.pos); buf.writeBoolean(p.success); },
        buf -> new SavedPayload(buf.readBlockPos(), buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
