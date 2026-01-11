package star.sequoia2.hud;

import com.google.gson.JsonArray;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;
import star.sequoia2.accessors.EventBusAccessor;
import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.accessors.RenderUtilAccessor;
import star.sequoia2.accessors.SettingsAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.configuration.JSONConfiguration;
import star.sequoia2.configuration.JsonCompound;
import star.sequoia2.events.HudElementChanged;
import star.sequoia2.gui.component.InteractableComponent;
import star.sequoia2.hud.positions.PositionKey;
import star.sequoia2.hud.positions.UIPosition;
import star.sequoia2.settings.Setting;
import star.sequoia2.settings.Settings;

import java.io.IOException;

import static star.sequoia2.client.SeqClient.mc;

public abstract class HUDElement extends InteractableComponent implements JSONConfiguration, FeaturesAccessor, EventBusAccessor, SettingsAccessor, RenderUtilAccessor {

    public static final String CONFIG = "hud";

    private static final String ACTIVE = "active";

    @Getter
    @Setter
    private boolean isEditing;
    private boolean isDragging;

    public final String name;
    public final String description;

    private boolean active = false;

    private float px, py;


    public HUDElement(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Settings settings() {
        return settingsState().fromHUDElement(this);
    }

    public void init() {
        UIPosition position = SeqClient.getUiPositions().get(PositionKey.fromHudElement(this)).orElse(new UIPosition(0, 0)).restore(mc.getWindow());
        x = position.x();
        y = position.y();
    }

    public void reset() {
        this.settings().all().forEach(Setting::reset);
    }

    public boolean isActive() {
        return this.active;
    }

    public void toggle() {
        synchronized (this) {
            if (active) {
                active = false;
                deactivate();
            } else {
                active = true;
                activate();
            }
        }
    }

    public void activate() {
        synchronized (this) {
            active = true;
            onActivate();
            subscribe(this);
            dispatch(new HudElementChanged(this));
        }
    }

    public void deactivate() {
        synchronized (this) {
            active = false;
            unsubscribe(this);
            onDeactivate();
            dispatch(new HudElementChanged(this));
        }
    }

    protected void onActivate() {}
    protected void onDeactivate() {}

    @Override
    public void render(DrawContext context, float mouseX, float mouseY, float delta) {
        if (isActive() && isEditing) {
            if (isDragging) {
                x += mouseX - px;
                y += mouseY - py;
            }

            int screenCenterX = mc.getWindow().getScaledWidth() / 2;
            int screenCenterY = mc.getWindow().getScaledHeight() / 2;

            boolean snapX = isDragging && Math.abs(mouseX - screenCenterX) <= 2;
            boolean snapY = isDragging && Math.abs(mouseY - screenCenterY) <= 2;

            if (snapX) {
                x = screenCenterX - width / 2;
            }
            if (snapY) {
                y = screenCenterY - height / 2;
            }

            if (x < 0) {
                x = 0;
            } else if (x + width > mc.getWindow().getScaledWidth()) {
                x = mc.getWindow().getScaledWidth() - width;
            }

            if (y < 0) {
                y = 0;
            } else if (y + height > mc.getWindow().getScaledHeight()) {
                y = mc.getWindow().getScaledHeight() - height;
            }

            int color = features().get(star.sequoia2.features.impl.Settings.class).map(settings -> settings.getColorNormal().get().getColorWithAlpha()).orElse(1);
            render2DUtil().fill(context.getMatrices(), (int) x, (int) y, width, height, color);

            if (isDragging) {
                int yellow = 0xFFFFFF00;
                if (snapX) {
                    render2DUtil().fill(context.getMatrices(), screenCenterX, 0, screenCenterX + 1, mc.getWindow().getScaledHeight(), yellow);
                }
                if (snapY) {
                    render2DUtil().fill(context.getMatrices(), 0, screenCenterY, mc.getWindow().getScaledWidth(), screenCenterY + 1, yellow);
                }
            }
        }
        px = mouseX;
        py = mouseY;
    }

    @Override
    public void mouseClicked(float mouseX, float mouseY, int button) {
        if (isActive() && isWithin(mouseX, mouseY)) {
            isDragging = true;
        }
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        if (isActive() && isDragging) {
            try {
                SeqClient.getUiPositions().set(PositionKey.fromHudElement(this), UIPosition.relativeToWindow(x, y, mc.getWindow()));
            } catch (IOException e) {
                throw new RuntimeException("Could not save hud position");
            }
        }

        isDragging = false;
    }

    @Override
    public JsonCompound toJSON() {
        JsonCompound json = new JsonCompound();
        JsonArray jsonArray = new JsonArray();
        this.settings().all().forEach(setting -> {
            JsonCompound settingJson = setting.toJson();
            if (!settingJson.isEmpty()) {
                jsonArray.add(settingJson);
            }
        });
        json.put(CONFIG, jsonArray);
        json.putString("class", this.getClass().getName());
        if (isActive()) {
            json.putBoolean(ACTIVE, true);
        }
        return json;
    }

    @Override
    public void fromJSON(JsonCompound compound) {
        for (Setting<?> setting : this.settings().all().toList()) {
            JsonArray list = compound.getList(CONFIG);
            list.forEach(jsonElement -> {
                JsonCompound settingJson = JsonCompound.wrap(jsonElement);
                String settingName = settingJson.getString(Setting.NAME);
                if (setting.name.equals(settingName)) {
                    setting.load(settingJson);
                }
            });
        }
        if (compound.contains(ACTIVE) && compound.getBoolean(ACTIVE) && this instanceof HUDElement hudElement) {
            hudElement.toggle();
        }
    }
}
