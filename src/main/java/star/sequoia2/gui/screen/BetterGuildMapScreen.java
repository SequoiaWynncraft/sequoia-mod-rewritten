package star.sequoia2.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wynntils.core.components.Models;
import com.wynntils.core.components.Services;
import com.wynntils.services.map.MapTexture;
import com.wynntils.services.map.pois.Poi;
import com.wynntils.services.map.pois.TerritoryPoi;
import com.wynntils.utils.colors.CommonColors;
import com.wynntils.utils.render.MapRenderer;
import com.wynntils.utils.render.RenderUtils;
import com.wynntils.utils.type.BoundingBox;
import com.wynntils.utils.type.BoundingShape;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import star.sequoia2.accessors.RenderUtilAccessor;
import star.sequoia2.accessors.TextRendererAccessor;

import java.util.ArrayList;
import java.util.List;

import static star.sequoia2.client.SeqClient.mc;

public class BetterGuildMapScreen extends Screen implements RenderUtilAccessor, TextRendererAccessor {

    static final VertexConsumerProvider.Immediate BUFFER_SOURCE = VertexConsumerProvider.immediate(new BufferAllocator(256));

    float renderWidth, renderHeight, renderX, renderY, renderedBorderXOffset, renderedBorderYOffset, mapWidth, mapHeight, centerX, centerZ, mapCenterX, mapCenterZ;
    float zoomLevel = 60f, zoomRenderScale = MapRenderer.getZoomRenderScaleFromLevel(60f);
    Poi hovered;

    public BetterGuildMapScreen() {
        super(Text.literal("Map"));
    }

    @Override
    protected void init() {
        renderWidth = this.width;
        renderHeight = this.height;
        renderX = 0f;
        renderY = 0f;
        renderedBorderXOffset = 0f;
        renderedBorderYOffset = 0f;
        mapWidth = renderWidth;
        mapHeight = renderHeight;
        centerX = renderX + renderedBorderXOffset + mapWidth / 2f;
        centerZ = renderY + renderedBorderYOffset + mapHeight / 2f;
        if (mc.player != null) {
            mapCenterX = (float) mc.player.getX();
            mapCenterZ = (float) mc.player.getZ();
        } else {
            mapCenterX = -360f;
            mapCenterZ = -3000f;
        }
        zoomRenderScale = MapRenderer.getZoomRenderScaleFromLevel(zoomLevel);
        hovered = null;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableDepthTest();
        renderMap(context);

        render2DUtil().enableScissor((int)(renderX + renderedBorderXOffset), (int)(renderY + renderedBorderYOffset), (int)(renderX + renderedBorderXOffset + mapWidth), (int)(renderY + renderedBorderYOffset + mapHeight));
        renderPois(context.getMatrices(), mouseX, mouseY);
        render2DUtil().disableScissor();

        RenderSystem.enableDepthTest();
    }

    protected void renderMap(DrawContext context) {
        MatrixStack poseStack = context.getMatrices();

        render2DUtil().enableScissor((int)(renderX + renderedBorderXOffset), (int)(renderY + renderedBorderYOffset), (int)(renderX + renderedBorderXOffset + mapWidth), (int)(renderY + renderedBorderYOffset + mapHeight));
        RenderUtils.drawRect(poseStack, CommonColors.BLACK, renderX + renderedBorderXOffset, renderY + renderedBorderYOffset, 0, mapWidth, mapHeight);

        BoundingBox textureBoundingBox = BoundingBox.centered(mapCenterX, mapCenterZ, width / zoomRenderScale, height / zoomRenderScale);
        List<MapTexture> maps = Services.Map.getMapsForBoundingBox(textureBoundingBox) != null
                ? Services.Map.getMapsForBoundingBox(textureBoundingBox)
                : new ArrayList<>();

        for (MapTexture map : maps) {
            float textureX = map.getTextureXPosition(mapCenterX);
            float textureZ = map.getTextureZPosition(mapCenterZ);
            MapRenderer.renderMapQuad(map, poseStack, BUFFER_SOURCE, centerX, centerZ, textureX, textureZ, mapWidth, mapHeight, 1f / zoomRenderScale);
        }

        BUFFER_SOURCE.draw();
        render2DUtil().disableScissor();
    }

    private void renderPois(MatrixStack matrixStack, int mouseX, int mouseY) {
        List<TerritoryPoi> advancementPois = Models.Territory.getTerritoryPoisFromAdvancement();
        List<Poi> renderedPois = new ArrayList<>(advancementPois);
        Models.Marker.USER_WAYPOINTS_PROVIDER.getPois().forEach(renderedPois::add);
        renderPois(renderedPois, matrixStack, BoundingBox.centered(mapCenterX, mapCenterZ, width / zoomRenderScale, height / zoomRenderScale), 1, mouseX, mouseY);
    }

