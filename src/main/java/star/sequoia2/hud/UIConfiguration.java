package star.sequoia2.hud;


import star.sequoia2.accessors.ConfigurationAccessor;
import star.sequoia2.configuration.JsonCompound;

public abstract class UIConfiguration implements ConfigurationAccessor {
    private static final String UI_KEY = "ui";
    protected final JsonCompound ui;

    public UIConfiguration() {
        if (!configuration().getFeatures().contains(UI_KEY)) {
            configuration().getFeatures().put(UI_KEY, new JsonCompound());
        }
        ui = configuration().getFeatures().getCompound(UI_KEY);
    }
}
