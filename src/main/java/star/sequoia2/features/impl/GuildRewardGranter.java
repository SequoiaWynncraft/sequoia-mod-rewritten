package star.sequoia2.features.impl;

import com.collarmc.pounce.Subscribe;
import com.wynntils.core.components.Managers;
import com.wynntils.core.components.Models;
import com.wynntils.features.inventory.ContainerSearchFeature;
import com.wynntils.models.containers.containers.GuildMemberListContainer;
import com.wynntils.models.containers.type.ContainerBounds;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import org.apache.commons.lang3.StringUtils;
import star.sequoia2.client.SeqClient;
import star.sequoia2.events.Render2DEvent;
import star.sequoia2.events.ScreenOpenedEvent;
import star.sequoia2.events.input.MouseButtonEvent;
import star.sequoia2.events.PacketEvent;
import star.sequoia2.features.ToggleFeature;
import star.sequoia2.settings.types.BooleanSetting;
import star.sequoia2.settings.types.IntSetting;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static star.sequoia2.client.SeqClient.mc;

public class GuildRewardGranter extends ToggleFeature {

    private final IntSetting pageDelayMs = settings().number("PageDelayMs", "Delay between page turns when scanning", 450, 50, 2000);
    private final IntSetting clickDelayMs = settings().number("ClickDelayMs", "Delay between number-key clicks", 300, 25, 1000);
    private final BooleanSetting autoScrollEnabled = settings().bool("AutoScroll", "Auto-scroll to unique search match", true);
    private final BooleanSetting blurSearchOnClick = settings().bool("BlurSearchAfterClick", "Unfocus Wynntils search after reward click", true);


    private static final int SLOT_NEXT = 28;
    private static final int SLOT_PREV = 10;
    private static final ContainerBounds MEMBER_BOUNDS = new GuildMemberListContainer().getBounds();
    private static final int HOTBAR_ASPECT = 1;
    private static final int HOTBAR_TOME = 2;
    private static final int HOTBAR_EMS = 3;
    private static final String DUMP_TARGET = "cinfrascitizen";
    private static final long AUTO_SCROLL_COOLDOWN_MS = 800;

