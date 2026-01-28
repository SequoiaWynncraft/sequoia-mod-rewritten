package star.sequoia2.utils.render;

import com.mojang.blaze3d.vertex.VertexFormat;
import mil.nga.color.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import star.sequoia2.accessors.TextRendererAccessor;

import static star.sequoia2.client.SeqClient.mc;

public class Render2DUtil implements TextRendererAccessor {

    private final Matrix4f cachedViewMatrix = new Matrix4f();
    private final Matrix4f cachedProjectionMatrix = new Matrix4f();

    public Vector2f worldToScreen(Vector3f worldPos, Matrix4f viewMatrix, Matrix4f projectionMatrix, int screenWidth, int screenHeight, boolean allowBehind) {
        Vector4f clipSpacePos = new Vector4f(worldPos, 1.0f);
        viewMatrix.transform(clipSpacePos);
        projectionMatrix.transform(clipSpacePos);
        if (clipSpacePos.w == 0.0f) return null;
        boolean behind = clipSpacePos.w < 0.0f;
        float ndcX = clipSpacePos.x / clipSpacePos.w;
        float ndcY = clipSpacePos.y / clipSpacePos.w;
        if (behind && allowBehind) { ndcX = -ndcX; ndcY = -ndcY; }
        if (behind && !allowBehind) return null;
        float screenX = ((ndcX + 1.0f) / 2.0f) * screenWidth;
        float screenY = ((1.0f - ndcY) / 2.0f) * screenHeight;
        return new Vector2f(screenX, screenY);
    }

    public static double lerp(double previous, double current, double tickDelta) { return previous + (current - previous) * tickDelta; }
    public static float lerp(float previous, float current, float tickDelta) { return previous + (current - previous) * tickDelta; }

    public void render2DAtWorldPos(DrawContext context, double worldX, double worldY, double worldZ, float tickdelta, float scale, boolean behind, RenderCallback renderAction) {
        if (mc.getCameraEntity() == null) return;

        Camera cam = mc.gameRenderer.getCamera();
        Vec3d camPos = cam.getCameraPos();

        float relX = (float) (worldX - camPos.x);
        float relY = (float) (worldY - camPos.y);
        float relZ = (float) (worldZ - camPos.z);

        org.joml.Quaternionf q = new org.joml.Quaternionf(cam.getRotation());
        q.conjugate();
        cachedViewMatrix.identity().rotate(q);

        float fov = mc.gameRenderer.getFov(cam, tickdelta, true);
        cachedProjectionMatrix.identity().setPerspective(
                (float) Math.toRadians(fov),
                (float) mc.getWindow().getScaledWidth() / mc.getWindow().getScaledHeight(),
                0.05f,
                4096f
        );

        Vector2f screenPos = worldToScreen(
                new Vector3f(relX, relY, relZ),
                cachedViewMatrix,
                cachedProjectionMatrix,
                mc.getWindow().getScaledWidth(),
                mc.getWindow().getScaledHeight(),
                behind
        );
        if (screenPos == null) return;

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.scale(scale, scale);
        renderAction.handleRender((screenPos.x() / scale), (screenPos.y() / scale));
        matrices.popMatrix();
    }

    public interface RenderCallback {
        void handleRender(final float x, final float y);
    }

    public void drawText(DrawContext context, String text, float x, float y, int color, boolean shadow) {
        if ((color >>> 24) == 0) {
            color |= 0xFF000000;
        }
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        context.drawText(textRenderer(), text, 0, 0, color, shadow);
        matrices.popMatrix();
    }


    public void drawItem(DrawContext context, ItemStack stack, float x, float y) {
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        context.drawItem(stack, 0, 0);
        matrices.popMatrix();
    }

    public void roundRectFilled(Matrix3x2fStack matrices, float x, float y, float x2, float y2, float radius, Color color) {
        renderRoundedQuad(matrices, x, y, x2, y2, radius, color, 4);
    }

    public void renderRoundedQuad(Matrix3x2fStack matrices, double x, double y, double x2, double y2, double radius, Color c, double samples) {
        renderRoundedQuadInternal(matrices, c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f, x, y, x2, y2, radius, samples);
    }

