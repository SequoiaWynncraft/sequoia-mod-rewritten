package star.sequoia2.features.impl;

import com.collarmc.pounce.Subscribe;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import star.sequoia2.events.PacketEvent;
import star.sequoia2.features.ToggleFeature;

import java.util.Locale;

import static star.sequoia2.client.SeqClient.mc;

public class EcoMessageFilter extends ToggleFeature {

    public EcoMessageFilter() {
        super("EcoMessageFilter", "Filters eco messages in chat for Strat+");
    }

    @Subscribe
    public void cancelPackets(PacketEvent.PacketReceiveEvent event){
        if (mc.player != null && event.packet() instanceof GameMessageS2CPacket(Text content, boolean overlay)){
            if (!overlay){
                if (containsEcoMessage(content)){
                    event.cancel();
                }
            }
        }
    }
    public boolean containsEcoMessage(Text message){
        String raw = message.getString();
        if (!hasEcoPrefix(raw)) {
            return false;
        }

        String normalized = raw.toLowerCase(Locale.ROOT);
        if ((normalized.contains(" removed ") && normalized.contains(" from "))
                || normalized.contains("from ")) {
            return true;
        }
        if (normalized.contains(" set ") && normalized.contains(" to level ") && normalized.contains(" on ")) {
            return true;
        }
        if (normalized.contains(" changed ")
                && (normalized.contains("bonuses") || normalized.contains("upgrades"))
                && normalized.contains(" on ")) {
            return true;
        }
        if (normalized.contains(" changed the") && (normalized.contains("cheapest") || normalized.contains("fastest"))) {
            return true;
        }
        if (normalized.contains(" changed the") || normalized.contains("tax")) {
            return true;
        }
        if (normalized.contains(" changed the ") && normalized.contains("borders")
                && (normalized.contains("to close") || normalized.contains("to open"))) {
            return true;
        }
        if (normalized.contains(" applied the loadout ") || normalized.contains(" updated loadout ")) {
            return true;
        }
        if (normalized.contains(" deleted the ") && normalized.contains(" loadout")) {
            return true;
        }
        if (normalized.contains(" territory ")
                && (normalized.contains(" is producing more")
                || normalized.contains(" production has stabilised")
                || normalized.contains(" is using more"))) {
            return true;
        }

        return false;
    }

    private static boolean hasEcoPrefix(String raw) {
        return raw.contains("\uDAFF\uDFFC\uE001\uDB00\uDC06")
                || raw.contains("\uDAFF\uDFFC\uE006\uDAFF\uDFFF\uE002\uDAFF\uDFFE");
    }

}