    private final Map<String, Integer> nameToSlot = new ConcurrentHashMap<>();
    private final Map<String, Integer> nameToPage = new ConcurrentHashMap<>();
    private int currentPage = 0;
    private boolean scanning = false;
    private String lastSearchQuery = "";
    private long lastAutoScrollMs = 0L;
    private volatile boolean stopDumpDueToNoEms = false;
    private boolean inMembersScreen = false;
    private long screenSession = 0L;
    private String pendingAutoScroll = "";
    private boolean initialScanDone = false;
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
        lastSearchQuery = "";
        lastAutoScrollMs = 0L;
        stopDumpDueToNoEms = false;
        inMembersScreen = false;
        screenSession = 0L;
        pendingAutoScroll = "";
        initialScanDone = false;
    }

    @Override
    protected void onDeactivate() {
        nameToSlot.clear();
        nameToPage.clear();
        scanning = false;
        inMembersScreen = false;
        screenSession++;
        lastSearchQuery = "";
        lastAutoScrollMs = 0L;
        stopDumpDueToNoEms = false;
        pendingAutoScroll = "";
        initialScanDone = false;
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen) || !(Models.Container.getCurrentContainer() instanceof GuildMemberListContainer)) {
            if (inMembersScreen) {
                screenSession++;
                inMembersScreen = false;
                scanning = false;
                nameToSlot.clear();
                nameToPage.clear();
                lastSearchQuery = "";
                lastAutoScrollMs = 0L;
                stopDumpDueToNoEms = false;
                pendingAutoScroll = "";
                initialScanDone = false;
            }
            return;
        }
        inMembersScreen = true;
        ensureButtons(screen, event.context());
        if (!initialScanDone && !scanning && pageHasHeads(screen)) {
            initialScanDone = true;
            scanAllPagesAsync();
        }
        maybeAutoNavigateSearch();
    }

    @Subscribe
    public void onScreenOpened(ScreenOpenedEvent event) {
        if (event.screen() instanceof GenericContainerScreen
                && Models.Container.getCurrentContainer() instanceof GuildMemberListContainer
                && !scanning) {
            scanAllPagesAsync();
        }
    }

    private void ensureButtons(GenericContainerScreen screen, DrawContext ctx) {
        int sx = getIntField(screen, "x", (screen.width - getIntField(screen, "backgroundWidth", 176)) / 2);
        int bw = getIntField(screen, "backgroundWidth", 176);
        int sy = getIntField(screen, "y", (screen.height - getIntField(screen, "backgroundHeight", 166)) / 2);
        int baseX = sx + bw + 4;
        int baseY = sy + 20;
        int w = 80;
        int h = 16;
        if (giveAspectBtn == null) {
            giveAspectBtn = new SimpleButton(baseX, baseY, w, h, Text.literal("Give Aspect"), searchClick(HOTBAR_ASPECT));
            giveTomeBtn = new SimpleButton(baseX, baseY + 16 + 4, w, h, Text.literal("Give Tome"), searchClick(HOTBAR_TOME));
            giveEmsBtn = new SimpleButton(baseX, baseY + (2 * (16 + 4)), w, h, Text.literal("Give Ems"), searchClick(HOTBAR_EMS));
            dumpEmsBtn = new SimpleButton(baseX, baseY + (3 * (16 + 4)), w, h, Text.literal("Dump Ems"), () -> giveToName(DUMP_TARGET, HOTBAR_EMS));
        }
        double scale = mc.getWindow().getScaleFactor();
        double mx = mc.mouse.getX() / scale;
        double my = mc.mouse.getY() / scale;
        renderButton(ctx, giveAspectBtn, mx, my);
        renderButton(ctx, giveTomeBtn, mx, my);
        renderButton(ctx, giveEmsBtn, mx, my);
        renderButton(ctx, dumpEmsBtn, mx, my);
    }

    private Runnable searchClick(int hotbarKey) {
        return () -> {
            String search = normalizeName(readSearchText().orElse(""));
            if (search.isEmpty()) {
                notify(Text.literal("Type a player name in the search bar first"), "guildrewardgranter-no-search");
                return;
            }
            giveToName(search, hotbarKey);
        };
    }

    private void giveToName(String target, int hotbarKey) {
        if (!isActive()) return;
        String normalizedTarget = normalizeName(target);
        if (hotbarKey == HOTBAR_EMS) {
            stopDumpDueToNoEms = false;
        }
        notify(prefixed(Text.literal("Attempting to give " + labelForHotbar(hotbarKey) + " to " + target)), "guildrewardgranter-status");
        SeqClient.SCHEDULER.execute(() -> {
            int clicks;
            try {
                clicks = hotbarKey == HOTBAR_ASPECT ? 20 : (hotbarKey == HOTBAR_EMS && DUMP_TARGET.equals(normalizedTarget)) ? 20 : 1;
                CompletableFuture<Void> ready = (!nameToSlot.isEmpty() || scanning) ? CompletableFuture.completedFuture(null) : scanAllPagesAsync();
                int clicksFinal = clicks;
                ready.thenComposeAsync(v -> attemptGive(normalizedTarget, hotbarKey, clicksFinal, true), SeqClient.SCHEDULER).whenComplete((clicked, ex) -> {
                    if (ex != null) {
                        SeqClient.error("GuildRewardGranter failed", ex);
                        notify(prefixed(Text.literal("Failed to give reward: " + ex.getMessage())), "guildrewardgranter-error");
                    } else if (!Boolean.TRUE.equals(clicked)) {
                        notify(prefixed(Text.literal("Player not found: " + target)), "guildrewardgranter-notfound");
                    } else {
                        notify(prefixed(Text.literal("Sent " + clicksFinal + " " + labelForHotbar(hotbarKey) + " click(s) to " + target)), "guildrewardgranter-status");
                    }
                });
            } catch (Exception e) {
                SeqClient.error("GuildRewardGranter failed", e);
                notify(prefixed(Text.literal("Failed to give reward: " + e.getMessage())), "guildrewardgranter-error");
            }
        });
    }

    private CompletableFuture<Void> scanAllPagesAsync() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            notify(prefixed(Text.literal("Not in guild members screen")), "guildrewardgranter-status");
            return CompletableFuture.completedFuture(null);
        }
        if (!isCurrentMembersScreen(screen)) {
            return CompletableFuture.completedFuture(null);
        }
        if (scanning) {
            return CompletableFuture.completedFuture(null);
        }
        scanning = true;
        long session = screenSession;
        nameToSlot.clear();
        nameToPage.clear();
        currentPage = 0;
        notify(prefixed(Text.literal("Scanning guild members...")), "guildrewardgranter-status");
        CompletableFuture<Integer> forward = scanForward(screen, 0, session);
        return forward.thenComposeAsync(pages -> rewindToFirst(screen, session).thenApply(v -> pages), SeqClient.SCHEDULER).handle((pages, ex) -> {
            scanning = false;
            if (ex != null) {
                SeqClient.error("GuildRewardGranter scan failed", ex);
                if (isCurrentMembersScreen(screen)) {
                    notify(prefixed(Text.literal("Scan failed: " + ex.getMessage())), "guildrewardgranter-status");
                }
            } else {
                int pageCount = pages == null ? 0 : pages;
                if (isCurrentMembersScreen(screen)) {
                    notify(prefixed(Text.literal("Scanned " + pageCount + " page(s), found " + nameToSlot.size() + " members")), "guildrewardgranter-status");
                }
            }
            if (StringUtils.isNotBlank(pendingAutoScroll)) {
                String pending = pendingAutoScroll;
                pendingAutoScroll = "";
                attemptAutoScroll(pending);
            }
            return null;
        });
    }

    private CompletableFuture<Integer> scanForward(GenericContainerScreen screen, int pages, long session) {
        if (!isCurrentMembersScreen(screen) || session != screenSession) {
            scanning = false;
            return CompletableFuture.completedFuture(pages);
        }
        readMembersOnPage(screen);
        if (!clickIfPresent(screen, SLOT_NEXT)) {
            return CompletableFuture.completedFuture(pages + 1);
        }
        currentPage++;
        return delay(pageDelayMs.get()).thenComposeAsync(v -> scanForward(screen, pages + 1, session), SeqClient.SCHEDULER);
    }

    private CompletableFuture<Void> rewindToFirst(GenericContainerScreen screen, long session) {
        if (!isCurrentMembersScreen(screen) || session != screenSession) {
            scanning = false;
            return CompletableFuture.completedFuture(null);
        }
        if (!clickIfPresent(screen, SLOT_PREV)) {
            return CompletableFuture.completedFuture(null);
        }
        currentPage = Math.max(0, currentPage - 1);
        return delay(pageDelayMs.get()).thenComposeAsync(v -> rewindToFirst(screen, session), SeqClient.SCHEDULER);
    }

    private void readMembersOnPage(GenericContainerScreen screen) {
        for (int slotIdx : MEMBER_BOUNDS.getSlots()) {
            Slot slot = screen.getScreenHandler().getSlot(slotIdx);
            if (slot != null && slot.hasStack() && slot.getStack().getItem() == Items.PLAYER_HEAD) {
                Optional<String> nameOpt = extractHeadName(slot);
                if (nameOpt.isPresent()) {
                    String name = nameOpt.get();
                    nameToSlot.put(name, slotIdx);
                    nameToPage.put(name, currentPage);
                }
            }
        }
    }

    private boolean clickVisiblePlayer(String targetName, int hotbarKey, int times) {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            return false;
        }
        if (!isCurrentMembersScreen(screen)) {
            return false;
        }
        String key = normalizeName(targetName);
        for (int slotIdx : MEMBER_BOUNDS.getSlots()) {
            Slot slot = screen.getScreenHandler().getSlot(slotIdx);
            if (slot != null && slot.hasStack() && slot.getStack().getItem() == Items.PLAYER_HEAD) {
                Optional<String> nameOpt = extractHeadName(slot);
                if (nameOpt.isPresent() && nameOpt.get().equals(key)) {
                    performClicksAsync(screen, slotIdx, hotbarKey, times);
                    return true;
                }
            }
        }
        return false;
    }

    private CompletableFuture<Boolean> clickPlayerAsync(String targetName, int hotbarKey, int times) {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            return CompletableFuture.completedFuture(false);
        }
        if (!isCurrentMembersScreen(screen)) {
            return CompletableFuture.completedFuture(false);
        }
        String key = normalizeName(targetName);
        String match = nameToPage.keySet().stream().filter(n -> n.equals(key)).findFirst().orElse(null);
        Integer page = match == null ? null : nameToPage.get(match);
        Integer slot = match == null ? null : nameToSlot.get(match);
        if (page == null || slot == null) {
            notify(prefixed(Text.literal("No entry cached for " + targetName + "; scan found " + nameToSlot.size() + " names")), "guildrewardgranter-status");
            return CompletableFuture.completedFuture(false);
        }
        return navigateToPage(screen, page).thenComposeAsync(success -> {
            if (!success) {
                return CompletableFuture.completedFuture(false);
            }
            Slot s = screen.getScreenHandler().getSlot(slot);
            return s == null ? CompletableFuture.completedFuture(false) : performClicksAsync(screen, slot, hotbarKey, times).thenApply(v -> true);
        }, SeqClient.SCHEDULER);
    }

    private CompletableFuture<Boolean> navigateToPage(GenericContainerScreen screen, int targetPage) {
        if (!isCurrentMembersScreen(screen)) {
            return CompletableFuture.completedFuture(false);
        }
        if (currentPage == targetPage) {
            return CompletableFuture.completedFuture(true);
        }
        if (currentPage < targetPage) {
            if (!clickIfPresent(screen, SLOT_NEXT)) {
                return CompletableFuture.completedFuture(false);
            }
            currentPage++;
            return delay(pageDelayMs.get()).thenComposeAsync(v -> navigateToPage(screen, targetPage), SeqClient.SCHEDULER);
        }
        if (!clickIfPresent(screen, SLOT_PREV)) {
            return CompletableFuture.completedFuture(false);
        }
        currentPage = Math.max(0, currentPage - 1);
        return delay(pageDelayMs.get()).thenComposeAsync(v -> navigateToPage(screen, targetPage), SeqClient.SCHEDULER);
    }

    private CompletableFuture<Void> performClicksAsync(GenericContainerScreen screen, int slotIdx, int hotbarKey, int times) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int i = 0; i < times; i++) {
            chain = chain.thenRunAsync(() -> {
                if (!isCurrentMembersScreen(screen)) {
                    return;
                }
                if (hotbarKey == HOTBAR_EMS && stopDumpDueToNoEms) {
                    return;
                }
                if (mc.interactionManager != null) {
                    mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slotIdx, hotbarKey - 1, SlotActionType.SWAP, mc.player);
                }
            }, SeqClient.SCHEDULER);
            if (i < times - 1) {
                chain = chain.thenComposeAsync(v -> {
                    if (!isCurrentMembersScreen(screen)) {
                        return CompletableFuture.completedFuture(null);
                    }
                    if (hotbarKey == HOTBAR_EMS && stopDumpDueToNoEms) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return delay(clickDelayMs.get());
                }, SeqClient.SCHEDULER);
            }
        }
        return chain;
    }

    private CompletableFuture<Void> delay(long millis) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        SeqClient.SCHEDULER.schedule(() -> f.complete(null), millis, TimeUnit.MILLISECONDS);
        return f;
    }

    private boolean isCurrentMembersScreen(GenericContainerScreen screen) {
        return mc.currentScreen == screen && Models.Container.getCurrentContainer() instanceof GuildMemberListContainer;
    }

    private boolean clickIfPresent(GenericContainerScreen screen, int slotIdx) {
        if (!isCurrentMembersScreen(screen)) {
            return false;
        }
        Slot slot = screen.getScreenHandler().getSlot(slotIdx);
        if (slot == null || !slot.hasStack()) {
            return false;
        }
        if (mc.interactionManager != null) {
            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slotIdx, 0, SlotActionType.PICKUP, mc.player);
        }
        return true;
    }

    private Optional<String> readSearchText() {
        try {
            ContainerSearchFeature searchFeature = Managers.Feature.getFeatureInstance(ContainerSearchFeature.class);
            Field lastSearchWidget = ContainerSearchFeature.class.getDeclaredField("lastSearchWidget");
            lastSearchWidget.setAccessible(true);
            Object widget = lastSearchWidget.get(searchFeature);
            if (widget == null) {
                return Optional.empty();
            }
            Method getText = widget.getClass().getMethod("getTextBoxInput", new Class[0]);
            Object res = getText.invoke(widget, new Object[0]);
            return Optional.ofNullable(res).map(Object::toString);
        } catch (Exception e) {
            SeqClient.warn("Failed to read search text via reflection", e);
            return Optional.empty();
        }
    }

    @Subscribe
    public void onChat(PacketEvent.PacketReceiveEvent event) {
        if (!(event.packet() instanceof GameMessageS2CPacket(Text content, boolean overlay))) return;
        if (content == null) return;
        String msg = content.getString();
        String plain = msg == null ? "" : msg.replaceAll("§.", "").toLowerCase(Locale.ROOT);
        if (plain.contains("guild does not have enough emeralds") || plain.contains("not have enough emeralds to send a reward")) {
            stopDumpDueToNoEms = true;
        }
    }

    private void maybeAutoNavigateSearch() {
        if (!autoScrollEnabled.get()) return;
        String search = normalizeName(readSearchText().orElse(""));
        if (search.equals(lastSearchQuery)) {
            return;
        }
        lastSearchQuery = search;
        if (search.length() < 2) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAutoScrollMs < AUTO_SCROLL_COOLDOWN_MS) {
            return;
        }
        if (scanning) {
            pendingAutoScroll = search;
        } else if (nameToSlot.isEmpty()) {
            pendingAutoScroll = search;
            scanAllPagesAsync();
        } else {
            attemptAutoScroll(search);
        }
    }

    private void attemptAutoScroll(String search) {
        String match = null;
        int count = 0;
        for (String n : nameToSlot.keySet()) {
            if (n.startsWith(search)) {
                match = n;
                count++;
                if (count > 1) {
                    break;
                }
            }
        }
        if (count == 1 && match != null) {
            Integer page = nameToPage.get(match);
            if (page != null && mc.currentScreen instanceof GenericContainerScreen screen) {
                lastAutoScrollMs = System.currentTimeMillis();
                navigateToPage(screen, page);
            }
        }
    }

    private Optional<String> extractHeadName(Slot slot) {
        String display = normalizeName(slot.getStack().getName().getString());
        if (StringUtils.isNotBlank(display)) {
            return Optional.of(display);
        }
        ProfileComponent profile = slot.getStack().get(DataComponentTypes.PROFILE);
        if (profile != null && profile.getGameProfile() != null) {
            String profName = normalizeName(profile.getGameProfile().name());
            if (StringUtils.isNotBlank(profName)) {
                return Optional.of(profName);
            }
        }
        return Optional.empty();
    }

    private boolean pageHasHeads(GenericContainerScreen screen) {
        for (int slotIdx : MEMBER_BOUNDS.getSlots()) {
            Slot slot = screen.getScreenHandler().getSlot(slotIdx);
            if (slot != null && slot.hasStack() && slot.getStack().getItem() == Items.PLAYER_HEAD) {
                return true;
            }
        }
        return false;
    }

    private CompletableFuture<Boolean> attemptGive(String targetName, int hotbarKey, int clicks, boolean allowRescan) {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen) || !isCurrentMembersScreen(screen)) {
            return CompletableFuture.completedFuture(false);
        }
        if (clickVisiblePlayer(targetName, hotbarKey, clicks)) {
            return CompletableFuture.completedFuture(true);
        }
        return clickPlayerAsync(targetName, hotbarKey, clicks).thenCompose(found -> {
            if (found || !allowRescan || scanning) {
                return CompletableFuture.completedFuture(found);
            }
            return scanAllPagesAsync().thenComposeAsync(v -> attemptGive(targetName, hotbarKey, clicks, false), SeqClient.SCHEDULER);
        });
    }

    private String normalizeName(String raw) {
        if (raw == null) {
            return "";
        }
        String stripped = raw.replaceAll("§.", "");
        String stripped2 = stripped.replaceAll("[^A-Za-z0-9_]", "");
        return stripped2.trim().toLowerCase(Locale.ROOT);
    }

    private void renderButton(DrawContext ctx, SimpleButton btn, double mx, double my) {
        boolean hover = btn.contains(mx, my);
        int bg = hover ? 0xFF2E8B57 : 0xDD000000;
        int border = hover ? 0xFFFFFFFF : 0xA0FFFFFF;
        int tx = hover ? 0xFFFFFFFF : 0xE0FFFFFF;
        ctx.fill(btn.x - 1, btn.y - 1, btn.x + btn.width + 1, btn.y + btn.height + 1, border);
        ctx.fill(btn.x, btn.y, btn.x + btn.width, btn.y + btn.height, bg);
        ctx.drawText(mc.textRenderer, btn.label, btn.x + 4, btn.y + ((btn.height - 8) / 2) + (hover ? -1 : 0), tx, false);
    }

    private int getIntField(Object target, String fieldName, int fallback) {
        try {
            Field f = target.getClass().getSuperclass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.getInt(target);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void blurSearchWidget() {
        try {
            ContainerSearchFeature searchFeature = Managers.Feature.getFeatureInstance(ContainerSearchFeature.class);
            Field lastSearchWidget = ContainerSearchFeature.class.getDeclaredField("lastSearchWidget");
            lastSearchWidget.setAccessible(true);
            Object widget = lastSearchWidget.get(searchFeature);
            if (widget == null) return;

            Method getText = findMethod(widget.getClass(), "getTextBoxInput");
            Method setText = findMethod(widget.getClass(), "setTextBoxInput", String.class);
            String saved = getText != null
                    ? Optional.ofNullable(getText.invoke(widget)).map(Object::toString).orElse("")
                    : "";

            Object screen = mc.currentScreen;
            if (screen != null) {
                Method setFocusedTextInput = findMethod(screen.getClass(), "setFocusedTextInput", widget.getClass());
                if (setFocusedTextInput == null) {
                    for (Method m : screen.getClass().getMethods()) {
                        if (m.getName().equals("setFocusedTextInput") && m.getParameterCount() == 1) {
                            setFocusedTextInput = m;
                            break;
                        }
                    }
                }
                if (setFocusedTextInput != null) {
                    setFocusedTextInput.setAccessible(true);
                    setFocusedTextInput.invoke(screen, new Object[]{null});
                }
                if (screen instanceof net.minecraft.client.gui.screen.Screen s) {
                    s.setFocused(null);
                } else {
                    Method setFocused = findMethod(screen.getClass(), "setFocused", Element.class);
                    if (setFocused != null) {
                        setFocused.setAccessible(true);
                        setFocused.invoke(screen, new Object[]{null});
                    }
                }
            }

            if (setText != null && StringUtils.isNotBlank(saved)) {
                setText.setAccessible(true);
                setText.invoke(widget, saved);
            }
        } catch (Exception ignored) {
        }
    }

    private Method findMethod(Class<?> cls, String name, Class<?>... params) {
        Class<?> cur = cls;
        while (cur != null) {
            try {
                return cur.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static String labelForHotbar(int key) {
        return switch (key) {
            case HOTBAR_ASPECT -> "Aspect";
            case HOTBAR_TOME -> "Tome";
            case HOTBAR_EMS -> "Ems";
            default -> "Reward";
        };
    }

    @Subscribe
    public void onMouse(MouseButtonEvent event) {
        if (event.action() != 1) return;
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
        if (!(Models.Container.getCurrentContainer() instanceof GuildMemberListContainer)) return;
        double scale = mc.getWindow().getScaleFactor();
        double mx = mc.mouse.getX() / scale;
        double my = mc.mouse.getY() / scale;
        if (giveAspectBtn != null && giveAspectBtn.contains(mx, my)) { giveAspectBtn.onClick().run(); event.cancel(); return; }
        if (giveTomeBtn != null && giveTomeBtn.contains(mx, my))   { giveTomeBtn.onClick().run(); event.cancel(); return; }
        if (giveEmsBtn != null && giveEmsBtn.contains(mx, my))    { giveEmsBtn.onClick().run(); event.cancel(); return; }
        if (dumpEmsBtn != null && dumpEmsBtn.contains(mx, my))    { dumpEmsBtn.onClick().run(); event.cancel(); }
        if (blurSearchOnClick.get()) {
            blurSearchWidget();
        }
    }

    public record SimpleButton(int x, int y, int width, int height, Text label, Runnable onClick) {
        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + width && my >= y && my <= y + height;
        }
    }
}
