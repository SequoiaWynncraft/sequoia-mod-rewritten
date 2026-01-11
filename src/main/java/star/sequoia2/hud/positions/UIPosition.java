package star.sequoia2.hud.positions;

import net.minecraft.client.util.Window;

public record UIPosition(float x, float y) {

//    Dividing here resulted in division by 0 shit which made it infinite so removing the relative stuff works.
    public static UIPosition relativeToWindow(float x, float y, Window ignoredWindow) {
        return new UIPosition(x, y);
    }

    public UIPosition restore(Window ignoredWindow) {
        return new UIPosition( x, y );
    }
}
