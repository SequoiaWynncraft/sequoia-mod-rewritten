package star.sequoia2.gui.hud.frame;

import mil.nga.color.Color;
import net.minecraft.client.gui.DrawContext;
import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.accessors.RenderUtilAccessor;
import star.sequoia2.accessors.TextRendererAccessor;
import star.sequoia2.features.impl.Settings;
import star.sequoia2.gui.component.InteractableComponent;
import star.sequoia2.gui.hud.button.HUDElementButton;
import star.sequoia2.hud.HUDElements;
import star.sequoia2.gui.screen.ClickGUIScreen;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static star.sequoia2.client.SeqClient.mc;

public class HudEditorFrame extends InteractableComponent implements FeaturesAccessor, RenderUtilAccessor, TextRendererAccessor {

    private final String name;
    private final List<HUDElementButton> hudElementButtons = new CopyOnWriteArrayList<>();

    private boolean isDragging;
    private float scrollY = 0f;
    private float frameHeight;
    private float px, py;
    private float closeX, closeY, closeW, closeH;

    public HudEditorFrame(HUDElements elements, float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.name = "HUD Elements";

        elements.all()
                .sorted(Comparator.comparing(element -> element.name))
                .forEach(hudElement -> hudElementButtons.add(new HUDElementButton(hudElement, this)));
    }

    public HudEditorFrame(HUDElements elements, float x, float y) {
        this(elements, x, y, 150.0f, 20.0f);
    }

    @Override
    public void render(DrawContext context, float mouseX, float mouseY, float delta) {
        Settings settings = features().get(Settings.class).orElse(null);
        if (settings == null) return;

        Color normal = settings.getThemeNormal();
        Color dark = settings.getThemeDark();
        Color light = settings.getThemeLight();
        Color accent1 = settings.getThemeAccent1();
        Color accent2 = settings.getThemeAccent2();
        float rounding = settings.getRounding().get();

        if (isDragging) {
            x += mouseX - px;
            y += mouseY - py;
        }

        x = Math.max(0, Math.min(x, mc.getWindow().getScaledWidth() - width));
        y = Math.max(0, Math.min(y, mc.getWindow().getScaledHeight() - height));

        frameHeight = 5.0f;
        for (HUDElementButton button : hudElementButtons) {
            frameHeight += button.getHeight() + 2.0f;
        }

        if (frameHeight > 300f) {
            frameHeight = 300f;
        }

        render2DUtil().drawGlow(context, x, y, x + width, y + height + frameHeight, dark, rounding);
        render2DUtil().roundRectFilled(context.getMatrices(), x, y, x + width, y + height, rounding, normal);

        context.drawText(textRenderer(), name, (int) (x + 5.0f), (int) (y + 5.0f), light.getColor(), true);

        closeW = 10.0f;
        closeH = 10.0f;
        closeX = x + width - closeW - 4.0f;
        closeY = y + 4.0f;

        render2DUtil().roundRectFilled(context.getMatrices(), closeX, closeY, closeX + closeW, closeY + closeH, rounding, accent2);
        context.drawText(
                textRenderer(),
                "X",
                (int) (closeX + (closeW - textRenderer().getWidth("X")) / 2),
                (int) (closeY + (closeH - textRenderer().fontHeight) / 2),
                light.getColor(),
                true
        );

        context.enableScissor((int) x, (int) (y + height), (int) (x + width), (int) (y + height + frameHeight));

        render2DUtil().roundRectFilled(context.getMatrices(), x, y + height, x + width, y + height + frameHeight,
                rounding, dark);

        float currentY = y + height + 5.0f + scrollY;
        float headerHeight = 18.0f;
        for (HUDElementButton button : hudElementButtons) {
            button.setPos(x + 5.0f, currentY);
            button.setDimensions(width - 10.0f, headerHeight);
            button.render(context, mouseX, mouseY, delta);
            currentY += button.getHeight() + 2.0f;
        }

        context.disableScissor();

        render2DUtil().fill(context.getMatrices(), x - 1, y - 1, x + width + 1, y, accent1.getColorWithAlpha());
        render2DUtil().fill(context.getMatrices(), x - 1, y + height + frameHeight, x + width + 1, y + height + frameHeight + 1, accent1.getColorWithAlpha());
        render2DUtil().fill(context.getMatrices(), x - 1, y, x, y + height + frameHeight, accent1.getColorWithAlpha());
        render2DUtil().fill(context.getMatrices(), x + width, y, x + width + 1, y + height + frameHeight, accent1.getColorWithAlpha());

        px = mouseX;
        py = mouseY;
    }

    @Override
    public void mouseMoved(float mouseX, float mouseY) {
        for (HUDElementButton button : hudElementButtons) {
            button.mouseMoved(mouseX, mouseY);
        }
    }

    @Override
    public void mouseClicked(float mouseX, float mouseY, int button) {
        if (button == 0 && isWithin(mouseX, mouseY, closeX, closeY, closeW, closeH)) {
            mc.setScreen(new ClickGUIScreen());
            return;
        }

        if (isWithin(mouseX, mouseY, x, y, width, height)) {
        }

        if (isWithin(mouseX, mouseY, x, y + height, width, frameHeight)) {
            for (HUDElementButton hudButton : hudElementButtons) {
                if (hudButton.isWithin(mouseX, mouseY)) {
                    hudButton.mouseClicked(mouseX, mouseY, button);
                    break;
                }
            }
        }
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        isDragging = false;
        for (HUDElementButton hudButton : hudElementButtons) {
            hudButton.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public void mouseScrolled(float mouseX, float mouseY, double horizontalAmount, double verticalAmount) {
        if (isWithin(mouseX, mouseY, x, y + height, width, frameHeight)) {
            scrollY = Math.min(0.0f, scrollY + (float) verticalAmount * 10f);
        }
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        for (HUDElementButton button : hudElementButtons) {
            button.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    @Override
    public void keyReleased(int keyCode, int scanCode, int modifiers) {
        for (HUDElementButton button : hudElementButtons) {
            button.keyReleased(keyCode, scanCode, modifiers);
        }
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        for (HUDElementButton button : hudElementButtons) {
            button.charTyped(chr, modifiers);
        }
    }

    public boolean isWithin(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public boolean isWithinTotal(float mouseX, float mouseY) {
        return isWithin(mouseX, mouseY, x, y, width, getTotalHeight());
    }

    public void setDragging(boolean dragging) {
        this.isDragging = dragging;
    }

    public boolean isDragging() {
        return isDragging;
    }

    public void setScroll(float scrollY) {
        this.scrollY = Math.min(0.0f, scrollY);
    }

    public float getScrollY() {
        return scrollY;
    }

    public float getTotalHeight() {
        return height + frameHeight;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }
}
