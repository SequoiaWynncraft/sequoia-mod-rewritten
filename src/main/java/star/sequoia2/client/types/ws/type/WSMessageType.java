package star.sequoia2.client.types.ws.type;

public enum WSMessageType {
    INVALID,

    G_CHAT_MESSAGE,
    G_CLIENT_COMMAND,
    G_RAID_SUBMISSION,
    G_I_STATE_UPDATE,
    NOT_IMPLEMENTED_1,
    G_IDENTIFY,
    NOT_IMPLEMENTED_2,
    NOT_IMPLEMENTED_3,
    G_AUTH,
    G_IC3H,
    G_RESOURCE_REQUEST,
    G_RESERVED_8,
    G_RESERVED_9,
    G_RESERVED_10,

    S_CHANNEL_MESSAGE,
    S_COMMAND_DATA,
    S_COMMAND_RESULT,
    S_CHAT_MESSAGE_BROADCAST,
    S_COMMAND_PIPE,
    S_RAID_SUBMISSION,
    S_MESSAGE,
    S_SESSION_RESULT,
    S_IC3_DATA,
    S_BINARY_DATA,
    S_RESERVED_7,
    S_REWARD_DATA,
    S_RESERVED_9,
    S_DEBUG_MESSAGE,

    D_CHANNEL_MESSAGE,
    D_GET_CONNECTED_CLIENT,
    D_SERVER_RESTART,
    D_SERVER_MESSAGE,

    G_ATTACK_ATTEMPTED,

    S_PARTY_INVITE,
    S_PARTY_MEMBER_LIST_REQUEST,

    G_PARTY_MEMBER_LIST,
    G_PARTY_INVITED,
    G_PARTY_CHANGE,

    G_MESSAGE_REFERENCE,
    S_MESSAGE_REFERENCE,

    S_SERVER_EMOJI_LIST,
    S_HTML_DATA,

    G_WAR_CMD,
    S_WAR_CMD,
    G_TERRITORY_DATA,

    S_REPLY,

    G_KEEPERS_PING,
    G_GUILD_BANK_EVENT,
    G_GUILD_BANK_STATE;
    public static WSMessageType fromValue(int value) {
        for (WSMessageType type : values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        return INVALID;
    }

    public int getValue() {
        return ordinal();
    }
}
