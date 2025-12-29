package star.sequoia2.features.impl.ws;

import com.collarmc.pounce.Preference;
import com.collarmc.pounce.Subscribe;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import star.sequoia2.accessors.EventBusAccessor;
import star.sequoia2.accessors.GuildParserAccessor;
import star.sequoia2.accessors.TeXParserAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.text.StyledText;
import star.sequoia2.events.PacketEvent;
import star.sequoia2.events.WynncraftLoginEvent;
import star.sequoia2.features.ToggleFeature;
import star.sequoia2.settings.types.BooleanSetting;
import star.sequoia2.utils.chatparser.GuildRaidParser;

import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static star.sequoia2.client.SeqClient.mc;

public class ChatHook extends ToggleFeature implements GuildParserAccessor, TeXParserAccessor, EventBusAccessor {

    public ChatHook() {
        super("ChatHook", "Chat related stuffs (type shi)", true);
    }

    BooleanSetting gRaidsUntilLvlUp = settings().bool("GRaidsUntilLvlUp", "Raids till levelup in raid comp message", false);

    private final Deque<PendingRaid> pendingRaids = new ConcurrentLinkedDeque<>();

    private boolean suppressNextGuStats = false;
    private boolean expectGuStats = false;
    private long statsRequestAtMs = 0L;
    private long suppressGuStatsStartedAtMs = 0L;

    private static final Pattern GUILD_XP_PATTERN = Pattern.compile("(?i)\\+(\\d+)([kmb])?\\s+Guild\\s+Experience");
    private static final Pattern XP_PATTERN = Pattern.compile(".*Needed XP:.*?(\\d+).*?/(\\d+).*");
    private static final long STATS_TIMEOUT_MS = 15000L;
    private static final long SUPPRESS_FAILSAFE_MS = 2500L;

    private long suppressTrailingBlankUntilMs = 0L;
    private static final long TRAILING_BLANK_SUPPRESS_MS = 5000L;

    private static final Pattern SECTION_CODES =
            Pattern.compile("§[0-9a-fk-or<>]", Pattern.CASE_INSENSITIVE);

    private static final String GUILD_CHAT_PREFIX1 = "§b\uE006\uE002§b §b\uE060";
    private static final String GUILD_CHAT_PREFIX2 = "§b\uE001§b §b\uE060";

    // i hate apostrophes
    private static final Pattern GUILD_CHAT_HOVER = Pattern.compile(
            "\\\\hover\\{§f[^}]+'(?:s)?§7\\s+real\\s+name\\s+is\\s+§f[\\w]+}\\{§3(?:§o)?[^}]+}\\s*§3:§b"
    );

    private static final Pattern GUILD_CHAT_PLAIN = Pattern.compile(
            "§3\\w+:§b"
    );

    private static final Pattern GUILD_CHAT_WYNNTILS_NAME = Pattern.compile(
            "§3[^\\s§/]+/§3§o[^\\s§]+"
    );

    // one “name” token: either plain colored name, or hover-visible colored name
    private static final String NAME_TOK =
            "(?:\\\\hover\\{[^}]+}\\{§e(?:§o)?[^}]+}§b|§e[^§\\r\\n]+§b)";

    // this actually works
    private static final Pattern GUILD_RAID_BLOCK = Pattern.compile("§b finished");
    private static final Pattern OTHER_GUILD_RAID_BLOCK = Pattern.compile("§b §bfinished");

    private static final Pattern AUTO_CONNECT =
            Pattern.compile("§6§lWelcome to Wynncraft!");

    private static final int   CACHE_SIZE    = 256;
    private static final long  DUP_WINDOW_MS = 1000L;
    private static final long  RECENT_RETENTION_MS = 60_000L;

