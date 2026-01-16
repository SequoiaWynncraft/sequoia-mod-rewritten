package star.sequoia2.gui.component.settings.impl;

import lombok.Getter;
import mil.nga.color.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import star.sequoia2.accessors.RenderUtilAccessor;
import star.sequoia2.features.impl.Settings;
import star.sequoia2.gui.component.settings.SettingComponent;
import star.sequoia2.settings.types.ColorSetting;

public class ColorSettingComponent extends SettingComponent<Color> implements RenderUtilAccessor {
    @Getter
    private boolean open = false;
    private final float[] selectedColor;
    private boolean dragging = false;

    public ColorSettingComponent(ColorSetting setting) {
        super(setting);
        float[] hsb = Settings.convertToHSB(setting);
        selectedColor = new float[] {hsb[0], hsb[1], 1.0f - hsb[2], hsb[3]};
    }

    @Override
    public void render(DrawContext context, float mouseX, float mouseY, float delta) {
        float left = contentX();
        float top = contentY();
        Color light = features().get(Settings.class).map(Settings::getThemeLight).orElse(Color.black());
        renderText(context, setting.name, left, top, light.getColor());
        float horizontalPos = left + textRenderer().getWidth(setting.name) + getGuiRoot().pad;

        render2DUtil().roundRectFilled(context.getMatrices(), horizontalPos, top, horizontalPos + 20f, top + textRenderer().fontHeight, 3f, setting.get());
        render2DUtil().drawGlow(context, horizontalPos, top, horizontalPos + 20f, top + textRenderer().fontHeight, setting.get(), 3f);

        if (open) {
            float pad = getGuiRoot().pad;
            float fontH = textRenderer().fontHeight;

            float pickerX1 = left + 1.0f;
            float pickerY1 = top + fontH + 2.0f + pad;
            float size = Math.max(64.0f, Math.min(getWidth() - 2.0f, 120.0f));
            float pickerX2 = pickerX1 + size;
            float pickerY2 = pickerY1 + size;

            float hueY1 = pickerY2 + 4.0f;
            float hueY2 = hueY1 + 10.0f;

            float alphaY1 = pickerY2 + 17.0f;
            float alphaY2 = alphaY1 + 10.0f;

            if (dragging) {
                if (isWithin(mouseX, mouseY, pickerX1, pickerY1, pickerX2, pickerY2)) {
                    selectedColor[1] = MathHelper.clamp((mouseX - pickerX1) / size, 0.0f, 1.0f);
                    selectedColor[2] = MathHelper.clamp((mouseY - pickerY1) / size, 0.0f, 1.0f);
                }
                if (isWithin(mouseX, mouseY, pickerX1, hueY1, pickerX2, hueY2)) {
                    selectedColor[0] = MathHelper.clamp((mouseX - pickerX1) / size, 0.0f, 1.0f);
                }
                if (isWithin(mouseX, mouseY, pickerX1, alphaY1, pickerX2, alphaY2)) {
                    selectedColor[3] = MathHelper.clamp((mouseX - pickerX1) / size, 0.0f, 1.0f);
                }
                java.awt.Color awt = java.awt.Color.getHSBColor(
                        MathHelper.clamp(selectedColor[0], 0.001f, 0.999f),
                        MathHelper.clamp(selectedColor[1], 0.001f, 0.999f),
                        1.0f - MathHelper.clamp(selectedColor[2], 0.001f, 0.999f)
                );
                awt = new java.awt.Color(awt.getRed() / 255.0f, awt.getGreen() / 255.0f, awt.getBlue() / 255.0f, MathHelper.clamp(selectedColor[3], 0.0f, 1.0f));
                setting.set(new Color(awt.getRed(), awt.getGreen(), awt.getBlue(), awt.getAlpha()));
            }

            float[] hsb = Settings.convertToHSB((ColorSetting) setting);
            int hueColor = java.awt.Color.HSBtoRGB(hsb[0], 1.0f, 1.0f);

            render2DUtil().fillGradientQuad(context.getMatrices(), pickerX1, pickerY1, pickerX2, pickerY2, 0xffffffff, hueColor, true);
            render2DUtil().fillGradientQuad(context.getMatrices(), pickerX1, pickerY1, pickerX2, pickerY2, 0x00000000, 0xff000000, false);

            for (float i = 0.0f; i < size; i += 1.0f) {
                float hue = i / (size - 1.0f);
                int c = java.awt.Color.getHSBColor(hue, 1.0f, 1.0f).getRGB();
                float x = pickerX1 + i;
                render2DUtil().fill(context.getMatrices(), x, hueY1, x + 1.0f, hueY2, c);
            }

            int checker1 = java.awt.Color.LIGHT_GRAY.getRGB();
            int checker2 = java.awt.Color.GRAY.getRGB();
            float sq = 2.0f;
            for (float gx = 0; gx < size; gx += sq) {
                for (float gy = 0; gy < (alphaY2 - alphaY1); gy += sq) {
                    int cc = (((int) (gx / sq) + (int) (gy / sq)) & 1) == 0 ? checker1 : checker2;
                    float x1 = pickerX1 + gx;
                    float y1 = alphaY1 + gy;
                    render2DUtil().fill(context.getMatrices(), x1, y1, x1 + sq, y1 + sq, cc);
                }
            }
            render2DUtil().fillGradient(context.getMatrices(), pickerX1, alphaY1, pickerX2, alphaY2, setting.get().getColorWithAlpha(), 0x0FFFFFF);

            float selX = pickerX1 + (size * selectedColor[1]);
            float selY = pickerY1 + (size * selectedColor[2]);
            render2DUtil().fill(context.getMatrices(), selX - 2.0f, selY - 2.0f, selX + 2.0f, selY + 2.0f, 0xff000000);
            render2DUtil().fill(context.getMatrices(), selX - 1.0f, selY - 1.0f, selX + 1.0f, selY + 1.0f, 0xffffffff);

            float hueKnobX = pickerX1 + (size * selectedColor[0]);
            render2DUtil().fill(context.getMatrices(), hueKnobX - 1.0f, hueY1 + 1.0f, hueKnobX + 2.0f, hueY2 - 1.0f, 0xff000000);
            render2DUtil().fill(context.getMatrices(), hueKnobX, hueY1 + 2.0f, hueKnobX + 1.0f, hueY2 - 2.0f, 0xffffffff);

            float alphaKnobX = pickerX1 + (size * selectedColor[3]);
            render2DUtil().fill(context.getMatrices(), alphaKnobX - 1.0f, alphaY1, alphaKnobX + 2.0f, alphaY2 - 1.0f, 0xff000000);
            render2DUtil().fill(context.getMatrices(), alphaKnobX, alphaY1 + 1.0f, alphaKnobX + 1.0f, alphaY2 - 3.0f, 0xffffffff);
        }
    }

