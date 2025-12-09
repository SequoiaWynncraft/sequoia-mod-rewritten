package star.sequoia2.features.impl;

import com.collarmc.pounce.Subscribe;
import com.wynntils.core.components.Managers;
import com.wynntils.core.components.Models;
import com.wynntils.features.inventory.ContainerSearchFeature;
import com.wynntils.models.containers.containers.GuildMemberListContainer;
import com.wynntils.models.containers.type.ContainerBounds;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;
import star.sequoia2.client.SeqClient;
import star.sequoia2.events.Render2DEvent;
import star.sequoia2.events.input.MouseButtonEvent;
import star.sequoia2.features.ToggleFeature;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static star.sequoia2.client.SeqClient.mc;

/**
 * Automates giving guild rewards (aspects/tomes/emeralds) from the guild member list.
 * Uses Wynntils' ContainerSearchFeature to read the search text, and simulates number-key clicks on player heads.
 */
public class GuildRewardGranter extends ToggleFeature {

    private static final int SLOT_NEXT = 28;
    private static final int SLOT_PREV = 10;
    private static final ContainerBounds MEMBER_BOUNDS = new GuildMemberListContainer().getBounds(); // 0,2 to 4,8

    private static final int HOTBAR_ASPECT = 1;
    private static final int HOTBAR_TOME = 2;
    private static final int HOTBAR_EMS = 3;
    private static final String DUMP_TARGET = "cinfrascitizen";

    private final Map<String, Integer> nameToSlot = new ConcurrentHashMap<>();
    private final Map<String, Integer> nameToPage = new ConcurrentHashMap<>();
    private int currentPage = 0;
    private boolean scanning = false;

    private SimpleButton giveAspectBtn;
    private SimpleButton giveTomeBtn;
    private SimpleButton giveEmsBtn;
    private SimpleButton dumpEmsBtn;

    public GuildRewardGranter() {
        super("GuildRewardGranter", "Automate giving guild rewards from the member list");
    }

    @Override
    protected void onActivate() {
        giveAspectBtn = null;
        giveTomeBtn = null;
        giveEmsBtn = null;
        dumpEmsBtn = null;
        nameToSlot.clear();
        nameToPage.clear();
        currentPage = 0;
        scanning = false;
    }

    @Override
    protected void onDeactivate() {
        nameToSlot.clear();
        nameToPage.clear();
        scanning = false;
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
        if (!(Models.Container.getCurrentContainer() instanceof GuildMemberListContainer)) return;

        ensureButtons(screen, event.context());
    }

    private void ensureButtons(GenericContainerScreen screen, DrawContext ctx) {
        int sx = getIntField(screen, "x", (screen.width - getIntField(screen, "backgroundWidth", 176)) / 2);
        int bw = getIntField(screen, "backgroundWidth", 176);
        int sy = getIntField(screen, "y", (screen.height - getIntField(screen, "backgroundHeight", 166)) / 2);

        int baseX = sx + bw + 4;
        int baseY = sy + 20;
        int w = 80;
        int h = 16;
        int gap = 4;

        if (giveAspectBtn == null) {
            giveAspectBtn = new SimpleButton(baseX, baseY, w, h, Text.literal("Give Aspect"),
                    () -> giveToSearch(HOTBAR_ASPECT));
            giveTomeBtn = new SimpleButton(baseX, baseY + (h + gap), w, h, Text.literal("Give Tome"),
                    () -> giveToSearch(HOTBAR_TOME));
            giveEmsBtn = new SimpleButton(baseX, baseY + 2 * (h + gap), w, h, Text.literal("Give Ems"),
                    () -> giveToSearch(HOTBAR_EMS));
            dumpEmsBtn = new SimpleButton(baseX, baseY + 3 * (h + gap), w, h, Text.literal("Dump Ems"),
                    () -> giveToName(DUMP_TARGET, HOTBAR_EMS));
        }

        double mx = mc.mouse.getX() * screen.width / mc.getWindow().getScaledWidth();
        double my = mc.mouse.getY() * screen.height / mc.getWindow().getScaledHeight();

        renderButton(ctx, giveAspectBtn, mx, my);
        renderButton(ctx, giveTomeBtn, mx, my);
        renderButton(ctx, giveEmsBtn, mx, my);
        renderButton(ctx, dumpEmsBtn, mx, my);
    }

    private void giveToSearch(int hotbarKey) {
        String search = readSearchText().orElse("").trim();
        if (search.isEmpty()) {
            notify(Text.literal("Type a player name in the search bar first"), "guildrewardgranter-no-search");
            return;
        }
        giveToName(search, hotbarKey);
    }

    private void giveToName(String target, int hotbarKey) {
        if (!isActive()) return;
        sendStatus("Attempting to give " + labelForHotbar(hotbarKey) + " to " + target);
        SeqClient.SCHEDULER.execute(() -> {
            try {
                scanAllPages();
                boolean clicked = clickPlayer(target, hotbarKey);
                if (!clicked) {
                    notify(Text.literal("Player not found: " + target), "guildrewardgranter-notfound");
                } else {
                    sendStatus("Sent " + labelForHotbar(hotbarKey) + " click to " + target);
                }
            } catch (Exception e) {
                SeqClient.error("GuildRewardGranter failed", e);
                notify(Text.literal("Failed to give reward: " + e.getMessage()), "guildrewardgranter-error");
            }
        });
    }

