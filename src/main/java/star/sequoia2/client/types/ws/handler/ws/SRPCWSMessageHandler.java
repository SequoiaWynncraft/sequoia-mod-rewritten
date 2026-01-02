package star.sequoia2.client.types.ws.handler.ws;

import net.minecraft.text.Text;
import star.sequoia2.accessors.NotificationsAccessor;
import star.sequoia2.accessors.TeXParserAccessor;
import star.sequoia2.client.types.ws.handler.WSMessageHandler;
import star.sequoia2.client.types.ws.message.ws.SRPCMessageWSMessage;

import static star.sequoia2.client.types.ws.WSConstants.GSON;
import static star.sequoia2.utils.XMLUtils.extractTextFromXml;

public class SRPCWSMessageHandler extends WSMessageHandler implements TeXParserAccessor, NotificationsAccessor {
    public SRPCWSMessageHandler(String message) {
        super(GSON.fromJson(message, SRPCMessageWSMessage.class), message);
    }

    @Override
    public void handle() {
        SRPCMessageWSMessage sRpcMessageWSMessage = (SRPCMessageWSMessage) wsMessage;
        if (sRpcMessageWSMessage.getMessageData().message_type().equals("message") ||
                sRpcMessageWSMessage.getMessageData().payload() instanceof String) {
            String tex = extractTextFromXml(String.valueOf(sRpcMessageWSMessage.getMessageData().payload()));
            notify(Text.literal("RPC ➤ ").append(teXParser().parseMutableText(tex)), "server-message-rpc");
        }
    }
}
