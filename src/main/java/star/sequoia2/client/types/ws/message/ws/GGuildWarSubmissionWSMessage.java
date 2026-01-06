package star.sequoia2.client.types.ws.message.ws;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import star.sequoia2.client.types.ws.WSConstants;
import star.sequoia2.client.types.ws.message.WSMessage;
import star.sequoia2.client.types.ws.type.WSMessageType;

public class GGuildWarSubmissionWSMessage extends WSMessage {

    public GGuildWarSubmissionWSMessage(Data data) {
        super(WSMessageType.G_GUILD_WAR_SUBMISSION.getValue(), WSConstants.GSON.toJsonTree(data));
    }

    public record Data(
            String territory,
            @SerializedName("submitted_by") String submittedBy,
            @SerializedName("submitted_at") String submittedAt,
            @SerializedName("start_time") String startTime,
            List<String> warrers,
            Results results,
            int seasonRating,
            boolean completed) {}

    public record Results(Stats stats) {}

    public record Stats(Damage damage, double attack, long health, double defence) {}

    public record Damage(long low, long high) {}
}
