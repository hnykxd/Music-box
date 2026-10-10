package dev.musicbox.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SettingsPayload(BlockPos pos, int instrument, int note, boolean preview) implements CustomPacketPayload {
    public static final Type<SettingsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("musicbox", "settings_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SettingsPayload> CODEC = StreamCodec.of(
        (buf, p) -> { buf.writeBlockPos(p.pos); buf.writeVarInt(p.instrument); buf.writeVarInt(p.note); buf.writeBoolean(p.preview); },
        buf -> new SettingsPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
