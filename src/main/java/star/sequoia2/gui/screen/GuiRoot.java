package star.sequoia2.gui.screen;

import lombok.Getter;
import net.minecraft.client.util.math.MatrixStack;
import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.accessors.RenderUtilAccessor;
import star.sequoia2.accessors.TextRendererAccessor;
import star.sequoia2.features.impl.Settings;
import star.sequoia2.gui.categories.RelativeComponent;
import mil.nga.color.Color;
import net.minecraft.client.gui.DrawContext;
import star.sequoia2.utils.render.TextureStorage;

import java.util.List;

public final class GuiRoot implements RenderUtilAccessor, TextRendererAccessor, FeaturesAccessor {

    @Getter
    private final List<RelativeComponent> categories;
    @Getter
    private int selected = 0;

    private float uiW, uiH;
    private float x, y;
    private float w, h;

    private boolean iconDark;

    public final float boxHeight = features().get(Settings.class).map(settings -> settings.getBoxH().get()).orElse(300f);
    public final float boxWidth = features().get(Settings.class).map(settings -> settings.getBoxW().get()).orElse(380f);
    public final float pad = features().get(Settings.class).map(settings -> settings.getPad().get()).orElse(5f);
    public final float btnW = features().get(Settings.class).map(settings -> settings.getBtndW().get()).orElse(50f);
    public final float btnH = features().get(Settings.class).map(settings -> settings.getBtnH().get()).orElse(20f);
    public final float btnGap = features().get(Settings.class).map(settings -> settings.getBtnGap().get()).orElse(3f);
    public final float rounding = features().get(Settings.class).map(settings -> settings.getRounding().get()).orElse(6f);

    public GuiRoot(List<RelativeComponent> categories) {
        this.categories = categories;
    }

