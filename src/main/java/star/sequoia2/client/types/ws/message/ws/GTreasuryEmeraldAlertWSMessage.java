package star.sequoia2.client.types.ws.message.ws;

import com.google.gson.annotations.SerializedName;
import star.sequoia2.client.types.ws.message.WSMessage;
import star.sequoia2.client.types.ws.type.WSMessageType;

import static star.sequoia2.client.types.ws.WSConstants.GSON;

public class GTreasuryEmeraldAlertWSMessage extends WSMessage {
    public GTreasuryEmeraldAlertWSMessage(Data data) {
        super(WSMessageType.G_KEEPERS_PING.getValue(), GSON.toJsonTree(data));
    }


    public record Data(
            boolean ping,
            @SerializedName("client_name") String clientName) {}
}
