package star.sequoia2.features.impl.ws;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wynntils.core.components.Models;
import com.wynntils.utils.mc.McUtils;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import org.apache.commons.lang3.StringUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.handler.ws.*;
import star.sequoia2.client.types.ws.message.WSMessage;
import star.sequoia2.client.types.ws.message.ws.GIdentifyWSMessage;
import star.sequoia2.client.types.ws.type.WSMessageType;
import star.sequoia2.features.ToggleFeature;
import star.sequoia2.http.HttpUtils;
import star.sequoia2.settings.types.BooleanSetting;
import star.sequoia2.utils.AccessTokenManager;
import star.sequoia2.utils.wynn.WynnUtils;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static star.sequoia2.client.SeqClient.SCHEDULER;
import static star.sequoia2.client.SeqClient.mc;
import static star.sequoia2.client.types.ws.WSConstants.GSON;


public class WebSocketFeature extends ToggleFeature {

    BooleanSetting autoReconnect = settings().bool("AutoReconnect", "Automatically reconnect when you disconnec", true);

    @Getter
    public BooleanSetting connectOnJoin = settings().bool("ConnectOnJoin", "Auto connect when joining", true);

    private static final String WS_DEV_URL = "ws://66.78.40.43:8085/ws";
    private static final String WS_PROD_URL = "wss://api.sequoia.ooo/ws";

    @Getter
    private WebSocketClient client;

    private boolean isFirstConnection = false;
    private boolean isAuthenticating = false;
    private boolean isAuthenticated = false;

    public WebSocketFeature() {
        super("WebSocket", "Websocket settings" ,true);
    }

    public void initClient() {
        java.util.UUID uuid = mc.getSession().getUuidOrNull();
        if (uuid == null) {
            SeqClient.warn("Player UUID is not available. WebSocket connection will not be established.");
            return;
        }
//        SeqClient.debug(String.format("Using %s for URI", Sequoia2.isDevelopmentEnvironment()
//                ? WS_DEV_URL
//                : WS_PROD_URL));

        initClient(
//                URI.create(Sequoia2.isDevelopmentEnvironment()
//                                ? WS_DEV_URL
//                                : WS_PROD_URL),
                SeqClient.testMode ? URI.create(WS_DEV_URL) : URI.create(WS_PROD_URL),
                Map.of(
                        "Authoworization",
                        "Bearer meowmeowAG6v92hc23LK5rqrSD279",
                        "X-UUID",
                        uuid.toString(),
                        "User-Agent",
                        HttpUtils.USER_AGENT));
    }

    private void initClient(URI serverUri, Map<String, String> httpHeaders) {
        if (client != null) {
            return;
        }

        client = new WebSocketClient(serverUri, httpHeaders) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                if (!isActive()) {
                    close();
                    return;
                }

                SeqClient.debug("WebSocket connection opened.");
                authenticate();
            }

            @Override
            public void onMessage(String s) {
                if (!isActive()) {
                    close();
                    return;
                }

                try {
                    WSMessage wsMessage = GSON.fromJson(s, WSMessage.class);
                    WSMessageType wsMessageType = WSMessageType.fromValue(wsMessage.getType());

                    SeqClient.debug("Received WebSocket message: " + wsMessage);

                    switch (wsMessageType) {
                        case S_CHANNEL_MESSAGE -> new SChannelMessageWSMessageHandler(s).handle();
                        case S_SESSION_RESULT -> new SSessionResultWSMessageHandler(s).handle();
                        case S_MESSAGE -> new SMessageWSMessageHandler(s).handle();
                        case S_COMMAND_PIPE -> new SCommandPipeWSMessageHandler(s).handle();
                        case S_BINARY_DATA -> new SBinaryDataWSMessageHandler(s).handle();
                        case S_COMMAND_RESULT -> new SCommandResultWSMessageHandler(s).handle();
                        case S_REWARD_DATA -> new SRewardWSMessageHandler(s).handle();
                        default -> SeqClient.warn("Unhandled WebSocket message type: " + wsMessageType);
                    }
                } catch (Exception exception) {
                    SeqClient.error("Failed to parse WebSocket message: " + s, exception);
                }
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                SeqClient.debug("WebSocket connection closed. Code: " + i
                        + (StringUtils.isNotBlank(s) ? ", Reason: " + s : ""));
                closeIfNeeded();
                tryReconnect(true);
            }

