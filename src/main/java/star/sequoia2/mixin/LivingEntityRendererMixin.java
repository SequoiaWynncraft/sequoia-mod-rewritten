package star.sequoia2.mixin;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import star.sequoia2.accessors.FeaturesAccessor;

import java.util.List;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends EntityRenderer<T, S>
        implements FeatureRendererContext<S, M>, FeaturesAccessor {

    @Shadow
    @Final
    protected List<FeatureRenderer<S, M>> features;

    @Unique
    private LivingEntity mainLivingEntityThing;

    protected LivingEntityRendererMixin(EntityRendererFactory.Context context) {
        super(context);
    }

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void seq$captureLivingEntity(T livingEntity, S livingEntityRenderState, float tickDelta, CallbackInfo ci) {
        this.mainLivingEntityThing = livingEntity;
    }

    // NOTE:
    // The old render-injection that used:
    //   render(LivingEntityRenderState, MatrixStack, VertexConsumerProvider, int)
    // no longer exists in 1.21.
    // It has been replaced by:
    //   render(LivingEntityRenderState, MatrixStack, OrderedRenderCommandQueue, CameraRenderState)
    // Re-hooking custom bar rendering needs to be done using the new OrderedRenderCommandQueue
    // API instead of VertexConsumerProvider/Tessellator. For now, the mixin only captures
    // the current LivingEntity via updateRenderState so the project compiles cleanly.

    // gpt wrote this ^^  bum ass class
}
