package star.sequoia2.features.impl;

import com.collarmc.pounce.Subscribe;
import com.wynntils.core.WynntilsMod;
import com.wynntils.core.components.Models;
import com.wynntils.models.character.event.CharacterDeathEvent;
import com.wynntils.models.war.type.WarBattleInfo;
import com.wynntils.models.war.type.WarTowerState;
import com.wynntils.utils.type.RangedValue;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.neoforged.bus.api.SubscribeEvent;
import star.sequoia2.client.types.ws.message.ws.GGuildWarSubmissionWSMessage;
import star.sequoia2.events.PacketEvent;
import star.sequoia2.events.PlayerTickEvent;
import star.sequoia2.features.ToggleFeature;
import star.sequoia2.features.impl.ws.WebSocket;

import static star.sequoia2.client.SeqClient.mc;
import static star.sequoia2.features.impl.ws.ChatHook.clean;


public class GuildWarTracker extends ToggleFeature {

    private static final double TRACKING_RADIUS_SQ = 120 * 120;
    private static final Pattern VALID_USERNAME = Pattern.compile("^[a-zA-Z0-9_]{2,16}$");
    private static final Pattern TERRITORY_CAPTURED = Pattern.compile("(?i)Territory\\s+Captured");
    private static final Pattern CAPTURED_TERRITORY = Pattern.compile("(?i)Captured\\s+\"([^\"]+)\"");
    private static final Pattern SEASON_RATING = Pattern.compile("(?i)\\+\\s*(\\d+)\\s+Season(?:al)?\\s+Rating");

    private WarContext activeContext;
    private String lastProcessedBattleId;
    private int lastProcessedStateHash;
    private boolean wynnDeathListenerRegistered;

    public GuildWarTracker() {
        super("GuildWarTracker", "Tracks guild war results", true);
    }

    @Override
    protected void onActivate() {
        registerWynnDeathListener();
    }

    @Override
    protected void onDeactivate() {
        unregisterWynnDeathListener();
    }

    @Subscribe
    public void onPlayerTick(PlayerTickEvent event) {
        if (mc.player == null) return;
        trackWarState();
    }

    @SubscribeEvent
    public void onCharacterDeath(CharacterDeathEvent event) {
        if (!isActive() || activeContext == null || activeContext.submissionSent) return;
        requestSubmission(activeContext.info, activeContext, false);
    }

    @Subscribe
    public void onChatPacket(PacketEvent.PacketReceiveEvent event) {
        if (!(event.packet() instanceof GameMessageS2CPacket packet) || packet.overlay()) return;
        if (activeContext == null || activeContext.submissionSent) return;

        String cleaned = clean(packet.content().getString());
        if (cleaned.isEmpty() || !TERRITORY_CAPTURED.matcher(cleaned).find()) return;

        Integer sr = parseSeasonRating(cleaned);
        if (sr == null) return;

        String territory = parseCapturedTerritory(cleaned);
        if (territory != null && !territoryMatches(activeContext, territory)) return;

        activeContext.seasonRating = sr;
        activeContext.completedFromChat = true;
        if (activeContext.pendingSubmission) requestSubmission(activeContext.info, activeContext, false);
    }

    private void trackWarState() {
        WarBattleInfo info = Models.GuildWarTower.getWarBattleInfo().orElse(null);
        if (info != null) {
            String battleId = buildBattleId(info);
            int stateHash = hashState(info.getCurrentState());
            if (activeContext != null
                    && battleId.equals(lastProcessedBattleId)
                    && stateHash == lastProcessedStateHash) return;
            lastProcessedBattleId = battleId;
            lastProcessedStateHash = stateHash;

            if (activeContext == null || !battleId.equals(activeContext.id)) {
                activeContext = new WarContext(
                        battleId,
                        info,
                        determineStartEpoch(info),
                        collectCurrentWarrers());
            } else {
                activeContext.info = info;
            }

            activeContext.lastKnownState = info.getCurrentState();
            if (!activeContext.submissionSent && isTowerDestroyed(activeContext.lastKnownState)) {
                requestSubmission(info, activeContext, false);
            }
        } else if (activeContext != null) {
            if (!activeContext.submissionSent) requestSubmission(activeContext.info, activeContext, true);
            activeContext = null;
            lastProcessedBattleId = null;
            lastProcessedStateHash = 0;
        }
    }

