package star.sequoia2.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ReloadableResourceManagerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import star.sequoia2.accessors.EventBusAccessor;
import star.sequoia2.events.EventStage;
import star.sequoia2.events.MinecraftFinishedLoading;
import star.sequoia2.events.ScreenOpenedEvent;
import star.sequoia2.events.TickEvent;
import star.sequoia2.utils.render.Pipelines;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin implements EventBusAccessor {

    @Shadow
    @Final
    private ReloadableResourceManagerImpl resourceManager;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/resource/ReloadableResourceManagerImpl;reload(Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Ljava/util/List;)Lnet/minecraft/resource/ResourceReload;", shift = At.Shift.BEFORE))
    private void init$beforeReload(CallbackInfo info) {
        resourceManager.registerReloader(new Pipelines.Reloader());
    }

    @Inject(at = @At("HEAD"), method = "tick")
    private void onPreTick(CallbackInfo info) {
        dispatch(new TickEvent(EventStage.PRE));
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        dispatch(new MinecraftFinishedLoading());
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void onSetScreen(net.minecraft.client.gui.screen.Screen screen, CallbackInfo ci) {
        dispatch(new ScreenOpenedEvent(screen));
    }
}