    private static final Map<Integer, Long> SEQ$RECENT =
            Collections.synchronizedMap(new LinkedHashMap<Integer, Long>(CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, Long> eldest) {
                    long now = System.currentTimeMillis();
                    if (now - eldest.getValue() > RECENT_RETENTION_MS) {
                        return true;
                    }
                    return size() > CACHE_SIZE;
                }
            });

    @Subscribe
    public void onChatMessage(PacketEvent.PacketReceiveEvent event) {
        if (!(event.packet() instanceof GameMessageS2CPacket(Text content, boolean overlay))) return;
        if (content == null || overlay) return;

        long now = System.currentTimeMillis();
        String raw = content.getString();

        if (gRaidsUntilLvlUp.get()) {
            if (suppressTrailingBlankUntilMs > now && seq$isEffectivelyBlank(raw)) {
                event.cancel();
                return;
            }

            if (expectGuStats && statsRequestAtMs > 0 && now - statsRequestAtMs > STATS_TIMEOUT_MS) {
                SeqClient.debug("Timed out waiting for gu stats response; resetting state");
                expectGuStats = false;
                suppressNextGuStats = false;
                statsRequestAtMs = 0L;
                suppressGuStatsStartedAtMs = 0L;
                suppressTrailingBlankUntilMs = now + TRAILING_BLANK_SUPPRESS_MS;
                PendingRaid dropped = pendingRaids.pollFirst();
                if (dropped != null) {
                    SeqClient.debug("Dropping pending raid after stats timeout");
                }
            }

            if (suppressNextGuStats && suppressGuStatsStartedAtMs > 0 && now - suppressGuStatsStartedAtMs > SUPPRESS_FAILSAFE_MS) {
                suppressNextGuStats = false;
                suppressGuStatsStartedAtMs = 0L;
                expectGuStats = false;
                statsRequestAtMs = 0L;
                suppressTrailingBlankUntilMs = now + TRAILING_BLANK_SUPPRESS_MS;
            }

            if (expectGuStats) {
                if (seq$isGuStatsHeader(content, raw)) {
                    suppressNextGuStats = true;
                    suppressGuStatsStartedAtMs = now;
                    expectGuStats = false;
                    statsRequestAtMs = 0L;
                    event.cancel();
                    return;
                }

                if (seq$isEffectivelyBlank(raw)) {
                    event.cancel();
                    return;
                }

                event.cancel();
                return;
            }

            if (suppressNextGuStats) {
                Matcher m = XP_PATTERN.matcher(raw);
                if (m.matches()) {
                    String currentXp = m.group(1);
                    String requiredXp = m.group(2);
                    try {
                        long cur = Long.parseLong(currentXp);
                        long need = Long.parseLong(requiredXp);
                        processPendingWithStats(cur, need);
                    } catch (Exception ignored) {
                    }
                }

                if (raw.contains("Total Members:")) {
                    suppressNextGuStats = false;
                    suppressGuStatsStartedAtMs = 0L;
                    suppressTrailingBlankUntilMs = now + TRAILING_BLANK_SUPPRESS_MS;
                }

                event.cancel();
                return;
            }
        }

        if (seq$shouldDrop(content)) {
            event.cancel();
            return;
        }

        if (AUTO_CONNECT.matcher(raw).find()) {
            SeqClient.debug("parsing as login...");
            dispatch(new WynncraftLoginEvent());
            Optional<WebSocket> wsFeature = features().getIfActive(WebSocket.class);
            boolean wsAuthenticated = wsFeature.map(WebSocket::isAuthenticated).orElse(false);
            if (wsFeature.map(WebSocket::getConnectOnJoin).map(BooleanSetting::get).orElse(false)
                    && !wsAuthenticated
                    && mc.player != null) {
                mc.player.networkHandler.sendCommand("seqconnect");
            }
            return;
        }

        StyledText styledText = StyledText.fromComponent(content);
        String tex = teXParser().toTeX(styledText.stripAlignment());
        String texNoMultiline = remove_multiline(tex);

        boolean isRaid = GUILD_RAID_BLOCK.matcher(texNoMultiline).find() || OTHER_GUILD_RAID_BLOCK.matcher(texNoMultiline).find();
        if (isRaid) {
            SeqClient.debug("parsing as guild raid completion...");
            guildRaidParser().parseGuildRaid(texNoMultiline);

            if (gRaidsUntilLvlUp.get()) {
                event.cancel();

                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendChatCommand("gu stats");
                    expectGuStats = true;
                    statsRequestAtMs = now;

                    long xpPerRaid = parseRaidXp(styledText);
                    PendingRaid pendingRaid = new PendingRaid(styledText, xpPerRaid, now);
                    pendingRaids.add(pendingRaid);
                    SeqClient.SCHEDULER.schedule(() -> timeoutPending(pendingRaid), STATS_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                }
            }
            return;
        }

        Optional<WebSocket> wsFeature = features().getIfActive(WebSocket.class);
        boolean wsEnabled = wsFeature.map(WebSocket::isActive).orElse(false);
        boolean wsAuthenticated = wsFeature.map(WebSocket::isAuthenticated).orElse(false);

        if (!wsEnabled || !wsAuthenticated) {
            return;
        }

        if ((tex.startsWith(GUILD_CHAT_PREFIX1) || tex.startsWith(GUILD_CHAT_PREFIX2)) &&
                ((GUILD_CHAT_HOVER.matcher(tex).results().limit(2).count()
                        + GUILD_CHAT_PLAIN.matcher(tex).results().limit(2).count()) + GUILD_CHAT_WYNNTILS_NAME.matcher(tex).results().limit(2).count()) == 1) {
            SeqClient.debug("parsing as guild chat message...");
            guildMessageParser().parseGuildMessage(tex);
        }
    }

    private static boolean seq$isEffectivelyBlank(String raw) {
        if (raw == null) return true;
        String s = remove_multiline(raw);
        return s.isBlank();
    }

    private static boolean seq$isGuStatsHeader(Text content, String raw) {
        if (isGuStatsHeader(content)) return true;
        String cleaned = remove_formatting(remove_multiline(raw));
        return cleaned.toLowerCase().contains("statistics") && cleaned.toLowerCase().contains("rewards");
    }

    public static String remove_multiline(String s) {
        if (s == null || s.isEmpty()) return "";
        s = s.replaceAll("§.\uE001§.", "").trim();
        StringBuilder out = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            if (Character.getType(cp) != Character.PRIVATE_USE) out.appendCodePoint(cp);
        });
        s = out.toString();
        s = s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
        return s;
    }

    public static String remove_formatting(String s) {
        return SECTION_CODES.matcher(s).replaceAll("");
    }

    public static String clean(String s) {
        if (s == null || s.isEmpty()) return "";
        return remove_formatting(remove_multiline(s));
    }

    private static int seq$keyFrom(Text msg) {
        String s = msg.getString().replaceAll("\\s+", " ").trim();
        return s.hashCode();
    }

    private static boolean seq$shouldDrop(Text msg) {
        long now = System.currentTimeMillis();
        int key = seq$keyFrom(msg);
        synchronized (SEQ$RECENT) {
            SEQ$RECENT.entrySet().removeIf(entry -> now - entry.getValue() > RECENT_RETENTION_MS);
            Long last = SEQ$RECENT.get(key);
            if (last != null && (now - last) <= DUP_WINDOW_MS) {
                return true;
            }
            SEQ$RECENT.put(key, now);
        }
        return false;
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

        mc.execute(() -> {
            if (mc.inGameHud != null) {
                mc.inGameHud.getChatHud().addMessage(out.getComponent());
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
        suppressGuStatsStartedAtMs = 0L;
        pendingRaids.clear();
        suppressTrailingBlankUntilMs = 0L;
    }

    private record PendingRaid(StyledText message, long xpPerRaid, long createdAtMs) {}
}
