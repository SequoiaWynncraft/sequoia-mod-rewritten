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
import star.sequoia2.gui.component.ModuleButton;
import star.sequoia2.gui.component.SearchBarComponent;
import star.sequoia2.gui.screen.GuiRoot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FeaturesCategory extends RelativeComponent implements RenderUtilAccessor, FeaturesAccessor, TextRendererAccessor, SettingsAccessor {
    private final List<ModuleButton> moduleButtons = new ArrayList<>();
    SearchBarComponent searchBarComponent;

    private float scrollOffset = 0f;
    private boolean draggingScrollbar = false;
    private float targetScrollOffset = 0f;
    private float dragThumbOffset = 0f;

    public FeaturesCategory() {
        super("Features");
        features().all()
                .filter(feature -> !feature.getName().equals("Settings"))
                .sorted(Comparator.comparing(Feature::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(feature -> moduleButtons.add(new ModuleButton(feature)));
        searchBarComponent = new SearchBarComponent();
    }

    private float totalContentHeight(GuiRoot root) {
        float h = 0f;
        for (ModuleButton b : moduleButtons) {
            if (searchBarComponent.isSearching() && !b.name.toLowerCase().contains(searchBarComponent.getSearch().toLowerCase())) continue;
            h += root.btnH * 2 + root.btnGap;
            if (b.isOpen()) h += b.getExpandedHeight();
        }
        return h + root.btnGap;
    }

    @Override
    public void render(DrawContext context, float mouseX, float mouseY, float delta) {
        float left = contentX();
        float top = contentY();
        float right = left + contentWidth();

        Color light = features().get(Settings.class).map(Settings::getThemeLight).orElse(Color.black());
        Color normal = features().get(Settings.class).map(Settings::getThemeNormal).orElse(Color.black());
        Color dark = features().get(Settings.class).map(Settings::getThemeDark).orElse(Color.black());
        Color accent1 = features().get(Settings.class).map(Settings::getThemeAccent1).orElse(Color.black());
        Color accent2 = features().get(Settings.class).map(Settings::getThemeAccent2).orElse(Color.black());

        GuiRoot root = getGuiRoot();

        if (root == null) {
            render2DUtil().drawText(context, "couldn't access root", left + 5f, top + 5f, light.getColor(), true);
            return;
        }

        float trackPad = 6f;
        float trackW = 2f;

        searchBarComponent.render(context, mouseX, mouseY, delta);
        searchBarComponent.setPos(left, top);
        searchBarComponent.setDimensions(contentWidth() - trackW - 4f, getGuiRoot().btnH);

        float viewportX = left;
        float viewportY = top + root.btnH;
        float viewportW = contentWidth() - trackW - 4f;
        float viewportH = contentHeight() - root.btnH;

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

        float drawOffset = 0f;
        for (ModuleButton button : moduleButtons) {
            if (searchBarComponent.isSearching() && !button.name.toLowerCase().contains(searchBarComponent.getSearch().toLowerCase())) continue;
            float itemH = root.btnH * 2;
            float y = viewportY + drawOffset - scrollOffset + root.btnGap;
            button.setPos(left, y);
            button.setDimensions(viewportW, itemH);
            button.render(context, mouseX, mouseY, delta);

            drawOffset += itemH + root.btnGap;
            if (button.isOpen()) {
                float eh = button.getExpandedHeight();
                drawOffset += eh;
            }
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
            float trackW = 2f;
            float trackPad = 6f;
            float viewportY = top + root.btnH;
            float viewportH = contentHeight() - root.btnH;
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

        for (ModuleButton moduleButton : moduleButtons) {
            if (searchBarComponent.isSearching() && !moduleButton.name.toLowerCase().contains(searchBarComponent.getSearch().toLowerCase())) continue;
            moduleButton.mouseClicked(mouseX, mouseY, button);
        }
        searchBarComponent.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(float mouseX, float mouseY) {
        if (draggingScrollbar) {
            GuiRoot root = getGuiRoot();
            if (root != null) {
                float left = contentX();
                float top = contentY();
                float right = left + contentWidth();
                float trackW = 2f;
                float trackPad = 6f;
                float viewportY = top + root.btnH;
                float viewportH = contentHeight() - root.btnH;
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

        for (ModuleButton moduleButton : moduleButtons) {
            if (searchBarComponent.isSearching() && !moduleButton.name.toLowerCase().contains(searchBarComponent.getSearch().toLowerCase())) continue;
            moduleButton.mouseMoved(mouseX, mouseY);
        }
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        if (button == 0) draggingScrollbar = false;

        for (ModuleButton moduleButton : moduleButtons) {
            if (searchBarComponent.isSearching() && !moduleButton.name.toLowerCase().contains(searchBarComponent.getSearch().toLowerCase())) continue;
            moduleButton.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public void mouseScrolled(float mouseX, float mouseY, double horizontalAmount, double verticalAmount) {
        GuiRoot root = getGuiRoot();
        if (root != null) {
            float viewportH = contentHeight() - root.btnH;
            float totalContent = totalContentHeight(root);
            float maxOffset = Math.max(0f, totalContent - viewportH);
            float step = root.btnH;
            targetScrollOffset = Math.max(0f, Math.min(maxOffset, targetScrollOffset - (float) verticalAmount * step));
        }

        for (ModuleButton moduleButton : moduleButtons) {
            if (searchBarComponent.isSearching() && !moduleButton.name.toLowerCase().contains(searchBarComponent.getSearch().toLowerCase())) continue;
            moduleButton.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        searchBarComponent.keyPressed(keyCode, scanCode, modifiers);
        if (keyCode == GLFW.GLFW_KEY_ENTER && searchBarComponent.isSearching()) {
            searchBarComponent.setSearching(false);
            searchBarComponent.setSearch("");
        }
        for (ModuleButton moduleButton : moduleButtons) {
            if (searchBarComponent.isSearching() && !moduleButton.name.toLowerCase().contains(searchBarComponent.getSearch().toLowerCase())) continue;
            moduleButton.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    @Override
    public void keyReleased(int keyCode, int scanCode, int modifiers) {
        for (ModuleButton moduleButton : moduleButtons) {
            if (searchBarComponent.isSearching() && !moduleButton.name.toLowerCase().contains(searchBarComponent.getSearch().toLowerCase())) continue;
            moduleButton.keyReleased(keyCode, scanCode, modifiers);
        }
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        searchBarComponent.charTyped(chr, modifiers);
        for (ModuleButton moduleButton : moduleButtons) {
            if (searchBarComponent.isSearching() && !moduleButton.name.toLowerCase().contains(searchBarComponent.getSearch().toLowerCase())) continue;
            moduleButton.charTyped(chr, modifiers);
        }
    }
}
