package star.sequoia2.features.impl;

import com.collarmc.pounce.Subscribe;
import com.wynntils.core.text.StyledText;
import com.wynntils.utils.mc.LoreUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import star.sequoia2.accessors.RenderUtilAccessor;
import star.sequoia2.events.Render3DEvent;
import star.sequoia2.events.WynncraftLoginEvent;
import star.sequoia2.features.ToggleFeature;
import star.sequoia2.settings.types.ColorSetting;
import star.sequoia2.settings.types.IntSetting;
import star.sequoia2.utils.TickScheduler;

import static star.sequoia2.client.SeqClient.mc;

public class TeleportIndicator extends ToggleFeature implements RenderUtilAccessor {

    ColorSetting color = settings().color("Color", "color of the box indicator", new mil.nga.color.Color(255, 255, 255));
    IntSetting mageRange = settings().number("Mage Range", "how far you will tp", 16, 1, 30);
    IntSetting shamanRange = settings().number("Shaman Range", "how far you will tp", 16, 1, 30);

    public TeleportIndicator() {
        super("TeleportIndicator", "Renders where you will teleport on mage and shaman");
        currentClass = Class.Mage;
        max = mageRange.get();
    }

    public Class currentClass = Class.Mage; //start as something with range
    double max = -1;

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (mc == null || mc.player == null || mc.world == null || max == -1) return;
        float tickDelta = event.delta();
        Vec3d start = mc.player.getCameraPosVec(tickDelta);
        Vec3d look = mc.player.getRotationVec(tickDelta);
        Vec3d end = start.add(look.multiply(max));
        BlockHitResult hit = mc.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            if (hit.getPos().distanceTo(start) < 3) return;
            BlockPos candidate = hit.getBlockPos().offset(hit.getSide());
            BlockPos target = mc.world.isAir(candidate) ? candidate : hit.getBlockPos();
            render3DUtil().drawBox(event.matrices(), target, color.get(), 0.5);
        } else {
            BlockPos target = BlockPos.ofFloored(end);
            if (!mc.world.isAir(target)) return;
            render3DUtil().drawBox(event.matrices(), target, color.get(), 0.5);
        }
    }
    @Subscribe
    public void onWynncraftJoin(WynncraftLoginEvent ignored){
        TickScheduler.scheduleTicks(() -> {
            currentClass = Class.Else;
            max = -1;
            if (mc.player.getMainHandStack()!= null){
                ItemStack item = mc.player.getMainHandStack();
                if (item.getItem() != null && item.getItem().equals(Items.POTION)){
                    for (StyledText loreLine : LoreUtils.getLore(item)) {
                        if (loreLine.getString().contains("§a✔§7 Class Req: Mage/Dark Wizard")){
                            currentClass = Class.Mage;
                            max = mageRange.get();
                        }
                        if (loreLine.getString().contains("§a✔§7 Class Req: Shaman/Skyseer")){
                            currentClass = Class.Shaman;
                            max = shamanRange.get();
                        }

                    }
                }
            }
        }, 10);
    }

    public enum Class{
        Mage,
        Shaman,
        Else
    }
}
