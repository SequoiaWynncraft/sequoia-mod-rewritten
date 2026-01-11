package star.sequoia2.hud.elements;

import com.wynntils.core.components.Model;
import com.wynntils.core.components.Models;
import com.wynntils.models.raid.type.HistoricRaidInfo;
import com.wynntils.models.raid.type.RaidRoomInfo;
import mil.nga.color.Color;
import net.minecraft.client.gui.DrawContext;
import star.sequoia2.hud.HUDElement;
import star.sequoia2.settings.types.BooleanSetting;
import star.sequoia2.settings.types.ColorSetting;
import star.sequoia2.settings.types.FloatSetting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GolemusTracker extends HUDElement {

    public final BooleanSetting showBackground = settings().bool("Show Background", "Show background behind text", true);
    public final BooleanSetting onlyInRaid = settings().bool("Only In Raid", "Show only in a raid", false);
    public final ColorSetting textColor = settings().color("Text Color", "Color of the text", new Color(255, 255, 255));
    public final FloatSetting textScale = settings().number("Text Scale", "Scale of the text", 1.0f, 0.5f, 3.0f);

    private final Map<String, Integer> roomCounts = new HashMap<>();
    private int cachedRaidCount = -1;

    public GolemusTracker() {
        super("GolemusTracker", "lets see if u got golemus");
    }

    @Override
    public void render(DrawContext context, float mouseX, float mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (!isActive()) return; //on hud elements we need this I cba to do differently sorry

        if (Models.Raid.getCurrentRaid() == null && onlyInRaid.get()) return;

        List<HistoricRaidInfo> raids = Models.Raid.historicRaids.get();
        if (raids.isEmpty()) return;

        if (raids.size() != cachedRaidCount) {
            recomputeRoomCounts(raids);
        }

        if (roomCounts.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("Rooms:");
        for (Map.Entry<String, Integer> entry : roomCounts.entrySet()) {
            sb.append("\n").append(entry.getKey()).append(": ").append(entry.getValue());
        }
        String text = sb.toString();

        String[] lines = text.split("\n");
        int maxLineWidth = 0;
        for (String line : lines) {
            int lineWidth = (int) (textRenderer().getWidth(line) * textScale.get());
            if (lineWidth > maxLineWidth) {
                maxLineWidth = lineWidth;
            }
        }

        int textHeight = (int) (textRenderer().fontHeight * textScale.get() * lines.length);

        this.width = maxLineWidth + 4;
        this.height = textHeight + 4;

        if (showBackground.get() && !isEditing()) {
            Color bgColor = new Color(0, 0, 0, 128);
            render2DUtil().roundRectFilled(context.getMatrices(), x, y, x + width, y + height, 3f, bgColor);
        }

        context.getMatrices().push();
        context.getMatrices().translate(x + 2, y + 2, 0);
        context.getMatrices().scale(textScale.get(), textScale.get(), 1.0f);

        int lineY = 0;
        for (String line : lines) {
            context.drawText(textRenderer(), line, 0, lineY, textColor.get().getColor(), true);
            lineY += textRenderer().fontHeight;
        }

        context.getMatrices().pop();
    }

    private void recomputeRoomCounts(List<HistoricRaidInfo> raids) {
        roomCounts.clear();
        for (HistoricRaidInfo raid : raids) {
            if (!"TCC".equals(raid.abbreviation())) continue;
            RaidRoomInfo room = raid.challenges().get(2); // 2nd room; change to 1 if your index is different
            if (room == null) continue;
            String name = room.getRoomName();
            if (name == null || name.isEmpty()) continue;
            roomCounts.merge(name, 1, Integer::sum);
        }
        cachedRaidCount = raids.size();
    }
}
