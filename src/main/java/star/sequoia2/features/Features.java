package star.sequoia2.features;

import com.collarmc.pounce.Subscribe;
import star.sequoia2.accessors.EventBusAccessor;
import star.sequoia2.events.input.KeyEvent;
import star.sequoia2.events.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import static star.sequoia2.client.SeqClient.mc;


public class Features implements EventBusAccessor {
    private final ConcurrentMap<Class<?>, Feature> features = new ConcurrentHashMap<>();

    public Features() {
        subscribe(this);
    }

    @SuppressWarnings("unchecked")
    public <M extends Feature> Optional<M> get(Class<M> moduleType) {
        Feature feature = features.get(moduleType);
        return Optional.ofNullable((M) feature);
    }

    @SuppressWarnings("unchecked")
    public <M extends Feature> Optional<M> getIfActive(Class<M> moduleType) {
        Feature feature = features.get(moduleType);
        return Optional.ofNullable((M) feature)
                .filter(m -> m instanceof ToggleFeature tm && tm.isActive());
    }

    /**
     * Add a feature
     * @param feature to register
     */
    public void add(Feature feature) {
        features.computeIfAbsent(feature.getClass(), aClass -> {
            // Non toggleable features always receive events
            if (!(feature instanceof ToggleFeature)) {
                subscribe(feature);
            }
            return feature;
        });
    }

    public Stream<Feature> all() {
        return features.values().stream();
    }

    public Optional<Feature> featureByClass(String clazz) {
        return features.values()
                .stream()
                .filter(feature -> feature.getClass().getName().equals(clazz))
                .findFirst();
    }

    public Optional<Feature> featureByName(String name) {
        return features.values()
                .stream()
                .filter(feature -> feature.getName().equals(name))
                .findFirst();
    }

    @Subscribe
    private void onKeyDown(KeyEvent event) {
        if (event.key() <= 0) return;
        if (mc.currentScreen != null || mc.inGameHud.getChatHud().isChatFocused()) return;

        all().forEach(feature -> {
            if (feature instanceof ToggleFeature toggleFeature) {
                if (!event.isKeyDown() && toggleFeature.keybind.get().matches(event) && event.action() != GLFW.GLFW_RELEASE) {
                    toggleFeature.toggle();
                }
                if (!event.isKeyDown() && toggleFeature.keybind.get().matches(event) && event.action() == GLFW.GLFW_RELEASE && !toggleFeature.keybind.getToggle()) {
                    toggleFeature.toggle();
                }
            }
        });
    }

    @Subscribe
    private void onMouseKey(MouseButtonEvent event) {
        if (mc.currentScreen != null || mc.inGameHud.getChatHud().isChatFocused()) return;
        all().forEach(feature -> {
            if (feature instanceof ToggleFeature toggleFeature) {
                if (!toggleFeature.keybind.get().matches(event)) return;
                if (event.action() == GLFW.GLFW_PRESS) {
                    toggleFeature.toggle();
                } else if (event.action() == GLFW.GLFW_RELEASE && !toggleFeature.keybind.getToggle()) {
                    toggleFeature.toggle();
                }
            }
        });
    }
}
