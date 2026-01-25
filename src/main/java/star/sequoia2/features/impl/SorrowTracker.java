package star.sequoia2.features.impl;

import com.collarmc.pounce.Subscribe;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import star.sequoia2.accessors.RenderUtilAccessor;
import star.sequoia2.accessors.TextRendererAccessor;
import star.sequoia2.events.PacketEvent;
import star.sequoia2.events.Render2DEvent;
import star.sequoia2.events.Render3DEvent;
import star.sequoia2.events.input.KeyEvent;
import star.sequoia2.features.ToggleFeature;
import star.sequoia2.settings.Binding;
import star.sequoia2.settings.types.BooleanSetting;
import star.sequoia2.settings.types.ColorSetting;
import star.sequoia2.settings.types.FloatSetting;
import star.sequoia2.settings.types.KeybindSetting;
import star.sequoia2.utils.Timer;

import static star.sequoia2.client.SeqClient.mc;

public class SorrowTracker extends ToggleFeature implements RenderUtilAccessor, TextRendererAccessor {

    BooleanSetting renderCustom = settings().bool("Render", "to render custom sorrow or not", false);
    BooleanSetting renderTimer = settings().bool("RenderTimer", "to render custom sorrow timer or not", true);

    FloatSetting xOffset = settings().number("XOffset", "Horizontal offset for sorrow timer", 0f, -500f, 500f);
    FloatSetting yOffset = settings().number("YOffset", "Vertical offset for sorrow timer", 0f, -500f, 500f);
    FloatSetting scale = settings().number("Scale", "Scale of the sorrow timer text", 1.0f, 0.5f, 5.0f);

    ColorSetting color = settings().color("Color", "color of the sorrow", new mil.nga.color.Color(255, 0, 0));

    BooleanSetting useBind = settings().bool("UseBind", "Wether to check for bind used", true);
    KeybindSetting keybind = settings().binding("SorrowBind", "Bind for detecting sorrow", Binding.none());

    BooleanSetting sneak = settings().bool("CheckSneaking", "toggle to check sneaking to detect sorrow", false);

    FloatSetting duration = settings().number("Duration", "Duration of the sorrow seconds", 1.6f, 0.1f, 20f);
    FloatSetting delay = settings().number("Delay", "Delay before sorrow starts seconds", 1.0f, 0.1f, 20f);

    private final Timer timer;
    private final Timer sorrowTimer;

    public SorrowTracker() {
        super("SorrowTracker", "Custom visuals for blood sorrow");
        timer = new Timer();
        sorrowTimer = new Timer();
        timer.reset();
    }

    @Subscribe
    public void onPacketReceive(PacketEvent.PacketReceiveEvent event) {
        if (!useBind.get() &&mc.player != null && event.packet() instanceof PlaySoundS2CPacket soundPacket && soundPacket.getSound().value().id().equals(SoundEvents.ENTITY_WITHER_SHOOT.id()) && (!sneak.get() || mc.player.isSneaking())) {
            sorrowTimer.reset();
        }
    }

    @Subscribe
    public void onKeyDown(KeyEvent event) {
        if (!useBind.get()) return;
        if (event.isKeyDown() && keybind.get().matches(event) && mc.currentScreen == null && (!sneak.get() || mc.player != null && mc.player.isSneaking())) {
            sorrowTimer.reset();
        }
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null || !renderTimer.get()) return;
        if (sorrowTimer.passed((long) ((delay.get() + duration.get()) * 1000L)) || !sorrowTimer.passed((long) (delay.get() * 1000L))) return;

        long elapsed = sorrowTimer.getPassed();
        long endMs = (long) ((delay.get() + duration.get()) * 1000L);
        long remainMs = Math.max(0L, endMs - elapsed);
        float secs = remainMs / 1000f;

        String text = String.format("%.1f", secs);
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        int tw = textRenderer().getWidth(text);
        int packed = color.get().getColorWithAlpha();

        float x = (w - tw) / 2f + xOffset.get();
        float y = h / 2f - 4 + yOffset.get();

