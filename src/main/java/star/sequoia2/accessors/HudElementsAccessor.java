package star.sequoia2.accessors;


import star.sequoia2.client.SeqClient;
import star.sequoia2.hud.HUDElements;

public interface HudElementsAccessor {
    default HUDElements hudElements() {
        return SeqClient.getHudElements();
    }
}