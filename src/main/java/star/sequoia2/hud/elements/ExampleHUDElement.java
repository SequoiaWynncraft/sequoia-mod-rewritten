package star.sequoia2.hud.elements;

import mil.nga.color.Color;
import net.minecraft.client.gui.DrawContext;
import star.sequoia2.accessors.TextRendererAccessor;
import star.sequoia2.hud.HUDElement;
import star.sequoia2.settings.types.BooleanSetting;
import star.sequoia2.settings.types.ColorSetting;
import star.sequoia2.settings.types.FloatSetting;

public class ExampleHUDElement extends HUDElement implements TextRendererAccessor {

    public final BooleanSetting showBackground = settings().bool("Show Background", "Show background behind text", true);
    public final ColorSetting textColor = settings().color("Text Color", "Color of the text", new Color(255, 255, 255));
    public final FloatSetting textScale = settings().number("Text Scale", "Scale of the text", 1.0f, 0.5f, 3.0f);

    public ExampleHUDElement() {
        super("Example HUD", "A simple example HUD element");
        this.width = 100;
        this.height = 20;
    }

    @Override
    public void render(DrawContext context, float mouseX, float mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        if (!isActive()) return;

        String text = "ur 6/7 rooms golemus lil bro";
        int textWidth = (int) (textRenderer().getWidth(text) * textScale.get());
        int textHeight = (int) (textRenderer().fontHeight * textScale.get());

        this.width = textWidth + 4;
        this.height = textHeight + 4;

        if (showBackground.get() && !isEditing()) {
            Color bgColor = new Color(0, 0, 0, 128);
            render2DUtil().roundRectFilled(context.getMatrices(), x, y, x + width, y + height, 3f, bgColor);
        }

        context.getMatrices().push();
        context.getMatrices().translate(x + 2, y + 2, 0);
        context.getMatrices().scale(textScale.get(), textScale.get(), 1.0f);
        context.drawText(textRenderer(), text, 0, 0, textColor.get().getColor(), true);
        context.getMatrices().pop();
    }

    // for symmetry THOSEKWNAOHDAOH
    @Override
    public void mouseMoved(float mouseX, float mouseY) {}

    @Override
    public void mouseScrolled(float mouseX, float mouseY, double horizontalAmount, double verticalAmount) {}

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {}

    @Override
    public void keyReleased(int keyCode, int scanCode, int modifiers) {}

    @Override
    public void charTyped(char chr, int modifiers) {}
}