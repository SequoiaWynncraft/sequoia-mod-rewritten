package star.sequoia2.client;

import com.collarmc.pounce.EventBus;
import com.collarmc.pounce.Preference;
import com.collarmc.pounce.Subscribe;
import com.mojang.logging.LogUtils;
import lombok.Getter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import star.sequoia2.accessors.EventBusAccessor;
import star.sequoia2.client.commands.Commands;
import star.sequoia2.client.notifications.Notifications;
import star.sequoia2.client.update.UpdateManager;
import star.sequoia2.configuration.Configuration;
import star.sequoia2.events.MinecraftFinishedLoading;
import star.sequoia2.features.Features;
import star.sequoia2.features.impl.*;
import star.sequoia2.features.impl.ws.ChatHook;
import star.sequoia2.features.impl.ws.DiscordChatBridge;
import star.sequoia2.features.impl.ws.WebSocket;
import star.sequoia2.gui.Fonts;
import star.sequoia2.gui.categories.Categories;
import star.sequoia2.hud.HUDElements;
import star.sequoia2.hud.elements.RaidRoomTracker;
import star.sequoia2.hud.positions.UIPositions;
import star.sequoia2.settings.SettingsState;
import star.sequoia2.utils.SoundUtil;
import star.sequoia2.utils.TickScheduler;
import star.sequoia2.utils.cache.Threading;
import star.sequoia2.utils.chatparser.GuildMessageParser;
import star.sequoia2.utils.chatparser.GuildRaidParser;
import star.sequoia2.utils.render.Render2DUtil;
import star.sequoia2.utils.render.Render3DUtil;
import star.sequoia2.utils.render.Themes;
import star.sequoia2.utils.text.parser.TeXParser;
import star.sequoia2.utils.wynn.HadesUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class SeqClient implements ClientModInitializer, EventBusAccessor {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String MOD_ID = "seq";

    @Getter
    public static File modJar;

    private static File SEQUOIA_FOLDER;

    @Getter
    public static int versionInt = 30500;

    @Getter
    public static String version = "0.0.4.0";

    public static boolean initialized = false;

    //enable and compile for testers
    public static boolean testMode = false;

    @Getter
    private static boolean debugMode = false;

    public static final MinecraftClient mc = MinecraftClient.getInstance();

    @Getter
    private static EventBus eventBus;

    @Getter
    private static Notifications notifications;

    @Getter
    private static Configuration configuration;

    @Getter
    private static Features features;

    @Getter
    private static SettingsState settings;

    @Getter
    private static Fonts fonts;

    @Getter
    private static Themes themes;

    @Getter
    private static Render2DUtil render2DUtil;

    @Getter
    private static Render3DUtil render3DUtil;

    @Getter
    private static SimpleProfileFetcher profileFetcher;

    @Getter
    private static GuildMessageParser guildMessageParser;

    @Getter
    private static GuildRaidParser guildRaidParser;

    @Getter
    private static TeXParser teXParser;

    @Getter
    private static UIPositions uiPositions;

    @Getter
    private static HUDElements hudElements;

    @Getter
    private static SoundUtil soundUtil;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Seq client.");
        eventBus = new EventBus(mc::execute); //before initializing everything else
        eventBus.subscribe(this);
        TickScheduler.init(eventBus);

        try {
            configuration = new Configuration();
        } catch (Exception e) {
            throw new IllegalStateException("could not read configuration", e);
        }

        SEQUOIA_FOLDER = new File(mc.runDirectory, "sequoia");

        uiPositions = new UIPositions();

        //Static init no need for instance in this case
        Threading tInit = new Threading();
        Thread thread = new Thread(tInit, "Sequoia-CacheInit");
        thread.setDaemon(true);
        thread.start();
        HadesUtils.init();

        fonts = new Fonts();

        themes = new Themes();

        notifications = new Notifications();
        soundUtil = new SoundUtil();
        render2DUtil = new Render2DUtil();
        render3DUtil = new Render3DUtil();

        locateModJar();
    }

    @Subscribe(value = Preference.MAIN, priority = 1)
    public void onFinishedLoading(MinecraftFinishedLoading ignored) {
        hudElements = new HUDElements();
        features = new Features();
        settings = new SettingsState();

        subscribe(settings);
        registerFeatures();
        registerHUDElements();

        try {
            settings.load(features, hudElements);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        features.get(Settings.class).ifPresent(Settings::applyUpdateChannelPreference);

        ClientCommandRegistrationCallback.EVENT.register(Commands::registerCommands);

        Categories.registerDefault();

        initialized = true;
        LOGGER.info("Initialization complete.");

        UpdateManager.scheduleAutomaticCheck();
    }

    @Subscribe(value = Preference.CALLER, priority = 2)
    public void onFinishedLoadingOnUI(MinecraftFinishedLoading ignored) {
        try {
            fonts.initializeFonts();
        } catch (Exception e) {
            LOGGER.warn("Failed to initialize fonts, continuing without custom fonts", e);
        }

        teXParser = new TeXParser();
        profileFetcher = new SimpleProfileFetcher(); //init late so hopefully service is created
        guildMessageParser = new GuildMessageParser();
        guildRaidParser = new GuildRaidParser();
    }

    private void registerFeatures() {
        features.add(new Settings()); // always first so you can get colors
        features.add(new SorrowTracker());
        features.add(new TeleportIndicator());
        features.add(new PartyHealthDisplay());
        features.add(new ChatHook());
        features.add(new DiscordChatBridge());
        features.add(new WebSocket());
        features.add(new EcoMessageFilter());
        features.add(new GuildRewardTracking());
        features.add(new GuildRewardGranter());
        features.add(new GuildWarTracker());
        //TODO: finish commented out features.
    }

    private void registerHUDElements() {
        hudElements.add(new RaidRoomTracker());
    }

    public static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Sequoia-Scheduler");
                t.setDaemon(true);
                return t;
            });

    public static MutableText prefix(Text text) {
        Settings settingsFeature = features == null ? null : features.get(Settings.class).orElse(null);
        int dark = settingsFeature != null ? settingsFeature.getTheme().get().getTheme().DARK : 0x6600cc;
        int light = settingsFeature != null ? settingsFeature.getTheme().get().getTheme().LIGHT : 0xf3e6ff;
        return teXParser.parseMutableText("\\pill{%s}{%s}{Sequoia} \\+{»} ",
                Integer.toHexString(dark),
                Integer.toHexString(light)).append(text);
    }

    public static File getModStorageDir(String dirName) {
        return new File(SEQUOIA_FOLDER, dirName);
    }

    public static void error(String message) {
        LOGGER.error(message);
    }

    public static void error(String message, Throwable t) {
        LOGGER.error(message, t);
    }

    public static void warn(String message) {
        LOGGER.warn(message);
    }

    public static void warn(String message, Throwable throwable) {
        LOGGER.warn(message, throwable);
    }

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void debug(String message) {
        if (debugMode) {
            LOGGER.info("[VERBOSE] {}", message);
        }
    }

    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
        LOGGER.info("Debug logging {}", enabled ? "enabled" : "disabled");
    }

    private void locateModJar() {
        if (modJar != null) return;
        CodeSource source = SeqClient.class.getProtectionDomain().getCodeSource();
        if (source == null) {
            LOGGER.info("Mod code source unavailable; update installer disabled for this session.");
            return;
        }
        try {
            File location = new File(source.getLocation().toURI());
            if (location.isFile()) {
                modJar = location;
                LOGGER.info("Detected Sequoia jar at {}", modJar.getAbsolutePath());
            } else {
                LOGGER.info("Sequoia is running from {} (not a jar); update installer disabled.", location);
            }
        } catch (URISyntaxException e) {
            LOGGER.warn("Failed to resolve mod jar location", e);
        }
    }
}