    private void registerWynnDeathListener() {
        if (wynnDeathListenerRegistered) return;
        WynntilsMod.registerEventListener(this);
        wynnDeathListenerRegistered = true;
    }

    private void unregisterWynnDeathListener() {
        if (!wynnDeathListenerRegistered) return;
        WynntilsMod.unregisterEventListener(this);
        wynnDeathListenerRegistered = false;
    }

    private void requestSubmission(WarBattleInfo info, WarContext context, boolean force) {
        if (info == null || context == null || context.submissionSent) return;
        if (!force && context.seasonRating == null) {
            context.pendingSubmission = true;
            return;
        }
        submitWar(info, context);
    }

    private void submitWar(WarBattleInfo info, WarContext context) {
        if (info == null || context == null) return;
        WarSummary summary = buildSummary(info);
        if (summary == null) return;

        WebSocket webSocket = features().getIfActive(WebSocket.class)
                .filter(WebSocket::isActive)
                .filter(WebSocket::isAuthenticated)
                .orElse(null);
        if (webSocket == null || mc.player == null) return;

        if (context.warrers.isEmpty()) context.warrers = collectCurrentWarrers();

        List<String> uniqueWarrers = context.warrers.isEmpty()
                ? List.of(mc.player.getGameProfile().getName())
                : new ArrayList<>(new LinkedHashSet<>(context.warrers));
        List<String> validWarrers = uniqueWarrers.stream()
                .filter(this::isValidUsername)
                .toList();
        if (validWarrers.isEmpty()) {
            String fallback = mc.player.getGameProfile().getName();
            if (isValidUsername(fallback)) validWarrers = List.of(fallback);
        }

        long submittedAtMillis = System.currentTimeMillis();
        String submittedAt = toRFC3339(submittedAtMillis);
        String startTime = toRFC3339(context.startEpochMs > 0 ? context.startEpochMs : submittedAtMillis);
        WarTowerState completionState = context.lastKnownState != null
                ? context.lastKnownState
                : info.getCurrentState();
        boolean completed = context.completedFromChat || isTowerDestroyed(completionState);
        int seasonRating = context.seasonRating != null ? context.seasonRating : 0;

        GGuildWarSubmissionWSMessage.Data data = new GGuildWarSubmissionWSMessage.Data(
                summary.territory(),
                mc.player.getUuidAsString(),
                submittedAt,
                startTime,
                validWarrers,
                new GGuildWarSubmissionWSMessage.Results(toWsStats(summary.stats())),
                seasonRating,
                completed);

        webSocket.sendMessage(new GGuildWarSubmissionWSMessage(data));
        context.submissionSent = true;
        context.pendingSubmission = false;
    }

    private WarSummary buildSummary(WarBattleInfo info) {
        if (info == null) return null;
        WarTowerState initialState = info.getInitialState();
        WarTowerState currentState = info.getCurrentState();
        if (initialState == null || currentState == null) {
            return null;
        }

        TowerStats initial = toStats(initialState);
        String territory = info.getTerritory() == null || info.getTerritory().isBlank()
                ? "Unknown Territory"
                : info.getTerritory();
        long durationSeconds = Math.max(0, info.getTotalLengthSeconds());
        return new WarSummary(territory, initial, durationSeconds);
    }

    private TowerStats toStats(WarTowerState state) {
        if (state == null) {
            return new TowerStats(0, 0, 0, 0, 0);
        }
        RangedValue damage = state.damage();
        long low = damage != null ? damage.low() : 0;
        long high = damage != null ? damage.high() : 0;
        return new TowerStats(low, high, state.attackSpeed(), state.health(), state.defense());
    }

    private long determineStartEpoch(WarBattleInfo info) {
        WarTowerState initial = info.getInitialState();
        return initial != null && initial.timestamp() > 0 ? initial.timestamp() : System.currentTimeMillis();
    }

    private String buildBattleId(WarBattleInfo info) {
        WarTowerState initial = info.getInitialState();
        long timestamp = initial != null ? initial.timestamp() : System.currentTimeMillis();
        String territory = info.getTerritory() == null ? "unknown" : info.getTerritory();
        return territory + ":" + timestamp;
    }

