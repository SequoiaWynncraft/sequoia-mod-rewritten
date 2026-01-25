package star.sequoia2.utils.wynn;

import com.wynntils.core.WynntilsMod;
import com.wynntils.core.components.Models;
import com.wynntils.handlers.container.scriptedquery.QueryBuilder;
import com.wynntils.handlers.container.scriptedquery.QueryStep;
import com.wynntils.handlers.container.scriptedquery.ScriptedContainerQuery;
import com.wynntils.handlers.container.type.ContainerContent;
import com.wynntils.models.containers.ContainerModel;
import com.wynntils.utils.wynn.InventoryUtils;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.realms.dto.PlayerInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.Services;
import star.sequoia2.utils.MinecraftUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.wynntils.models.character.CharacterModel.GUILD_MENU_SLOT;
import static star.sequoia2.client.SeqClient.mc;

public final class WynnUtils {
    private static final Pattern WYNNCRAFT_SERVER_PATTERN =
            Pattern.compile("^(?:(.*)\\.)?wynncraft\\.(?:com|net|org)$");

    private static final UUID WORLD_LIST_ENTRY = UUID.fromString("16ff7452-714f-3752-b3cd-c3cb2068f6af");
    private static final Pattern WORLD_NAME_TABLIST_ENTRY = Pattern.compile("^§f {2}§lGlobal \\[(.*)]$");

    private static final Comparator<PlayerInfo> PLAYER_INFO_COMPARATOR =
            Comparator.comparing(playerInfo -> playerInfo.name, String::compareToIgnoreCase);

    private static final Pattern GUILD_TABLIST_ENTRY_PATTERN = Pattern.compile("§b§l  Guild");

    private static final Pattern PARTY_TABLIST_ENTRY_PATTERN = Pattern.compile("§e  §lParty");
    private static final Pattern[] PLAYER_NOT_IN_PARTY_TABLIST_ENTRIES = {
            Pattern.compile("§7Make a party"), Pattern.compile("§7by typing:"), Pattern.compile("§7/party create")
    };

    private WynnUtils() {}

    public static String getWorldFromTablist() {


        // Single-player title screen or not yet connected
        if (mc.getNetworkHandler() == null) {
            return "";
        }

        return mc.getNetworkHandler()
                .getPlayerList()                 // List<PlayerListEntry>
                .stream()
                .filter(entry ->
                        Objects.equals(entry.getProfile().id(), WORLD_LIST_ENTRY))
                .findFirst()
                .map(entry -> entry.getProfile().name())
                .orElse("");
    }


    private static boolean isValidPartyMemberEntry(String name) {
        if (StringUtils.isBlank(name)) {
            return false;
        }

        if (PARTY_TABLIST_ENTRY_PATTERN.matcher(name).matches()) {
            return false;
        }

        if (GUILD_TABLIST_ENTRY_PATTERN.matcher(name).matches()) {
            return false;
        }

        for (Pattern pattern : PLAYER_NOT_IN_PARTY_TABLIST_ENTRIES) {
            if (pattern.matcher(name).matches()) {
                return false;
            }
        }

        return MinecraftUtils.isValidUsername(name);
    }

    public static List<String> getTabList() {
        // 1. Not connected yet?  ->  empty list
        if (mc.getNetworkHandler() == null) {
            return Collections.emptyList();
        }

        PlayerListHud tabHud = mc.inGameHud.getPlayerListHud();

        return mc.getNetworkHandler()
                .getListedPlayerListEntries()             // Collection<PlayerListEntry>
                .stream()
//                .sorted(PlayerListHud.ENTRY_ORDERING)     // vanilla comparator TODO access widener this bitch
                .map(tabHud::getPlayerName)               // Text component
                .map(Text::getString)                     // strip formatting
                .collect(Collectors.toList());
    }


    public static List<String> getTabListWithoutEmptyLines() {
        return getTabList().stream().filter(StringUtils::isNotBlank).toList();
    }

    public static List<String> getPartyMembersFromTabList() {
        List<String> tabListWithoutEmptyLines = getTabListWithoutEmptyLines();
        int partyIndex = tabListWithoutEmptyLines.indexOf("§e  §lParty");

        if (partyIndex == -1) {
            return Collections.emptyList();
        }

        return tabListWithoutEmptyLines.subList(partyIndex + 1, tabListWithoutEmptyLines.size()).stream()
                .takeWhile(WynnUtils::isValidPartyMemberEntry)
                .map(WynnUtils::getUnformattedString)
                .toList();
    }