            @Override
            public void onError(Exception e) {
                if (!isActive()) {
                    close();
                    return;
                }

                SeqClient.error("Error occurred in WebSocket connection", e);
                setAuthenticating(false);
                setAuthenticated(false);
                if (StringUtils.equals(e.getMessage(), "java.net.ConnectException: Connection refused: connect")) {
                    client.close();
                    tryReconnect(true);
                }
            }
        };
    }

    public String sendMessage(Object object) {
        if (!isActive()) {
            return null;
        }

        if (client == null) {
            initClient();
        }

        if (!client.isOpen()) {
            return null;
        }

        if (!isAuthenticating && !isAuthenticated && (Models.WorldState.onWorld() || Models.WorldState.onHousing())) {
            authenticate();
            return null;
        }

        try {
            String json = GSON.toJson(object);
            if (StringUtils.isBlank(json)) {
                return null;
            }

            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            if (jsonObject.has("data")) {
                JsonElement dataElement = jsonObject.get("data");
                if (dataElement.isJsonObject()) {
                    JsonObject data = dataElement.getAsJsonObject();
                    if (data.has("access_token")) {
                        // better debug
                        String tok = data.get("access_token").getAsString();
                        data.addProperty("access_token", redactMiddle(tok, 4, 4));
                    }
                }
            }

            SeqClient.debug("Sending WebSocket message: " + jsonObject);
            client.send(json);
            return json;
        } catch (RuntimeException exception) {
            SeqClient.error("Failed to send WebSocket message", exception);
            return null;
        }
    }

    public void authenticate() {
        authenticate(false);
    }

    public void authenticate(boolean receivedInvalidTokenResult) {
        if (!isActive()) {
            return;
        }

        if (McUtils.player() == null || StringUtils.isBlank(McUtils.player().getUuidAsString())) {
            SeqClient.warn("Player UUID is not available. WebSocket connection will not be established.");
            return;
        }

        WynnUtils.isSequoiaGuildMember().whenComplete((isSequoiaGuildMember, throwable) -> {
            if (throwable != null) {
                SeqClient.error("Failed to check if player is a Sequoia guild member");
                return;
            }

            if (!isSequoiaGuildMember) {
                return;
            }

            if (isAuthenticating()) {
                SeqClient.debug("Already authenticating with WebSocket server.");
                return;
            }

            setAuthenticating(true);
            setAuthenticated(false);
            SeqClient.debug("Authenticating with WebSocket server.");

            if (receivedInvalidTokenResult) {
                AccessTokenManager.invalidateAccessToken();
            }

            GIdentifyWSMessage gIdentifyWSMessage = new GIdentifyWSMessage(new GIdentifyWSMessage.Data(
                    AccessTokenManager.retrieveAccessToken(),
                    McUtils.player().getUuidAsString(),
                    SeqClient.getVersionInt()));
            sendMessage(gIdentifyWSMessage);
        });
    }

    public boolean isAuthenticating() {
        if (!isActive()) {
            return false;
        }
        return isAuthenticating;
    }

    public void setAuthenticating(boolean isAuthenticating) {
        if (!isActive()) {
            return;
        }
        this.isAuthenticating = isAuthenticating;
    }

    public boolean isAuthenticated() {
        if (!isActive()) {
            return false;
        }
        return isAuthenticated;
    }

    public void setAuthenticated(boolean isAuthenticated) {
        if (!isActive()) {
            return;
        }
        this.isAuthenticated = isAuthenticated;
    }

    public void connectIfNeeded() {
        if (!isActive()) {
            return;
        }

        if (client == null) {
            initClient();
        }

        if (client.isOpen()) {
            return;
        }

        if (!isFirstConnection) {
            isFirstConnection = true;
            client.connect();
        } else {
            client.reconnect();
        }
    }

    public void closeIfNeeded() {
        if (client == null) {
            return;
        }

        if (client.isOpen()) {
            client.close();
        }

        setAuthenticating(false);
        setAuthenticated(false);
    }

    public void tryReconnect(boolean respectAutoReconnectPreference) {

        if (respectAutoReconnectPreference && isActive() && !autoReconnect.get()) {
            return;
        }

        if (!Models.WorldState.onWorld() && !Models.WorldState.onHousing()) return;

        /* schedule a check 10 s (20 * 10 ticks) from now */
        SCHEDULER.schedule(() -> mc.execute(() -> {
            if (!isActive()) return;                // feature was disabled meanwhile

            if (client == null) initClient();        // create it lazily

            if (!client.isOpen()) client.reconnect();
        }), 10, TimeUnit.SECONDS);

    }

    @Override
    public void onDeactivate() {
        closeIfNeeded();
    }

    private static String redactMiddle(String s, int head, int tail) {
        if (s == null) return null;
        int len = s.length();
        if (len > head + tail) {
            return s.substring(0, head) + "..." + s.substring(len - tail);
        }
        // too short to safely reveal 4+4; show first/last char only if possible
        if (len > 2) return s.substring(0, 1) + "..." + s.substring(len - 1);
        return "...";
    }

}