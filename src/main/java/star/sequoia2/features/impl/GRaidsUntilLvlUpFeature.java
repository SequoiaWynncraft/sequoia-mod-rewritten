package star.sequoia2.features.impl;

import com.collarmc.pounce.Subscribe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import star.sequoia2.accessors.EventBusAccessor;
import star.sequoia2.accessors.TeXParserAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.text.StyledText;
import star.sequoia2.events.PacketEvent;
import star.sequoia2.features.ToggleFeature;
import star.sequoia2.utils.chatparser.GuildRaidParser;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static star.sequoia2.features.impl.ws.ChatHookFeature.remove_multiline;


public class GRaidsUntilLvlUpFeature extends ToggleFeature implements TeXParserAccessor, EventBusAccessor {
    private final Deque<PendingRaid> pendingRaids = new ConcurrentLinkedDeque<>();

    private boolean suppressNextGuStats = false;
    private boolean expectGuStats = false;
    private long statsRequestAtMs = 0L;

    private static final Pattern GUILD_RAID_BLOCK = Pattern.compile("§b finished");
    private static final Pattern OTHER_GUILD_RAID_BLOCK = Pattern.compile("§b §bfinished");
    private static final Pattern GUILD_XP_PATTERN = Pattern.compile("(?i)\\+(\\d+)([kmb])?\\s+Guild\\s+Experience");
    private static final Pattern XP_PATTERN = Pattern.compile(".*Needed XP:.*?(\\d+).*?/(\\d+).*");
    private static final long STATS_TIMEOUT_MS = 15000L;

    private static final Pattern SECTION_CODES =
            Pattern.compile("§[0-9a-fk-or<>]", Pattern.CASE_INSENSITIVE);

    public GRaidsUntilLvlUpFeature() {
        super("Guild raid completion levelup progress", "Shows you the amount of graids that are needed to reach the next guild level.", true);
    }

    public static int calculateNeededRaids(long current, long needed, long xpPerRaid) {
        long missing = needed - current;

        if (missing <= 0) return 0;

        return (int) Math.ceil((double) missing / xpPerRaid);
    }

    public static boolean isGuStatsHeader(Text text) {
        Style s = text.getStyle();
        if (!s.isBold()) return false;
        if (s.getColor() != TextColor.fromFormatting(Formatting.GOLD)) return false;
        if (!(text.getContent() instanceof PlainTextContent.Literal literal)) return false;

        String t = literal.string();
        return !t.isEmpty();
    }

    @Subscribe
    public void onChatMessage(PacketEvent.PacketReceiveEvent event) {
        if (!(event.packet() instanceof GameMessageS2CPacket(Text content, boolean overlay))) return;
        if (content == null || overlay) return;
        if(!features().getIfActive(GRaidsUntilLvlUpFeature.class).map(GRaidsUntilLvlUpFeature::isActive).orElse(false)) {
            return;
        }

        String raw = content.getString();
        if (expectGuStats && statsRequestAtMs > 0 && System.currentTimeMillis() - statsRequestAtMs > STATS_TIMEOUT_MS) {
            SeqClient.debug("Timed out waiting for gu stats response; resetting state");
            expectGuStats = false;
            suppressNextGuStats = false;
            statsRequestAtMs = 0L;
            current = 0L;
            needed = 0L;
            signalStatsReady();
        }

        if(expectGuStats && (raw.isBlank() || content.toString().equals("empty"))) {
            event.cancel();
            return;
        }
        if(expectGuStats && GRaidsUntilLvlUpFeature.isGuStatsHeader(content)) {
            suppressNextGuStats = true;
            expectGuStats = false;
            statsRequestAtMs = 0L;
        }
        if (suppressNextGuStats) {
            if(raw.contains("Total Members:")) {
                suppressNextGuStats = false;
            }

            Matcher m = XP_PATTERN.matcher(raw);

            if (m.matches()) {
                String currentXp = m.group(1);
                String requiredXp = m.group(2);

                try {
                    long cur = Long.parseLong(currentXp);
                    long need = Long.parseLong(requiredXp);
                    processPendingWithStats(cur, need);
                } catch (Exception ignored) {}
            }

            event.cancel();
            return;
        }


        StyledText styledText = StyledText.fromComponent(content);
        String tex = teXParser().toTeX(styledText.stripAlignment());

        tex = remove_multiline(tex);

        if (GUILD_RAID_BLOCK.matcher(tex).find() || OTHER_GUILD_RAID_BLOCK.matcher(tex).find()) {
            if(MinecraftClient.getInstance().getNetworkHandler() == null) return;
            MinecraftClient.getInstance().getNetworkHandler().sendChatCommand("gu stats");
            expectGuStats = true;
            statsRequestAtMs = System.currentTimeMillis();
            event.cancel();

            long xpPerRaid = parseRaidXp(styledText);
            PendingRaid pendingRaid = new PendingRaid(styledText, xpPerRaid, System.currentTimeMillis());
            pendingRaids.add(pendingRaid);
            SeqClient.SCHEDULER.schedule(() -> timeoutPending(pendingRaid), STATS_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    private long parseRaidXp(StyledText message) {
        String plain = SECTION_CODES.matcher(message.getString()).replaceAll("");
        if (plain.isEmpty()) return 0L;

        int finIdx = plain.indexOf("finished");
        if (finIdx < 0) return 0L;
        String tail = plain.substring(finIdx);

        return GuildRaidParser.parseScaled(
                GuildRaidParser.matchGroup(GUILD_XP_PATTERN, tail, 1),
                GuildRaidParser.matchGroup(GUILD_XP_PATTERN, tail, 2));
    }

    private void processPendingWithStats(long current, long needed) {
        PendingRaid pending = pendingRaids.pollFirst();
        if (pending == null) return;
        long xpPerRaid = pending.xpPerRaid;
        if (xpPerRaid <= 0) {
            SeqClient.debug("Guild raid XP parse failed; skipping raid count message");
            return;
        }
        StyledText message = pending.message;
        int raidsLeft = calculateNeededRaids(current, needed, xpPerRaid);
        StyledText out = message.append((needed == 0L ? "" : "§3. §b" + raidsLeft + " guild raids left to level up."));

        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().inGameHud != null) {
                MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(out.getComponent());
            }
        });
    }

    private void timeoutPending(PendingRaid pending) {
        if (pendingRaids.remove(pending)) {
            SeqClient.debug("Timed out waiting for guild stats after raid completion");
        }
    }

    @Override
    protected void onDeactivate() {
        suppressNextGuStats = false;
        expectGuStats = false;
        statsRequestAtMs = 0L;
        pendingRaids.clear();
    }

    @Override
    protected void onActivate() {
        // no-op; kept for symmetry
    }

    private record PendingRaid(StyledText message, long xpPerRaid, long createdAtMs) {}
}
}
