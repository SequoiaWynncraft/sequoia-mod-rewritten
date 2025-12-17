package star.sequoia2.features.impl.ws;

import com.collarmc.pounce.Preference;
import com.collarmc.pounce.Subscribe;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import star.sequoia2.accessors.EventBusAccessor;
import star.sequoia2.accessors.GuildParserAccessor;
import star.sequoia2.accessors.TeXParserAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.text.StyledText;
import star.sequoia2.events.PacketEvent;
import star.sequoia2.events.WynncraftLoginEvent;
import star.sequoia2.features.ToggleFeature;
import star.sequoia2.settings.types.BooleanSetting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static star.sequoia2.client.SeqClient.mc;

public class ChatHookFeature extends ToggleFeature implements GuildParserAccessor, TeXParserAccessor, EventBusAccessor {

    public ChatHookFeature() {
        super("ChatHook", "Chat related stuffs (type shi)", true);
    }

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

    // raid header
    private static final Pattern GUILD_RAID_HEADER = Pattern.compile(
            "^(?:§b)+\\s*"
                    + NAME_TOK + "\\s*,\\s*"
                    + NAME_TOK + "\\s*,\\s*"
                    + NAME_TOK + "\\s*,\\s*and\\s*"
                    + NAME_TOK + "\\s*$",
            Pattern.DOTALL
    );

//    // allow any §-style code
//    private static final String SEC = "§[0-9a-fk-or<>]";
//
//    // raid block (more tolerant)
//    private static final Pattern GUILD_RAID_BLOCK = Pattern.compile(
//            "^(?:§b)+\\s*"
//                    + NAME_TOK + "\\s*,\\s*"
//                    + NAME_TOK + "\\s*,\\s*"
//                    + NAME_TOK + "\\s*,\\s*and\\s*"
//                    + NAME_TOK
//                    + ".*?finished\\s*(?:" + SEC + ")*\\s*§3[^§\\r\\n]+§b",
//            Pattern.DOTALL
//    );


    private static final String GUILD_RAID_PREFIX1 = "§b\uE006\uE002§b";
    private static final String GUILD_RAID_PREFIX2 = "§b\uE001§b";
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

    @Subscribe(value = Preference.MAIN, priority = 1)
    public void onChatMessage(PacketEvent.PacketReceiveEvent event) {
        if (!(event.packet() instanceof GameMessageS2CPacket(Text content, boolean overlay))) return;
        if (content == null || overlay) return;
        if (seq$shouldDrop(content)) return;

        String raw = content.getString();
        // Fast-path auto-connect without TeX parsing
        if (AUTO_CONNECT.matcher(raw).find()) {
            SeqClient.debug("parsing as login...");
            dispatch(new WynncraftLoginEvent());
            Optional<WebSocketFeature> wsFeature = features().getIfActive(WebSocketFeature.class);
            boolean wsAuthenticated = wsFeature.map(WebSocketFeature::isAuthenticated).orElse(false);
            if (wsFeature.map(WebSocketFeature::getConnectOnJoin).map(BooleanSetting::get).orElse(false)
                    && !wsAuthenticated
                    && mc.player != null) {
                mc.player.networkHandler.sendCommand("seqconnect");
            }
            return;
        }

        Optional<WebSocketFeature> wsFeature = features().getIfActive(WebSocketFeature.class);
        Optional<ChatHookFeature> chatHookFeature = features().getIfActive(ChatHookFeature.class);
        boolean wsEnabled = wsFeature.map(WebSocketFeature::isActive).orElse(false);
        boolean wsAuthenticated = wsFeature.map(WebSocketFeature::isAuthenticated).orElse(false);
        boolean hookActive = chatHookFeature.map(ChatHookFeature::isActive).orElse(false);

        if (!wsEnabled || !wsAuthenticated || !hookActive) {
            return;
        }

        StyledText styledText = StyledText.fromComponent(content);
        String tex = teXParser().toTeX(styledText.stripAlignment());

        if ((tex.startsWith(GUILD_CHAT_PREFIX1) || tex.startsWith(GUILD_CHAT_PREFIX2)) &&
                ((GUILD_CHAT_HOVER.matcher(tex).results().limit(2).count()
                        + GUILD_CHAT_PLAIN.matcher(tex).results().limit(2).count()) + GUILD_CHAT_WYNNTILS_NAME.matcher(tex).results().limit(2).count()) == 1) {
            SeqClient.debug("parsing as guild chat message...");
            guildMessageParser().parseGuildMessage(tex);
            return;
        }

        tex = remove_multiline(tex);

        if (GUILD_RAID_BLOCK.matcher(tex).find() || OTHER_GUILD_RAID_BLOCK.matcher(tex).find()) {
            SeqClient.debug("parsing as guild raid completion...");
            guildRaidParser().parseGuildRaid(tex);
        }
    }

    private static String getString(ChatMessageS2CPacket messagePacket) {
        Text message = messagePacket.unsignedContent();
//            "[12:12:02] [Render thread/INFO] (sequoia2) [VERBOSE] [CHAT] §b\uDAFF\uDFFC\uE006\uDAFF\uDFFF\uE002\uDAFF\uDFFE §ebad_and_sad§b, §eMasss§b, §e§owar tank§b, and §e§oTotal Obliteration§b finished §3The Nameless §b\uDAFF\uDFFC\uE001\uDB00\uDC06§3 Anomaly§b and claimed §32x Aspects§b, §32048x §b\uDAFF\uDFFC\uE001\uDB00\uDC06§3 Emeralds§b, and §3+10367m Guild Experience\n"
//            StyledText styledText = StyledText.fromComponent(message);
//
//            String tex = teXParser().toTeX(styledText.stripAlignment());
//

        String tex = message.toString();
        return tex;
    }

    private static final Pattern SECTION_CODES =
            Pattern.compile("§[0-9a-fk-or<>]", Pattern.CASE_INSENSITIVE);

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
}
