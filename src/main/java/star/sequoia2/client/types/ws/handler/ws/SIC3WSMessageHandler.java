package star.sequoia2.client.types.ws.handler.ws;

import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.client.types.ws.handler.WSMessageHandler;
import star.sequoia2.client.types.ws.message.ws.SIC3DataWSMessage;

import static star.sequoia2.client.types.ws.WSConstants.GSON;

public class SIC3WSMessageHandler extends WSMessageHandler implements FeaturesAccessor {
    public SIC3WSMessageHandler(String message) {
        super(GSON.fromJson(message, SIC3DataWSMessage.class), message);
    }

    @Override
    public void handle() {
        SIC3DataWSMessage sic3DataWSMessage = (SIC3DataWSMessage) wsMessage;
        SIC3DataWSMessage.Data data = sic3DataWSMessage.getChatMessage();

        // RTSWar removed; no-op for IC3 data
    }
}