    private boolean isTowerDestroyed(WarTowerState state) {
        return state != null && state.health() <= 0;
    }

    private GGuildWarSubmissionWSMessage.Stats toWsStats(TowerStats stats) {
        if (stats == null) {
            return new GGuildWarSubmissionWSMessage.Stats(
                    new GGuildWarSubmissionWSMessage.Damage(0, 0),
                    0,
                    0,
                    0);
        }
        return new GGuildWarSubmissionWSMessage.Stats(
                new GGuildWarSubmissionWSMessage.Damage(stats.damageLow(), stats.damageHigh()),
                stats.attackSpeed(),
                stats.health(),
                stats.defence());
    }

    private List<String> collectCurrentWarrers() {
        if (mc.player == null || mc.world == null) return Collections.emptyList();

        LinkedHashSet<String> uniqueNames = new LinkedHashSet<>();
        uniqueNames.add(mc.player.getGameProfile().getName());

        for (PlayerEntity other : mc.world.getPlayers()) {
            if (other == null || other == mc.player) continue;
            if (!isWithinTrackingRange(other)) continue;

            String name = other.getGameProfile() != null
                    ? other.getGameProfile().getName()
                    : other.getName().getString();
            uniqueNames.add(name);
        }

        uniqueNames.removeIf(name -> name == null || name.isBlank());
        return uniqueNames.isEmpty() ? Collections.emptyList() : new ArrayList<>(uniqueNames);
    }

    private boolean isWithinTrackingRange(PlayerEntity other) {
        if (other == null) return false;
        return mc.player != null && mc.player.squaredDistanceTo(other) <= TRACKING_RADIUS_SQ;
    }

    private int hashState(WarTowerState state) {
        if (state == null) return 0;
        long damageLow = state.damage() == null ? 0 : state.damage().low();
        long damageHigh = state.damage() == null ? 0 : state.damage().high();
        int hash = Long.hashCode(damageLow);
        hash = 31 * hash + Long.hashCode(damageHigh);
        hash = 31 * hash + Double.hashCode(state.attackSpeed());
        hash = 31 * hash + Long.hashCode(state.health());
        hash = 31 * hash + Double.hashCode(state.defense());
        hash = 31 * hash + Long.hashCode(state.timestamp());
        return hash;
    }

    private boolean isValidUsername(String name) {
        return name != null && VALID_USERNAME.matcher(name).matches();
    }

    private static Integer parseSeasonRating(String cleaned) {
        if (cleaned == null) return null;
        java.util.regex.Matcher matcher = SEASON_RATING.matcher(cleaned);
        if (!matcher.find()) return null;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String parseCapturedTerritory(String cleaned) {
        if (cleaned == null) return null;
        java.util.regex.Matcher matcher = CAPTURED_TERRITORY.matcher(cleaned);
        if (!matcher.find()) return null;
        String territory = matcher.group(1);
        return territory == null ? null : territory.trim();
    }

    private static boolean territoryMatches(WarContext context, String territory) {
        if (context == null || territory == null) return false;
        String expected = context.info != null ? context.info.getTerritory() : null;
        if (expected == null || expected.isBlank()) return true;
        return expected.equalsIgnoreCase(territory.trim());
    }

    private static final class WarContext {
        private final String id;
        private WarBattleInfo info;
        private final long startEpochMs;
        private List<String> warrers;
        private WarTowerState lastKnownState;
        private Integer seasonRating;
        private boolean pendingSubmission;
        private boolean completedFromChat;
        private boolean submissionSent;

        private WarContext(String id, WarBattleInfo info, long startEpochMs, List<String> warrers) {
            this.id = id;
            this.info = info;
            this.startEpochMs = startEpochMs;
            this.warrers = warrers == null ? new ArrayList<>() : new ArrayList<>(warrers);
        }
    }

    private String toRFC3339(long epochMillis) {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis));
    }

    private record WarSummary(String territory, TowerStats stats, long durationSeconds) {}

    private record TowerStats(long damageLow, long damageHigh, double attackSpeed, long health, double defence) {}
}
