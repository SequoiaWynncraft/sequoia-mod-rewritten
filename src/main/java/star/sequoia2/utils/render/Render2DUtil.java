package star.sequoia2.utils.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mil.nga.color.Color;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.*;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import star.sequoia2.accessors.TextRendererAccessor;
import star.sequoia2.gui.screen.ClickGUIScreen;

import static star.sequoia2.client.SeqClient.mc;

public class Render2DUtil implements TextRendererAccessor {

    public static Vec3d lastCameraPos;
    private static Float lastYaw = null;
    private static Float lastPitch = null;
    private float fovMultiplier = 1.0F;

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

    private Matrix4f getViewMatrixFromEntity(Entity entity, float tickDelta) {
        if (lastCameraPos == null) lastCameraPos = mc.gameRenderer.getCamera().getPos();
        double cameraInterpX = lerp(lastCameraPos.getX(), mc.gameRenderer.getCamera().getPos().getX(), tickDelta);
        double cameraInterpY = lerp(lastCameraPos.getY(), mc.gameRenderer.getCamera().getPos().getY(), tickDelta);
        double cameraInterpZ = lerp(lastCameraPos.getZ(), mc.gameRenderer.getCamera().getPos().getZ(), tickDelta);
        lastCameraPos = mc.gameRenderer.getCamera().getPos();

        float currentYaw = entity.getYaw();
        float currentPitch = entity.getPitch();
        if (lastYaw == null) lastYaw = currentYaw;
        if (lastPitch == null) lastPitch = currentPitch;
        float interpYaw = lerp(lastYaw, currentYaw, tickDelta);
        float interpPitch = lerp(lastPitch, currentPitch, tickDelta);
        lastYaw = currentYaw; lastPitch = currentPitch;
        float safePitch = Math.max(-89.9f, Math.min(interpPitch, 89.9f));

        Vector3f up = new Vector3f(0, 1, 0);
        float yawRad = (float) Math.toRadians(interpYaw);
        float pitchRad = (float) Math.toRadians(safePitch);
        float forwardX = -(float) Math.cos(pitchRad) * (float) Math.sin(yawRad);
        float forwardY = -(float) Math.sin(pitchRad);
        float forwardZ = (float) Math.cos(pitchRad) * (float) Math.cos(yawRad);
        Vector3f forward = new Vector3f(forwardX, forwardY, forwardZ);

        Vector3f position;
        Vector3f target;
        if (mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT) {
            position = new Vector3f((float) cameraInterpX, (float) cameraInterpY, (float) cameraInterpZ);
            target = new Vector3f(position).sub(forward);
        } else {
            position = new Vector3f((float) cameraInterpX, (float) cameraInterpY, (float) cameraInterpZ);
            target = new Vector3f(position).add(forward);
        }
        return new Matrix4f().lookAt(position, target, up);
    }

    public void updateFov() {
        float targetFovMult = 1.0F;
        if (mc.cameraEntity instanceof AbstractClientPlayerEntity entity) {
            targetFovMult = entity.getFovMultiplier(mc.options.getPerspective().isFirstPerson(), mc.options.getFovEffectScale().getValue().floatValue());
        }
        float smoothingFactor = 0.01F;
        fovMultiplier += (targetFovMult - fovMultiplier) * smoothingFactor;
    }

    public void render2DAtWorldPos(DrawContext context, double worldX, double worldY, double worldZ, float tickdelta, float scale, boolean behind, RenderCallback renderAction) {
        if (mc.cameraEntity == null) return;
        Matrix4f viewMatrix = getViewMatrixFromEntity(mc.player, tickdelta);
        updateFov();
        Matrix4f projectionMatrix = new Matrix4f().perspective((float) Math.toRadians(mc.options.getFov().getValue() * fovMultiplier), (float) mc.getWindow().getWidth() / mc.getWindow().getHeight(), 0.1f, 64000000f);
        Vector2f screenPos = worldToScreen(new Vector3f((float) worldX, (float) worldY, (float) worldZ), viewMatrix, projectionMatrix, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight(), behind);
        if (screenPos == null) return;
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.scale(scale, scale, scale);
        renderAction.handleRender((screenPos.getX() / scale) / 2, (screenPos.getY() / scale) / 2);
        matrices.pop();
    }

    public interface RenderCallback {
        void handleRender(final float x, final float y);
    }