    public void renderRoundedQuadInternal(Matrix3x2fStack matrices, float cr, float cg, float cb, float ca, double x, double y, double x2, double y2, double radius, double samples) {
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        double[][] map = new double[][]{
                {x2 - radius, y2 - radius, radius},
                {x2 - radius, y + radius,  radius},
                {x + radius,  y + radius,  radius},
                {x + radius,  y2 - radius, radius}
        };
        org.joml.Vector2f p = new org.joml.Vector2f();
        for (int i = 0; i < 4; i++) {
            double[] cur = map[i];
            double rad = cur[2];
            for (double r = i * 90d; r < (360 / 4d + i * 90d); r += (90 / samples)) {
                float rad1 = (float) Math.toRadians(r);
                float sin = (float) (Math.sin(rad1) * rad);
                float cos = (float) (Math.cos(rad1) * rad);
                float vx = (float) cur[0] + sin;
                float vy = (float) cur[1] + cos;
                matrices.transformPosition(vx, vy, p);
                b.vertex(p.x, p.y, 0.0F).color(cr, cg, cb, ca);
            }
            float rad1 = (float) Math.toRadians((360 / 4d + i * 90d));
            float sin = (float) (Math.sin(rad1) * rad);
            float cos = (float) (Math.cos(rad1) * rad);
            float vx = (float) cur[0] + sin;
            float vy = (float) cur[1] + cos;
            matrices.transformPosition(vx, vy, p);
            b.vertex(p.x, p.y, 0.0F).color(cr, cg, cb, ca);
        }
        BuiltBuffer built = b.end();
        Layers.getGlobalQuads().draw(built);
    }

    public void fill(Matrix3x2fStack matrices, double x, double y, double x2, double y2, int color) {
        double left = Math.min(x, x2);
        double right = Math.max(x, x2);
        double top = Math.min(y, y2);
        double bottom = Math.max(y, y2);
        float a = (float) ColorHelper.getAlpha(color) / 255.0f;
        float r = (float) ColorHelper.getRed(color) / 255.0f;
        float g = (float) ColorHelper.getGreen(color) / 255.0f;
        float b = (float) ColorHelper.getBlue(color) / 255.0f;
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        org.joml.Vector2f p = new org.joml.Vector2f();

        matrices.transformPosition((float) right, (float) bottom, p);
        buf.vertex(p.x, p.y, 0).color(r, g, b, a);

        matrices.transformPosition((float) right, (float) top, p);
        buf.vertex(p.x, p.y, 0).color(r, g, b, a);

        matrices.transformPosition((float) left, (float) top, p);
        buf.vertex(p.x, p.y, 0).color(r, g, b, a);

        matrices.transformPosition((float) left, (float) bottom, p);
        buf.vertex(p.x, p.y, 0).color(r, g, b, a);

        BuiltBuffer built = buf.end();
        Layers.getGlobalQuads().draw(built);
    }

    public void fillGradient(Matrix3x2fStack matrices, double x, double y, double x2, double y2, int colorStart, int colorEnd) {
        fillGradient(matrices, x, y, x2, y2, colorStart, colorEnd, 0);
    }

