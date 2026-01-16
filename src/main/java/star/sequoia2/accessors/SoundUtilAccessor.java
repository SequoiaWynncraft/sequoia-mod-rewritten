package star.sequoia2.accessors;

import star.sequoia2.client.SeqClient;
import star.sequoia2.utils.SoundUtil;

public interface SoundUtilAccessor {
    default SoundUtil soundUtil() {
        return SeqClient.getSoundUtil();
    }
}
