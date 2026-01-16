package star.sequoia2.features;

import star.sequoia2.accessors.EventBusAccessor;
import star.sequoia2.accessors.NotificationsAccessor;
import star.sequoia2.accessors.SettingsAccessor;
import star.sequoia2.accessors.SoundUtilAccessor;
import star.sequoia2.configuration.JsonCompound;
import star.sequoia2.events.ModuleChangedEvent;
import star.sequoia2.settings.Binding;
import star.sequoia2.settings.Setting;
import star.sequoia2.settings.types.KeybindSetting;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Optional;

public abstract class ToggleFeature extends Feature implements SettingsAccessor, EventBusAccessor, NotificationsAccessor, SoundUtilAccessor {

    private static final String ACTIVE = "active";

    @Setter
    private boolean drawn = false;

    public final KeybindSetting keybind = settings().binding("Key", "Key to toggle this feature", Binding.none());

    public ToggleFeature(String name, String description, boolean defaultActiveState) {
        super(name, description);
        this.active = defaultActiveState;
    }

    public ToggleFeature(String name, String description) {
        super(name, description);
    }

    @Getter
    private boolean active = false;

    public void activate() {
        synchronized (this) {
            active = true;
            onActivate();
            subscribe(this);
            soundUtil().playEnableSound();
            dispatch(new ModuleChangedEvent(this));
        }
    }

    public void deactivate() {
        synchronized (this) {
            active = false;
            onDeactivate();
            unsubscribe(this);
            soundUtil().playDisableSound();
            dispatch(new ModuleChangedEvent(this));
        }
    }

    protected void onActivate() {}

    protected void onDeactivate() {}

    public void toggle() {
        synchronized (this) {
            if (active) {
                active = false;
                deactivate();
            } else {
                active = true;
                activate();
            }
            notify(Text.of(active ? this.name + Formatting.GREEN + " on" : this.name + Formatting.RED + " off"), this.name);
        }
    }

    public boolean getDrawn() {
        return this.drawn;
    }

    @Override
    public void fromJSON(JsonCompound compound) {
        super.fromJSON(compound);
        this.settings().all().forEach(setting -> {
            compound.getList(FEATURE).forEach(jsonElement -> {
                JsonCompound settingJson = JsonCompound.wrap(jsonElement);
                String settingName = settingJson.getString(Setting.NAME);
                if (setting.name.equals(settingName)) {
                    setting.load(settingJson);
                }
            });
        });
        if (compound.contains(ACTIVE) && compound.getBoolean(ACTIVE) && this instanceof ToggleFeature toggleModule) {
            toggleModule.deactivate();
            toggleModule.activate();
        }
    }

    @Override
    public JsonCompound toJSON() {
        JsonCompound json = super.toJSON();
        if (isActive()) {
            json.putBoolean(ACTIVE, true);
        }
        return json;
    }
}