    public void drawText(DrawContext context, String text, float x, float y, int color, boolean shadow) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
        context.drawText(textRenderer(), text, 0, 0, color, shadow);
        matrices.pop();
    }

    public void drawItem(DrawContext context, ItemStack stack, float x, float y) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
        context.drawItem(stack, 0, 0);
        matrices.pop();
    }

    public void roundRectFilled(MatrixStack matrices, float x, float y, float x2, float y2, float radius, Color color) {
        renderRoundedQuad(matrices, x, y, x2, y2, radius, color, 4);
    }

    public void setupRender() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public void endRender() {
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public void setRectanglePoints(BufferBuilder buffer, MatrixStack matrices, float x, float y, float x2, float y2) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        buffer.vertex(m, x,  y,  0);
        buffer.vertex(m, x,  y2, 0);
        buffer.vertex(m, x2, y2, 0);
        buffer.vertex(m, x2, y,  0);
    }

    public void quarterCircle(MatrixStack matrices, float x, float y, float x2, float y2, int color, int rotation) {
        Matrix4f posMatrix = matrices.peek().getPositionMatrix();
        float w = Math.max(0f, x2 - x);
        float h = Math.max(0f, y2 - y);
        float radius = Math.min(w, h);
        float cx, cy;
        switch (rotation) {
            case 1 -> { cx = x2 - radius; cy = y2 - radius; }
            case 2 -> { cx = x2 - radius; cy = y + radius; }
            case 3 -> { cx = x + radius; cy = y + radius; }
            case 4 -> { cx = x + radius; cy = y2 - radius; }
            default -> { cx = x + radius; cy = y + radius; }
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder outline = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        double base = 90.0 * (rotation - 1);
        for (double deg = base; deg < base + 90.0; deg += 0.5) {
            float r = (float) Math.toRadians(deg);
            float px = (float) (cx + Math.sin(r) * radius);
            float py = (float) (cy + Math.cos(r) * radius);
            outline.vertex(posMatrix, px, py, 0.0F).color(color);
        }
        BufferRenderer.drawWithGlobalProgram(outline.end());

        BufferBuilder tri = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        float vx1, vy1, vx2, vy2;
        switch (rotation) {
            case 1 -> { vx1 = x2 - radius; vy1 = y2; vx2 = x2; vy2 = y2 - radius; }
            case 2 -> { vx1 = x2 - radius; vy1 = y;  vx2 = x2; vy2 = y + radius; }
            case 3 -> { vx1 = x + radius; vy1 = y;  vx2 = x;  vy2 = y + radius; }
            case 4 -> { vx1 = x + radius; vy1 = y2; vx2 = x;  vy2 = y2 - radius; }
            default -> { vx1 = x; vy1 = y; vx2 = x; vy2 = y; }
        }
        tri.vertex(posMatrix, vx1, vy1, 0.0F).color(color);
        tri.vertex(posMatrix, vx2, vy2, 0.0F).color(color);
        BufferRenderer.drawWithGlobalProgram(tri.end());
        RenderSystem.disableBlend();
    }

    public void renderRoundedQuad(MatrixStack matrices, double x, double y, double x2, double y2, double radius, Color c, double samples) {
        setupRender();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        renderRoundedQuadInternal(matrices, c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f, x, y, x2, y2, radius, samples);
        endRender();
    }

    public void renderRoundedQuadInternal(MatrixStack matrices, float cr, float cg, float cb, float ca, double x, double y, double x2, double y2, double radius, double samples) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        double[][] map = new double[][]{
                {x2 - radius, y2 - radius, radius},
                {x2 - radius, y + radius,  radius},
                {x + radius,  y + radius,  radius},
                {x + radius,  y2 - radius, radius}
        };
        for (int i = 0; i < 4; i++) {
            double[] cur = map[i];
            double rad = cur[2];
            for (double r = i * 90d; r < (360 / 4d + i * 90d); r += (90 / samples)) {
                float rad1 = (float) Math.toRadians(r);
                float sin = (float) (Math.sin(rad1) * rad);
                float cos = (float) (Math.cos(rad1) * rad);
                b.vertex(m, (float) cur[0] + sin, (float) cur[1] + cos, 0.0F).color(cr, cg, cb, ca);
            }
            float rad1 = (float) Math.toRadians((360 / 4d + i * 90d));
            float sin = (float) (Math.sin(rad1) * rad);
            float cos = (float) (Math.cos(rad1) * rad);
            b.vertex(m, (float) cur[0] + sin, (float) cur[1] + cos, 0.0F).color(cr, cg, cb, ca);
        }
        BufferRenderer.drawWithGlobalProgram(b.end());
    }

    public void fill(MatrixStack matrices, double x, double y, double x2, double y2, int color) {
        double left = Math.min(x, x2);
        double right = Math.max(x, x2);
        double top = Math.min(y, y2);
        double bottom = Math.max(y, y2);
        float a = (float) ColorHelper.getAlpha(color) / 255.0f;
        float r = (float) ColorHelper.getRed(color) / 255.0f;
        float g = (float) ColorHelper.getGreen(color) / 255.0f;
        float b = (float) ColorHelper.getBlue(color) / 255.0f;
        Matrix4f m = matrices.peek().getPositionMatrix();
        BufferBuilder buf = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        RenderSystem.enableBlend();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        buf.vertex(m, (float) right,  (float) bottom, 0).color(r, g, b, a);
        buf.vertex(m, (float) right,  (float) top,    0).color(r, g, b, a);
        buf.vertex(m, (float) left,   (float) top,    0).color(r, g, b, a);
        buf.vertex(m, (float) left,   (float) bottom, 0).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    public void fillGradient(MatrixStack matrices, double x, double y, double x2, double y2, int colorStart, int colorEnd) {
        fillGradient(matrices, x, y, x2, y2, colorStart, colorEnd, 0);
    }

    protected void fillGradient(MatrixStack matrices, double x, double y, double x2, double y2, int colorStart, int colorEnd, int z) {
        RenderSystem.enableBlend();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        fillGradient(matrices, buffer, x, y, x2, y2, z, colorStart, colorEnd);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }

    protected void fillGradient(MatrixStack matrices, BufferBuilder builder, double x, double y, double x2, double y2, double z, int colorStart, int colorEnd) {
        float sa = (float) ColorHelper.getAlpha(colorStart) / 255.0f;
        float sr = (float) ColorHelper.getRed(colorStart) / 255.0f;
        float sg = (float) ColorHelper.getGreen(colorStart) / 255.0f;
        float sb = (float) ColorHelper.getBlue(colorStart) / 255.0f;
        float ea = (float) ColorHelper.getAlpha(colorEnd) / 255.0f;
        float er = (float) ColorHelper.getRed(colorEnd) / 255.0f;
        float eg = (float) ColorHelper.getGreen(colorEnd) / 255.0f;
        float eb = (float) ColorHelper.getBlue(colorEnd) / 255.0f;
        Matrix4f m = matrices.peek().getPositionMatrix();
        builder.vertex(m, (float) x,  (float) y,  (float) z).color(er, eg, eb, ea);
        builder.vertex(m, (float) x,  (float) y2, (float) z).color(er, eg, eb, ea);
        builder.vertex(m, (float) x2, (float) y2, (float) z).color(sr, sg, sb, sa);
        builder.vertex(m, (float) x2, (float) y,  (float) z).color(sr, sg, sb, sa);
    }

    public void fillGradientQuad(MatrixStack matrices, float x, float y, float x2, float y2, int startColor, int endColor, boolean sideways) {
        float sa = (float) (startColor >>> 24 & 255) / 255.0F;
        float sr = (float) (startColor >>> 16 & 255) / 255.0F;
        float sg = (float) (startColor >>> 8 & 255) / 255.0F;
        float sb = (float) (startColor & 255) / 255.0F;
        float ea = (float) (endColor >>> 24 & 255) / 255.0F;
        float er = (float) (endColor >>> 16 & 255) / 255.0F;
        float eg = (float) (endColor >>> 8 & 255) / 255.0F;
        float eb = (float) (endColor & 255) / 255.0F;
        Matrix4f m = matrices.peek().getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        if (sideways) {
            buf.vertex(m, x,  y,  0.0F).color(sr, sg, sb, sa);
            buf.vertex(m, x,  y2, 0.0F).color(sr, sg, sb, sa);
            buf.vertex(m, x2, y2, 0.0F).color(er, eg, eb, ea);
            buf.vertex(m, x2, y,  0.0F).color(er, eg, eb, ea);
        } else {
            buf.vertex(m, x2, y,  0.0F).color(sr, sg, sb, sa);
            buf.vertex(m, x,  y,  0.0F).color(sr, sg, sb, sa);
            buf.vertex(m, x,  y2, 0.0F).color(er, eg, eb, ea);
            buf.vertex(m, x2, y2, 0.0F).color(er, eg, eb, ea);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    public void drawGradient(MatrixStack matrices, int x, int y, int x2, int y2, int z, float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        drawGradientQuad(matrices, x, x2, y, y2, z,
                (u + 0.0F) / (float) textureWidth,
                (u + (float) regionWidth) / (float) textureWidth,
                (v + 0.0F) / (float) textureHeight,
                (v + (float) regionHeight) / (float) textureHeight);
    }

    private void drawGradientQuad(MatrixStack matrices, int x, int x2, int y, int y2, int z, float u0, float u1, float v0, float v1) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(m, (float) x,  (float) y,  (float) z).texture(u0, v0);
        buffer.vertex(m, (float) x,  (float) y2, (float) z).texture(u0, v1);
        buffer.vertex(m, (float) x2, (float) y2, (float) z).texture(u1, v1);
        buffer.vertex(m, (float) x2, (float) y,  (float) z).texture(u1, v0);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    public void roundGradientFilled(MatrixStack matrixStack, float x, float y, float x2, float y2, float radius, Color startColor, Color endColor, boolean sideways) {
        renderRoundedGradientQuad(matrixStack, x, y, x2, y2, radius, 5,
                new java.awt.Color(startColor.getRed(), startColor.getGreen(), startColor.getBlue(), startColor.getAlpha()).getRGB(),
                new java.awt.Color(endColor.getRed(), endColor.getGreen(), endColor.getBlue(), endColor.getAlpha()).getRGB(), sideways);
    }

    public void renderRoundedGradientQuad(MatrixStack matrices,
                                          float x, float y, float x2, float y2,
                                          float radius, float samples,
                                          int startColor, int endColor,
                                          boolean sideways) {
        float w = Math.max(0.0f, x2 - x);
        float h = Math.max(0.0f, y2 - y);
        radius = Math.max(0.0f, Math.min(radius, Math.min(w, h) * 0.5f));
        setupRender();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        Matrix4f m = matrices.peek().getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        float cx = (float) ((x + x2) * 0.5);
        float cy = (float) ((y + y2) * 0.5);
        float tCenter = sideways ? (float) ((cx - x) / Math.max(1e-6, (x2 - x))) : (float) ((cy - y) / Math.max(1e-6, (y2 - y)));
        float[] cc = lerpColor(startColor, endColor, tCenter);
        buf.vertex(m, cx, cy, 0.0f).color(cc[0], cc[1], cc[2], cc[3]);
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
                buf.vertex(m, px, py, 0.0f).color(col[0], col[1], col[2], col[3]);
            }
        }
        {
            float t = sideways ? (float) ((firstPx - x) / Math.max(1e-6, (x2 - x))) : (float) ((firstPy - y) / Math.max(1e-6, (y2 - y)));
            float[] col = lerpColor(startColor, endColor, t);
            buf.vertex(m, firstPx, firstPy, 0.0f).color(col[0], col[1], col[2], col[3]);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());
        endRender();
    }

    public void drawGlow(DrawContext ctx, float x1, float y1, float x2, float y2, Color baseColor, float radius) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);

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

        RenderSystem.disableBlend();
    }

    public void drawTexture(DrawContext context, Identifier texture, float x, float y, float x2, float y2) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
        context.drawTexture(RenderLayer::getGuiTextured , texture, 0, 0, 0f, 0f, (int) (x2 - x), (int) (y2 - y), (int) (x2 - x), (int) (y2 - y));
        matrices.pop();
    }

    public void drawTextureColored(DrawContext context, Identifier texture, float x, float y, float x2, float y2, int color) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
        context.drawTexture(RenderLayer::getGuiTextured , texture, 0, 0, 0f, 0f, (int) (x2 - x), (int) (y2 - y), (int) (x2 - x), (int) (y2 - y), color);
        matrices.pop();
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

    public void enableScissor(int x, int y, int x2, int y2) {
        setScissor(ClickGUIScreen.SCISSOR_STACK.push(new ScreenRect(x, y, x2 - x, y2 - y)));
    }

    public void disableScissor() { setScissor(ClickGUIScreen.SCISSOR_STACK.pop()); }

    private void setScissor(ScreenRect rect) {
        if (rect != null) {
            Window window = mc.getWindow();
            int i = window.getFramebufferHeight();
            double d = window.getScaleFactor();
            double e = (double) rect.getLeft() * d;
            double f = (double) i - (double) rect.getBottom() * d;
            double g = (double) rect.width() * d;
            double h = (double) rect.height() * d;
            RenderSystem.enableScissor((int) e, (int) f, Math.max(0, (int) g), Math.max(0, (int) h));
        } else {
            RenderSystem.disableScissor();
        }
    }
}
