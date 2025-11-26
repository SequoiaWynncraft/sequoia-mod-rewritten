package star.sequoia2.features.impl;

import com.collarmc.pounce.Subscribe;
import com.wynntils.core.components.Models;
import com.wynntils.models.war.type.WarBattleInfo;
import com.wynntils.models.war.type.WarTowerState;
import com.wynntils.utils.type.RangedValue;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import star.sequoia2.client.types.ws.message.ws.GGuildWarSubmissionWSMessage;
import star.sequoia2.events.PlayerTickEvent;
import star.sequoia2.features.ToggleFeature;
import star.sequoia2.features.impl.ws.WebSocketFeature;

import static star.sequoia2.client.SeqClient.mc;


public class GuildWarTracker extends ToggleFeature {

    private static final double TRACKING_RADIUS_SQ = 120 * 120;

    private WarContext activeContext;
    private boolean playerWasDead;
    private String lastProcessedBattleId;
    private int lastProcessedStateHash;

    public GuildWarTracker() {
        super("GuildWarTracker", "Tracks guild war results and reports them to Sequoia services", true);
    }

    @Subscribe
    public void onPlayerTick(PlayerTickEvent event) {
        if (mc.player == null) return;
        trackWarState();
        detectPlayerDeath();
    }

    private void trackWarState() {
        WarBattleInfo info = Models.GuildWarTower.getWarBattleInfo().orElse(null);
        if (info != null) {
            String battleId = buildBattleId(info);
            int stateHash = hashState(info.getCurrentState());
            if (activeContext != null
                    && battleId.equals(lastProcessedBattleId)
                    && stateHash == lastProcessedStateHash) {
                return;
            }
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
                submitWar(info, activeContext);
            }
        } else if (activeContext != null) {
            if (!activeContext.submissionSent) {
                submitWar(activeContext.info, activeContext);
            }
            activeContext = null;
            lastProcessedBattleId = null;
            lastProcessedStateHash = 0;
        }
    }

    private void detectPlayerDeath() {
        if (activeContext == null || activeContext.submissionSent) {
            playerWasDead = false;
            return;
        }
        assert mc.player != null;
        boolean isDead = mc.player.isDead() || mc.player.getHealth() <= 0;
        if (!playerWasDead && isDead) {
            handlePlayerDeath();
        }
        playerWasDead = isDead;
    }

    private void handlePlayerDeath() {
        if (activeContext != null && !activeContext.submissionSent) {
            submitWar(activeContext.info, activeContext);
        }
    }

    private void submitWar(WarBattleInfo info, WarContext context) {
        if (info == null || context == null) return;
        WarSummary summary = buildSummary(info);
        if (summary == null) return;

        WebSocketFeature webSocket = features().getIfActive(WebSocketFeature.class)
                .filter(WebSocketFeature::isActive)
                .filter(WebSocketFeature::isAuthenticated)
                .orElse(null);
        if (webSocket == null || mc.player == null) {
            return;
        }

        if (context.warrers.isEmpty()) {
            context.warrers = collectCurrentWarrers();
        }

        List<String> uniqueWarrers = context.warrers.isEmpty()
                ? List.of(mc.player.getGameProfile().getName())
                : new ArrayList<>(new LinkedHashSet<>(context.warrers));

        long submittedAtMillis = System.currentTimeMillis();
        String submittedAt = toRFC3339(submittedAtMillis);
        String startTime = toRFC3339(context.startEpochMs > 0 ? context.startEpochMs : submittedAtMillis);

        GGuildWarSubmissionWSMessage.Data data = new GGuildWarSubmissionWSMessage.Data(
                summary.territory(),
                mc.player.getUuidAsString(),
                submittedAt,
                startTime,
                uniqueWarrers,
                new GGuildWarSubmissionWSMessage.Results(toWsStats(summary.stats())));

        webSocket.sendMessage(new GGuildWarSubmissionWSMessage(data));
        context.submissionSent = true;
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
        assert mc.player != null;
        return mc.player.squaredDistanceTo(other) <= TRACKING_RADIUS_SQ;
    }

    private int hashState(WarTowerState state) {
        if (state == null) {
            return 0;
        }
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

    private static final class WarContext {
        private final String id;
        private WarBattleInfo info;
        private final long startEpochMs;
        private List<String> warrers;
        private WarTowerState lastKnownState;
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
