package dev.musicbox.mixin;

import dev.musicbox.MusicBox;
import dev.musicbox.NoteSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoteBlock.class)
public abstract class NoteBlockMixin {
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void musicbox$openEmpty(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (player.isShiftKeyDown()) return;
        if (player instanceof ServerPlayer serverPlayer) MusicBox.open(serverPlayer, pos);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void musicbox$openItem(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (player.isShiftKeyDown()) return;
        if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) MusicBox.open(serverPlayer, pos);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
    @Inject(method = "playNote", at = @At("HEAD"), cancellable = true)
    private void musicbox$play(Entity entity, BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        NoteSettings settings = MusicBox.settings(serverLevel, pos);
        if (settings == null || settings.voice().isAutomatic()) return;
        if (level.getBlockState(pos.above()).isAir()
                || (settings.voice().isVanilla() && settings.voice().vanillaInstrument().worksAboveNoteBlock())) {
            MusicBox.play(serverLevel, pos, settings);
            level.gameEvent(entity, GameEvent.NOTE_BLOCK_PLAY, pos);
        }
        ci.cancel();
    }
}