    protected void fillGradient(Matrix3x2fStack matrices, double x, double y, double x2, double y2, int colorStart, int colorEnd, int z) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        fillGradient(matrices, buffer, x, y, x2, y2, z, colorStart, colorEnd);
        BuiltBuffer built = buffer.end();
        Layers.getGlobalQuads().draw(built);
    }

    protected void fillGradient(Matrix3x2fStack matrices, BufferBuilder builder, double x, double y, double x2, double y2, double z, int colorStart, int colorEnd) {
        float sa = (float) ColorHelper.getAlpha(colorStart) / 255.0f;
        float sr = (float) ColorHelper.getRed(colorStart) / 255.0f;
        float sg = (float) ColorHelper.getGreen(colorStart) / 255.0f;
        float sb = (float) ColorHelper.getBlue(colorStart) / 255.0f;
        float ea = (float) ColorHelper.getAlpha(colorEnd) / 255.0f;
        float er = (float) ColorHelper.getRed(colorEnd) / 255.0f;
        float eg = (float) ColorHelper.getGreen(colorEnd) / 255.0f;
        float eb = (float) ColorHelper.getBlue(colorEnd) / 255.0f;
        org.joml.Vector2f p = new org.joml.Vector2f();

        matrices.transformPosition((float) x, (float) y, p);
        builder.vertex(p.x, p.y, (float) z).color(er, eg, eb, ea);

        matrices.transformPosition((float) x, (float) y2, p);
        builder.vertex(p.x, p.y, (float) z).color(er, eg, eb, ea);

        matrices.transformPosition((float) x2, (float) y2, p);
        builder.vertex(p.x, p.y, (float) z).color(sr, sg, sb, sa);

        matrices.transformPosition((float) x2, (float) y, p);
        builder.vertex(p.x, p.y, (float) z).color(sr, sg, sb, sa);
    }

    public void fillGradientQuad(Matrix3x2fStack matrices, float x, float y, float x2, float y2, int startColor, int endColor, boolean sideways) {
        float sa = (float) (startColor >>> 24 & 255) / 255.0F;
        float sr = (float) (startColor >>> 16 & 255) / 255.0F;
        float sg = (float) (startColor >>> 8 & 255) / 255.0F;
        float sb = (float) (startColor & 255) / 255.0F;
        float ea = (float) (endColor >>> 24 & 255) / 255.0F;
        float er = (float) (endColor >>> 16 & 255) / 255.0F;
        float eg = (float) (endColor >>> 8 & 255) / 255.0F;
        float eb = (float) (endColor & 255) / 255.0F;
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        org.joml.Vector2f p = new org.joml.Vector2f();

        if (sideways) {
            matrices.transformPosition(x, y, p);
            buf.vertex(p.x, p.y, 0.0F).color(sr, sg, sb, sa);

            matrices.transformPosition(x, y2, p);
            buf.vertex(p.x, p.y, 0.0F).color(sr, sg, sb, sa);

            matrices.transformPosition(x2, y2, p);
            buf.vertex(p.x, p.y, 0.0F).color(er, eg, eb, ea);

            matrices.transformPosition(x2, y, p);
            buf.vertex(p.x, p.y, 0.0F).color(er, eg, eb, ea);
        } else {
            matrices.transformPosition(x2, y, p);
            buf.vertex(p.x, p.y, 0.0F).color(sr, sg, sb, sa);

            matrices.transformPosition(x, y, p);
            buf.vertex(p.x, p.y, 0.0F).color(sr, sg, sb, sa);

            matrices.transformPosition(x, y2, p);
            buf.vertex(p.x, p.y, 0.0F).color(er, eg, eb, ea);

            matrices.transformPosition(x2, y2, p);
            buf.vertex(p.x, p.y, 0.0F).color(er, eg, eb, ea);
        }

        BuiltBuffer built = buf.end();
        Layers.getGlobalQuads().draw(built);
    }

    public void drawGradient(Matrix3x2fStack matrices, int x, int y, int x2, int y2, int z, float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        drawGradientQuad(matrices, x, x2, y, y2, z,
                (u + 0.0F) / (float) textureWidth,
                (u + (float) regionWidth) / (float) textureWidth,
                (v + 0.0F) / (float) textureHeight,
                (v + (float) regionHeight) / (float) textureHeight);
    }

    private void drawGradientQuad(Matrix3x2fStack matrices, int x, int x2, int y, int y2, int z, float u0, float u1, float v0, float v1) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        org.joml.Vector2f p = new org.joml.Vector2f();

        matrices.transformPosition((float) x, (float) y, p);
        buffer.vertex(p.x, p.y, (float) z).texture(u0, v0);

        matrices.transformPosition((float) x, (float) y2, p);
        buffer.vertex(p.x, p.y, (float) z).texture(u0, v1);

        matrices.transformPosition((float) x2, (float) y2, p);
        buffer.vertex(p.x, p.y, (float) z).texture(u1, v1);

        matrices.transformPosition((float) x2, (float) y, p);
        buffer.vertex(p.x, p.y, (float) z).texture(u1, v0);

        BuiltBuffer built = buffer.end();
        Layers.getGlobalQuads().draw(built);
    }

    public void roundGradientFilled(Matrix3x2fStack matrixStack, float x, float y, float x2, float y2, float radius, Color startColor, Color endColor, boolean sideways) {
        renderRoundedGradientQuad(matrixStack, x, y, x2, y2, radius, 5,
                new java.awt.Color(startColor.getRed(), startColor.getGreen(), startColor.getBlue(), startColor.getAlpha()).getRGB(),
                new java.awt.Color(endColor.getRed(), endColor.getGreen(), endColor.getBlue(), endColor.getAlpha()).getRGB(), sideways);
    }

    public void renderRoundedGradientQuad(Matrix3x2fStack matrices,
                                          float x, float y, float x2, float y2,
                                          float radius, float samples,
                                          int startColor, int endColor,
                                          boolean sideways) {
        float w = Math.max(0.0f, x2 - x);
        float h = Math.max(0.0f, y2 - y);
        radius = Math.max(0.0f, Math.min(radius, Math.min(w, h) * 0.5f));

        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        org.joml.Vector2f p = new org.joml.Vector2f();

        float cx = (float) ((x + x2) * 0.5);
        float cy = (float) ((y + y2) * 0.5);
        float tCenter = sideways ? (float) ((cx - x) / Math.max(1e-6, (x2 - x))) : (float) ((cy - y) / Math.max(1e-6, (y2 - y)));
        float[] cc = lerpColor(startColor, endColor, tCenter);
        matrices.transformPosition(cx, cy, p);
        buf.vertex(p.x, p.y, 0.0f).color(cc[0], cc[1], cc[2], cc[3]);

        double[][] corners = new double[][]{
                {x2 - radius, y2 - radius, radius},
                {x2 - radius, y + radius,  radius},
                {x + radius,  y + radius,  radius},
                {x + radius,  y2 - radius, radius}
        };
        float firstPx = 0f, firstPy = 0f; boolean firstSet = false;
        for (int i = 0; i < 4; i++) {
            double[] c = corners[i];
            double cxArc = c[0], cyArc = c[1], rad = c[2];
            for (double deg = i * 90.0; deg <= (i + 1) * 90.0; deg += (90.0 / Math.max(1.0, samples))) {
                float r = (float) Math.toRadians(deg);
                float px = (float) (cxArc + Math.sin(r) * rad);
                float py = (float) (cyArc + Math.cos(r) * rad);
                float t = sideways ? (float) ((px - x) / Math.max(1e-6, (x2 - x))) : (float) ((py - y) / Math.max(1e-6, (y2 - y)));
                float[] col = lerpColor(startColor, endColor, t);
                if (!firstSet) { firstPx = px; firstPy = py; firstSet = true; }
                matrices.transformPosition(px, py, p);
                buf.vertex(p.x, p.y, 0.0f).color(col[0], col[1], col[2], col[3]);
            }
        }
        {
            float t = sideways ? (float) ((firstPx - x) / Math.max(1e-6, (x2 - x))) : (float) ((firstPy - y) / Math.max(1e-6, (y2 - y)));
            float[] col = lerpColor(startColor, endColor, t);
            matrices.transformPosition(firstPx, firstPy, p);
            buf.vertex(p.x, p.y, 0.0f).color(col[0], col[1], col[2], col[3]);
        }

        BuiltBuffer built = buf.end();
        Layers.getGlobalQuads().draw(built);
    }

    public void drawGlow(DrawContext ctx, float x1, float y1, float x2, float y2, Color baseColor, float radius) {

        final int layers = 20;
        final float maxExpand = 7f;

        for (int i = 1; i <= layers; i++) {
            float t = (float) i / layers;
            float expand = t * maxExpand;
            float a = (1f - t) * (1f - t) * 0.35f * 0.6f;

            Color c = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(),
                    Math.min(255, Math.max(0, (int) (a * baseColor.getAlpha()))));

            roundRectFilled(
                    ctx.getMatrices(),
                    x1 - expand, y1 - expand,
                    x2 + expand, y2 + expand,
                    radius + expand * 0.6f,
                    c
            );
        }

    }

    public void drawTexture(DrawContext context, Identifier texture, float x, float y, float x2, float y2) {
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        context.drawTexture(
                net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                texture,
                0,
                0,
                0f,
                0f,
                (int) (x2 - x),
                (int) (y2 - y),
                (int) (x2 - x),
                (int) (y2 - y)
        );
        matrices.popMatrix();
    }

    public void drawTextureColored(DrawContext context, Identifier texture, float x, float y, float x2, float y2, int color) {
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        context.drawTexture(
                net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                texture,
                0,
                0,
                0f,
                0f,
                (int) (x2 - x),
                (int) (y2 - y),
                (int) (x2 - x),
                (int) (y2 - y),
                color
        );
        matrices.popMatrix();
    }

    private static float[] lerpColor(int startARGB, int endARGB, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a0 = (startARGB >>> 24) & 0xFF, r0 = (startARGB >>> 16) & 0xFF, g0 = (startARGB >>> 8) & 0xFF, b0 = (startARGB) & 0xFF;
        int a1 = (endARGB >>> 24) & 0xFF, r1 = (endARGB >>> 16) & 0xFF, g1 = (endARGB >>> 8) & 0xFF, b1 = (endARGB) & 0xFF;
        float a = (a0 + (a1 - a0) * t) / 255f;
        float r = (r0 + (r1 - r0) * t) / 255f;
        float g = (g0 + (g1 - g0) * t) / 255f;
        float b = (b0 + (b1 - b0) * t) / 255f;
        return new float[]{r, g, b, a};
    }
}