        Matrix3x2fStack matrices = event.context().getMatrices();
        matrices.pushMatrix();
        float s = scale.get();
        matrices.scale(s, s);
        render2DUtil().drawText(event.context(), text, x / s, y / s, packed, true);
        matrices.popMatrix();
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null || !renderCustom.get()) return;
        if (sorrowTimer.passed((long) ((delay.get() + duration.get()) * 1000L)) || !sorrowTimer.passed((long) (delay.get() * 1000L))) return;

        float delta = render3DUtil().getTickDelta();

        Vec3d stomach = render3DUtil().lerp(mc.player.getLastRenderPos().add(0, 1, 0), mc.player.getEntityPos().add(0, 1, 0), delta);

        Vec3d forward = mc.player.getRotationVec(delta).normalize();

        Vec3d upWorld = new Vec3d(0, 1, 0);

        float yawDeg = mc.player.getYaw(delta);
        double yaw = Math.toRadians(yawDeg);
        Vec3d forwardFlat = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3d right = upWorld.crossProduct(forwardFlat).normalize();
        Vec3d up = upWorld;

        double side = 0;
        double height = mc.player.getHeight() - 1.5;
        double ahead = 0.01;

        if (mc.player.getMainArm() == Arm.RIGHT) side = -side;

        Vec3d start = stomach
                .add(right.multiply(side))
                .add(up.multiply(height))
                .add(forwardFlat.multiply(ahead));

        Vec3d end = start.add(forward.multiply(20.0));
        int packedColor = color.get().getColorWithAlpha();

        render3DUtil().drawLine(event.matrices(), start, end, color.get(), 4f, true);

        final int ringCount = 6;
        final float baseRadius = 0.1f;
        for (int i = 0; i < ringCount; i++) {
            float phaseOffset = i / (float) ringCount;
            drawCircle(event, start, end, baseRadius, phaseOffset, packedColor);
        }
    }

    private void drawCircle(Render3DEvent event, Vec3d start, Vec3d end, float baseRadius, float phaseOffset, int packedColor) {
        final float segStart = 0.02f, segEnd = 0.80f;
        final float segLen   = segEnd - segStart;
        final float segMid   = (segStart + segEnd) * 0.5f;
        final float segHalf  = segLen * 0.5f;

        float seconds = timer.getPassed() / 1500.0f;
        final float cyclesPerSecond = 0.6f;
        float slide01 = (seconds * cyclesPerSecond + phaseOffset) % 1.0f;
        float tOnLine = segStart + slide01 * segLen;

        float distNorm = Math.abs(tOnLine - segMid) / segHalf;
        float grow     = 1.0f - (float) Math.pow(distNorm, 2.0);
        final float extraRadius = 0.22f;
        float radius = baseRadius + extraRadius * grow;

        Vec3d axis   = end.subtract(start).normalize();
        Vec3d center = start.lerp(end, tOnLine);
        Vec3d ref = Math.abs(axis.dotProduct(new Vec3d(0, 1, 0))) > 0.99 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d u = axis.crossProduct(ref).normalize();
        Vec3d v = axis.crossProduct(u).normalize();

        float spinRad = (float) (seconds * Math.PI * 2.0 * 0.35);
        double cs = Math.cos(spinRad), sn = Math.sin(spinRad);
        Vec3d ur = new Vec3d(u.x * cs - v.x * sn, u.y * cs - v.y * sn, u.z * cs - v.z * sn);
        Vec3d vr = new Vec3d(u.x * sn + v.x * cs, u.y * sn + v.y * cs, u.z * sn + v.z * cs);

        Vec3d p0 = center.add(ur.multiply(-radius)).add(vr.multiply( radius));
        Vec3d p1 = center.add(ur.multiply( radius)).add(vr.multiply( radius));
        Vec3d p2 = center.add(ur.multiply( radius)).add(vr.multiply(-radius));
        Vec3d p3 = center.add(ur.multiply(-radius)).add(vr.multiply(-radius));

        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_TEXTURE_COLOR
        );

        MatrixStack matrices = event.matrices();
        matrices.push();

        Matrix4f mat = matrices.peek().getPositionMatrix();
        Vec3d cam = mc.getEntityRenderDispatcher().camera.getCameraPos();
        buffer.vertex(mat, (float)(p0.x - cam.x), (float)(p0.y - cam.y), (float)(p0.z - cam.z)).texture(0, 1).color(packedColor);
        buffer.vertex(mat, (float)(p1.x - cam.x), (float)(p1.y - cam.y), (float)(p1.z - cam.z)).texture(1, 1).color(packedColor);
        buffer.vertex(mat, (float)(p2.x - cam.x), (float)(p2.y - cam.y), (float)(p2.z - cam.z)).texture(1, 0).color(packedColor);
        buffer.vertex(mat, (float)(p3.x - cam.x), (float)(p3.y - cam.y), (float)(p3.z - cam.z)).texture(0, 0).color(packedColor);

        matrices.pop();
    }
}
