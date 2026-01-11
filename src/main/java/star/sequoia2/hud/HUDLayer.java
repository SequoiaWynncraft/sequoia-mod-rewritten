package star.sequoia2.hud;

import com.collarmc.pounce.Subscribe;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import star.sequoia2.accessors.EventBusAccessor;
import star.sequoia2.accessors.HudElementsAccessor;
import star.sequoia2.events.Render2DEvent;
import star.sequoia2.gui.component.InteractableComponent;

import static star.sequoia2.client.SeqClient.mc;

public class HUDLayer extends InteractableComponent implements EventBusAccessor, HudElementsAccessor {
    private boolean initialized = false;

    private boolean isEditing;

    public HUDLayer() {
    }

    public void setEditing(boolean editing) {
        isEditing = editing;
        hudElements().all().forEach(element -> element.setEditing(isEditing));
    }

    public void init() {
        hudElements().all().forEach(element -> {
            subscribe(element);
            element.init();
        });
    }

    @Override
    public void render(DrawContext context, float mouseX, float mouseY, float delta) {
        if (!initialized) {
            initialized = true;
            init();
        }
        hudElements().all().forEach(element -> element.render(context, mouseX, mouseY, delta));
    }

    @Override
    public void mouseClicked(float mouseX, float mouseY, int button) {
        hudElements().all().forEach(element -> element.mouseClicked(mouseX, mouseY, button));
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        hudElements().all().forEach(element -> element.mouseReleased(mouseX, mouseY, button));
    }

    @Override
    public void mouseScrolled(float mouseX, float mouseY, double horizontalAmount, double verticalAmount) {

    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        hudElements().all().forEach(element -> element.keyPressed(keyCode, scanCode, modifiers));
    }

    @Override
    public void keyReleased(int keyCode, int scanCode, int modifiers){
        hudElements().all().forEach(element -> element.keyReleased(keyCode, scanCode, modifiers));
    }


    // these are for symmetry #thosewhoknow (need to have these if we wana keep interactable abstract)
    @Override
    public void mouseMoved(float mouseX, float mouseY) {}

    @Override
    public void charTyped(char chr, int modifiers) {}

    @Subscribe
    public void onRender(Render2DEvent event) {
        if (mc.currentScreen instanceof InventoryScreen
                || mc.currentScreen instanceof ChatScreen
                || mc.currentScreen == null) {
            render(event.context(), 0, 0, 0);
        }
    }
}
