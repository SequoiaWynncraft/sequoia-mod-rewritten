package star.sequoia2.features.impl;

import com.collarmc.pounce.Subscribe;
import com.ibm.icu.impl.Pair;
import com.wynntils.core.components.Models;
import com.wynntils.core.text.StyledText;
import com.wynntils.handlers.container.scriptedquery.QueryBuilder;
import com.wynntils.handlers.container.scriptedquery.QueryStep;
import com.wynntils.handlers.container.scriptedquery.ScriptedContainerQuery;
import com.wynntils.handlers.container.type.ContainerContent;
import com.wynntils.models.containers.ContainerModel;
import com.wynntils.utils.mc.LoreUtils;
import com.wynntils.utils.wynn.InventoryUtils;
import lombok.Getter;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.message.ws.GTreasuryEmeraldAlertWSMessage;
import star.sequoia2.events.PacketEvent;
import star.sequoia2.events.RaidCompleteFromChatEvent;
import star.sequoia2.features.ToggleFeature;
import star.sequoia2.features.impl.ws.WebSocket;
import star.sequoia2.settings.types.BooleanSetting;
import star.sequoia2.settings.types.IntSetting;
import star.sequoia2.utils.wynn.WynnUtils;

import java.util.HashMap;
import java.util.Optional;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static star.sequoia2.client.SeqClient.mc;

public class GuildRewardTracking extends ToggleFeature {
    private static final int GUILD_REWARDS_ITEM_SLOT = 27;
    private static final Pattern GUILD_REWARDS_EMERALDS_PATTERN = Pattern.compile("^§aEmeralds: §f(\\d+)§7/(\\d+)$");
    private static final Pattern GUILD_REWARDS_TOMES_PATTERN = Pattern.compile("^§5Guild Tomes: §f(\\d+)§7/(\\d+)$");
    private static final Pattern GUILD_REWARDS_ASPECTS_PATTERN = Pattern.compile("^§#d6401effAspects: §f(\\d+)§7/(\\d+)$");

    private static final Pattern OVERFLOW_GUILD_REWARDS_EMERALDS_PATTERN = Pattern.compile("^§aEmeralds: §c(\\d+)§7/(\\d+)$");
    private static final Pattern OVERFLOW_GUILD_REWARDS_TOMES_PATTERN = Pattern.compile("^§5Guild Tomes: §c(\\d+)§7/(\\d+)$");
    private static final Pattern OVERFLOW_GUILD_REWARDS_ASPECTS_PATTERN = Pattern.compile("^§#d6401effAspects: §c(\\d+)§7/(\\d+)$");
    private static final Pattern TREASURY_REWARD_PATTERN = Pattern.compile(
            "\\uDAFF\\uDFFC\\uE001\\uDB00\\uDC06\\s+(?:§.)*([\\p{L}\\p{N}_]+)\\s+rewarded\\s+((?:§.|[^§])+?)\\s+to\\s+(?:§.)*([\\p{L}\\p{N}_]+)",
            Pattern.CASE_INSENSITIVE
    );
    public GuildRewardTracking() {
        super("GuildRewardTracking", "Tracks and notifies when guild rewards are over a certain point.");
    }

    @Getter
    IntSetting emeraldNotifyValue = settings().number("Emeralds", "Emeralds Notify Value", 100,0,100);
    @Getter
    IntSetting aspectNotifyValue = settings().number("Aspects", "Aspects Notify Value", 100,0,100);
    @Getter
    IntSetting tomeNotifyValue = settings().number("Tomes", "Tomes Notify Value", 100,0,100);

    @Getter
    BooleanSetting sendPing = settings().bool("SendPing", "Enable to send Keepers ping when emeralds are above 90%", false);