    public void setPos(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void setDimensions(float width, float height) {
        this.w = width;
        this.h = height;
    }

    public void layout(float uiWidth, float uiHeight) {
        this.uiW = uiWidth;
        this.uiH = uiHeight;
    }

    public void render(DrawContext context, float mouseX, float mouseY, float delta) {
        MatrixStack matrices = context.getMatrices();
        float bx = (uiW - boxWidth) / 2f;
        float by = (uiH - boxHeight) / 2f;

        float maxBtnWidth = btnW;
        for (RelativeComponent c : categories) {
            int textWidth = textRenderer().getWidth(c.name);
            float needed = textWidth + pad * 2f;
            if (needed > maxBtnWidth) {
                maxBtnWidth = needed;
            }
        }

        Color normal = features().get(Settings.class).map(Settings::getThemeNormal).orElse(Color.black());
        Color dark = features().get(Settings.class).map(Settings::getThemeDark).orElse(Color.black());
        Color light = features().get(Settings.class).map(Settings::getThemeLight).orElse(Color.black());
        Color accent1 = features().get(Settings.class).map(Settings::getThemeAccent1).orElse(Color.black());
        Color accent2 = features().get(Settings.class).map(Settings::getThemeAccent2).orElse(Color.black());
        Color accent3 = features().get(Settings.class).map(Settings::getThemeAccent3).orElse(Color.black());

        float menuW = maxBtnWidth + pad * 2f;

        render2DUtil().drawGlow(context, bx, by, bx + boxWidth, by + boxHeight, dark, rounding);

        render2DUtil().roundGradientFilled(matrices, bx, by, bx + boxWidth, by + boxHeight, rounding, dark, dark, false);

        matrices.push();
        context.enableScissor((int) bx, (int) by, (int) (bx + menuW), (int) (by + boxHeight));
        render2DUtil().roundGradientFilled(matrices, bx, by, bx + boxWidth, by + boxHeight, rounding, dark, normal, false);
        context.disableScissor();
        matrices.pop();

        float logoX = bx + (menuW - btnW) / 2f;
        float logoY = by + pad;

        render2DUtil().drawTexture(context,
                iconDark ? TextureStorage.icon_dark : TextureStorage.icon,
                logoX,
                logoY,
                logoX + btnW,
                logoY + btnW);

        float listX = bx + pad;
        float listY = by + btnW + pad * 2;

        int settingsIdx = -1;
        for (int i = 0; i < categories.size(); i++) {
            if ("Settings".equals(categories.get(i).name)) {
                settingsIdx = i;
                break;
            }
        }

        int drawIdx = 0;
        for (int i = 0; i < categories.size(); i++) {
            if (i == settingsIdx) continue;
            RelativeComponent c = categories.get(i);
            float byTop = listY + drawIdx * (btnH + btnGap);

            boolean hover = mouseX >= listX && mouseX <= listX + maxBtnWidth && mouseY >= byTop && mouseY <= byTop + btnH;

            if (hover) {
                render2DUtil().roundRectFilled(matrices, listX, byTop, listX + maxBtnWidth, byTop + btnH, rounding, new Color(dark.getRed(), dark.getGreen(), dark.getBlue(), 50));
            }

            float textX = listX + (maxBtnWidth - textRenderer().getWidth(c.name)) / 2f;
            float textY = byTop + (btnH - textRenderer().fontHeight) / 2f;

            if (i == selected) {
                render2DUtil().drawGlow(context, listX, byTop, listX + maxBtnWidth, byTop + btnH, accent2, rounding);
            }

            context.drawText(textRenderer(), c.name, (int) textX, (int) textY - (hover ? 1 : 0), light.getColor(), true);
            drawIdx++;
        }

        if (settingsIdx != -1) {
            float settingsY = by + boxHeight - pad - btnW - btnW - btnGap; // Move up to make room for HUD Editor button
            boolean hoverSettings = mouseX >= listX && mouseX <= listX + btnW && mouseY >= settingsY && mouseY <= settingsY + btnW;

            Color sStart;
            if (selected == settingsIdx) {
                sStart = accent2;
            } else if (hoverSettings) {
                sStart = accent1;
            } else {
                sStart = light;
            }

            render2DUtil().drawTextureColored(context, TextureStorage.cogs, listX, settingsY, listX + btnW, settingsY + btnW, new java.awt.Color(sStart.getRed(), sStart.getGreen(), sStart.getBlue(), sStart.getAlpha()).getRGB());
        }

        float hudEditorY = by + boxHeight - pad - btnW;
        boolean hoverHudEditor = mouseX >= listX && mouseX <= listX + btnW && mouseY >= hudEditorY && mouseY <= hudEditorY + btnW;

        Color hudEditorColor;
        if (hoverHudEditor) {
            hudEditorColor = accent1;
        } else {
            hudEditorColor = light;
        }

        render2DUtil().roundRectFilled(matrices, listX, hudEditorY, listX + btnW, hudEditorY + btnW, rounding, new Color(hudEditorColor.getRed(), hudEditorColor.getGreen(), hudEditorColor.getBlue(), hoverHudEditor ? 255 : 180));
        context.drawText(textRenderer(), "HUD", (int) (listX + (btnW - textRenderer().getWidth("HUD")) / 2f), (int) (hudEditorY + (btnW - textRenderer().fontHeight) / 2f), light.getColor(), true);

        float contentX = bx + menuW + pad;
        float contentY = by + pad;
        float contentW = boxWidth - (contentX - bx) - pad;
        float contentH = boxHeight - pad * 2f;

        if (!categories.isEmpty()) {
            RelativeComponent current = categories.get(selected);
            current.setPos(contentX, contentY);
            current.setDimensions(contentW, contentH);
            current.render(context, mouseX, mouseY, delta);
        }
    }

    public void mouseMoved(float mouseX, float mouseY) {
        if (categories.isEmpty()) return;
        RelativeComponent current = categories.get(selected);
        float bx = (uiW - boxWidth) / 2f;
        float by = (uiH - boxHeight) / 2f;
        current.mouseMoved(mouseX - bx, mouseY - by);
    }

    public void mouseClicked(float mouseX, float mouseY, int button) {
        float bx = (uiW - boxWidth) / 2f;
        float by = (uiH - boxHeight) / 2f;

        float ix = bx + pad;
        float iy = by + pad;
        if (mouseX >= ix && mouseX <= ix + btnW && mouseY >= iy && mouseY <= iy + btnW) {
            iconDark = !iconDark;
            return;
        }

        float maxBtnWidth = btnW;
        for (RelativeComponent c : categories) {
            int textWidth = textRenderer().getWidth(c.name);
            float needed = textWidth + pad * 2f;
            if (needed > maxBtnWidth) {
                maxBtnWidth = needed;
            }
        }

        float listX = bx + pad;
        float listY = by + btnW + pad * 2;

        int settingsIdx = -1;
        for (int i = 0; i < categories.size(); i++) {
            if ("Settings".equals(categories.get(i).name)) {
                settingsIdx = i;
                break;
            }
        }

        int drawIdx = 0;
        for (int i = 0; i < categories.size(); i++) {
            if (i == settingsIdx) continue;
            float byTop = listY + drawIdx * (btnH + btnGap);
            if (mouseX >= listX && mouseX <= listX + maxBtnWidth && mouseY >= byTop && mouseY <= byTop + btnH) {
                selected = i;
                return;
            }
            drawIdx++;
        }

        if (settingsIdx != -1) {
            float settingsX = listX;
            float settingsY = by + boxHeight - pad - btnW - btnW - btnGap;
            if (mouseX >= settingsX && mouseX <= settingsX + btnW && mouseY >= settingsY && mouseY <= settingsY + btnW) {
                selected = settingsIdx;
                return;
            }
        }

        float hudEditorX = listX;
        float hudEditorY = by + boxHeight - pad - btnW;
        if (mouseX >= hudEditorX && mouseX <= hudEditorX + btnW && mouseY >= hudEditorY && mouseY <= hudEditorY + btnW) {
            star.sequoia2.client.SeqClient.mc.setScreen(new star.sequoia2.gui.screen.HUDEditorScreen());
            return;
        }

        if (categories.isEmpty()) return;
        categories.get(selected).mouseClicked(mouseX, mouseY, button);
    }

    public void mouseReleased(float mouseX, float mouseY, int button) {
        if (categories.isEmpty()) return;
        float bx = (uiW - boxWidth) / 2f;
        float by = (uiH - boxHeight) / 2f;
        categories.get(selected).mouseReleased(mouseX - bx, mouseY - by, button);
    }

    public void mouseScrolled(float mouseX, float mouseY, double horizontalAmount, double verticalAmount) {
        if (categories.isEmpty()) return;
        float bx = (uiW - boxWidth) / 2f;
        float by = (uiH - boxHeight) / 2f;
        categories.get(selected).mouseScrolled(mouseX - bx, mouseY - by, horizontalAmount, verticalAmount);
    }

    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (categories.isEmpty()) return;
        categories.get(selected).keyPressed(keyCode, scanCode, modifiers);
    }

    public void keyReleased(int keyCode, int scanCode, int modifiers) {
        if (categories.isEmpty()) return;
        categories.get(selected).keyReleased(keyCode, scanCode, modifiers);
    }

    public void charTyped(char chr, int modifiers) {
        if (categories.isEmpty()) return;
        categories.get(selected).charTyped(chr, modifiers);
    }
}
