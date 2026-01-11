package star.sequoia2.gui.hud.button;

import mil.nga.color.Color;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.accessors.RenderUtilAccessor;
import star.sequoia2.accessors.TextRendererAccessor;
import star.sequoia2.features.impl.Settings;
import star.sequoia2.gui.component.InteractableComponent;
import star.sequoia2.gui.component.settings.SettingComponent;
import star.sequoia2.gui.component.settings.impl.BooleanSettingComponent;
import star.sequoia2.gui.component.settings.impl.CalculatedEnumSettingComponent;
import star.sequoia2.gui.component.settings.impl.ColorSettingComponent;
import star.sequoia2.gui.component.settings.impl.EnumSettingComponent;
import star.sequoia2.gui.component.settings.impl.KeybindSettingComponent;
import star.sequoia2.gui.component.settings.impl.SliderComponent;
import star.sequoia2.gui.component.settings.impl.TextInputSettingComponent;
import star.sequoia2.gui.hud.frame.HudEditorFrame;
import star.sequoia2.gui.screen.GuiRoot;
import star.sequoia2.hud.HUDElement;
import star.sequoia2.settings.Setting;
import star.sequoia2.settings.types.BooleanSetting;
import star.sequoia2.settings.types.CalculatedEnumSetting;
import star.sequoia2.settings.types.ColorSetting;
import star.sequoia2.settings.types.DoubleSetting;
import star.sequoia2.settings.types.EnumSetting;
import star.sequoia2.settings.types.FloatSetting;
import star.sequoia2.settings.types.IntSetting;
import star.sequoia2.settings.types.KeybindSetting;
import star.sequoia2.settings.types.TextSetting;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class HUDElementButton extends InteractableComponent implements FeaturesAccessor, RenderUtilAccessor, TextRendererAccessor {

    private final HUDElement hudElement;
    private final HudEditorFrame parent;
    private final List<SettingComponent<?>> settingComponents = new CopyOnWriteArrayList<>();
    private boolean open = false;

    public HUDElementButton(HUDElement hudElement, HudEditorFrame parent) {
        this.hudElement = hudElement;
        this.parent = parent;
        this.height = 18.0f;
        createComponents();
    }

    private void createComponents() {
        try {
            hudElement.settings().all().forEach(setting -> addSettingsComponents(settingComponents, setting));
        } catch (Exception ignored) {
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

    @Override
    public void render(DrawContext context, float mouseX, float mouseY, float delta) {
        Settings settings = features().get(Settings.class).orElse(null);
        if (settings == null) return;

        GuiRoot root = settings.getClickGui().getRoot();

        float left = x;
        float top = y;
        float right = left + width;
        float bottom = getCurrentBottom();

        boolean hovering = mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;

        Color bgStart = settings.getThemeNormal();
        Color dark = settings.getThemeDark();
        Color light = settings.getThemeLight();
        Color accent1 = settings.getThemeAccent1();
        Color accent2 = settings.getThemeAccent2();
        Color accent3 = settings.getThemeAccent3();

        Color bgEnd = hovering ? accent1 : accent3;
        render2DUtil().roundGradientFilled(context.getMatrices(), left, top, right, bottom, root.rounding, bgEnd, accent1, true);

        context.getMatrices().push();
        context.getMatrices().translate(left + root.pad, top + textRenderer().fontHeight, 0);
        context.getMatrices().scale(1.1f, 1.1f, 0);

        int textColor = hudElement.isActive() ? accent2.getColor() : light.getColor();
        renderText(context, hudElement.name, 0, 0 - (float) textRenderer().fontHeight / 2, textColor, true);
        context.getMatrices().pop();

        String symbol = open ? "-" : "+";
        renderText(context, symbol, right - root.pad - textRenderer().getWidth(symbol), top + (height - textRenderer().fontHeight) / 2f, accent2.getColor(), true);

        if (open) {
            float offsetY = height + root.btnGap * 0.5f;
            for (SettingComponent<?> settingComp : settingComponents) {
                settingComp.setPos(left + root.pad, top + offsetY);
                settingComp.setDimensions(width - root.pad * 2f, root.btnH * 0.8f);
                settingComp.render(context, mouseX, mouseY, delta);
                if (settingComp instanceof ColorSettingComponent colorSettingComponent && colorSettingComponent.isOpen()) {
                    settingComp.setDimensions(width - root.pad * 2f, root.pad + colorSettingComponent.getPickerHeight());
                }
                offsetY += settingComp.contentHeight() + root.btnGap * 0.5f;
            }
        }
    }

    private float getCurrentBottom() {
        float base = y + height;
        if (!open) return base;
        return base + getExpandedHeight();
    }

    public float getExpandedHeight() {
        if (!open || settingComponents.isEmpty()) return 0.0f;
        Settings settings = features().get(Settings.class).orElse(null);
        if (settings == null) return 0.0f;
        GuiRoot root = settings.getClickGui().getRoot();
        float h = 0.0f;
        for (SettingComponent<?> comp : settingComponents) {
            h += comp.contentHeight() + root.btnGap * 0.5f;
        }
        return h;
    }

    @Override
    public void mouseClicked(float mouseX, float mouseY, int button) {
        float left = x;
        float top = y;
        float right = left + width;
        float headerBottom = top + height;

        if (mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= headerBottom) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                open = !open;
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                hudElement.toggle();
            }
        }

        if (!open) return;

        float contentTop = headerBottom;
        float contentBottom = getCurrentBottom();
        if (mouseX >= left && mouseX <= right && mouseY >= contentTop && mouseY <= contentBottom) {
            for (SettingComponent<?> comp : this.settingComponents) {
                comp.mouseClicked(mouseX, mouseY, button);
            }
        }
    }

    @Override
    public void mouseMoved(float mouseX, float mouseY) {
        if (open) {
            for (SettingComponent<?> comp : this.settingComponents) {
                comp.mouseMoved(mouseX, mouseY);
            }
        }
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        if (open) {
            for (SettingComponent<?> comp : this.settingComponents) {
                comp.mouseReleased(mouseX, mouseY, button);
            }
        }
    }

    @Override
    public void mouseScrolled(float mouseX, float mouseY, double horizontalAmount, double verticalAmount) {
        if (open) {
            for (SettingComponent<?> comp : this.settingComponents) {
                comp.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            }
        }
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (open) {
            for (SettingComponent<?> comp : this.settingComponents) {
                comp.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public void keyReleased(int keyCode, int scanCode, int modifiers) {
        if (open) {
            for (SettingComponent<?> comp : this.settingComponents) {
                comp.keyReleased(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        if (open) {
            for (SettingComponent<?> comp : this.settingComponents) {
                comp.charTyped(chr, modifiers);
            }
        }
    }

    public boolean isWithin(float mouseX, float mouseY) {
        float left = x;
        float top = y;
        float right = left + width;
        float bottom = getCurrentBottom();
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }

    public float getHeight() {
        return getCurrentBottom() - y;
    }

    public boolean isOpen() {
        return open;
    }
}
