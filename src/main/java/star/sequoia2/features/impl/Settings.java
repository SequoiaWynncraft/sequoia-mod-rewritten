package star.sequoia2.features.impl;

import com.collarmc.pounce.Subscribe;
import lombok.Getter;
import lombok.Setter;
import mil.nga.color.Color;
import org.lwjgl.glfw.GLFW;
import net.minecraft.util.Formatting;
import net.minecraft.text.Text;
import star.sequoia2.accessors.NotificationsAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.update.UpdateChannel;
import star.sequoia2.client.update.UpdateManager;
import star.sequoia2.events.SettingChanged;
import star.sequoia2.events.input.KeyEvent;
import star.sequoia2.features.Feature;
import star.sequoia2.gui.Fonts;
import star.sequoia2.gui.screen.ClickGUIScreen;
import star.sequoia2.settings.Binding;
import star.sequoia2.settings.types.*;
import star.sequoia2.utils.render.Themes;

import static star.sequoia2.client.SeqClient.mc;

@Getter
public class Settings extends Feature implements NotificationsAccessor {

    public final KeybindSetting menuKeybind = settings().binding("GuiKey:", "Opens the ClickGui", Binding.withKey(GLFW.GLFW_KEY_O));

    public EnumSetting<Themes.ThemeEnum> theme = settings().options("ChatTheme", "ChatTheme Setting", Themes.ThemeEnum.NEXUS, Themes.ThemeEnum.class);

    public CalculatedEnumSetting<Fonts.Font> defaultFont = settings().options("Font", "HUD font", "Minecraft", () -> SeqClient.getFonts().fonts());

    IntSetting volume = settings().number("Volume", "Volume of UI sounds.", 100, 0, 100);

    ColorSetting colorNormal = settings().color("Normal", "Normal color", new Color(39473836));
    ColorSetting colorDark = settings().color("Dark", "Dark color", new Color(-14012845));
    ColorSetting colorLight = settings().color("Light", "Light color", new Color(-66308));
    ColorSetting colorAccent1 = settings().color("Accent1", "Accent1 color", new Color(-12041351));
    ColorSetting colorAccent2 = settings().color("Accent2", "Accent2 color", new Color(-12615215));
    ColorSetting colorAccent3 = settings().color("Accent3", "Accent3 color", new Color(-12567948));

    FloatSetting boxW = settings().number("GuiWidth", "", 400f, 200f, 600f);
    FloatSetting boxH = settings().number("GuiHeight", "", 300f, 200f, 600f);
    FloatSetting pad = settings().number("Pad", "", 5f, 1f, 10f);
    FloatSetting btndW = settings().number("ButtonWidth", "", 50f, 40f, 60f);
    FloatSetting btnH = settings().number("ButtonHeight", "", 20f, 20f, 22f);
    FloatSetting btnGap = settings().number("ButtonGap", "", 3f, 1f, 5f);
    FloatSetting rounding = settings().number("Rounding", "", 3f, 0f, 8f);

    EnumSetting<UpdateChannel> updateChannel = settings().options("UpdateChannel", "Choose which update channel to use", UpdateChannel.STABLE, UpdateChannel.class);

    @Setter
    public ClickGUIScreen clickGui;

    public Settings() {
        super("Settings", "Client settings");
        UpdateManager.setChannel(updateChannel.get());
    }

    public int getNormalColorInt() {
        return theme.get().getTheme().NORMAL;
    }

    public Color getThemeNormal() {
        return colorNormal.get();
    }

    public Color getThemeDark() {
        return colorDark.get();
    }

    public Color getThemeLight() {
        return colorLight.get();
    }

    public Color getThemeAccent1() {
        return colorAccent1.get();
    }

    public Color getThemeAccent2() {
        return colorAccent2.get();
    }

    public Color getThemeAccent3() {
        return colorAccent3.get();
    }

    public UpdateChannel getUpdateChannel() {
        return updateChannel.get();
    }

    public void applyUpdateChannelPreference() {
        UpdateManager.setChannel(updateChannel.get());
    }

    @Subscribe
    public void onKeyDown(KeyEvent event) {
        if (!event.isKeyDown() && this.menuKeybind.get().matches(event) && mc.currentScreen == null) {
            event.cancel();
            clickGui = new ClickGUIScreen();
            mc.setScreen(clickGui);
        }
    }

    @Subscribe
    public void onSettingChanged(SettingChanged event) {
        if (event.setting() == updateChannel) {
            UpdateChannel channel = updateChannel.get();
            UpdateManager.setChannel(channel);
            if (channel == UpdateChannel.NIGHTLY) {
                notify(Text.translatable("sequoia.update.channel.warning").formatted(Formatting.YELLOW));
            }
            UpdateManager.checkForUpdates(true);
        }
    }

    public static float[] convertToHSB(ColorSetting color) {
        Color value = color.get();
        float[] hsbVals = java.awt.Color.RGBtoHSB(value.getRed(), value.getGreen(), value.getBlue(), null);
        return new float[] { hsbVals[0], hsbVals[1], hsbVals[2],  value.getAlpha() / 255.0f };
    }
}
