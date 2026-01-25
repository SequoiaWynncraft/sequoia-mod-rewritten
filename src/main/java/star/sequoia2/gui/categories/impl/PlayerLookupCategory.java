package star.sequoia2.gui.categories.impl;

import com.mojang.logging.LogUtils;
import mil.nga.color.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import star.sequoia2.accessors.RenderUtilAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.services.wynn.player.PlayerResponse;
import star.sequoia2.client.types.Services;
import star.sequoia2.features.impl.Settings;
import star.sequoia2.gui.categories.RelativeComponent;
import star.sequoia2.gui.component.SearchBarComponent;
import star.sequoia2.utils.render.TextureStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static star.sequoia2.client.SeqClient.mc;

public class PlayerLookupCategory extends RelativeComponent implements RenderUtilAccessor {
    private static final Logger LOGGER = LogUtils.getLogger();

    SearchBarComponent searchBarComponent;

    private OtherClientPlayerEntity previewPlayer;
    private String previewedUsername;

    private volatile PlayerResponse currentPlayer;

    public PlayerLookupCategory() {
        super("Lookup");
        searchBarComponent = new SearchBarComponent();
    }

    @Override
    public void render(DrawContext context, float mouseX, float mouseY, float delta) {
        float left = contentX();
        float top = contentY();
        float right = left + contentWidth();
        float bottom = top + contentHeight();
        Matrix3x2fStack matrices = context.getMatrices();

        Color normal = features().get(Settings.class).map(Settings::getThemeNormal).orElse(Color.black());
        Color dark = features().get(Settings.class).map(Settings::getThemeDark).orElse(Color.black());
        Color light = features().get(Settings.class).map(Settings::getThemeLight).orElse(Color.black());
        Color accent1 = features().get(Settings.class).map(Settings::getThemeAccent1).orElse(Color.black());
        Color accent2 = features().get(Settings.class).map(Settings::getThemeAccent2).orElse(Color.black());
        Color accent3 = features().get(Settings.class).map(Settings::getThemeAccent3).orElse(Color.black());

        searchBarComponent.render(context, mouseX, mouseY, delta);
        searchBarComponent.setPos(left, top);
        searchBarComponent.setDimensions(contentWidth() - 25f, getGuiRoot().btnH);

        boolean hoverEnter = isWithin(mouseX, mouseY, right - 25f, top, 25, getGuiRoot().btnH);
        Color enterStart = hoverEnter ? light : normal;
        render2DUtil().roundRectFilled(context.getMatrices(), right - 25f, top, right, top + getGuiRoot().btnH, getGuiRoot().rounding, enterStart);

        matrices.pushMatrix();
        matrices.translate(right - 25f, top);
        context.drawTexture(
                net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                TextureStorage.enter,
                0,
                0,
                0f,
                0f,
                (int) getGuiRoot().btnH,
                (int) getGuiRoot().btnH,
                (int) getGuiRoot().btnH,
                (int) getGuiRoot().btnH,
                hoverEnter
                        ? new java.awt.Color(dark.getRed(), dark.getGreen(), dark.getBlue(), dark.getAlpha()).getRGB()
                        : new java.awt.Color(light.getRed(), light.getGreen(), light.getBlue(), light.getAlpha()).getRGB()
        );
        matrices.popMatrix();

        if (currentPlayer != null) {
            String username = currentPlayer.getUsername();
            boolean online = currentPlayer.isOnline();
            String server = currentPlayer.getServer();

            float playtime = currentPlayer.getPlaytime();

            PlayerResponse.Guild guildObj = currentPlayer.getGuild();
            String guildName = guildObj != null ? guildObj.getName() : null;
            String guildRank = guildObj != null ? guildObj.getRank() : null;

            Map<String, Integer> raidsList = null;
            if (currentPlayer.getGlobalData() != null && currentPlayer.getGlobalData().getRaids() != null) {
                raidsList = currentPlayer.getGlobalData().getRaids().getList();
            }

            int warsComplete = currentPlayer.getGlobalData() != null ? currentPlayer.getGlobalData().getWars() : 0;

            int totalLevel = currentPlayer.getGlobalData() != null ? currentPlayer.getGlobalData().getTotalLevels() : 0;

            float x = left;
            float y = top + getGuiRoot().btnH + 6f;
            float line = textRenderer().fontHeight + 4f;

            render2DUtil().drawText(context, (username != null ? username : "(unknown)") + (online ? "  • ONLINE" : "  • offline"), x, y, light.getColor(), true);
            y += line;

            render2DUtil().drawText(context, "Total playtime: " + String.format("%.1f hrs", playtime), x, y, light.getColor(), true);
            y += line;

            render2DUtil().drawText(context, "Total level: " + totalLevel, x, y, light.getColor(), true);
            y += line;

            render2DUtil().drawText(context, (guildName != null) ? guildRank + " of " + guildName + " guild." : "Not in a guild", x, y, light.getColor(), true);
            y += line;

            render2DUtil().drawText(context, "Wars: " + warsComplete, x, y, light.getColor(), true);
            y += line;

            if (online) {
                render2DUtil().drawText(context, "Server: " + (server != null ? server : "-"), x, y, light.getColor(), true);
                y += line;
            } else {
                render2DUtil().drawText(context, "Last known server: " + (server != null ? server : "-"), x, y, light.getColor(), true);
                y += line;
            }

            render2DUtil().drawText(context, "Raid completions (total across all characters):", x, y, accent2.getColor(), true);
            y += line;

            if (raidsList == null || raidsList.isEmpty()) {
                render2DUtil().drawText(context, "No raids found.", x, y, light.getColor(), true);
                y += line;
            } else {
                List<Map.Entry<String, Integer>> entries = new ArrayList<>(raidsList.entrySet());
                entries.sort((a, b) -> a.getKey().compareToIgnoreCase(b.getKey()));

                for (Map.Entry<String, Integer> e : entries) {
                    String raidName = e.getKey();
                    int clears = e.getValue() != null ? e.getValue() : 0;

                    if (y + line > bottom - 4) break;

                    render2DUtil().drawText(context, raidName + ": " + clears, x, y, light.getColor(), true);
                    y += line;
                }
            }
        } else {
            render2DUtil().drawText(context, "§8Search to see stats", left + ((contentWidth()) / 2) - (textRenderer().getWidth("Search to see stats") / 2f), top + (contentHeight() / 2), light.getColor(), true);
        }

        if (previewPlayer != null) {
            int pad = 6;
            int boxHeight = (int) 100f;
            int boxWidth = (int) 50f;
            int x2 = (int) (right - pad);
            int y1 = (int) (top + getGuiRoot().btnH + 6);
            int x1 = x2 - boxWidth;
            int y2 = y1 + boxHeight;

            previewPlayer.bodyYaw += delta * 10f;
            previewPlayer.headYaw = previewPlayer.bodyYaw;

            render2DUtil().roundGradientFilled(context.getMatrices(), x1 - 1, y1 - 1, x2 + 1, y2 + 1, getGuiRoot().rounding, dark, normal, true);

            InventoryScreen.drawEntity(
                    context,
                    x1, y1, x2, y2,
                    boxWidth - 10,
                    0.0625f,
                    mouseX, mouseY,
                    previewPlayer
            );
        }
    }

