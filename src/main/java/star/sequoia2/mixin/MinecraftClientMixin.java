package star.sequoia2.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import star.sequoia2.accessors.EventBusAccessor;
import star.sequoia2.events.MinecraftFinishedLoading;
import star.sequoia2.events.ScreenOpenedEvent;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin implements EventBusAccessor {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        dispatch(new MinecraftFinishedLoading());
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void onSetScreen(net.minecraft.client.gui.screen.Screen screen, CallbackInfo ci) {
        dispatch(new ScreenOpenedEvent(screen));
    }
}
