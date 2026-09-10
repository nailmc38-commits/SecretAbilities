package com.secret.abilities.mixin;

import com.secret.abilities.ModState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "canWalkOnFluid", at = @At("HEAD"), cancellable = true)
    private void secretabilities$walkOnWater(FluidState state, CallbackInfoReturnable<Boolean> cir) {
        if (!ModState.walkOnWater || ModState.localPlayerUuid == null) {
            return;
        }

        Object self = this;
        if (self instanceof PlayerEntity player
                && player.getUuid().equals(ModState.localPlayerUuid)
                && state.isIn(FluidTags.WATER)) {
            cir.setReturnValue(true);
        }
    }
}
