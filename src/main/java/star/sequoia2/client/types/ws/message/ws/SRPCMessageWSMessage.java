package star.sequoia2.client.types.ws.message.ws;

import star.sequoia2.client.types.ws.message.WSMessage;
import star.sequoia2.client.types.ws.type.WSMessageType;

import static star.sequoia2.client.types.ws.WSConstants.GSON;

public class SRPCMessageWSMessage extends WSMessage {
    public SRPCMessageWSMessage(Data data) {
        super(WSMessageType.S_RPC_MESSAGE.getValue(), GSON.toJsonTree(data));
    }

    public SRPCMessageWSMessage.Data getMessageData() {
        return GSON.fromJson(getData(), SRPCMessageWSMessage.Data.class);

    }

    public record Data (
            String message_type,
            String metadata,
            Object payload
    ){}
    //data
    //type SRPCMessageData struct {
    //	MessageType NRPCMessageType `json:"message_type"` // "message" or "event"
    //	Metadata    map[string]any  `json:"metadata"`
    //	Payload     any             `json:"payload"` // if type is "message" then it must be serializable from xft, else  it can be anything
    //}
}
