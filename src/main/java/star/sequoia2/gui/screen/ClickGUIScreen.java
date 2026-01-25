package star.sequoia2.gui.screen;

import lombok.Setter;
import net.minecraft.client.gui.Click;
import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.accessors.RenderUtilAccessor;
import star.sequoia2.gui.categories.Categories;
import star.sequoia2.gui.component.ScissorStack;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import static star.sequoia2.client.SeqClient.mc;

public class ClickGUIScreen extends Screen implements FeaturesAccessor, RenderUtilAccessor {

    public static int MOUSE_X;
    public static int MOUSE_Y;
    public static boolean MOUSE_RIGHT_CLICK;
    public static boolean MOUSE_RIGHT_HOLD;
    public static boolean MOUSE_LEFT_CLICK;
    public static boolean MOUSE_LEFT_HOLD;
    public static final ScissorStack SCISSOR_STACK = new ScissorStack();

    @Setter
    private boolean closeOnEscape = true;

    @Getter
    public final GuiRoot root;

    private float openScale = 0f;

    public ClickGUIScreen() {
        super(Text.literal("Seq"));
        root = new GuiRoot(Categories.all().toList());
    }

    @Override
    protected void init() {
        super.init();
        openScale = 0f;
    }

    private double[] getScaledMouse(double mouseX, double mouseY) {
        float base = 2.0f;
        float sf = (float) mc.getWindow().getScaleFactor();
        float scale = base / sf;
        return new double[]{ mouseX / scale, mouseY / scale };
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        double[] scaled = getScaledMouse(mouseX, mouseY);
        mouseX = scaled[0];
        mouseY = scaled[1];
        if (root != null) root.mouseMoved((float) mouseX, (float) mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(Click click, boolean outsideScreen) {
        double mx = click.x();
        double my = click.y();
        int button = click.button();
        double[] scaled = getScaledMouse(mx, my);
        mx = scaled[0];
        my = scaled[1];

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            MOUSE_LEFT_CLICK = true;
            MOUSE_LEFT_HOLD = true;
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            MOUSE_RIGHT_CLICK = true;
            MOUSE_RIGHT_HOLD = true;
        }

        if (root != null) root.mouseClicked((float) mx, (float) my, button);
        return super.mouseClicked(click, outsideScreen);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mx = click.x();
        double my = click.y();
        int button = click.button();
        double[] scaled = getScaledMouse(mx, my);
        mx = scaled[0];
        my = scaled[1];

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            MOUSE_LEFT_HOLD = false;
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            MOUSE_RIGHT_HOLD = false;
        }

        if (root != null) root.mouseReleased((float) mx, (float) my, button);
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double[] scaled = getScaledMouse(mouseX, mouseY);
        mouseX = scaled[0];
        mouseY = scaled[1];
        if (root != null) root.mouseScrolled((float) mouseX, (float) mouseY, horizontalAmount, verticalAmount);
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
        float scaledHeight = (context.getScaledWindowHeight() / appliedScale);
        float scaledWidth = (context.getScaledWindowWidth() / appliedScale);
        context.getMatrices().scale(appliedScale, appliedScale);

        if (root != null) {
            root.setPos(0, 0);
            root.setDimensions(scaledWidth, scaledHeight);
            root.layout(scaledWidth, scaledHeight);
            root.render(context, fixedMouseX, fixedMouseY, delta);
        }

        MOUSE_LEFT_CLICK = false;
        MOUSE_RIGHT_CLICK = false;
        MOUSE_X = fixedMouseX;
        MOUSE_Y = fixedMouseY;

        context.getMatrices().popMatrix();
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.getKeycode();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        if (root != null) root.keyPressed(keyCode, scanCode, modifiers);
        return super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        int keyCode = input.getKeycode();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        if (root != null) root.keyReleased(keyCode, scanCode, modifiers);
        return super.keyReleased(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();
        if (root != null) root.charTyped(chr, modifiers);
        return super.charTyped(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
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
    public boolean shouldCloseOnEsc() {
        return closeOnEscape;
    }
}
