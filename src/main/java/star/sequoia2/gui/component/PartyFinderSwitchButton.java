package star.sequoia2.gui.component;

import com.mojang.logging.LogUtils;
import lombok.Getter;
import mil.nga.color.Color;
import net.minecraft.client.gui.DrawContext;
import org.slf4j.Logger;
import star.sequoia2.accessors.ConfigurationAccessor;
import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.accessors.RenderUtilAccessor;
import star.sequoia2.accessors.SettingsAccessor;
import star.sequoia2.features.impl.Settings;
import star.sequoia2.gui.categories.RelativeComponent;
import star.sequoia2.gui.categories.impl.PartyFinderCategory;
import star.sequoia2.gui.component.settings.SettingComponent;
import star.sequoia2.gui.component.settings.impl.*;
import star.sequoia2.gui.screen.GuiRoot;
import star.sequoia2.settings.Setting;
import star.sequoia2.settings.types.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PartyFinderSwitchButton extends RelativeComponent implements SettingsAccessor, FeaturesAccessor, RenderUtilAccessor, ConfigurationAccessor {
    private static final Logger LOGGER = LogUtils.getLogger();

    private List<SettingComponent<?>> settingComponents = new CopyOnWriteArrayList<>();
    @Getter
    private boolean open = false;

    public int extraHeight = 0;

    public PartyFinderSwitchButton() {
        super("Create a party");
        settingComponents = createComponents();
    }

    private List<SettingComponent<?>> createComponents() {
        List<SettingComponent<?>> components = new CopyOnWriteArrayList<>();
        return components;
    }

    @Override
    public void render(DrawContext context, float mouseX, float mouseY, float delta) {
        GuiRoot root = features().get(Settings.class).map(Settings::getClickGui).orElseThrow().getRoot();

        float left = contentX();
        float top = contentY();
        float right = left + contentWidth();
        float bottom = getCurrentBottom();

        boolean hovering = mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;

        Color bgStart = features().get(Settings.class).map(Settings::getThemeNormal).orElse(Color.black());
        Color dark = features().get(Settings.class).map(Settings::getThemeDark).orElse(Color.black());
        Color light = features().get(Settings.class).map(Settings::getThemeLight).orElse(Color.black());
        Color accent1 = features().get(Settings.class).map(Settings::getThemeAccent1).orElse(Color.black());
        Color accent2 = features().get(Settings.class).map(Settings::getThemeAccent2).orElse(Color.black());
        Color accent3 = features().get(Settings.class).map(Settings::getThemeAccent3).orElse(Color.black());

        Color bgEnd = hovering ? accent1 : accent3;
        render2DUtil().roundGradientFilled(context.getMatrices(), left, top, right, bottom, root.rounding, bgEnd, accent1, true);

        context.getMatrices().push();
        context.getMatrices().translate(left + root.pad, top + textRenderer().fontHeight, 0);
        context.getMatrices().scale(1.1f, 1.1f, 0);

        renderText(context, PartyFinderCategory.isCreatingParty ? "Go back" : "Create a party", PartyFinderCategory.isCreatingParty ? 20 : 1, -2, light.getColor(), true);

        context.getMatrices().pop();

        if (open) {
            float offsetY = contentHeight() + root.btnGap * 0.5f;
            for (SettingComponent<?> settingComp : settingComponents) {
                settingComp.setPos(left + root.pad, top + offsetY);
                settingComp.setDimensions(contentWidth() - root.pad * 2f, root.btnH * 0.8f);
                settingComp.render(context, mouseX, mouseY, delta);
                offsetY += settingComp.contentHeight() + root.btnGap * 0.5f;
            }
        }
    }


    private float getCurrentBottom() {
        float base = contentY() + contentHeight();
        if (!open) return base;
        return base + getExpandedHeight();
    }

    public float getExpandedHeight() {
        GuiRoot root = features().get(Settings.class).map(Settings::getClickGui).orElseThrow().getRoot();
        float height = 0;
        for (SettingComponent<?> comp : settingComponents) {
            height += comp.contentHeight() + root.btnGap * 0.5f;
        }
        return height;
    }

    @Override
    public void mouseClicked(float mouseX, float mouseY, int button) {
        if (isWithinContent(mouseX, mouseY)) {
            PartyFinderCategory.isCreatingParty = !PartyFinderCategory.isCreatingParty;
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
}