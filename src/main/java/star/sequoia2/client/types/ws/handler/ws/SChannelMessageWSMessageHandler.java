package star.sequoia2.client.types.ws.handler.ws;

import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.accessors.NotificationsAccessor;
import star.sequoia2.accessors.TeXParserAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.handler.WSMessageHandler;
import star.sequoia2.client.types.ws.message.ws.SChannelMessageWSMessage;
import star.sequoia2.features.impl.ws.DiscordChatBridge;

import java.util.List;

import static star.sequoia2.client.types.ws.WSConstants.GSON;
import static star.sequoia2.utils.XMLUtils.extractTextFromXml;

public class SChannelMessageWSMessageHandler extends WSMessageHandler implements TeXParserAccessor, FeaturesAccessor, NotificationsAccessor {
    public SChannelMessageWSMessageHandler(String message) {
        super(GSON.fromJson(message, SChannelMessageWSMessage.class), message);
    }

    private final String MESSAGE_FORMAT = "\\gradient%s{%s}\\={:} \\-{%s}";

    @Override
    public void handle() {
        SeqClient.debug(wsMessage.toString());
        if (features().getIfActive(DiscordChatBridge.class).map(DiscordChatBridge::isActive).orElse(false)
                && features().getIfActive(DiscordChatBridge.class).map(discordChatBridge -> discordChatBridge.getSendDiscordMessageToChat().get()).orElse(false)) {
            SChannelMessageWSMessage sChannelMessageWSMessage = (SChannelMessageWSMessage) wsMessage;
            SChannelMessageWSMessage.Data d = sChannelMessageWSMessage.getSChannelMessageData();
            String name = d.displayName() == null ? "" : d.displayName();
            String msg = d.message() == null ? "" : d.message();
            String messageTeX = isLikelyXml(msg) ? extractTextFromXml(msg) : teXParser().sanitize(msg);
            notify(teXParser().parseMutableText(
                    MESSAGE_FORMAT,
                    formatColorArgs(d.color()),
                    teXParser().sanitize(name),
                    messageTeX
            ), "discord-bridge");
        }
    }

    private static boolean isLikelyXml(String s) {
        if (s == null) return false;
        String t = s.trim();
        return !t.isEmpty() && t.charAt(0) == '<';
    }

    public static String formatColorArgs(List<Integer> colors) {
        if (colors == null || colors.isEmpty()) return "{1}{ffffff}";
        StringBuilder sb = new StringBuilder();
        sb.append("{").append(colors.size()).append("}");
        for (Integer color : colors) {
            int c = color == null ? 0xffffff : color;
            sb.append("{").append(String.format("%06x", c)).append("}");
        }
        return sb.toString();
    }
}
