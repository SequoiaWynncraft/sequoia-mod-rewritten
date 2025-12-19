package star.sequoia2.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import star.sequoia2.accessors.TeXParserAccessor;
import star.sequoia2.client.types.command.Command;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

@Deprecated
public class DummyCommand extends Command implements TeXParserAccessor {

    @Override
    public String getCommandName() {
        return "testing";
    }

    @Override
    public CommandNode<FabricClientCommandSource> register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess access) {
        return dispatcher.register(
                literal(getCommandName())
                        .then(argument("text", greedyString())
                                .executes(this::inner
                                )
                        )
        );
    }

//    private int inner(CommandContext<FabricClientCommandSource> ctx) {
//        ctx.getSource().sendFeedback(parseMutableText(ctx.getArgument("text", String.class)));
//        return 1;
//    }

    private int inner(CommandContext<FabricClientCommandSource> ctx) {
//        List<Integer> test = List.of(0, 1);
//        int[] payload = test.stream().mapToInt(Integer::intValue).toArray();

//        GIC3HWSMessage.Data data = new GIC3HWSMessage.Data(
//                GIC3HWSMessage.opCodes.INVALID.getValue(), 0, "", payload, List.of("*")
//        );


//        GIC3HWSMessage.Data data = new GIC3HWSMessage.Data(
//                GIC3HWSMessage.opCodes.INVALID.getValue(), 0, "", payload, List.of("fe44e5b2-e31c-4c3c-8806-b065bf437411")
//        );
//        GIC3HWSMessage gIC3HWSMessage = new GIC3HWSMessage(data);
//        Managers.Feature.getFeatureInstance(WebSocket.class).sendMessage(gIC3HWSMessage);
        ctx.getSource().sendFeedback(teXParser().parseMutableText(ctx.getArgument("text", String.class)));
        return 1;
    }
}
