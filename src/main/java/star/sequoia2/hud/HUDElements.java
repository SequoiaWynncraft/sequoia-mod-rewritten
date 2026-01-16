package star.sequoia2.hud;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

public class HUDElements {
    private final ConcurrentMap<Class<?>, HUDElement> hudElements = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <M extends HUDElement> Optional<M> get(Class<M> hudElementType) {
        HUDElement hudElement = hudElements.get(hudElementType);
        return Optional.ofNullable((M) hudElement);
    }

    @SuppressWarnings("unchecked")
    public <M extends HUDElement> Optional<M> getIfActive(Class<M> hudElementType) {
        HUDElement hudElement = hudElements.get(hudElementType);
        return Optional.ofNullable((M) hudElement);
    }

    /**
     * Add a module
     * @param hudElement to register
     */
    public void add(HUDElement hudElement) {
        hudElements.putIfAbsent(hudElement.getClass(), hudElement);
    }

    public Stream<HUDElement> all() {
        return hudElements.values().stream();
    }

    public Optional<HUDElement> elementByClass(String clazz) {
        return all().filter(hudElement -> hudElement.getClass().getName().equals(clazz)).findFirst();
    }
}
