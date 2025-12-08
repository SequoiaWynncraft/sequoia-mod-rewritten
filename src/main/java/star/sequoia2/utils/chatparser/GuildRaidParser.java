package star.sequoia2.utils.chatparser;

import star.sequoia2.accessors.EventBusAccessor;
import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.message.ws.guildraid.GGuildRaidWSMessage;
import star.sequoia2.client.types.ws.message.ws.guildraid.GuildRaid;
import star.sequoia2.client.types.ws.message.ws.guildraid.RaidType;
import star.sequoia2.events.RaidCompleteFromChatEvent;
import star.sequoia2.features.impl.ws.WebSocketFeature;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static star.sequoia2.client.SeqClient.mc;
import static star.sequoia2.features.impl.ws.ChatHookFeature.remove_formatting;
import static star.sequoia2.utils.cache.SequoiaMemberCache.isSequoiaMember;

public class GuildRaidParser implements FeaturesAccessor, EventBusAccessor {
    // \hover{<hoverText>}{<visible>}
    private static final Pattern HOVER = Pattern.compile("\\\\hover\\{([^}]*)}\\{([^}]*)}");
    // "<nick>'s real name is <user>"
    private static final Pattern REALNAME = Pattern.compile("(?i)(.*?)['’]s?\\s+real\\s+name\\s+is\\s+(.+)");

    // finished <raid name> and claimed ...
    private static final Pattern RAID_NAME = Pattern.compile("(?i)finished\\s+(.*?)\\s+and\\s+claimed");
    private static final Pattern ASPECTS   = Pattern.compile("(?i)(\\d+)x\\s+Aspects");
    private static final Pattern EMERALDS  = Pattern.compile("(?i)(\\d+)x\\s+Emeralds");
    private static final Pattern GXP       = Pattern.compile("(?i)\\+(\\d+)([kmb])?\\s+Guild\\s+Experience");
    private static final Pattern SR        = Pattern.compile("(?i)\\+(\\d+)\\s+Seasonal\\s+Rating");

    public void parseGuildRaid(String tex) {
        try {
            String plain = remove_formatting(tex);
            SeqClient.debug(String.format("plain raid: %s", plain));
            if (plain.isEmpty()) return;

            // split into header (players) + tail (rewards) at the first "finished"
            int finIdx = plain.indexOf("finished");
            if (finIdx < 0) return; // not a raid block for any reason
            String header = plain.substring(0, finIdx);
            String tail   = plain.substring(finIdx);

            // collect 4 players
            List<String> players = new ArrayList<>(4);

            // hovers (if needed)
            Matcher mh = HOVER.matcher(header);
            LinkedHashSet<String> uniq = new LinkedHashSet<>(4);
            while (mh.find()) {
                String hoverText = mh.group(1);
                String visible   = mh.group(2);
                String user = extractRealUser(hoverText);
                String candidate = cleanName(user != null ? user : visible);
                if (candidate.isEmpty()) continue;
                if (!isSequoiaMember(candidate)) {
                    SeqClient.debug(candidate + " is not a Sequoia member!");
                    return;
                }
                uniq.add(candidate);
                if (uniq.size() == 4) break;
            }
            // remove hovers and parse remaining plain names (comma-separated, last "and")
            String headerNoHover = mh.replaceAll(""); // removes all hover blocks from header
            for (String tok : splitNames(headerNoHover)) {
                if (uniq.size() == 4) break;
                String name = cleanName(tok);
                if (name.isEmpty()) continue;
                if (!isSequoiaMember(name)) {
                    SeqClient.debug(name + " is not a Sequoia member!");
                    return;
                }
                uniq.add(name);
            }
            players.addAll(uniq);
            if (players.size() != 4) return; // need exactly 4

            // parse from rest of block
            String raidDisplay = matchGroup(RAID_NAME, tail);
            long aspects  = parseLong(matchGroup(ASPECTS, tail), 0);
            long emeralds = parseLong(matchGroup(EMERALDS, tail), 0);
            long xp       = parseScaled(matchGroup(GXP, tail, 1), matchGroup(GXP, tail, 2));
            long sr       = parseLong(matchGroup(SR, tail), 0);

            SeqClient.debug(String.format(
                    "[GuildRaid] raid=%s | players=%s | aspects=%,d | emeralds=%,d | gxp=%,d | sr=%,d",
                    (raidDisplay == null ? "?" : raidDisplay),
                    players,
                    aspects,
                    emeralds,
                    xp,
                    sr
            ));

            RaidType type = RaidType.getRaidType((raidDisplay));
            if (type == null) return;

            SeqClient.debug(type.getDisplayName());

            UUID reporter = mc.player.getUuid();

            if (reporter == null) return;

            GuildRaid payload =
                    new GuildRaid(
                            type, players, reporter, aspects, emeralds, xp, sr
                    );
            dispatch(new RaidCompleteFromChatEvent());
            Optional<WebSocketFeature> wsFeature = features().getIfActive(WebSocketFeature.class);
            if (wsFeature.map(WebSocketFeature::isActive).orElse(false)
                    && wsFeature.map(WebSocketFeature::isAuthenticated).orElse(false)) {
                wsFeature.ifPresent(webSocketFeature -> webSocketFeature.sendMessage(new GGuildRaidWSMessage(payload)));
            }

        } catch (Exception e) {
            SeqClient.error("Failed to parse/send guild raid", e);
        }
    }

    public static String matchGroup(Pattern p, String s) { return matchGroup(p, s, 1); }
    public static String matchGroup(Pattern p, String s, int group) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(group) : null;
    }

    private static String extractRealUser(String hoverText) {
        if (hoverText == null) return null;
        Matcher m = REALNAME.matcher(hoverText);
        if (m.find()) return cleanName(m.group(2)); // get rid of the hover text stuff
        return null;
    }

    private static String cleanName(String s) {
        if (s == null) return "";
        s = s.replaceAll("[{}\\\\]", "");  // drop TeX braces/backslashes if they leak
        s = s.replaceAll("\\s{2,}", " ").trim();

        int slash = s.indexOf('/');
        if (slash > 0) s = s.substring(0, slash).trim();

        return s;
    }

    private static String[] splitNames(String headerNoHover) {
        if (headerNoHover == null) return new String[0];
        // Replace " and " with comma, then split by comma
        String s = headerNoHover.replaceAll("\\s*\\band\\b\\s*", ",");
        String[] parts = s.split("\\s*,\\s*");

        return java.util.Arrays.stream(parts).filter(t -> !t.isEmpty())
                           .toArray(String[]::new);
    }


    private static long parseLong(String n, long def) {
        if (n == null) return def;
        try { return Long.parseLong(n); } catch (NumberFormatException e) { return def; }
    }

    public static long parseScaled(String n, String suffix) {
        long base = parseLong(n, 0);
        if (suffix == null || suffix.isEmpty()) return base;
        return switch (Character.toLowerCase(suffix.charAt(0))) {
            case 'k' -> base * 1_000L;
            case 'm' -> base * 1_000_000L;
            case 'b' -> base * 1_000_000_000L;
            default -> base;
        };

    }

}