    @Subscribe
    private void cancelPing(PacketEvent.PacketReceiveEvent event) {
        Optional<WebSocket> wsFeature = features().getIfActive(WebSocket.class);
        if (!wsFeature.map(WebSocket::isActive).orElse(false)) return;
        if (!(event.packet() instanceof GameMessageS2CPacket packet) || packet.overlay()) return;

        String raw = packet.content().toString();
        Matcher matcher = TREASURY_REWARD_PATTERN.matcher(raw);
        if (!matcher.find()) return;

        String reward = matcher.group(2).replaceAll("§.", "");
        if (!reward.toLowerCase(Locale.ROOT).contains("emerald")) return;

        GTreasuryEmeraldAlertWSMessage payload = new GTreasuryEmeraldAlertWSMessage(
                new GTreasuryEmeraldAlertWSMessage.Data(
                        false,
                        mc.player.getName().getString()
                )
        );
        wsFeature.ifPresent(webSocketFeature -> webSocketFeature.sendMessage(payload));
    }
    //                &b󏿼󐀆 &3GAZtheMiner rewarded &e1024 Emeralds&3 to cinfrascitizen
    //                &b󏿼󏿿󏿾 &3Shisouhan rewarded &ean Aspect&3 to LegendaryVirus
    //                &b󏿼󐀆 &3Obstacles_ rewarded &ean Aspect&3 to Obstacles_
    //                &b󏿼󐀆 &3Obstacles_ rewarded &e1024 Emeralds&3 to Obstacles_

    @Subscribe
    public void onRaidComp(RaidCompleteFromChatEvent event){
        processGuildRewards();
    }



    public void processGuildRewards() {
        SeqClient.debug("Starting to parse guild rewards");
        Optional<WebSocket> wsFeature = features().getIfActive(WebSocket.class);

        checkGuildRewards().thenAcceptAsync(rewardStorage -> {
                    if (rewardStorage == null){return;}
                    if (rewardStorage.isEmpty()){return;}
                    Pair<Integer, Integer> emeralds = rewardStorage.getOrDefault(GuildRewardType.EMERALD, Pair.of(-1, -1));
                    Pair<Integer, Integer> aspects = rewardStorage.getOrDefault(GuildRewardType.ASPECT, Pair.of(-1, -1));
                    Pair<Integer, Integer> tomes = rewardStorage.getOrDefault(GuildRewardType.TOME, Pair.of(-1, -1));
                    int emeraldValue = (emeralds.first * 100 / emeralds.second);
                    int aspectValue = (aspects.first * 100 / aspects.second);
                    int tomeValue = (tomes.first * 100 / tomes.second);
                    if (emeraldValue >= emeraldNotifyValue.get()) {
                        notify(Text.of("Emeralds are above value of : " + emeraldNotifyValue.get() + "%"), "guildreward-emerald");
                    }
                    if (aspectValue >= aspectNotifyValue.get()) {
                        notify(Text.of("Aspects are above value of : " + aspectNotifyValue.get() + "%"), "guildreward-aspect");
                    }
                    if (tomeValue >= tomeNotifyValue.get()) {
                        notify(Text.of("Tomes are above value of : " + tomeNotifyValue.get() + "%"), "guildreward-tome");
                    }

                    if(emeraldValue>= 90 && sendPing.get()){
                        if (!wsFeature.map(WebSocket::isActive).orElse(false)) return;
                        GTreasuryEmeraldAlertWSMessage payload = new GTreasuryEmeraldAlertWSMessage(
                                new GTreasuryEmeraldAlertWSMessage.Data(
                                        true,
                                        mc.player.getName().getString()
                                )
                        );
                        wsFeature.ifPresent(webSocketFeature -> webSocketFeature.sendMessage(payload));
                    }

                }
        );

    }