    protected void renderPois(List<Poi> pois, MatrixStack matrixStack, BoundingBox textureBoundingBox, float poiScale, int mouseX, int mouseY) {
        hovered = null;

        List<Poi> filteredPois = getRenderedPois(pois, textureBoundingBox, poiScale, mouseX, mouseY);

        for (Poi poi : filteredPois) {
            if (!(poi instanceof TerritoryPoi territoryPoi)) continue;

            float poiRenderX = MapRenderer.getRenderX(poi, mapCenterX, centerX, zoomRenderScale);
            float poiRenderZ = MapRenderer.getRenderZ(poi, mapCenterZ, centerZ, zoomRenderScale);

            for (String tradingRoute : territoryPoi.getTerritoryInfo().getTradingRoutes()) {
                Poi routePoi = null;
                for (Poi filteredPoi : filteredPois) {
                    if (filteredPoi.getName().equals(tradingRoute)) {
                        routePoi = filteredPoi;
                        break;
                    }
                }
                if (routePoi != null && filteredPois.contains(routePoi)) {
                    float x = MapRenderer.getRenderX(routePoi, mapCenterX, centerX, zoomRenderScale);
                    float z = MapRenderer.getRenderZ(routePoi, mapCenterZ, centerZ, zoomRenderScale);
                    RenderUtils.drawLine(matrixStack, CommonColors.DARK_GRAY, poiRenderX, poiRenderZ, x, z, 0, 1);
                }
            }
        }

        VertexConsumerProvider.Immediate bufferSource = mc.getBufferBuilders().getEntityVertexConsumers();

        for (int i = filteredPois.size() - 1; i >= 0; i--) {
            Poi poi = filteredPois.get(i);

            float poiRenderX = MapRenderer.getRenderX(poi, mapCenterX, centerX, zoomRenderScale);
            float poiRenderZ = MapRenderer.getRenderZ(poi, mapCenterZ, centerZ, zoomRenderScale);

            poi.renderAt(matrixStack, bufferSource, poiRenderX, poiRenderZ, hovered == poi, poiScale, zoomRenderScale, zoomLevel, true);
        }

        bufferSource.drawCurrentLayer();
    }

    protected List<Poi> getRenderedPois(List<Poi> pois, BoundingBox textureBoundingBox, float poiScale, int mouseX, int mouseY) {
        List<Poi> filteredPois = new ArrayList<>();

        for (int i = pois.size() - 1; i >= 0; --i) {
            Poi poi = pois.get(i);
            if (poi.getLocation() != null && poi.isVisible(zoomRenderScale, zoomLevel)) {
                float poiRenderX = MapRenderer.getRenderX(poi, mapCenterX, centerX, zoomRenderScale);
                float poiRenderZ = MapRenderer.getRenderZ(poi, mapCenterZ, centerZ, zoomRenderScale);
                float poiWidth = (float) poi.getWidth(zoomRenderScale, poiScale);
                float poiHeight = (float) poi.getHeight(zoomRenderScale, poiScale);
                BoundingBox filterBox = BoundingBox.centered((float) poi.getLocation().getX(), (float) poi.getLocation().getZ(), poiWidth, poiHeight);
                BoundingBox mouseBox = BoundingBox.centered(poiRenderX, poiRenderZ, poiWidth, poiHeight);
                if (BoundingShape.intersects(filterBox, textureBoundingBox)) {
                    filteredPois.add(poi);
                    if (hovered == null && mouseBox.contains((float) mouseX, (float) mouseY)) hovered = poi;
                }
            }
        }

        if (hovered != null) {
            filteredPois.remove(hovered);
            filteredPois.add(0, hovered);
        }
        return filteredPois;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        adjustZoomLevel((float)(2.0F * deltaY));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { this.close(); return true; }
        else if (keyCode != 61 && keyCode != 334) {
            if (keyCode != 45 && keyCode != 333) {
                InputUtil.Key key = InputUtil.fromKeyCode(keyCode, scanCode);
                net.minecraft.client.option.KeyBinding.setKeyPressed(key, true);
                return false;
            } else { adjustZoomLevel(-2.0F); return true; }
        } else { adjustZoomLevel(2.0F); return true; }
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        InputUtil.Key key = InputUtil.fromKeyCode(keyCode, scanCode);
        net.minecraft.client.option.KeyBinding.setKeyPressed(key, false);
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && mouseX >= this.renderX && mouseX <= this.renderX + this.renderWidth && mouseY >= this.renderY && mouseY <= this.renderY + this.renderHeight) {
            this.updateMapCenter((float)(this.mapCenterX - dragX / this.zoomRenderScale), (float)(this.mapCenterZ - dragY / this.zoomRenderScale));
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    protected void setZoomLevel(float zoomLevel) {
        this.zoomLevel = Math.max(1.0F, Math.min(100.0F, zoomLevel));
        this.zoomRenderScale = MapRenderer.getZoomRenderScaleFromLevel(this.zoomLevel);
    }

    private void adjustZoomLevel(float delta) { this.setZoomLevel(this.zoomLevel + delta); }

    protected void updateMapCenter(float newX, float newZ) { this.mapCenterX = newX; this.mapCenterZ = newZ; }
}
