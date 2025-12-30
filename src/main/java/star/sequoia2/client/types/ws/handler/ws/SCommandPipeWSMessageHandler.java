package star.sequoia2.client.types.ws.handler.ws;

import org.apache.commons.lang3.StringUtils;
import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.ws.handler.WSMessageHandler;
import star.sequoia2.client.types.ws.message.ws.SCommandPipeWSMessage;
import star.sequoia2.features.impl.ws.WebSocket;

import java.util.Optional;

import static star.sequoia2.client.types.ws.WSConstants.GSON;

public class SCommandPipeWSMessageHandler extends WSMessageHandler implements FeaturesAccessor {
    public SCommandPipeWSMessageHandler(String message) {
        super(GSON.fromJson(message, SCommandPipeWSMessage.class), message);
    }

    @Override
    public void handle() {
        Optional<WebSocket> wsFeature = features().getIfActive(WebSocket.class);
        if (StringUtils.equals("Invalid token", wsMessage.getData().getAsString())) {
            SeqClient.debug("Received invalid token response. Requesting a new token.");
            wsFeature.ifPresent(webSocketFeature -> webSocketFeature.authenticate(true));
        } else if (StringUtils.equals("Authenticated.", wsMessage.getData().getAsString())) {
            SeqClient.debug("Authenticated with WebSocket server.");
            wsFeature.ifPresent(webSocketFeature -> webSocketFeature.setAuthenticating(false));
            wsFeature.ifPresent(webSocketFeature -> webSocketFeature.setAuthenticated(true));
        }
    }
}