    private CompletableFuture<Map<GuildRewardType, Pair<Integer, Integer>>> checkGuildRewards() {
        SeqClient.debug("Starting Rewards Query");
        CompletableFuture<Map<GuildRewardType, Pair<Integer, Integer>>> result = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            QueryBuilder queryBuilder = ScriptedContainerQuery.builder("Guild Reward Query");

            queryBuilder.onError(message -> {
                SeqClient.warn("Error querying guild rewards: " + message);
                result.completeExceptionally(new RuntimeException("Error querying guild rewards: " + message));
            });

            SeqClient.debug("Setting up Guild Reward Query query steps");
            queryBuilder
                    .then(QueryStep.useItemInHotbar(InventoryUtils.COMPASS_SLOT_NUM)
                            .expectContainerTitle(ContainerModel.CHARACTER_INFO_NAME)
                            .processIncomingContainer(WynnUtils::parseCharacterContainerForGuildInfo))
                    .conditionalThen(
                            content -> StringUtils.isNotBlank(Models.Guild.getGuildName()),
                            QueryStep.clickOnSlot(26).expectContainerTitle("[a-zA-Z\\s]+: Manage"))
                    .then(QueryStep.clickOnSlot(0).expectContainerTitle("[a-zA-Z\\s]+: Members"))
                    .then(QueryStep.clickOnSlot(2)
                            .expectContainerTitle("[a-zA-Z\\s]+: Members")
                            .processIncomingContainer(content -> {
                                Map<GuildRewardType, Pair<Integer, Integer>> rewardStorage = getRewardsValue(content);
                                result.complete(rewardStorage);
                            }));

            queryBuilder.build().executeQuery();
            SeqClient.debug("Guild Reward query setup complete");
        });

        return result;
    }

    private Map<GuildRewardType, Pair<Integer, Integer>> getRewardsValue(ContainerContent content) {
        Map<GuildRewardType, Pair<Integer, Integer>> rewardStorage = new HashMap<>();
        ItemStack guildRewardsItem = content.items().get(GUILD_REWARDS_ITEM_SLOT);
        if (!guildRewardsItem.getItem().equals(Items.GOLDEN_APPLE)) {
            SeqClient.error("Could not parse guild rewards from item: " + LoreUtils.getLore(guildRewardsItem));
            return rewardStorage;
        }

        for (StyledText loreLine : LoreUtils.getLore(guildRewardsItem)) {
            if (SeqClient.isDebugMode()) {
                SeqClient.debug("Item: " + loreLine);
            }
            if (loreLine.contains("Rewards are unavailable")){break;}
            Matcher emeraldsMatcher = GUILD_REWARDS_EMERALDS_PATTERN.matcher(loreLine.getString());
            Matcher tomesMatcher = GUILD_REWARDS_TOMES_PATTERN.matcher(loreLine.getString());
            Matcher aspectsMatcher = GUILD_REWARDS_ASPECTS_PATTERN.matcher(loreLine.getString());
            Matcher OemeraldsMatcher = OVERFLOW_GUILD_REWARDS_EMERALDS_PATTERN.matcher(loreLine.getString());
            Matcher OtomesMatcher = OVERFLOW_GUILD_REWARDS_TOMES_PATTERN.matcher(loreLine.getString());
            Matcher OaspectsMatcher = OVERFLOW_GUILD_REWARDS_ASPECTS_PATTERN.matcher(loreLine.getString());
            if (emeraldsMatcher.matches()) {
                int emeralds = Integer.parseInt(emeraldsMatcher.group(1));
                int emeraldsMax = Integer.parseInt(emeraldsMatcher.group(2));
                SeqClient.debug("Emeralds found: " + emeralds);
                rewardStorage.put(GuildRewardType.EMERALD, Pair.of(emeralds, emeraldsMax));
            } else if (aspectsMatcher.matches()) {
                int aspects = Integer.parseInt(aspectsMatcher.group(1));
                int aspectsMax = Integer.parseInt(aspectsMatcher.group(2));
                SeqClient.debug("Aspects found: " + aspects);
                rewardStorage.put(GuildRewardType.ASPECT, Pair.of(aspects, aspectsMax));
            } else if (tomesMatcher.matches()) {
                int tomes = Integer.parseInt(tomesMatcher.group(1));
                int tomesMax = Integer.parseInt(tomesMatcher.group(2));
                SeqClient.debug("Tomes found: " + tomes);
                rewardStorage.put(GuildRewardType.TOME, Pair.of(tomes, tomesMax));
            }
            if (OemeraldsMatcher.matches()) {
                int emeralds = Integer.parseInt(OemeraldsMatcher.group(1));
                int emeraldsMax = Integer.parseInt(OemeraldsMatcher.group(2));
                SeqClient.debug("Emeralds found: " + emeralds);
                rewardStorage.put(GuildRewardType.EMERALD, Pair.of(emeralds, emeraldsMax));
            } else if (OaspectsMatcher.matches()) {
                int aspects = Integer.parseInt(OaspectsMatcher.group(1));
                int aspectsMax = Integer.parseInt(OaspectsMatcher.group(2));
                SeqClient.debug("Aspects found: " + aspects);
                rewardStorage.put(GuildRewardType.ASPECT, Pair.of(aspects, aspectsMax));
            } else if (OtomesMatcher.matches()) {
                int tomes = Integer.parseInt(OtomesMatcher.group(1));
                int tomesMax = Integer.parseInt(OtomesMatcher.group(2));
                SeqClient.debug("Tomes found: " + tomes);
                rewardStorage.put(GuildRewardType.TOME, Pair.of(tomes, tomesMax));
            }
        }
        return rewardStorage;
    }

    public enum GuildRewardType {
        EMERALD,
        ASPECT,
        TOME
    }
}
