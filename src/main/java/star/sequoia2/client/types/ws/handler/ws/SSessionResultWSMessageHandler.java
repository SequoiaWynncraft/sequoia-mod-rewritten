package star.sequoia2.client.types.ws.handler.ws;

import org.apache.commons.lang3.StringUtils;
import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.handler.WSMessageHandler;
import star.sequoia2.client.types.ws.message.ws.SSessionResultWSMessage;
import star.sequoia2.features.impl.ws.WebSocket;
import star.sequoia2.utils.AccessTokenManager;

import java.util.regex.Pattern;
import java.util.Optional;

import static star.sequoia2.client.commands.SeqDisconnectCommand.deleteToken;
import static star.sequoia2.client.types.ws.WSConstants.GSON;


public class SSessionResultWSMessageHandler extends WSMessageHandler implements FeaturesAccessor {
    private static final Pattern JWT_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");

    public SSessionResultWSMessageHandler(String message) {
        super(GSON.fromJson(message, SSessionResultWSMessage.class), message);
    }

    @Override
    public void handle() {
        Optional<WebSocket> wsFeature = features().getIfActive(WebSocket.class);
        SSessionResultWSMessage sSessionResultWSMessage = GSON.fromJson(message, SSessionResultWSMessage.class);
        SSessionResultWSMessage.Data sSessionResultWSMessageData = sSessionResultWSMessage.getSSessionResultData();

        if (sSessionResultWSMessageData.error()) {
            return;
        }

        String result = sSessionResultWSMessageData.result();

//        Sequoia2.debug("SessionResult:" + result);

        if (StringUtils.equals(result, "Invalid token")) {
            SeqClient.debug("Invalid token.");
            deleteToken();
            return;
        }

        if (StringUtils.equals(result, "Authentication pending.")) {
            wsFeature.ifPresent(webSocketFeature -> webSocketFeature.setAuthenticating(true));
            wsFeature.ifPresent(webSocketFeature -> webSocketFeature.setAuthenticated(false));
            SeqClient.debug("Authentication pending, waiting for successful authentication.");
            return;
        }

        if (StringUtils.equals(result, "Authenticated.")) {
            wsFeature.ifPresent(webSocketFeature -> webSocketFeature.setAuthenticating(false));
            wsFeature.ifPresent(webSocketFeature -> webSocketFeature.setAuthenticated(true));
            SeqClient.debug("Websocket authenticated!");
            return;
        }

        if (StringUtils.equals(result, "REDACTED")) {
            SeqClient.debug("Token redacted.");
            return;
        }

        if (JWT_PATTERN.matcher(result).matches()) {

            wsFeature.ifPresent(webSocketFeature -> webSocketFeature.setAuthenticating(false));
            wsFeature.ifPresent(webSocketFeature -> webSocketFeature.setAuthenticated(true));
            SeqClient.debug("Authenticated with WebSocket server.");

            if (!StringUtils.equals(AccessTokenManager.retrieveAccessToken(), result)) {
                AccessTokenManager.storeAccessToken(result);
            }
        } else {
            SeqClient.error("Failed to authenticate with WebSocket server: " + result);
        }
    }
}