    @Override
    public void mouseClicked(float mouseX, float mouseY, int button) {
        searchBarComponent.mouseClicked(mouseX, mouseY, button);
        float left = contentX();
        float top = contentY();
        float right = left + contentWidth();
        if (isWithin(mouseX, mouseY, right - 25f, top, 25, getGuiRoot().btnH) && searchBarComponent.isSearching()) {
            Services.Player.getPlayerFullResult(searchBarComponent.getSearch())
                    .thenAccept(resp -> {
                        currentPlayer = resp;
                        tryMakePreviewFromStats(resp);
                    })
                    .exceptionally(ex -> {
                        LOGGER.warn(ex.getMessage());
                        return null;
                    });
            searchBarComponent.setSearching(false);
            searchBarComponent.setSearch("");
        }
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        searchBarComponent.keyPressed(keyCode, scanCode, modifiers);
        if (keyCode == GLFW.GLFW_KEY_ENTER && searchBarComponent.isSearching()) {
            Services.Player.getPlayerFullResult(searchBarComponent.getSearch())
                    .thenAccept(resp -> {
                        currentPlayer = resp;
                        tryMakePreviewFromStats(resp);
                    })
                    .exceptionally(ex -> {
                        LOGGER.warn(ex.getMessage());
                        return null;
                    });
            searchBarComponent.setSearching(false);
            searchBarComponent.setSearch("");
        }
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        searchBarComponent.charTyped(chr, modifiers);
    }

    private void tryMakePreviewFromStats(PlayerResponse stats) {
        String username = stats != null ? stats.getUsername() : null;
        String uuidStr  = stats != null ? stats.getUuid() : null;

        if (uuidStr == null || uuidStr.isBlank()) return;
        if (previewPlayer != null && username != null && username.equalsIgnoreCase(previewedUsername)) return;

        if (mc.world == null) return;

        SeqClient.getProfileFetcher().fetchByUUID(UUID.fromString(uuidStr))
                .thenAccept(gameProfile -> {
                    previewPlayer = gameProfile.map(profile -> new OtherClientPlayerEntity(mc.world, profile)).orElse(null);
                });
        if (previewPlayer != null) {
            previewPlayer.bodyYaw = 180f;
            previewPlayer.headYaw = 180f;
            previewedUsername = username;
        }
    }
}
