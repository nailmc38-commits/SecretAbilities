package com.secret.abilities.mixin;

import com.secret.abilities.ModState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void voidclient$esp(CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null) return;

        if (ModState.playerEsp
                && self instanceof PlayerEntity player
                && !player.getUuid().equals(client.player.getUuid())) {
            cir.setReturnValue(true);
            return;
        }

        if (ModState.mobEsp
                && self instanceof LivingEntity
                && !(self instanceof PlayerEntity)) {
            cir.setReturnValue(true);
        }
    }
}
