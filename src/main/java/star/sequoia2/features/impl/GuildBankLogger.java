package star.sequoia2.features.impl;

import com.collarmc.pounce.Subscribe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import star.sequoia2.client.types.ws.message.ws.GGuildBankEventWSMessage;
import star.sequoia2.client.types.ws.message.ws.GGuildBankStateWSMessage;
import star.sequoia2.events.PacketEvent;
import star.sequoia2.events.PlayerTickEvent;
import star.sequoia2.features.ToggleFeature;
import star.sequoia2.features.impl.ws.WebSocketFeature;

import static star.sequoia2.client.SeqClient.mc;

/**
 * Captures guild bank changes and forwards them to the Sequoia backend so officers have real-time logs.
 */
public class GuildBankLogger extends ToggleFeature {

    private static final Pattern BANK_EVENT_PATTERN = Pattern.compile(
            "^§b(?:\\uE006\\uE002|\\uE001) §3(?<username>.+)§b (?<type>withdrew|deposited) §e(?<amount>\\d+)x (?<item>.+)§b (?:from|to) the Guild Bank \\(§3(?<tier>Everyone|High Ranked)§b\\)$");
    private static final Pattern BANK_SCREEN_TITLE = Pattern.compile(".+: Bank \\((?<tier>.+)\\)");

    private String lastStateSignature;

    public GuildBankLogger() {
        super("GuildBankLogger", "Logs guild bank activity and inventories for officers");
    }

    @Subscribe
    private void onChat(PacketEvent.PacketReceiveEvent event) {
        if (!(event.packet() instanceof GameMessageS2CPacket packet) || packet.overlay()) {
            return;
        }

        Text content = packet.content();
        if (content == null) return;

        String raw = content.getString();
        if (raw == null || raw.isEmpty()) return;

        Matcher matcher = BANK_EVENT_PATTERN.matcher(raw);
        if (!matcher.matches()) return;

        String username = stripFormatting(matcher.group("username"));
        String type = matcher.group("type");
        int amount = Integer.parseInt(matcher.group("amount"));
        String item = stripFormatting(matcher.group("item"));
        boolean hr = matcher.group("tier").equalsIgnoreCase("High Ranked");
        String action = type.equalsIgnoreCase("deposited") ? "added" : "removed";

        sendEvent(new GGuildBankEventWSMessage.Data(item, amount, username, action, hr, System.currentTimeMillis()));
    }

    @Subscribe
    private void onPlayerTick(PlayerTickEvent event) {
        if (mc.player == null) {
            lastStateSignature = null;
            return;
        }
        Screen currentScreen = mc.currentScreen;
        if (!(currentScreen instanceof HandledScreen<?> handledScreen)) {
            lastStateSignature = null;
            return;
        }
        String title = handledScreen.getTitle().getString();
        Matcher matcher = BANK_SCREEN_TITLE.matcher(title);
        if (!matcher.matches()) {
            return;
        }
        boolean hr = matcher.group("tier").equalsIgnoreCase("High Ranked");
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler == null || handler == mc.player.playerScreenHandler) {
            return;
        }

        List<GGuildBankStateWSMessage.Item> items = collectItems(handler);
        String signature = createSignature(hr, items);
        if (signature.equals(lastStateSignature)) {
            return;
        }

        if (sendState(new GGuildBankStateWSMessage.Data(hr, items))) {
            lastStateSignature = signature;
        }
    }

    private List<GGuildBankStateWSMessage.Item> collectItems(ScreenHandler handler) {
        List<GGuildBankStateWSMessage.Item> items = new ArrayList<>();
        for (Slot slot : handler.slots) {
            if (slot.inventory == mc.player.getInventory()) continue;
            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty()) continue;
            String name = stack.getName().getString();
            List<String> nbtLines = extractTooltipLines(stack);
            items.add(new GGuildBankStateWSMessage.Item(name, nbtLines, stack.getCount()));
        }
        return items;
    }

    private List<String> extractTooltipLines(ItemStack stack) {
        if (mc.player == null) return Collections.emptyList();
        List<Text> tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, mc.player, TooltipType.BASIC);
        if (tooltip.isEmpty()) return Collections.emptyList();
        List<String> lines = new ArrayList<>(tooltip.size());
        for (Text line : tooltip) {
            String cleaned = line.getString().trim();
            if (!cleaned.isEmpty()) {
                lines.add(cleaned);
            }
        }
        return lines;
    }

    private boolean sendEvent(GGuildBankEventWSMessage.Data data) {
        WebSocketFeature ws = activeWebSocket();
        if (ws == null) return false;
        ws.sendMessage(new GGuildBankEventWSMessage(data));
        return true;
    }

    private boolean sendState(GGuildBankStateWSMessage.Data data) {
        WebSocketFeature ws = activeWebSocket();
        if (ws == null) return false;
        ws.sendMessage(new GGuildBankStateWSMessage(data));
        return true;
    }

    private WebSocketFeature activeWebSocket() {
        return features().getIfActive(WebSocketFeature.class)
                .filter(WebSocketFeature::isActive)
                .filter(WebSocketFeature::isAuthenticated)
                .orElse(null);
    }

    private static String stripFormatting(String value) {
        return value == null ? "" : value.replaceAll("§.", "").trim();
    }

    private String createSignature(boolean hr, List<GGuildBankStateWSMessage.Item> items) {
        // i could probably put some more effort into this signature builder, but it keeps spam in check for now
        StringBuilder builder = new StringBuilder(hr ? "1" : "0");
        for (GGuildBankStateWSMessage.Item item : items) {
            builder.append('|')
                    .append(item.itemName())
                    .append(':')
                    .append(item.amount());
            for (String line : item.nbtData()) {
                builder.append('#').append(line);
            }
        }
        return builder.toString();
    }
}
