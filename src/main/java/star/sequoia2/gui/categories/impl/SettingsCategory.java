package star.sequoia2.gui.categories.impl;

import mil.nga.color.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;
import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.accessors.RenderUtilAccessor;
import star.sequoia2.accessors.SettingsAccessor;
import star.sequoia2.accessors.TextRendererAccessor;
import star.sequoia2.features.Feature;
import star.sequoia2.features.impl.Settings;
import star.sequoia2.gui.categories.RelativeComponent;
import star.sequoia2.gui.component.settings.SettingComponent;
import star.sequoia2.gui.component.settings.impl.*;
import star.sequoia2.gui.screen.GuiRoot;
import star.sequoia2.settings.Setting;
import star.sequoia2.settings.types.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SettingsCategory extends RelativeComponent implements RenderUtilAccessor, TextRendererAccessor, FeaturesAccessor, SettingsAccessor {
    private final List<SettingComponent<?>> settingComponents = new CopyOnWriteArrayList<>();
    private float scrollOffset = 0f;
    private boolean draggingScrollbar = false;
    private float targetScrollOffset = 0f;
    private float dragThumbOffset = 0f;

    public SettingsCategory() {
        super("Settings");
        Feature settingsFeature = features().get(Settings.class).map(f -> (Feature) f).orElse(null);
        if (settingsFeature != null) {
            for (Setting<?> setting : settingsState().fromFeature(settingsFeature).all().toList()) {
                addSettingsComponents(settingComponents, setting);
            }
        }
    }

    @SuppressWarnings({"unchecked"})
    private void addSettingsComponents(List<SettingComponent<?>> components, Setting<?> setting) {
        if (setting instanceof KeybindSetting keybindSetting) {
            components.add(new KeybindSettingComponent(keybindSetting));
        } else if (setting instanceof BooleanSetting booleanSetting) {
            components.add(new BooleanSettingComponent(booleanSetting));
        } else if (setting instanceof EnumSetting enumSetting) {
            components.add(new EnumSettingComponent(enumSetting));
        } else if (setting instanceof CalculatedEnumSetting calculatedEnumSetting) {
            components.add(new CalculatedEnumSettingComponent(calculatedEnumSetting));
        } else if (setting instanceof ColorSetting colorSetting) {
            components.add(new ColorSettingComponent(colorSetting));
        } else if (setting instanceof DoubleSetting doubleSetting) {
            components.add(new SliderComponent<>(doubleSetting));
        } else if (setting instanceof FloatSetting floatSetting) {
            components.add(new SliderComponent<>(floatSetting));
        } else if (setting instanceof IntSetting intSetting) {
            components.add(new SliderComponent<>(intSetting));
        } else if (setting instanceof TextSetting textSetting) {
            components.add(new TextInputSettingComponent(textSetting));
        }
    }

    private float totalContentHeight(GuiRoot root) {
        float h = 0f;
        for (SettingComponent<?> comp : settingComponents) {
            float ch = root.btnH * 0.8f;
            if (comp instanceof ColorSettingComponent c && c.isOpen()) {
                ch += c.getPickerHeight();
            }
            h += ch + root.btnGap * 0.5f;
        }
        return h + root.btnGap * 0.5f;
    }

    @Override
    public void render(DrawContext context, float mouseX, float mouseY, float delta) {
        float left = contentX();
        float top = contentY();
        float right = left + contentWidth();
        float bottom = top + contentHeight();

        Color light = features().get(Settings.class).map(Settings::getThemeLight).orElse(Color.black());
        Color normal = features().get(Settings.class).map(Settings::getThemeNormal).orElse(Color.black());
        Color dark = features().get(Settings.class).map(Settings::getThemeDark).orElse(Color.black());
        Color accent1 = features().get(Settings.class).map(Settings::getThemeAccent1).orElse(Color.black());
        Color accent2 = features().get(Settings.class).map(Settings::getThemeAccent2).orElse(Color.black());

        render2DUtil().roundGradientFilled(context.getMatrices(), left, top, right, bottom, 8, normal, dark, true);
        GuiRoot root = getGuiRoot();
        if (root == null) {
            render2DUtil().drawText(context, "couldn't access root", left + 5f, top + 5f, light.getColor(), true);
            return;
        }

        float trackPad = 6f;
        float trackW = 3f;

        float viewportX = left;
        float viewportY = top;
        float viewportW = contentWidth() - trackW - 4f;
        float viewportH = contentHeight();

        float trackX = right - trackW;
        float trackY = viewportY + trackPad;
        float trackH = Math.max(0f, viewportH - trackPad * 2f);

        float totalContent = totalContentHeight(root);
        float maxOffset = Math.max(0f, totalContent - viewportH);
        if (targetScrollOffset > maxOffset) targetScrollOffset = maxOffset;
        if (targetScrollOffset < 0f) targetScrollOffset = 0f;

        float k = 0.18f;
        if (draggingScrollbar) {
            scrollOffset = targetScrollOffset;
        } else {
            scrollOffset += (targetScrollOffset - scrollOffset) * k;
        }

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        context.enableScissor((int) viewportX, (int) viewportY, (int) (viewportX + viewportW), (int) (viewportY + viewportH));

        float offsetY = 0f;
        for (SettingComponent<?> comp : settingComponents) {
            float ch = root.btnH * 0.8f;
            if (comp instanceof ColorSettingComponent c && c.isOpen()) {
                ch += c.getPickerHeight();
            }
            float y = viewportY + offsetY - scrollOffset + root.btnGap * 0.5f;
            comp.setPos(left + root.pad, y);
            comp.setDimensions(viewportW - root.pad * 2f, root.btnH * 0.8f);
            comp.render(context, mouseX, mouseY, delta);
            offsetY += ch + root.btnGap * 0.5f;
        }

        context.disableScissor();
        matrices.popMatrix();

        if (totalContent > viewportH && trackH > 0f) {
            float thumbH = 20f;
            float available = Math.max(0f, trackH - thumbH);
            float thumbY = trackY + (maxOffset == 0 ? 0 : (scrollOffset / maxOffset) * available);
            boolean overThumb = mouseX >= trackX && mouseX <= trackX + trackW && mouseY >= thumbY && mouseY <= thumbY + thumbH;
            Color scrollColor = overThumb || draggingScrollbar ? light : accent2;
            render2DUtil().roundRectFilled(matrices, trackX, thumbY, trackX + trackW, thumbY + thumbH, 0.5f, scrollColor);
        }
    }

    @Override
    public void mouseClicked(float mouseX, float mouseY, int button) {
        GuiRoot root = getGuiRoot();
        if (root != null && button == 0) {
            float left = contentX();
            float top = contentY();
            float right = left + contentWidth();
            float trackW = 3f;
            float trackPad = 6f;
            float viewportY = top;
            float viewportH = contentHeight();
            float trackX = right - trackW;
            float trackY = viewportY + trackPad;
            float trackH = Math.max(0f, viewportH - trackPad * 2f);

            float totalContent = totalContentHeight(root);
            float maxOffset = Math.max(0f, totalContent - viewportH);
            if (totalContent > viewportH && trackH > 0f) {
                float thumbH = 20f;
                float available = Math.max(0f, trackH - thumbH);
                float thumbY = trackY + (maxOffset == 0 ? 0 : (scrollOffset / maxOffset) * available);
                boolean overThumb = mouseX >= trackX && mouseX <= trackX + trackW && mouseY >= thumbY && mouseY <= thumbY + thumbH;

                draggingScrollbar = false;
                if (overThumb) {
                    draggingScrollbar = true;
                    targetScrollOffset = scrollOffset;
                    dragThumbOffset = mouseY - thumbY;
                    if (dragThumbOffset < 0f) dragThumbOffset = 0f;
                    if (dragThumbOffset > thumbH) dragThumbOffset = thumbH;
                } else if (mouseX >= trackX && mouseX <= trackX + trackW && mouseY >= trackY && mouseY <= trackY + trackH) {
                    float desiredThumbY = Math.max(trackY, Math.min(trackY + available, mouseY - thumbH * 0.5f));
                    float ratio = available == 0f ? 0f : (desiredThumbY - trackY) / available;
                    targetScrollOffset = Math.max(0f, Math.min(maxOffset, ratio * maxOffset));
                    draggingScrollbar = true;
                    dragThumbOffset = thumbH * 0.5f;
                }
            }
        }

        for (SettingComponent<?> comp : settingComponents) {
            comp.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public void mouseMoved(float mouseX, float mouseY) {
        if (draggingScrollbar) {
            GuiRoot root = getGuiRoot();
            if (root != null) {
                float left = contentX();
                float top = contentY();
                float right = left + contentWidth();
                float trackW = 3f;
                float trackPad = 6f;
                float viewportY = top;
                float viewportH = contentHeight();
                float trackY = viewportY + trackPad;
                float trackH = Math.max(0f, viewportH - trackPad * 2f);
                float totalContent = totalContentHeight(root);
                float maxOffset = Math.max(0f, totalContent - viewportH);
                if (totalContent > viewportH && trackH > 0f && maxOffset > 0f) {
                    float thumbH = 20f;
                    float available = trackH - thumbH;
                    float by = top - root.pad;
                    float globalMouseY = mouseY + by;
                    float desiredThumbY = globalMouseY - dragThumbOffset;
                    float minY = trackY;
                    float maxY = trackY + available;
                    if (desiredThumbY < minY) desiredThumbY = minY;
                    if (desiredThumbY > maxY) desiredThumbY = maxY;
                    float ratio = available == 0f ? 0f : (desiredThumbY - trackY) / available;
                    targetScrollOffset = Math.max(0f, Math.min(maxOffset, ratio * maxOffset));
                }
            }
        }

        for (SettingComponent<?> comp : settingComponents) {
            comp.mouseMoved(mouseX, mouseY);
        }
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        if (button == 0) draggingScrollbar = false;
        for (SettingComponent<?> comp : settingComponents) {
            comp.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public void mouseScrolled(float mouseX, float mouseY, double horizontalAmount, double verticalAmount) {
        GuiRoot root = getGuiRoot();
        if (root != null) {
            float viewportH = contentHeight();
            float totalContent = totalContentHeight(root);
            float maxOffset = Math.max(0f, totalContent - viewportH);
            float step = root.btnH * 0.5f;
            targetScrollOffset = Math.max(0f, Math.min(maxOffset, targetScrollOffset - (float) verticalAmount * step));
        }
        for (SettingComponent<?> comp : settingComponents) {
            comp.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        for (SettingComponent<?> comp : settingComponents) {
            comp.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
        }
    }

    @Override
    public void keyReleased(int keyCode, int scanCode, int modifiers) {
        for (SettingComponent<?> comp : settingComponents) {
            comp.keyReleased(keyCode, scanCode, modifiers);
        }
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        for (SettingComponent<?> comp : settingComponents) {
            comp.charTyped(chr, modifiers);
        }
    }
}
