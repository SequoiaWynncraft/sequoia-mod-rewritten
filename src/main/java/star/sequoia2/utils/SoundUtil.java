package star.sequoia2.utils;

import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.features.impl.Settings;

import static star.sequoia2.client.SeqClient.mc;


public class SoundUtil implements FeaturesAccessor {
    public static final Identifier enableSound = Identifier.of("seq:enable");
    public static final Identifier disableSound = Identifier.of("seq:disable");
    public static final Identifier hoverSound = Identifier.of("seq:hover");
    public static final Identifier badroomSound = Identifier.of("seq:badroom");
    public static SoundEvent enableSoundEvent = SoundEvent.of(enableSound);
    public static SoundEvent disableSoundEvent = SoundEvent.of(disableSound);
    public static SoundEvent hoverSoundEvent = SoundEvent.of(hoverSound);
    public static SoundEvent badroomSoundEvent = SoundEvent.of(badroomSound);

    public SoundUtil() {
        Registry.register(Registries.SOUND_EVENT, enableSound, enableSoundEvent);
        Registry.register(Registries.SOUND_EVENT, disableSound, disableSoundEvent);
        Registry.register(Registries.SOUND_EVENT, hoverSound, hoverSoundEvent);
        Registry.register(Registries.SOUND_EVENT, badroomSound, badroomSoundEvent);
    }

    public void playSound(SoundEvent sound) {
        featureIfPresent(Settings.class, sounds -> {
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(PositionedSoundInstance.ui(sound, 1f, sounds.getVolume().get() / 100f));
            }
        });
    }

    public void playEnableSound() {
        playSound(enableSoundEvent);
    }

    public void playDisableSound() {
        playSound(disableSoundEvent);
    }

    public void playHoverSound() {
        playSound(hoverSoundEvent);
    }

    public void playBadroomSound() {
        playSound(badroomSoundEvent);
    }
}
