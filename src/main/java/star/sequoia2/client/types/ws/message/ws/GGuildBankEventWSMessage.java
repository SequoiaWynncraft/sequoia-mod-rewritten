package star.sequoia2.client.types.ws.message.ws;

import com.google.gson.annotations.SerializedName;
import star.sequoia2.client.types.ws.WSConstants;
import star.sequoia2.client.types.ws.message.WSMessage;
import star.sequoia2.client.types.ws.type.WSMessageType;

public class GGuildBankEventWSMessage extends WSMessage {

    public GGuildBankEventWSMessage(Data data) {
        super(WSMessageType.G_GUILD_BANK_EVENT.getValue(), WSConstants.GSON.toJsonTree(data));
    }

    public record Data(
            String item,
            int amount,
            String username,
            String action,
            @SerializedName("hr") boolean highRank,
            @SerializedName("timestamp") long timestampMillis) {}
}
