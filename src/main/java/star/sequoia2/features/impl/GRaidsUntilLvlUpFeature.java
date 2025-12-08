package star.sequoia2.features.impl;

import com.collarmc.pounce.Subscribe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import star.sequoia2.accessors.EventBusAccessor;
import star.sequoia2.accessors.TeXParserAccessor;
import star.sequoia2.client.types.text.StyledText;
import star.sequoia2.events.PacketEvent;
import star.sequoia2.features.ToggleFeature;
import star.sequoia2.utils.chatparser.GuildRaidParser;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static star.sequoia2.features.impl.ws.ChatHookFeature.remove_multiline;


public class GRaidsUntilLvlUpFeature extends ToggleFeature implements TeXParserAccessor, EventBusAccessor {
    public static volatile Long current = 0L;
    public static volatile Long needed = 0L;

    public static final BlockingQueue<star.sequoia2.client.types.text.StyledText> raidMessageQueue = new LinkedBlockingQueue<>();
    private static CompletableFuture<Void> raidWorker = null;

    private boolean suppressNextGuStats = false;
    private boolean expectGuStats = false;

    private static final Pattern GUILD_RAID_BLOCK = Pattern.compile("§b finished");
    private static final Pattern OTHER_GUILD_RAID_BLOCK = Pattern.compile("§b §bfinished");

    private static final Pattern SECTION_CODES =
            Pattern.compile("§[0-9a-fk-or<>]", Pattern.CASE_INSENSITIVE);

    public GRaidsUntilLvlUpFeature() {
        super("Guild raid completion levelup progress", "Shows you the amount of graids that are needed to reach the next guild level.", true);
    }

    public static int calculateNeededRaids(long current, long needed, long xpPerRaid) {
        long missing = needed - current;

        if (missing <= 0) return 0;

        return (int) Math.ceil((double) missing / xpPerRaid);
    }

    public static void startRaidWorker() {
        if (raidWorker != null && !raidWorker.isDone()) return;

        raidWorker = CompletableFuture.runAsync(() -> {
            try {
                while (!raidMessageQueue.isEmpty()) {
                    StyledText message = raidMessageQueue.take();
                    System.out.println(message);
                    long start = System.currentTimeMillis();

                    while (GRaidsUntilLvlUpFeature.needed == 0L) {
                        Thread.sleep(100);

                        if (System.currentTimeMillis() - start > 15000) {
                            break;
                        }
                    }

                    String plain = SECTION_CODES.matcher(message.getString()).replaceAll("");
                    if (plain.isEmpty()) {
                        return;
                    }

                    // split into header (players) + tail (rewards) at the first "finished"
                    int finIdx = plain.indexOf("finished");
                    if (finIdx < 0) {
                        return; // not a raid block for any reason
                    }
                    String tail = plain.substring(finIdx);

                    long xp = GuildRaidParser.parseScaled(
                            GuildRaidParser.matchGroup(Pattern.compile("(?i)\\+(\\d+)([kmb])?\\s+Guild\\s+Experience"), tail, 1),
                            GuildRaidParser.matchGroup(Pattern.compile("(?i)\\+(\\d+)([kmb])?\\s+Guild\\s+Experience"), tail, 2));

                    star.sequoia2.client.types.text.StyledText out = message.append((needed == 0L ? "" : "§3. §b" + GRaidsUntilLvlUpFeature.calculateNeededRaids(current, needed, xp) + " guild raids left to level up."));

                    MinecraftClient.getInstance().execute(() -> {
                        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(out.getComponent());
                        GRaidsUntilLvlUpFeature.current = 0L;
                        GRaidsUntilLvlUpFeature.needed = 0L;
                    });
                }

            } catch (InterruptedException ignored) { }
        });
    }

    public static boolean isGuStatsHeader(Text text) {
        Style s = text.getStyle();
        if (!s.isBold()) return false;
        if (s.getColor() != TextColor.fromFormatting(Formatting.GOLD)) return false;
        if (!(text.getContent() instanceof PlainTextContent.Literal literal)) return false;

        String t = literal.string();
        return !t.isEmpty();
    }

    @Subscribe
    public void onChatMessage(PacketEvent.PacketReceiveEvent event) {
        if (!(event.packet() instanceof GameMessageS2CPacket(Text content, boolean overlay))) return;
        if (content == null || overlay) return;
        if(!features().getIfActive(GRaidsUntilLvlUpFeature.class).map(GRaidsUntilLvlUpFeature::isActive).orElse(false)) {
            return;
        }

        String raw = content.getString();
        System.out.println("raw: " + raw + " content: " + content + " " + raw.isEmpty());
        if(expectGuStats && (raw.isBlank() || content.toString().equals("empty"))) {
            System.out.println(event);
            event.cancel();
            return;
        }
        if(expectGuStats && GRaidsUntilLvlUpFeature.isGuStatsHeader(content)) {
            suppressNextGuStats = true;
            expectGuStats = false;
        }
        if (suppressNextGuStats) {
            if(raw.contains("Total Members:")) {
                suppressNextGuStats = false;
            }

            Pattern xpPattern = Pattern.compile(".*Needed XP:.*?(\\d+).*?/(\\d+).*");
            Matcher m = xpPattern.matcher(raw);

            if (m.matches()) {
                String currentXp = m.group(1);
                String requiredXp = m.group(2);
                System.out.println("Parsed XP: " + currentXp + "/" + requiredXp);

                try {
                    GRaidsUntilLvlUpFeature.current = Long.parseLong(currentXp);
                    GRaidsUntilLvlUpFeature.needed = Long.parseLong(requiredXp);
                } catch (Exception ignored) {}
            }

            event.cancel();
            return;
        }


        StyledText styledText = StyledText.fromComponent(content);
        String tex = teXParser().toTeX(styledText.stripAlignment());

        tex = remove_multiline(tex);

        if (GUILD_RAID_BLOCK.matcher(tex).find() || OTHER_GUILD_RAID_BLOCK.matcher(tex).find()) {
            if(MinecraftClient.getInstance().getNetworkHandler() == null) return;
            MinecraftClient.getInstance().getNetworkHandler().sendChatCommand("gu stats");
            expectGuStats = true;
            event.cancel();

            GRaidsUntilLvlUpFeature.raidMessageQueue.add(styledText);
            GRaidsUntilLvlUpFeature.startRaidWorker();
        }
    }
}
