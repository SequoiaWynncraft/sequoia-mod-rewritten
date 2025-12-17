package star.sequoia2.events.input;

import com.collarmc.pounce.Cancelable;
import com.collarmc.pounce.EventInfo;
import com.collarmc.pounce.Preference;
import org.lwjgl.glfw.GLFW;

@EventInfo(preference = Preference.CALLER)
public record KeyEvent(long window, int key, int scancode, int action, int modifiers) implements Cancelable {
    public boolean isKeyDown() {
        return action == GLFW.GLFW_REPEAT;
    }
}
