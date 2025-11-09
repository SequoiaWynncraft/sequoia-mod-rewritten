package star.sequoia2.client.types.ws.message.ws;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import star.sequoia2.client.types.ws.WSConstants;
import star.sequoia2.client.types.ws.message.WSMessage;
import star.sequoia2.client.types.ws.type.WSMessageType;

public class GGuildBankStateWSMessage extends WSMessage {

    public GGuildBankStateWSMessage(Data data) {
        super(WSMessageType.G_GUILD_BANK_STATE.getValue(), WSConstants.GSON.toJsonTree(data));
    }

    public record Data(@SerializedName("hr_bank") boolean highRankBank, List<Item> items) {}

    public record Item(
            @SerializedName("item_name") String itemName,
            @SerializedName("nbt_data") List<String> nbtData,
            int amount) {}
}
