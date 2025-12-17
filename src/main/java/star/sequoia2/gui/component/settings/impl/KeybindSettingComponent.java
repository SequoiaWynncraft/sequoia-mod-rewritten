package star.sequoia2.gui.component.settings.impl;

import mil.nga.color.Color;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import star.sequoia2.features.impl.Settings;
import star.sequoia2.gui.component.settings.SettingComponent;
import star.sequoia2.settings.Binding;
import star.sequoia2.settings.types.KeybindSetting;
import star.sequoia2.utils.Timer;

public class KeybindSettingComponent extends SettingComponent<Binding> {
    private boolean listening;

    Timer announceTimer = new Timer();

    public KeybindSettingComponent(KeybindSetting setting) {
        super(setting);
    }

    @Override
    public void render(DrawContext context, float mouseX, float mouseY, float delta) {
        float left = contentX();
        float top = contentY();

        Color light = features().get(Settings.class).map(Settings::getThemeLight).orElse(Color.black());

        final Binding binding = setting.get();
        final KeybindSetting keybindSetting = (KeybindSetting) setting;
        String val;
        if (announceTimer.getTicksPassed() < 20) {
            val = keybindSetting.getToggle() ? "Toggle" : "Hold";
        }
        else if (listening) {
            val = "...";
        } else if (!binding.isSet()) {
            val = "None";
        } else {
            val = binding.name();
        }

        renderText(context, setting.name + " " + val, left, top, light.getColor());
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                setting.set(Binding.none());
                listening = false;
                return;
            } else {
                setting.set(Binding.withKey(keyCode));
                listening = false;
            }
            return;
        }
        super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void mouseClicked(float mouseX, float mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (isWithinContent(mouseX, mouseY)) {
            if (!listening && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && leftShiftHeld) {
                KeybindSetting keybindSetting = (KeybindSetting) setting;
                keybindSetting.setToggle(!keybindSetting.getToggle());
                announceTimer.reset();
                return;
            }
            if (!listening && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                listening = true;
            } else if (listening) {
                setting.set(Binding.withButton(button));
                listening = false;
            }
        }
    }
}
