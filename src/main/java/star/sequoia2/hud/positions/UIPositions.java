package star.sequoia2.hud.positions;

import star.sequoia2.configuration.JsonCompound;
import star.sequoia2.hud.UIConfiguration;

import java.io.IOException;
import java.util.Optional;

public class UIPositions extends UIConfiguration {
    private final JsonCompound positions;

    public UIPositions() {
        super();
        // Create the position JSON if it is not found
        JsonCompound positionJson = this.ui.getCompound("position");
        this.ui.put("position", positionJson);
        positions = positionJson;
    }

    public void set(PositionKey key, UIPosition offset) throws IOException {
        JsonCompound pos = positions.getCompound(key.key());
        positions.put(key.key(), pos);
        boolean changed = offset.x() != pos.getFloat("x") || offset.y() != pos.getFloat("y");
        pos.putFloat("x", offset.x());
        pos.putFloat("y", offset.y());
        if (changed) {
            configuration().save();
        }
    }

    public Optional<UIPosition> get(PositionKey key) {
        JsonCompound pos = positions.getCompound(key.key());
        if (pos.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new UIPosition(pos.getFloat("x"), pos.getFloat("y")));
    }
}
