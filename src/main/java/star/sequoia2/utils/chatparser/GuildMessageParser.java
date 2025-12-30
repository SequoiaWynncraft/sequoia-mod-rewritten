package star.sequoia2.utils.chatparser;

import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.message.ws.GChatMessageWSMessage;
import star.sequoia2.features.impl.ws.ChatHook;
import star.sequoia2.features.impl.ws.WebSocket;
import star.sequoia2.utils.TimeUtils;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static star.sequoia2.client.SeqClient.mc;
import static star.sequoia2.features.impl.ws.ChatHook.clean;
import static star.sequoia2.utils.cache.SequoiaMemberCache.isSequoiaMember;

public class GuildMessageParser implements FeaturesAccessor {

    private static final Pattern GUILD_CHAT_HOVER = Pattern.compile(
            "\\\\hover\\{(?<hover>[^}]*)}\\{(?<visible>[^}]*)}\\s*§3:\\s*§[0-9a-fk-or<>]\\s*(?<msg>.*)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern GUILD_CHAT_PLAIN = Pattern.compile(
            "§3\\s*(?<name>[^:]+):\\s*§[0-9a-fk-or<>]\\s*(?<msg>.*)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // "<nick>'s real name is <user>"
    private static final Pattern REALNAME_IN_HOVER = Pattern.compile(
            "(?i)(.*?)'?s?\\s+real\\s+name\\s+is\\s+(.*)");

    public void parseGuildMessage(String tex) {
        try {
            String username = null, nickname = null, guildMsg = null;
            Optional<WebSocket> wsFeature = features().getIfActive(WebSocket.class);
            Optional<ChatHook> chatFeature = features().getIfActive(ChatHook.class);
            boolean wsEnabled = wsFeature.map(WebSocket::isActive).orElse(false);
            boolean wsAuthenticated = wsFeature.map(WebSocket::isAuthenticated).orElse(false);
            boolean chatEnabled = chatFeature.map(ChatHook::isActive).orElse(false);

            Matcher mh = GUILD_CHAT_HOVER.matcher(tex);
            if (mh.find()) {
                String hover   = mh.group("hover");
                String visible = mh.group("visible");
                String msgRaw  = mh.group("msg");

                String hoverClean   = clean(hover);
                String visibleClean = clean(visible);
                String msgClean     = clean(msgRaw);

                Matcher rn = REALNAME_IN_HOVER.matcher(hoverClean);
                if (rn.find()) {
                    nickname = rn.group(1).trim();
                    username = rn.group(2).trim();
                }
                if (username == null || username.isEmpty()) {
                    username = visibleClean;   // fallback: visible name
                    nickname = null;
                }
                if (!isSequoiaMember(username)) {
                    return;
                }
                guildMsg = msgClean;

            } else {
                Matcher mp = GUILD_CHAT_PLAIN.matcher(tex);
                if (!mp.find()) return; // not a guild chat line (defensive)
                username = clean(mp.group("name"));
                guildMsg = clean(mp.group("msg"));

                int slash = username.indexOf('/');
                if (slash > 0) { nickname = username.substring(slash + 1).trim(); username = username.substring(0, slash).trim(); }
            }

//            Sequoia2.debug(String.format("[GUILD CHAT] %s%s: %s", username, nickname != null ? nickname : "", guildMsg));
            if (guildMsg.contains("  ")) return;
            if (username.isBlank() || guildMsg.isBlank()) return;
            if (!wsEnabled) return;
            if (!wsAuthenticated) return;
            if (!chatEnabled) return;

            GChatMessageWSMessage payload = new GChatMessageWSMessage(
                    new GChatMessageWSMessage.Data(
                            username,
                            nickname,
                            guildMsg,
                            TimeUtils.wsTimestamp(),
                            mc.player.getName().getString()
                    )
            );
            features().getIfActive(WebSocket.class).map(webSocketFeature -> webSocketFeature.sendMessage(payload));
        } catch (Exception e) {
            SeqClient.error("Failed to parse guild chat message", e);
        }
    }



}