    @Override
    public void mouseClicked(float mouseX, float mouseY, int button) {
        if (isWithin(mouseX, mouseY) && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            open = !open;
        }
        if (!open) return;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            float left = contentX();
            float top = contentY();
            float pad = getGuiRoot().pad;
            float fontH = textRenderer().fontHeight;

            float pickerX1 = left + 1.0f;
            float pickerY1 = top + fontH + 2.0f + pad;
            float size = Math.max(64.0f, Math.min(getWidth() - 2.0f, 120.0f));
            float pickerX2 = pickerX1 + size;
            float pickerY2 = pickerY1 + size;

            float hueY1 = pickerY2 + 4.0f;
            float hueY2 = hueY1 + 10.0f;

            float alphaY1 = pickerY2 + 17.0f;
            float alphaY2 = alphaY1 + 10.0f;

            if (isWithin(mouseX, mouseY, pickerX1, pickerY1, pickerX2, pickerY2)
                    || isWithin(mouseX, mouseY, pickerX1, hueY1, pickerX2, hueY2)
                    || isWithin(mouseX, mouseY, pickerX1, alphaY1, pickerX2, alphaY2)) {
                dragging = true;
            }
        }
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false;
        }
    }

    private boolean isWithin(double mx, double my, float x1, float y1, float x2, float y2) {
        return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
    }

    public float getPickerHeight() {
        float pickerHeight = 150f;
        return pickerHeight + getHeight();
    }
}
