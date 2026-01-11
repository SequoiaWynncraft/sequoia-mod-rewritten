package star.sequoia2.settings;

import com.collarmc.pounce.Subscribe;
import com.google.gson.JsonArray;
import star.sequoia2.accessors.ConfigurationAccessor;
import star.sequoia2.configuration.JsonCompound;
import star.sequoia2.events.ModuleChangedEvent;
import star.sequoia2.events.SettingChanged;
import star.sequoia2.features.Feature;
import star.sequoia2.features.Features;
import star.sequoia2.client.SeqClient;
import star.sequoia2.hud.HUDElement;
import star.sequoia2.hud.HUDElements;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages all client settings
 */
public class SettingsState implements ConfigurationAccessor {

    private final ConcurrentMap<Feature, Settings> featureSettings = new ConcurrentHashMap<>();
    private final ConcurrentMap<HUDElement, Settings> hudElementSettings = new ConcurrentHashMap<>();

    public Settings fromFeature(Feature feature) {
        return featureSettings.compute(feature, (theFeature, featureSettings) -> Objects.requireNonNullElseGet(featureSettings, () -> getOrCreateSettingJSON(feature.getClass())
                .map(compound -> new Settings())
                .orElse(new Settings())));
    }

    public Settings fromHUDElement(HUDElement element) {
        return hudElementSettings.compute(element, (theModule, moduleSettings) -> Objects.requireNonNullElseGet(moduleSettings, () -> getOrCreateSettingJSON(element.getClass())
                .map(compound -> new Settings())
                .orElse(new Settings())));
    }

    private Optional<JsonCompound> getOrCreateSettingJSON(Class<?> clazz) {
        JsonArray settings = configuration().getFeatures().getList("settings");
        if (settings == null) return Optional.empty();
        return settings.asList().stream()
                .map(JsonCompound::wrap)
                .filter(compound -> clazz.getName().equals(compound.getString("class")))
                .findFirst();
    }

    public void load(Features features, HUDElements hudElements) {
        JsonArray settingsList = configuration().getFeatures().getList("settings");
        if (settingsList == null) return;
        settingsList.forEach(jsonElement -> {
            JsonCompound json = JsonCompound.wrap(jsonElement);
            String clazz = json.getString("class");
            features.featureByClass(clazz).ifPresent(module -> {
                module.reset();
                module.fromJSON(json);
            });
            hudElements.elementByClass(clazz).ifPresent(hudElement -> {
                hudElement.reset();
                hudElement.fromJSON(json);
            });
        });
    }

    private final Object saveLock = new Object();

    private void save() {
        synchronized (saveLock) {
            try {
                JsonArray array = new JsonArray();
                featureSettings.forEach((module, settings) -> {
                    JsonCompound moduleJson = module.toJSON();
                    array.add(moduleJson);
                });
                configuration().getFeatures().put("settings", array);
                configuration().save();
            } catch (IOException e) {
                SeqClient.warn("could not save configuration", e);
            } catch (RuntimeException e) {
                SeqClient.warn("unexpected error during configuration save", e);
            }
        }
    }

    @Subscribe
    private void onModuleChanged(ModuleChangedEvent ignored) {
        save();
    }

    @Subscribe
    private void onSettingChanged(SettingChanged ignored) {
        save();
    }
}