    public static String getUnformattedString(String string) {
        return string.replaceAll("\uDAFF\uDFFC\uE006\uDAFF\uDFFF\uE002\uDAFF\uDFFE", "")
                .replaceAll("\uDAFF\uDFFC\uE001\uDB00\uDC06", "")
                .replaceAll("§.", "")
                .replaceAll("&.", "")
                .replaceAll("\\[[0-9:]+]", "")
                .replaceAll("\\s+", " ")
                .replaceAll("\\n", "")
                .replaceAll("[^\\x20-\\x7E]", "")
                .replaceAll("\u00A0", " ")
                .replaceAll("\\[", "")
                .replaceAll("]", "")
                .trim();
    }

    public static boolean isWynncraftServer(String host) {
        return WYNNCRAFT_SERVER_PATTERN.matcher(host).matches();
    }

    public static boolean isWynncraftWorld(String input) {
        return WORLD_NAME_TABLIST_ENTRY.matcher(input).matches();
    }

    public static CompletableFuture<Boolean> isSequoiaGuildMember() {
        return Services.Player.getPlayer(mc.player.getName().getString()).thenApplyAsync(playerResponse -> {
            if (playerResponse == null) {
                SeqClient.debug("playerResponse is null, querying Character Info for guild info");

                QueryBuilder queryBuilder = ScriptedContainerQuery.builder("Character Info Query");
                queryBuilder.onError(msg -> WynntilsMod.warn("Error querying Character Info: " + msg));
                queryBuilder.then(QueryStep.useItemInHotbar(InventoryUtils.COMPASS_SLOT_NUM)
                        .expectContainerTitle(ContainerModel.CHARACTER_INFO_NAME)
                        .processIncomingContainer(WynnUtils::parseCharacterContainerForGuildInfo));

                Models.Guild.addGuildContainerQuerySteps(queryBuilder);

                queryBuilder.build().executeQuery();
                return StringUtils.equals(Models.Guild.getGuildName(), "Sequoia");
            }

            SeqClient.debug(playerResponse.getUsername() + "'s guild: "
                    + playerResponse.getGuild().getName());
            return playerResponse.getGuild() != null && StringUtils.equals(playerResponse.getGuild().getName(), "Sequoia");
        });
    }

    public static void parseCharacterContainerForGuildInfo(ContainerContent container) {
        ItemStack guildInfoItem = container.items().get(GUILD_MENU_SLOT);
        Models.Guild.parseGuildInfoFromGuildMenu(guildInfoItem);
    }

    /**
     * Renders a circle with the given radius. Some notes for future reference:<p>
     * - The circle is rendered at the player's feet, from the ground to HEIGHT blocks above the ground.<p>
     * - .color() takes floats from 0-1, but ints from 0-255<p>
     * - Increase SEGMENTS to make the circle smoother, but it will also increase the amount of vertices (and thus the amount of memory used and the amount of time it takes to render)<p>
     * - The order of the consumer.vertex() calls matter. Here, we draw a quad, so we do bottom left corner, top left corner, top right corner, bottom right corner. This is filled in with the color we set.<p>
     *
     * @param poseStack The pose stack to render with. This is supposed to be the pose stack from the event.
     *                  We do the translation here, so no need to do it before passing it in.
     * @param radius The radius of the circle.
     * @param color The color of the circle.
     */ // NOT FUNCTIONAL, also not to do since idk what this could be used for
//    public static void renderCircle(
//            MultiBufferSource.BufferSource bufferSource,
//            int circleSegments,
//            float circleHeight,
//            PoseStack poseStack,
//            Position position,
//            float radius,
//            int color) {
//        RenderSystem.disableCull();
//
//        poseStack.pushPose();
//        poseStack.translate(-position.x(), -position.y(), -position.z());
//        VertexConsumer consumer = bufferSource.getBuffer(CustomRenderType.POSITION_COLOR_QUAD);
//
//        Matrix4f matrix4f = poseStack.last().pose();
//        double angleStep = 2 * Math.PI / circleSegments;
//        double angle = -(System.currentTimeMillis() % 40000) * 2 * Math.PI / 40000.0;
//        for (int i = 0; i < circleSegments; i++) {
//            float x = (float) (position.x() + Math.sin(angle) * radius);
//            float z = (float) (position.z() + Math.cos(angle) * radius);
//            consumer.addVertex(matrix4f, x, (float) position.y(), z).setColor(color);
//            consumer.addVertex(matrix4f, x, (float) position.y() + circleHeight, z)
//                    .setColor(color);
//            angle += angleStep;
//            float x2 = (float) (position.x() + Math.sin(angle) * radius);
//            float z2 = (float) (position.z() + Math.cos(angle) * radius);
//            consumer.addVertex(matrix4f, x2, (float) position.y() + circleHeight, z2)
//                    .setColor(color);
//            consumer.addVertex(matrix4f, x2, (float) position.y(), z2).setColor(color);
//        }
//
//        bufferSource.endBatch();
//        poseStack.popPose();
//        RenderSystem.enableCull();
//    }
}