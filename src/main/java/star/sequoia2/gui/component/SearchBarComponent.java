package star.sequoia2.gui.component;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;
import star.sequoia2.accessors.RenderUtilAccessor;
import star.sequoia2.accessors.TextRendererAccessor;
import star.sequoia2.features.impl.Settings;
import star.sequoia2.gui.categories.RelativeComponent;
import star.sequoia2.gui.screen.GuiRoot;
import org.lwjgl.glfw.GLFW;

import static star.sequoia2.client.SeqClient.mc;

@Getter @Setter
public class SearchBarComponent extends RelativeComponent implements TextRendererAccessor, RenderUtilAccessor {
    protected boolean searching = false;
    protected String search = "";

    public SearchBarComponent() {
        super("SearchBarComponent");
    }

    @Override
    public void render(DrawContext context, float mouseX, float mouseY, float delta) {
        int textColor = 0xFFFFFFFF;
        String display;
        GuiRoot root = getGuiRoot();
        float left = contentX();
        float top = contentY();
        float right = left + contentWidth();
        float bottom = top + contentHeight();

        context.enableScissor((int) left, (int) top, (int) right, (int) bottom);

        boolean showCaret = searching && ((System.currentTimeMillis() / 500L) % 2L == 0L);

        if (searching) {
            display = search.isEmpty() ? "" : search;
            features().get(Settings.class).map(Settings::getClickGui).orElseThrow().setCloseOnEscape(false);
        } else {
            display = "§8Click to search";
            features().get(Settings.class).map(Settings::getClickGui).orElseThrow().setCloseOnEscape(true);
        }

        float scale = 1.5f;

        context.getMatrices().push();
        context.getMatrices().translate(contentX() + (contentWidth() / 2) - (textRenderer().getWidth(display)), contentY() + (contentHeight() / 2) - textRenderer().fontHeight, 0);
        context.getMatrices().scale(scale, scale, 1.0f);

        render2DUtil().drawText(context, display + (showCaret ? "_" : ""), 0, 0, textColor, true);

        context.getMatrices().pop();

        context.disableScissor();
    }

    @Override
    public void mouseClicked(float mouseX, float mouseY, int button) {
        if (isWithinContent(mouseX, mouseY)) {
            searching = true;
        }
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F && modifiers == GLFW.GLFW_MOD_CONTROL) {
            searching = !searching;
            if (!searching) search = "";
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V) {
            String clipboard = mc.keyboard.getClipboard();
            search += clipboard;
            return;
        }
        if (searching) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searching = false;
                search = "";
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty()) {
                    search = search.substring(0, search.length() - 1);
                }
            }
        }
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        if (searching) {
            search += chr;
        }
    }

    public String getSearch() {
        return search;
    }
}
