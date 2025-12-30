package star.sequoia2.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import java.util.Optional;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.accessors.NotificationsAccessor;
import star.sequoia2.client.types.command.Command;
import star.sequoia2.features.impl.ws.WebSocket;
import star.sequoia2.utils.wynn.WynnUtils;
import star.sequoia2.utils.TickScheduler;

import static star.sequoia2.client.SeqClient.mc;

public class SeqConnectCommand extends Command implements FeaturesAccessor, NotificationsAccessor {
    @Override
    public String getCommandName() {
        return "seqconnect";
    }

    @Override
    public CommandNode<FabricClientCommandSource> register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess access) {
        return dispatcher.register(
                ClientCommandManager.literal(getCommandName()).executes(this::auth
                )
        );

    }

//    private int auth(CommandContext<FabricClientCommandSource> ctx) {
//        if (!Sequoia2.getWebSocketFeature().isEnabled()) {
//            ctx.getSource()
//                    .sendError(
//                            Sequoia2.prefix(Text.translatable("sequoia.feature.webSocket.featureDisabled")));
//            return 1;
//        }
//
//        if (Boolean.FALSE.equals(WynnUtils.isSequoiaGuildMember().join())) {
//            ctx.getSource()
//                    .sendError(Sequoia2.prefix(Text.translatable("sequoia.command.notASequoiaGuildMember")));
//            return 1;
//        }
//        CompletableFuture
//                .runAsync(() -> Sequoia2.getWebSocketFeature().connectIfNeeded()) // common pool
//                .orTimeout(10, TimeUnit.SECONDS)                                    // auto‑fail after 10 s
//                .handleAsync((ok, ex) -> {
//
//        if (Sequoia2.getWebSocketFeature().getClient() == null) {
//            Sequoia2.getWebSocketFeature().initClient();
//        }
//
//        if (Sequoia2.getWebSocketFeature().getClient().isOpen()) {
//            ctx.getSource()
//                    .sendError(Sequoia2.prefix(Text.translatable("sequoia.command.connect.alreadyConnected")));
//            return 1;
//        }
//
//        ctx.getSource()
//                .sendFeedback(
//                        Sequoia2.prefix(Text.translatable("sequoia.command.connect.connecting")));
//
//                    Sequoia2.getWebSocketFeature().connectIfNeeded();
//                    SCHEDULER.schedule(() -> MinecraftClient.getInstance().execute(() -> {
//
//                        WebSocketClient ws = Sequoia2.getWebSocketFeature().getClient();
//                        if (ws == null || ws.isOpen()) return;   // either connected or feature gone
//
//                        ctx.getSource().sendError(
//                                Sequoia2.prefix(Text.translatable(
//                                        "sequoia.command.connect.failedToConnect")));
//
//                    }), 10, TimeUnit.SECONDS);
//
//                    Managers.TickScheduler.scheduleLater(
//                            () -> {
//                                if (!Sequoia2.getWebSocketFeature().getClient().isOpen()) {
//                                    ctx.getSource()
//                                            .sendError(Sequoia2.prefix(
//                                                    Text.translatable("sequoia.command.connect.failedToConnect")));
//                                }
//                            },
//                            20 * 10);
//                    return null;
//                });
//        return 1;
//    }

    private int auth(CommandContext<FabricClientCommandSource> ctx) {
        Optional<WebSocket> wsFeature = features().getIfActive(WebSocket.class);
        if (wsFeature.map(WebSocket::isActive).orElse(false)) {
            // continue
        } else {
            ctx.getSource()
                    .sendError(
                            prefixed(Text.translatable("sequoia.feature.webSocket.featureDisabled")));
            return 1;
        }

        WynnUtils.isSequoiaGuildMember()
                .whenComplete((isMember, ex) -> mc.execute(() -> {
                    if (ex != null || !Boolean.TRUE.equals(isMember)) {
                        ctx.getSource().sendError(
                                prefixed(Text.translatable("sequoia.command.notASequoiaGuildMember")));
                        return;
                    }

                    if (wsFeature.map(WebSocket::getClient).isEmpty()) {
                        wsFeature.ifPresent(WebSocket::initClient);
                    }

                    if (wsFeature.map(webSocketFeature -> webSocketFeature.getClient().isOpen()).orElse(false)) {
                        ctx.getSource()
                                .sendError(prefixed(Text.translatable("sequoia.command.connect.alreadyConnected")));
                        return;
                    }

                    ctx.getSource()
                            .sendFeedback(
                                    prefixed(Text.translatable("sequoia.command.connect.connecting")));
                    wsFeature.ifPresent(WebSocket::connectIfNeeded);
                    TickScheduler.scheduleTicks(() -> {
                        if (!wsFeature.map(webSocketFeature -> webSocketFeature.getClient().isOpen()).orElse(false)) {
                            ctx.getSource()
                                    .sendError(prefixed(
                                            Text.translatable("sequoia.command.connect.failedToConnect")));
                        }
                    }, 20 * 10);
                }));

        return 1;
    }
}