    private void scanAllPages() throws InterruptedException {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            sendStatus("Not in guild members screen");
            return;
        }
        if (scanning) return;
        scanning = true;
        try {
            nameToSlot.clear();
            nameToPage.clear();
            currentPage = 0;
            sendStatus("Scanning guild members...");
            int pages = 0;

            // scan forward until no next
            while (true) {
                readMembersOnPage(screen);
                if (!clickIfPresent(screen, SLOT_NEXT)) break;
                currentPage++;
                Thread.sleep(150);
                pages++;
            }
            // go back to first page
            while (clickIfPresent(screen, SLOT_PREV)) {
                currentPage = Math.max(0, currentPage - 1);
                Thread.sleep(150);
            }
            sendStatus("Scanned " + (pages + 1) + " page(s), found " + nameToSlot.size() + " members");
        } finally {
            scanning = false;
        }
    }

    private void readMembersOnPage(GenericContainerScreen screen) {
        for (int slotIdx : MEMBER_BOUNDS.getSlots()) {
            Slot slot = screen.getScreenHandler().getSlot(slotIdx);
            if (slot == null || !slot.hasStack()) continue;
            if (slot.getStack().getItem() != Items.PLAYER_HEAD) continue;
            String name = slot.getStack().getName().getString();
            if (StringUtils.isBlank(name)) continue;
            nameToSlot.put(name.toLowerCase(Locale.ROOT), slotIdx);
            nameToPage.put(name.toLowerCase(Locale.ROOT), currentPage);
        }
    }

    private boolean clickPlayer(String targetName, int hotbarKey) throws InterruptedException {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return false;
        String key = targetName.toLowerCase(Locale.ROOT);
        Integer page = nameToPage.get(key);
        Integer slot = nameToSlot.get(key);
        if (page == null || slot == null) {
            sendStatus("No entry cached for " + targetName + "; scan found " + nameToSlot.size() + " names");
            return false;
        }
        // navigate to target page
        while (currentPage < page && clickIfPresent(screen, SLOT_NEXT)) {
            currentPage++;
            Thread.sleep(120);
        }
        while (currentPage > page && clickIfPresent(screen, SLOT_PREV)) {
            currentPage--;
            Thread.sleep(120);
        }
        Slot s = screen.getScreenHandler().getSlot(slot);
        if (s == null) return false;
        // number key click
        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot, hotbarKey - 1, net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
        return true;
    }

    private boolean clickIfPresent(GenericContainerScreen screen, int slotIdx) {
        Slot slot = screen.getScreenHandler().getSlot(slotIdx);
        if (slot == null || !slot.hasStack()) return false;
        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slotIdx, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
        return true;
    }

    private Optional<String> readSearchText() {
        try {
            ContainerSearchFeature searchFeature = Managers.Feature.getFeatureInstance(ContainerSearchFeature.class);
            Field lastSearchWidget = ContainerSearchFeature.class.getDeclaredField("lastSearchWidget");
            lastSearchWidget.setAccessible(true);
            Object widget = lastSearchWidget.get(searchFeature);
            if (widget == null) return Optional.empty();
            Method getText = widget.getClass().getMethod("getTextBoxInput");
            Object res = getText.invoke(widget);
            return Optional.ofNullable(res).map(Object::toString);
        } catch (Exception e) {
            SeqClient.warn("Failed to read search text via reflection", e);
            return Optional.empty();
        }
    }

    private record SimpleButton(int x, int y, int width, int height, Text label, Runnable onClick) {
        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + width && my >= y && my <= y + height;
        }
    }

    @Subscribe
    public void onMouse(MouseButtonEvent event) {
        if (event.action() != 1) return; // press
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
        if (!(Models.Container.getCurrentContainer() instanceof GuildMemberListContainer)) return;
        double mx = mc.mouse.getX() * screen.width / mc.getWindow().getScaledWidth();
        double my = mc.mouse.getY() * screen.height / mc.getWindow().getScaledHeight();
        if (giveAspectBtn != null && giveAspectBtn.contains(mx, my)) { giveAspectBtn.onClick().run(); event.cancel(); return; }
        if (giveTomeBtn != null && giveTomeBtn.contains(mx, my))   { giveTomeBtn.onClick().run(); event.cancel(); return; }
        if (giveEmsBtn != null && giveEmsBtn.contains(mx, my))    { giveEmsBtn.onClick().run(); event.cancel(); return; }
        if (dumpEmsBtn != null && dumpEmsBtn.contains(mx, my))    { dumpEmsBtn.onClick().run(); event.cancel(); }
    }

    private void renderButton(DrawContext ctx, SimpleButton btn, double mx, double my) {
        boolean hover = btn.contains(mx, my);
        int bg = hover ? 0xCC2E8B57 : 0xAA000000;
        int border = hover ? 0xFFFFFFFF : 0x80FFFFFF;
        int tx = hover ? 0xFFFFFFFF : 0xE0FFFFFF;
        ctx.fill(btn.x - 1, btn.y - 1, btn.x + btn.width + 1, btn.y + btn.height + 1, border);
        ctx.fill(btn.x, btn.y, btn.x + btn.width, btn.y + btn.height, bg);
        ctx.drawText(mc.textRenderer, btn.label, btn.x + 4, btn.y + (btn.height - 8) / 2 + (hover ? -1 : 0), tx, false);
    }

    private int getIntField(Object target, String fieldName, int fallback) {
        try {
            Field f = target.getClass().getSuperclass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.getInt(target);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void sendStatus(String msg) {
        mc.execute(() -> mc.getMessageHandler().onGameMessage(SeqClient.prefix(Text.literal(msg)), false));
    }

    private static String labelForHotbar(int key) {
        return switch (key) {
            case HOTBAR_ASPECT -> "Aspect";
            case HOTBAR_TOME -> "Tome";
            case HOTBAR_EMS -> "Ems";
            default -> "Reward";
        };
    }
}
