package star.sequoia2.gui.screen;

import lombok.Setter;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.accessors.HudElementsAccessor;
import star.sequoia2.accessors.RenderUtilAccessor;
import star.sequoia2.features.impl.Settings;
import star.sequoia2.hud.HudEditorFrame;
import star.sequoia2.hud.positions.PositionKey;
import star.sequoia2.hud.positions.UIPosition;

import java.io.IOException;

import static star.sequoia2.client.SeqClient.mc;

public class HUDEditorScreen extends Screen implements FeaturesAccessor, RenderUtilAccessor, HudElementsAccessor {

    public static int MOUSE_X;
    public static int MOUSE_Y;
    public static boolean MOUSE_RIGHT_CLICK;
    public static boolean MOUSE_RIGHT_HOLD;
    public static boolean MOUSE_LEFT_CLICK;
    public static boolean MOUSE_LEFT_HOLD;

    @Setter
    private boolean closeOnEscape = true;

    private final Settings settings;
    private final HudEditorFrame frame;
    private HudEditorFrame focus;
    private float openScale = 0f;

    public HUDEditorScreen() {
        super(Text.literal("HUD Editor"));
        this.settings = features().get(Settings.class).orElse(null);

        UIPosition position = star.sequoia2.client.SeqClient.getUiPositions()
                .get(PositionKey.fromHudUI())
                .orElse(new UIPosition(2.0f, 15.0f));
        this.frame = new HudEditorFrame(hudElements(), position.x(), position.y());
    }

    @Override
    protected void init() {
        super.init();
        openScale = 0f;
        if (settings != null) {
            settings.hudLayer.setEditing(true);
        }
    }

    private double[] getScaledMouse(double mouseX, double mouseY) {
        float base = 2.0f;
        float sf = (float) mc.getWindow().getScaleFactor();
        float scale = base / sf;
        return new double[]{ mouseX / scale, mouseY / scale };
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        double[] scaled = getScaledMouse(mouseX, mouseY);
        int fixedMouseX = (int) scaled[0];
        int fixedMouseY = (int) scaled[1];

        float base = 2.0f;
        float sf = (float) mc.getWindow().getScaleFactor();
        openScale = Math.min(base, openScale + delta);
        float appliedScale = Math.max(0.0001f, openScale / sf);

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(appliedScale, appliedScale);

        if (frame.isWithinTotal(fixedMouseX, fixedMouseY)) {
            focus = frame;
        }
        if (frame.isWithin(fixedMouseX, fixedMouseY) && MOUSE_LEFT_HOLD && checkDragging()) {
            frame.setDragging(true);
        }

        frame.render(context, fixedMouseX, fixedMouseY, delta);

        if (frame.isDragging()) {
            try {
                star.sequoia2.client.SeqClient.getUiPositions().set(
                        PositionKey.fromHudUI(),
                        new UIPosition(frame.getX(), frame.getY())
                );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (settings != null) {
            settings.hudLayer.render(context, fixedMouseX, fixedMouseY, delta);
        }

        MOUSE_LEFT_CLICK = false;
        MOUSE_RIGHT_CLICK = false;
        MOUSE_X = fixedMouseX;
        MOUSE_Y = fixedMouseY;

        context.getMatrices().popMatrix();
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        double[] scaled = getScaledMouse(mouseX, mouseY);
        mouseX = scaled[0];
        mouseY = scaled[1];
        frame.mouseMoved((float) mouseX, (float) mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(Click click, boolean outsideScreen) {
        double mouseX = click.x();
        double mouseY = click.y();
        int mouseButton = click.button();

        double[] scaled = getScaledMouse(mouseX, mouseY);
        mouseX = scaled[0];
        mouseY = scaled[1];

        if (settings != null) {
            settings.hudLayer.mouseClicked((float) mouseX, (float) mouseY, mouseButton);
        }

        if (mouseButton == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            MOUSE_LEFT_CLICK = true;
            MOUSE_LEFT_HOLD = true;
        } else if (mouseButton == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            MOUSE_RIGHT_CLICK = true;
            MOUSE_RIGHT_HOLD = true;
        }

        frame.mouseClicked((float) mouseX, (float) mouseY, mouseButton);
        return super.mouseClicked(click, outsideScreen);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        double[] scaled = getScaledMouse(mouseX, mouseY);
        mouseX = scaled[0];
        mouseY = scaled[1];

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            MOUSE_LEFT_HOLD = false;
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            MOUSE_RIGHT_HOLD = false;
        }

        frame.mouseReleased((float) mouseX, (float) mouseY, button);

        if (settings != null) {
            settings.hudLayer.mouseReleased((float) mouseX, (float) mouseY, button);
        }

        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double[] scaled = getScaledMouse(mouseX, mouseY);
        mouseX = scaled[0];
        mouseY = scaled[1];

        if (focus != null) {
            focus.setScroll(focus.getScrollY() + (float) verticalAmount * 10f);
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.getKeycode();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        frame.keyPressed(keyCode, scanCode, modifiers);
        return super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        int keyCode = input.getKeycode();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        frame.keyReleased(keyCode, scanCode, modifiers);
        return super.keyReleased(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();
        frame.charTyped(chr, modifiers);
        return super.charTyped(input);
    }

    @Override
    public void removed() {
        super.removed();
        if (settings != null) {
            settings.hudLayer.setEditing(false);
        }
    }

    @Override
    public void close() {
        super.close();
        MOUSE_LEFT_CLICK = false;
        MOUSE_LEFT_HOLD = false;
        MOUSE_RIGHT_CLICK = false;
        MOUSE_RIGHT_HOLD = false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return closeOnEscape;
    }

    public boolean isWithin(double mouseX, double mouseY, double x, double y, double width, double height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private boolean checkDragging() {
        return !frame.isDragging();
    }
}
