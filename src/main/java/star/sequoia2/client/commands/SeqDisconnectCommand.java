package star.sequoia2.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import star.sequoia2.accessors.FeaturesAccessor;
import star.sequoia2.accessors.NotificationsAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.types.command.Command;
import star.sequoia2.client.types.command.suggestions.SuggestionProviders;
import star.sequoia2.features.impl.ws.WebSocket;
import star.sequoia2.utils.wynn.WynnUtils;
import star.sequoia2.utils.TickScheduler;

import java.util.Objects;
import java.util.Optional;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static star.sequoia2.client.SeqClient.mc;
import static star.sequoia2.utils.AccessTokenManager.invalidateAccessToken;

public class SeqDisconnectCommand extends Command implements FeaturesAccessor, NotificationsAccessor {
    @Override
    public String getCommandName() {
        return "seqdisconnect";
    }

    @Override
    public CommandNode<FabricClientCommandSource> register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess access) {
        return dispatcher.register(
                ClientCommandManager.literal(getCommandName())
                        .executes(this::auth)
                        .then(argument("deletetoken", word())
                                .suggests(SuggestionProviders.DELETE_TOKEN_SUGGESTION_PROVIDER)
                                .executes(this::auth))
        );

    }

    public static void deleteToken() {
        try {
            invalidateAccessToken();
            SeqClient.debug(I18n.translate("sequoia.command.deletetoken.success"));
        } catch (Exception exception) {
            SeqClient.debug(I18n.translate("sequoia.command.deletetoken.error"));
        }
    }

    public void deleteToken(CommandContext<FabricClientCommandSource> ctx) {
        try {
            invalidateAccessToken();
            ctx.getSource()
                    .sendFeedback(
                            prefixed(Text.translatable("sequoia.command.deletetoken.success")));
        } catch (Exception exception) {
            ctx.getSource()
                    .sendError(
                            prefixed(Text.translatable("sequoia.command.deletetoken.error")));
        }
    }

    private void sorter(CommandContext<FabricClientCommandSource> ctx) {
        Optional<WebSocket> wsFeature = features().getIfActive(WebSocket.class);
        if (wsFeature.map(WebSocket::isActive).orElse(false)) {
        } else {
            ctx.getSource()
                    .sendError(
                            prefixed(Text.translatable("sequoia.feature.webSocket.featureDisabled")));
            return;
        }

        WynnUtils.isSequoiaGuildMember()
                .whenComplete((isMember, ex) -> mc.execute(() -> {
                    if (ex != null || !Boolean.TRUE.equals(isMember)) {
                        ctx.getSource().sendError(
                                prefixed(Text.translatable("sequoia.command.notASequoiaGuildMember")));
                        return;
                    }

                    if (wsFeature.map(WebSocket::getClient).isEmpty()
                            || !wsFeature.map(webSocketFeature -> webSocketFeature.getClient().isOpen()).orElse(false)) {
                        ctx.getSource()
                                .sendError(prefixed(Text.translatable("sequoia.command.disconnect.notConnected")));
                        return;
                    }

                    ctx.getSource()
                            .sendFeedback(
                                    prefixed(Text.translatable("sequoia.command.disconnect.disconnecting"))

                            );
                    wsFeature.ifPresent(WebSocket::closeIfNeeded);
                    TickScheduler.scheduleTicks(() -> {
                        if (wsFeature.map(webSocketFeature -> webSocketFeature.getClient().isClosed()).orElse(true)) {
                            ctx.getSource()
                                    .sendFeedback(
                                            prefixed(
                                                    Text.translatable("sequoia.command.disconnect.disconnected"))
                                    );
                            return;
                        }

                        ctx.getSource()
                                .sendError(prefixed(
                                        Text.translatable("sequoia.command.disconnect.failedToDisconnect")));
                    }, 5);
                }));
    }

    private int auth(CommandContext<FabricClientCommandSource> ctx) {
        sorter(ctx);

        if (Objects.equals(ctx.getInput(), "seqdisconnect deletetoken")) {
            deleteToken(ctx);
        }
        return 1;
    }
}
