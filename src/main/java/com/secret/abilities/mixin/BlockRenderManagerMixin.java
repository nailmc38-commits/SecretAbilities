package com.secret.abilities.mixin;

import com.secret.abilities.ModState;
import com.secret.abilities.XrayBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BlockRenderManager.class)
public abstract class BlockRenderManagerMixin {
    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void secretabilities$xrayBlocks(
            BlockState state,
            BlockPos pos,
            BlockRenderView world,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            List<BlockModelPart> parts,
            CallbackInfo ci
    ) {
        if (ModState.xray && !XrayBlocks.shouldRender(state)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFluid", at = @At("HEAD"), cancellable = true)
    private void secretabilities$xrayFluids(
            BlockPos pos,
            BlockRenderView world,
            VertexConsumer vertexConsumer,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        if (ModState.xray) {
            ci.cancel();
        }
    }
}
