package star.sequoia2.hud.elements;

import com.collarmc.pounce.Subscribe;
import com.wynntils.core.components.Models;
import com.wynntils.models.raid.type.HistoricRaidInfo;
import com.wynntils.models.raid.type.RaidInfo;
import com.wynntils.models.raid.type.RaidRoomInfo;
import mil.nga.color.Color;
import net.minecraft.client.gui.DrawContext;
import star.sequoia2.accessors.EventBusAccessor;
import star.sequoia2.accessors.SoundUtilAccessor;
import star.sequoia2.events.SettingChanged;
import star.sequoia2.hud.HUDElement;
import star.sequoia2.settings.types.BooleanSetting;
import star.sequoia2.settings.types.ColorSetting;
import star.sequoia2.settings.types.EnumSetting;
import star.sequoia2.settings.types.FloatSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RaidRoomTracker extends HUDElement implements EventBusAccessor, SoundUtilAccessor {

    public final BooleanSetting showBackground = settings().bool("Show Background", "Show background behind text", true);
    public final BooleanSetting onlyInRaid = settings().bool("Only In Raid", "Show only in a raid", false);
    public final ColorSetting textColor = settings().color("Text Color", "Color of the text", new Color(255, 255, 255));
    public final ColorSetting badRoomColor = settings().color("Bad Room Color", "Color when getting bad room", new Color(255, 85, 85));
    public final FloatSetting textScale = settings().number("Text Scale", "Scale of the text", 1.0f, 0.5f, 3.0f);
    public final EnumSetting<RaidSelection> raidSelection = settings().options("Raid", "Which raid to track", RaidSelection.TCC, RaidSelection.class);
    public final EnumSetting<RoomSelection> roomSelection = settings().options("Room", "Which room to track", RoomSelection.SECOND, RoomSelection.class);
    public final EnumSetting<SoundTarget> soundTarget = settings().options("Sound Target", "Which room variant triggers sound", SoundTarget.ANY, SoundTarget.class);
    public final BooleanSetting soundOnly = settings().bool("Sound Only", "Only play sound, do not render text", false);
    public final BooleanSetting onlyCurrentSession = settings().bool("Only Current Session", "Track only data from current session", false);

    private final Map<String, Integer> roomCountsBase = new HashMap<>();
    private final Map<String, Integer> roomCountsLive = new HashMap<>();
    private final List<String> roomVariants = new ArrayList<>(2);
    private String lastRoom = "";
    private boolean wasInSelectedRaid = false;
    private int lastHistoricSize = -1;
    private boolean dirty = true;

    private long badRoomFlashUntil = 0L;
    private static final long BAD_ROOM_FLASH_DURATION = 5000L;

    public RaidRoomTracker() {
        super("RaidRoomTracker", "lets see if u got golemus");
    }

    @Override
    public void onDeactivate() {
        roomCountsBase.clear();
        roomCountsLive.clear();
        roomVariants.clear();
        lastRoom = "";
        wasInSelectedRaid = false;
        lastHistoricSize = -1;
        dirty = true;
        badRoomFlashUntil = 0L;
    }

    @Override
    public void render(DrawContext context, float mouseX, float mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (!isActive()) return; //on hud elements we need this I cba to do differently sorry

        List<HistoricRaidInfo> raids = Models.Raid.historicRaids.get();
        if (!onlyCurrentSession.get() && !raids.isEmpty()) {
            if (dirty || raids.size() != lastHistoricSize) {
                rebuildFromHistoricRaids(raids);
                lastHistoricSize = raids.size();
                dirty = false;
            }
        }

        if (Models.Raid.getCurrentRaid() == null && onlyInRaid.get()) {
            updateLiveTracking();
            return;
        }

        updateLiveTracking();

        Map<String, Integer> merged = new HashMap<>(roomCountsBase);
        for (Map.Entry<String, Integer> entry : roomCountsLive.entrySet()) {
            merged.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }

        if (soundOnly.get()) return;
        if (merged.isEmpty()) return;

        List<String> ordered = new ArrayList<>();
        if (roomVariants.size() > 0 && merged.containsKey(roomVariants.get(0))) {
            ordered.add(roomVariants.get(0));
        }
        if (roomVariants.size() > 1 && merged.containsKey(roomVariants.get(1))) {
            ordered.add(roomVariants.get(1));
        }
        for (String k : merged.keySet()) {
            if (!ordered.contains(k)) ordered.add(k);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Rooms:");
        for (String name : ordered) {
            sb.append("\n").append(name).append(": ").append(merged.get(name));
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
        long now = System.currentTimeMillis();
        boolean useBadColor = now < badRoomFlashUntil;
        int baseColor = useBadColor ? badRoomColor.get().getColor() : textColor.get().getColor();

        for (String line : lines) {
            context.drawText(textRenderer(), line, 0, lineY, baseColor, true);
            lineY += textRenderer().fontHeight;
        }

        context.getMatrices().pop();
    }

    private void rebuildFromHistoricRaids(List<HistoricRaidInfo> raids) {
        roomCountsBase.clear();
        roomVariants.clear();

        RaidSelection selection = raidSelection.get();
        if (selection == null) return;
        String selectedAbbrev = selection.name();
        int roomNumber = roomSelection.get().number();

        for (HistoricRaidInfo raid : raids) {
            if (!selectedAbbrev.equals(raid.abbreviation())) continue;

            RaidRoomInfo room = raid.challenges().get(roomNumber);
            if (room == null) continue;

            String name = room.getRoomName();
            if (name == null || name.isEmpty()) continue;

            if (!roomVariants.contains(name) && roomVariants.size() < 2) {
                roomVariants.add(name);
            }

            roomCountsBase.merge(name, 1, Integer::sum);
        }
    }

    private void updateLiveTracking() {
        RaidInfo currentRaid = Models.Raid.getCurrentRaid();
        RaidSelection selection = raidSelection.get();
        boolean inSelected = false;
        int roomNumber = roomSelection.get().number();

        if (currentRaid != null && selection != null) {
            String abbrev = currentRaid.getRaidKind().getAbbreviation();
            if (selection.name().equals(abbrev)) {
                inSelected = true;

                String roomName = Models.Raid.getRoomName(roomNumber);
                if (!roomName.isEmpty() && !roomName.equals(lastRoom)) {
                    lastRoom = roomName;
                    roomCountsLive.merge(roomName, 1, Integer::sum);
                    handleRoomSound(roomName);
                }
            }
        }

        if (!inSelected && wasInSelectedRaid) {
            if (onlyCurrentSession.get()) {
                for (Map.Entry<String, Integer> entry : roomCountsLive.entrySet()) {
                    roomCountsBase.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            }
            lastRoom = "";
            roomCountsLive.clear();
        }

        wasInSelectedRaid = inSelected;
    }

    private void handleRoomSound(String roomName) {
        SoundTarget target = soundTarget.get();
        if (target == SoundTarget.OFF) return;

        if (!roomVariants.contains(roomName) && roomVariants.size() < 2) {
            roomVariants.add(roomName);
        }

        boolean trigger = false;

        if (target == SoundTarget.ANY) {
            trigger = true;
        } else {
            int idx = roomVariants.indexOf(roomName);
            if (idx != -1) {
                if (target == SoundTarget.VARIANT_ONE && idx == 0) trigger = true;
                if (target == SoundTarget.VARIANT_TWO && idx == 1) trigger = true;
            }
        }

        if (trigger) {
            badRoomFlashUntil = System.currentTimeMillis() + BAD_ROOM_FLASH_DURATION;
            soundUtil().playBadroomSound();
        }
    }

    @Subscribe
    private void onSettingChanged(SettingChanged event) {
        if (event.setting() != raidSelection
                && event.setting() != roomSelection
                && event.setting() != soundTarget
                && event.setting() != badRoomColor
                && event.setting() != soundOnly
                && event.setting() != onlyCurrentSession) return;

        roomCountsBase.clear();
        roomCountsLive.clear();
        roomVariants.clear();
        lastRoom = "";
        wasInSelectedRaid = false;
        lastHistoricSize = -1;
        dirty = true;
        badRoomFlashUntil = 0L;
    }

    public enum RaidSelection {
        TNA,
        TCC,
        NOL,
        NOG
    }

    public enum RoomSelection {
        FIRST(1),
        SECOND(2),
        THIRD(3);

        private final int roomNumber;

        RoomSelection(int roomNumber) {
            this.roomNumber = roomNumber;
        }

        public int number() {
            return roomNumber;
        }
    }

    public enum SoundTarget {
        OFF,
        ANY,
        VARIANT_ONE,
        VARIANT_TWO
    }
}
