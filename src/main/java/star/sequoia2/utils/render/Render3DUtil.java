package star.sequoia2.utils.render;

import com.mojang.blaze3d.vertex.VertexFormat;
import mil.nga.color.Color;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import static star.sequoia2.client.SeqClient.mc;

public class Render3DUtil {
    public void drawBoxFilled(MatrixStack stack, Box box, Color color) {

        java.awt.Color c = new java.awt.Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());

        float minX = (float) (box.minX - mc.getEntityRenderDispatcher().camera.getCameraPos().getX());
        float minY = (float) (box.minY - mc.getEntityRenderDispatcher().camera.getCameraPos().getY());
        float minZ = (float) (box.minZ - mc.getEntityRenderDispatcher().camera.getCameraPos().getZ());
        float maxX = (float) (box.maxX - mc.getEntityRenderDispatcher().camera.getCameraPos().getX());
        float maxY = (float) (box.maxY - mc.getEntityRenderDispatcher().camera.getCameraPos().getY());
        float maxZ = (float) (box.maxZ - mc.getEntityRenderDispatcher().camera.getCameraPos().getZ());

        BufferBuilder bufferBuilder = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, minY, minZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, minY, minZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, minY, maxZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, minY, maxZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());

        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, maxY, minZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, maxY, maxZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, maxY, maxZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, maxY, minZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());

        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, minY, minZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, maxY, minZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, maxY, minZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, minY, minZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());

        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, minY, minZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, maxY, minZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, maxY, maxZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, minY, maxZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());

        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, minY, maxZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, minY, maxZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, maxY, maxZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, maxY, maxZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());

        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, minY, minZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, minY, maxZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, maxY, maxZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, maxY, minZ).color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());

        BuiltBuffer built = bufferBuilder.end();
        Layers.getGlobalQuads().draw(built);
    }

    public void drawBoxFilled(MatrixStack stack, Vec3d vec, Color c) {
        drawBoxFilled(stack, Box.from(vec), c);
    }

    public void drawBoxFilled(MatrixStack stack, BlockPos bp, Color c) {
        drawBoxFilled(stack, new Box(bp), c);
    }

    public void drawBox(MatrixStack stack, Box box, Color color, double lineWidth) {
        java.awt.Color c = new java.awt.Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());

        Vec3d cam = mc.getEntityRenderDispatcher().camera.getCameraPos();
        float minX = (float) (box.minX - cam.x);
        float minY = (float) (box.minY - cam.y);
        float minZ = (float) (box.minZ - cam.z);
        float maxX = (float) (box.maxX - cam.x);
        float maxY = (float) (box.maxY - cam.y);
        float maxZ = (float) (box.maxZ - cam.z);

        float r = c.getRed() / 255f;
        float g = c.getGreen() / 255f;
        float b = c.getBlue() / 255f;
        float a = c.getAlpha() / 255f;

        BufferBuilder bufferBuilder = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR);

        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, minY, minZ).color(r, g, b, a);
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, minY, minZ).color(r, g, b, a);

        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, minY, minZ).color(r, g, b, a);
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, minY, maxZ).color(r, g, b, a);

        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, minY, maxZ).color(r, g, b, a);
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, minY, maxZ).color(r, g, b, a);

        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, minY, maxZ).color(r, g, b, a);
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, minY, minZ).color(r, g, b, a);

        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, maxY, minZ).color(r, g, b, a);
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, maxY, minZ).color(r, g, b, a);

        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, maxY, minZ).color(r, g, b, a);
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, maxY, maxZ).color(r, g, b, a);

        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, maxY, maxZ).color(r, g, b, a);
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, maxY, maxZ).color(r, g, b, a);

        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, maxY, maxZ).color(r, g, b, a);
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, maxY, minZ).color(r, g, b, a);

        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, minY, minZ).color(r, g, b, a);
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, maxY, minZ).color(r, g, b, a);

        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, minY, minZ).color(r, g, b, a);
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, maxY, minZ).color(r, g, b, a);

        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, minY, maxZ).color(r, g, b, a);
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), maxX, maxY, maxZ).color(r, g, b, a);

        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, minY, maxZ).color(r, g, b, a);
        bufferBuilder.vertex(stack.peek().getPositionMatrix(), minX, maxY, maxZ).color(r, g, b, a);

        BuiltBuffer built = bufferBuilder.end();
        Layers.getGlobalLines(lineWidth).draw(built);
    }

    public void drawBox(MatrixStack stack, Vec3d vec, Color c, double lineWidth) {
        drawBox(stack, Box.from(vec), c, lineWidth);
    }

    public void drawBox(MatrixStack stack, BlockPos bp, Color c, double lineWidth) {
        drawBox(stack, new Box(bp), c, lineWidth);
    }

    public void drawLine(MatrixStack matrices, Vec3d start, Vec3d end, Color c, double lineWidth) {
        drawLine(matrices, start, end, c, lineWidth, false);
    }

    public void drawLine(MatrixStack matrices, Vec3d start, Vec3d end, Color c, double lineWidth, boolean depth) {

        if (depth) {
        }//fix ts

        BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR);

        float red   = c.getRed() / 255f;
        float green = c.getGreen() / 255f;
        float blue  = c.getBlue() / 255f;
        float alpha = c.getAlpha() / 255f;

        Vec3d camPos = mc.getEntityRenderDispatcher().camera.getCameraPos();

        bufferBuilder.vertex(matrices.peek().getPositionMatrix(), (float)(start.x - camPos.x), (float)(start.y - camPos.y), (float)(start.z - camPos.z))
                .color(red, green, blue, alpha);

        bufferBuilder.vertex(matrices.peek().getPositionMatrix(), (float)(end.x - camPos.x), (float)(end.y - camPos.y), (float)(end.z - camPos.z))
                .color(red, green, blue, alpha);

        BuiltBuffer built = bufferBuilder.end();
        Layers.getGlobalLines(lineWidth).draw(built);
    }

    public float getTickDelta() {
        return mc.getRenderTickCounter().getTickProgress(true);
    }

    public Vec3d lerp(Vec3d old, Vec3d current, float delta) {
        double x = old.x + (current.x - old.x) * delta;
        double y = old.y + (current.y - old.y) * delta;
        double z = old.z + (current.z - old.z) * delta;
        return new Vec3d(x, y, z);
    }
}
