package star.sequoia2.gui.component;

import star.sequoia2.accessors.RenderUtilAccessor;
import star.sequoia2.accessors.TextRendererAccessor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;

@Getter
public abstract class Component implements Drawable, TextRendererAccessor, RenderUtilAccessor {

    protected float x;
    protected float y;
    @Setter
    protected float width;
    @Setter
    protected float height;

    @Override
    public abstract void render(DrawContext context, float mouseX, float mouseY, float delta);

    protected void renderText(DrawContext context, String text, float x, float y, int color) {
        renderText(context, text, x, y, color, true);
    }

    protected void renderText(DrawContext context, String text, float x, float y, int color, boolean shadow) {
        render2DUtil().drawText(context, text, (int) x, (int) y, color, shadow);
    }

    public boolean isWithin(double xval, double yval) {
        return isWithin((float) xval, (float) yval);
    }

    public boolean isWithin(float xval, float yval) {
        return isWithin(xval, yval, x, y, width, height);
    }

    public boolean isWithin(double mouseX, double mouseY, double x, double y, double width, double height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void setPos(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void setDimensions(float width, float height) {
        setWidth(width);
        setHeight(height);
    }
}
