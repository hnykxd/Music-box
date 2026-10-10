package dev.musicbox;

import dev.musicbox.network.*;
import java.io.IOException;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MusicBox implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("musicbox");
    private static final Map<MinecraftServer, SettingsStore> STORES = new IdentityHashMap<>();
    private static final Map<UUID, EditSession> SESSIONS = new HashMap<>();
    private record EditSession(ServerLevel level, BlockPos pos, long openedAt, long lastPreview) {}

    @Override public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(OpenPayload.TYPE, OpenPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SavedPayload.TYPE, SavedPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SettingsPayload.TYPE, SettingsPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SettingsPayload.TYPE,
            (payload, context) -> handle(context.player(), payload));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> store(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(MusicBox::flush);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { STORES.remove(server); SESSIONS.clear(); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 100 == 0) {
                flush(server);
                long now = System.nanoTime();
                SESSIONS.values().removeIf(s -> now - s.openedAt > 600_000_000_000L);
            }
        });
        LOGGER.info("Music Box 1.1.0 initialized: all vanilla instruments and pitches + 8 extra instruments");
    }

    private static SettingsStore store(MinecraftServer server) {
        return STORES.computeIfAbsent(server, s -> new SettingsStore(s.getWorldPath(LevelResource.ROOT).resolve("data/musicbox.json")));
    }
    private static String dimension(ServerLevel level) { return level.dimension().identifier().toString(); }
    public static NoteSettings settings(ServerLevel level, BlockPos pos) {
        return store(level.getServer()).get(dimension(level), pos.asLong());
    }
    public static void removed(ServerLevel level, BlockPos pos) {
        SettingsStore store = STORES.get(level.getServer());
        if (store != null) store.remove(dimension(level), pos.asLong());
        SESSIONS.values().removeIf(s -> s.level == level && s.pos.equals(pos));
    }
    private static void flush(MinecraftServer server) {
        SettingsStore data = STORES.get(server);
        if (data == null) return;
        try { data.flush(); } catch (IOException e) { LOGGER.error("Cannot save music box settings", e); }
    }
    private static boolean mayEdit(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        return player.isAlive() && !player.isSpectator() && player.mayBuild()
            && player.isWithinBlockInteractionRange(pos, 1.0) && level.hasChunkAt(pos)
            && level.getBlockState(pos).is(Blocks.NOTE_BLOCK)
            && level.mayInteract(player, pos) && player.mayInteract(level, pos);
    }
    public static void open(ServerPlayer player, BlockPos pos) {
        if (!mayEdit(player, pos)) return;
        if (!ServerPlayNetworking.canSend(player, OpenPayload.TYPE)) {
            player.sendSystemMessage(Component.literal("请在客户端安装 Music Box 音符盒模组后重新连接。"));
            return;
        }
        NoteSettings settings = settings(player.level(), pos);
        if (settings == null || settings.voice().isAutomatic()) {
            settings = new NoteSettings(Instrument.VANILLA_AUTO.ordinal(),
                54 + player.level().getBlockState(pos).getValue(NoteBlock.NOTE));
        }
        SESSIONS.put(player.getUUID(), new EditSession(player.level(), pos.immutable(), System.nanoTime(), 0));
        ServerPlayNetworking.send(player, new OpenPayload(pos, settings.instrument(), settings.note()));
    }
    private static void handle(ServerPlayer player, SettingsPayload payload) {
        EditSession session = SESSIONS.get(player.getUUID());
        long now = System.nanoTime();
        if (session == null || session.level != player.level() || !session.pos.equals(payload.pos())
            || now - session.openedAt > 600_000_000_000L || !mayEdit(player, payload.pos())
            || !Instrument.valid(payload.instrument())
            || payload.note() < Instrument.values()[payload.instrument()].minNote()
            || payload.note() > Instrument.values()[payload.instrument()].maxNote()) {
            if (!payload.preview()) reject(player, payload.pos(), "message.musicbox.invalid");
            return;
        }
        NoteSettings settings = new NoteSettings(payload.instrument(), payload.note());
        if (payload.preview()) {
            if (now - session.lastPreview < 100_000_000L) return;
            SESSIONS.put(player.getUUID(), new EditSession(session.level, session.pos, session.openedAt, now));
            // Preview is private and never changes the stored block settings.
            SoundEvent sound = sound(player.level(), payload.pos(), settings);
            if (sound == null) return;
            player.connection.send(new ClientboundSoundPacket(Holder.direct(sound), SoundSource.RECORDS,
                payload.pos().getX() + .5, payload.pos().getY() + .5, payload.pos().getZ() + .5,
                1.0f, pitch(player.level(), payload.pos(), settings), player.level().getRandom().nextLong()));
            return;
        }
        try {
            store(player.level().getServer()).put(dimension(player.level()), payload.pos().asLong(), settings);
            if (settings.voice().isVanilla()) {
                var state = player.level().getBlockState(payload.pos());
                player.level().setBlock(payload.pos(), state.setValue(NoteBlock.NOTE, settings.note() - 54), 3);
            }
            SESSIONS.remove(player.getUUID());
            ServerPlayNetworking.send(player, new SavedPayload(payload.pos(), true));
        } catch (IOException e) {
            LOGGER.error("Cannot save note block at {}", payload.pos(), e);
            reject(player, payload.pos(), "message.musicbox.save_failed");
        }
    }
    private static void reject(ServerPlayer player, BlockPos pos, String message) {
        if (ServerPlayNetworking.canSend(player, SavedPayload.TYPE)) ServerPlayNetworking.send(player, new SavedPayload(pos, false));
        player.sendSystemMessage(Component.translatable(message));
    }
    public static void play(ServerLevel level, BlockPos pos, NoteSettings settings) {
        SoundEvent sound = sound(level, pos, settings);
        if (sound == null) return;
        level.playSound(null, pos, sound, SoundSource.RECORDS, 3.0f, pitch(level, pos, settings));
        level.sendParticles(ParticleTypes.NOTE, pos.getX() + .5, pos.getY() + 1.2, pos.getZ() + .5,
            0, (settings.note() - settings.voice().minNote()) / 24.0, 0, 0, 1);
    }
    private static NoteBlockInstrument vanillaVoice(ServerLevel level, BlockPos pos, NoteSettings settings) {
        return settings.voice().isAutomatic() ? level.getBlockState(pos).getValue(NoteBlock.INSTRUMENT)
            : settings.voice().vanillaInstrument();
    }
    private static SoundEvent sound(ServerLevel level, BlockPos pos, NoteSettings settings) {
        if (!settings.voice().isVanilla()) return settings.voice().sound();
        NoteBlockInstrument voice = vanillaVoice(level, pos, settings);
        if (voice.hasCustomSound()) {
            if (level.getBlockEntity(pos.above()) instanceof SkullBlockEntity skull && skull.getNoteBlockSound() != null)
                return SoundEvent.createVariableRangeEvent(skull.getNoteBlockSound());
            return null;
        }
        return voice.getSoundEvent().value();
    }
    private static float pitch(ServerLevel level, BlockPos pos, NoteSettings settings) {
        if (settings.voice().isVanilla() && !vanillaVoice(level, pos, settings).isTunable()) return 1.0f;
        return settings.pitch();
    }
}
