package com.secret.abilities.mixin;

import com.secret.abilities.ModState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void secretabilities$playerEsp(CallbackInfoReturnable<Boolean> cir) {
        if (!ModState.playerEsp) {
            return;
        }

        Object self = this;
        MinecraftClient client = MinecraftClient.getInstance();

        if (self instanceof PlayerEntity player
                && client.player != null
                && !player.getUuid().equals(client.player.getUuid())) {
            cir.setReturnValue(true);
        }
    }
}
