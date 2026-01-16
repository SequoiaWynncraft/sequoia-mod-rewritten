package star.sequoia2.hud.positions;


import star.sequoia2.hud.HUDElement;

import java.util.Objects;

public final class PositionKey {
    private final String key;

    private PositionKey(String key) {
        this.key = key;
    }

    public static PositionKey fromHudElement(HUDElement HUDElement) {
        return new PositionKey("hud." + HUDElement.getClass().getName());
    }

    public static PositionKey fromHudUI() {
        return new PositionKey("hudui.HUD");
    }

    public String key() {
        return key;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (PositionKey) obj;
        return Objects.equals(this.key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return "PositionKey[" +
                "key=" + key + ']';
    }

}
