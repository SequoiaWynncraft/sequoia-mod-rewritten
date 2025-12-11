package star.sequoia2.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import star.sequoia2.accessors.NotificationsAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.client.commands.SeqUpdateCommand.SourceBridge;
import star.sequoia2.client.types.command.Command;
import star.sequoia2.client.update.ReleaseInfo;
import star.sequoia2.client.update.UpdateChannel;
import star.sequoia2.client.update.UpdateManager;

public class SeqUpdateCommand extends Command implements NotificationsAccessor {

    @Override
    public String getCommandName() {
        return "sequpdate";
    }

    @Override
    public CommandNode<FabricClientCommandSource> register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess access) {
        return dispatcher.register(
                ClientCommandManager.literal(getCommandName())
                        .executes(ctx -> {
                            UpdateChannel channel = UpdateManager.getChannel();
                            String cached = UpdateManager.getCachedRelease().map(ReleaseInfo::displayVersion).orElse("unknown");
                            ctx.getSource().sendFeedback(prefixed(Text.literal("Channel: " + channel.displayName() + " (latest known: " + cached + ")").formatted(Formatting.GRAY)));
                            ctx.getSource().sendFeedback(prefixed(Text.literal("Checking for " + channel.displayName() + " updates...").formatted(Formatting.GRAY)));
                            UpdateManager.checkForUpdates(true);
                            return 1;
                        })
                        .then(ClientCommandManager.literal("install")
                                .executes(ctx -> {
                                    UpdateManager.installLatest(new SourceBridge(ctx.getSource()));
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("force")
                                .executes(ctx -> {
                                    var cached = UpdateManager.getCachedRelease()
                                            .orElseGet(() -> {
                                                SeqClient.debug("Force update: refreshing release info");
                                                UpdateManager.checkForUpdates(true);
                                                return UpdateManager.getCachedRelease().orElse(null);
                                            });
                                    if (cached == null) {
                                        ctx.getSource().sendFeedback(prefixed(Text.literal("No release info cached; run /sequpdate first.").formatted(Formatting.RED)));
                                        return 0;
                                    }
                                    UpdateManager.forceInstall(cached, new SourceBridge(ctx.getSource()));
                                    return 1;
                                }))
        );
    }

    public static class SourceBridge implements UpdateManager.FabricClientCommandSourceBridge, NotificationsAccessor {
        private final FabricClientCommandSource source;

        public SourceBridge(FabricClientCommandSource source) {
            this.source = source;
        }

        @Override
        public void reply(String message, Formatting formatting) {
            source.sendFeedback(prefixed(Text.literal(message).formatted(formatting)));
        }
    }
}
