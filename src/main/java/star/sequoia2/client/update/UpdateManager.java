package star.sequoia2.client.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import star.sequoia2.accessors.NotificationsAccessor;
import star.sequoia2.client.SeqClient;
import star.sequoia2.http.HttpClients;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UpdateManager {

    private static final String RELEASES_API = "https://api.github.com/repos/SequoiaWynncraft/sequoia-mod-rewritten/releases";

    private static final AtomicBoolean checking = new AtomicBoolean(false);
    private static final AtomicBoolean installing = new AtomicBoolean(false);
    private static final AtomicBoolean autoCheckQueued = new AtomicBoolean(false);
    private static final AtomicBoolean autoCheckCompleted = new AtomicBoolean(false);

    private static volatile ReleaseInfo cachedRelease;
    private static volatile UpdateChannel activeChannel = UpdateChannel.STABLE;
    private static final UpdateState UPDATE_STATE = UpdateState.load();
    private static final NotificationsAccessor NOTIFIER = new NotificationsAccessor() {};

    private UpdateManager() {}

    public static void setChannel(UpdateChannel channel) {
        if (channel == null) channel = UpdateChannel.STABLE;
        activeChannel = channel;
        cachedRelease = null;
    }

    public static UpdateChannel getChannel() {
        return activeChannel;
    }

    public static void scheduleAutomaticCheck() {
        if (SeqClient.getModJar() == null) {
            SeqClient.debug("Skipping update check: mod jar not resolved (likely dev environment).");
            return;
        }
        processAppliedMarker();
        cleanupBackup();
        if (!autoCheckQueued.compareAndSet(false, true)) return;
        SeqClient.SCHEDULER.schedule(UpdateManager::waitForWorldThenCheck, 5, TimeUnit.SECONDS);
    }

    private static void waitForWorldThenCheck() {
        if (autoCheckCompleted.get()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            SeqClient.SCHEDULER.schedule(UpdateManager::waitForWorldThenCheck, 3, TimeUnit.SECONDS);
            return;
        }
        autoCheckCompleted.set(true);
        notifyUser(Text.literal("Checking for " + activeChannel.displayName() + " updates...").formatted(Formatting.GRAY));
        checkForUpdates(false);
    }

    public static void checkForUpdates(boolean manualTrigger) {
        if (!checking.compareAndSet(false, true)) {
            if (manualTrigger) {
                notifyUser(Text.literal("Already checking for updates."));
            } else {
                SeqClient.SCHEDULER.schedule(() -> checkForUpdates(false), 2, TimeUnit.SECONDS);
            }
            return;
        }

        CompletableFuture
                .supplyAsync(UpdateManager::fetchLatestRelease)
                .whenComplete((opt, throwable) -> {
                    checking.set(false);
                    if (throwable != null) {
                        if (manualTrigger) {
                            notifyUser(Text.literal("Update check failed: " + throwable.getMessage()).formatted(Formatting.RED));
                        }
                        return;
                    }
                    opt.ifPresent(release -> {
                        cachedRelease = release;
                        if (isNewerThanLocal(release)) {
                            announceUpdate(release);
                        } else {
                            Formatting color = manualTrigger ? Formatting.GREEN : Formatting.DARK_GREEN;
                            String sig = (manualTrigger ? "manual" : "auto") + "-uptodate-" + release.channel().name() + "-" + release.displayVersion();
                            notifyUser(Text.literal(release.channel().displayName() + " channel is up to date (" + release.displayVersion() + ").")
                                            .formatted(color),
                                    sig);
                        }
                    });
                    if (opt.isEmpty() && manualTrigger) {
                        notifyUser(Text.literal("No release information available.").formatted(Formatting.YELLOW));
                    }
                });
    }

    public static Optional<ReleaseInfo> getCachedRelease() {
        ReleaseInfo release = cachedRelease;
        if (release == null || release.channel() != activeChannel) {
            return Optional.empty();
        }
        return Optional.of(release);
    }

    private static Optional<ReleaseInfo> fetchLatestRelease() {
        JsonArray releases = HttpClients.UPDATE_API.getJson(RELEASES_API, JsonArray.class);
        if (releases == null) {
            return Optional.empty();
        }

        UpdateChannel channel = activeChannel;
        for (JsonElement element : releases) {
            if (!element.isJsonObject()) continue;
            JsonObject json = element.getAsJsonObject();
            String tag = json.has("tag_name") ? json.get("tag_name").getAsString() : "";
            if (!tag.equals(channel.tagPrefix())) continue;
            boolean prerelease = json.has("prerelease") && json.get("prerelease").getAsBoolean();
            if (channel.requiresPrerelease() != prerelease) continue;
            String downloadUrl = extractDownloadUrl(json.getAsJsonArray("assets"), channel.assetName());
            if (downloadUrl == null || downloadUrl.isBlank()) {
                continue;
            }
            String name = json.has("name") ? json.get("name").getAsString() : tag;
            String changelog = json.has("body") ? json.get("body").getAsString() : "";
            String htmlUrl = json.has("html_url") ? json.get("html_url").getAsString() : "";
            String published = json.has("published_at") ? json.get("published_at").getAsString() : "";
            return Optional.of(new ReleaseInfo(tag, name, downloadUrl, htmlUrl, changelog, published, channel));
        }
        return Optional.empty();
    }

    private static String extractDownloadUrl(JsonArray assets, String preferredName) {
        if (assets == null) return null;
        String fallback = null;
        for (JsonElement element : assets) {
            if (!element.isJsonObject()) continue;
            JsonObject asset = element.getAsJsonObject();
            String name = asset.has("name") ? asset.get("name").getAsString() : "";
            if (!name.endsWith(".jar")) continue;
            if (preferredName != null && !preferredName.isBlank() && name.equalsIgnoreCase(preferredName)) {
                if (asset.has("browser_download_url")) {
                    return asset.get("browser_download_url").getAsString();
                }
            }
            if (asset.has("browser_download_url")) {
                fallback = asset.get("browser_download_url").getAsString();
            }
        }
        return fallback;
    }

    private static boolean isNewerThanLocal(ReleaseInfo release) {
        if (release == null || release.tag() == null) return false;
        String tag = release.tag();
        String local = SeqClient.getVersion();
        String storedTag = UPDATE_STATE.lastInstalledTag(release.channel());
        String storedSignature = UPDATE_STATE.lastInstalledSignature(release.channel());
        String currentSignature = release.signature();
        if (tag.equalsIgnoreCase(local)) return false;
        if (storedSignature != null) {
            return !currentSignature.equals(storedSignature);
        }
        if (storedTag != null && tag.equalsIgnoreCase(storedTag)) {
            return true;
        }
        return storedTag == null || !tag.equalsIgnoreCase(storedTag);
    }

    private static void announceUpdate(ReleaseInfo release) {
        MutableText text = Text.literal(release.channel().displayName() + " update available: ")
                .formatted(Formatting.GOLD)
                .append(Text.literal(release.displayVersion()).formatted(Formatting.AQUA))
                .append(Text.literal(" – click to install").formatted(Formatting.GRAY))
                .styled(style -> style
                        .withClickEvent(new ClickEvent.RunCommand("/sequpdate install"))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Text.literal("Download and install the latest Sequoia build"))));

        notifyUser(text);
    }

    public static void installLatest(FabricClientCommandSourceBridge source) {
        if (!installing.compareAndSet(false, true)) {
            source.reply("An installation is already in progress.", Formatting.YELLOW);
            return;
        }

        ReleaseInfo release = cachedRelease;
        if (release == null) {
            installing.set(false);
            source.reply("No release information cached. Run /sequpdate first.", Formatting.RED);
            return;
        }

        if (!isNewerThanLocal(release)) {
            installing.set(false);
            source.reply("You're already on " + release.displayVersion() + ".", Formatting.GREEN);
            return;
        }

        if (SeqClient.getModJar() == null) {
            installing.set(false);
            source.reply("Unable to determine mod jar location. Automatic install is unavailable in this environment.", Formatting.RED);
            return;
        }

        Path modJarPath = SeqClient.getModJar().toPath();
        Path modsDir = modJarPath.getParent();

        source.reply("Downloading " + release.channel().displayName() + " build " + release.displayVersion() + "...", Formatting.AQUA);

        CompletableFuture
                .runAsync(() -> downloadAndScheduleInstall(modJarPath, modsDir, release))
                .whenComplete((unused, throwable) -> {
                    installing.set(false);
                    if (throwable != null) {
                        source.reply("Update failed: " + throwable.getMessage(), Formatting.RED);
                    } else {
                        source.reply("Update downloaded. It will be applied on game exit.", Formatting.GREEN);
                    }
                });
    }

    public static void forceInstall(ReleaseInfo release, FabricClientCommandSourceBridge source) {
        if (!installing.compareAndSet(false, true)) {
            source.reply("An installation is already in progress.", Formatting.YELLOW);
            return;
        }

        if (release == null) {
            installing.set(false);
            source.reply("No release information available. Run /sequpdate first.", Formatting.RED);
            return;
        }

        if (SeqClient.getModJar() == null) {
            installing.set(false);
            source.reply("Unable to determine mod jar location. Automatic install is unavailable in this environment.", Formatting.RED);
            return;
        }

        Path modJarPath = SeqClient.getModJar().toPath();
        Path modsDir = modJarPath.getParent();

        source.reply("Forcing download of " + release.channel().displayName() + " build " + release.displayVersion() + "...", Formatting.AQUA);

        CompletableFuture
                .runAsync(() -> downloadAndScheduleInstall(modJarPath, modsDir, release, true))
                .whenComplete((unused, throwable) -> {
                    installing.set(false);
                    if (throwable != null) {
                        source.reply("Update failed: " + throwable.getMessage(), Formatting.RED);
                    } else {
                        source.reply("Update downloaded (forced). It will be applied on game exit.", Formatting.GREEN);
                    }
                });
    }

    private static final AtomicBoolean shutdownHookAdded = new AtomicBoolean(false);

    private static void downloadAndScheduleInstall(Path modJarPath, Path modsDir, ReleaseInfo release) {
        downloadAndScheduleInstall(modJarPath, modsDir, release, false);
    }

    private static void downloadAndScheduleInstall(Path modJarPath, Path modsDir, ReleaseInfo release, boolean forceHelper) {
        Path temp = null;
        try {
            Path updatesDir = SeqClient.getModStorageDir("updates").toPath();
            Files.createDirectories(updatesDir);
            Path pending = updatesDir.resolve("seq-update.jar");
            Path meta = updatesDir.resolve("seq-update.meta");

            temp = Files.createTempFile(updatesDir, "seq-update", ".jar");
            downloadFile(release.downloadUrl(), temp);

            Files.move(temp, pending, StandardCopyOption.REPLACE_EXISTING);
            writeMeta(meta, release);
            registerShutdownHook(modJarPath, pending, meta, release, forceHelper);
            SeqClient.info("Update downloaded to " + pending + "; will install on shutdown.");
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {}
            }
        }
    }

    private static void registerShutdownHook(Path modJarPath, Path pending, Path meta, ReleaseInfo release) {
        registerShutdownHook(modJarPath, pending, meta, release, false);
    }

    private static void registerShutdownHook(Path modJarPath, Path pending, Path meta, ReleaseInfo release, boolean forceHelper) {
        if (!shutdownHookAdded.compareAndSet(false, true)) return;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (!Files.exists(pending)) {
                    SeqClient.warn("Pending update not found; skipping install.");
                    return;
                }
                if (isWindows() || forceHelper) {
                    launchWindowsHelper(modJarPath, pending, meta);
                    return;
                }
                Path backup = modJarPath.resolveSibling(modJarPath.getFileName().toString() + ".bak");
                try {
                    Files.copy(modJarPath, backup, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    SeqClient.warn("Could not create backup for " + modJarPath);
                }

                Files.copy(pending, modJarPath, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(pending);
                UPDATE_STATE.save(release.channel(), release.tag(), release.signature());
                SeqClient.info("Applied Sequoia update " + release.displayVersion() + " on shutdown.");

                try {
                    Files.deleteIfExists(backup);
                } catch (IOException ignored) {
                    SeqClient.warn("Failed to delete backup " + backup);
                }
            } catch (Exception ex) {
                SeqClient.warn("Failed to apply pending update on shutdown: " + ex.getMessage(), ex);
            }
        }, "SeqUpdateShutdownHook"));
    }

    private static void launchWindowsHelper(Path modJarPath, Path pending, Path meta) {
        try {
            Path updatesDir = pending.getParent();
            if (updatesDir == null) return;
            Path applied = updatesDir.resolve("seq-update.applied");
            Files.deleteIfExists(applied);

            Path helperJar = updatesDir.resolve("seq-update-helper.jar");
            try {
                Files.copy(modJarPath, helperJar, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                SeqClient.warn("Failed to prepare helper jar: " + ex.getMessage(), ex);
                return;
            }

            String javaExe = resolveJavaExecutable();
            ProcessBuilder pb = new ProcessBuilder(
                    javaExe,
                    "-cp",
                    helperJar.toString(),
                    UpdateApplier.class.getName(),
                    modJarPath.toString(),
                    pending.toString(),
                    meta.toString(),
                    applied.toString(),
                    helperJar.toString()
            );
            pb.inheritIO().start();
            SeqClient.info("Launched external update helper to swap jar after exit: " + helperJar);
        } catch (Exception ex) {
            SeqClient.warn("Failed to launch Windows update helper: " + ex.getMessage(), ex);
        }
    }

    private static String resolveJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            Path javaBin = Path.of(javaHome, "bin", "java.exe");
            if (Files.exists(javaBin)) {
                return javaBin.toString();
            }
        }
        return "java";
    }

    private static void writeMeta(Path meta, ReleaseInfo release) {
        try {
            String content = release.channel().name() + "\n" + release.tag() + "\n" + release.signature();
            Files.writeString(meta, content);
        } catch (IOException e) {
            SeqClient.warn("Failed to write update metadata: " + e.getMessage(), e);
        }
    }

    private static void downloadFile(String downloadUrl, Path target) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(500_000);
        connection.setRequestProperty("Accept", "application/octet-stream");
        int status = connection.getResponseCode();
        if (status >= 400) {
            throw new IOException("HTTP " + status + " while downloading update");
        }

        try (InputStream in = connection.getInputStream();
             OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void notifyUser(Text text) {
        notifyUser(text, text == null ? "" : text.getString());
    }

    private static void notifyUser(Text text, String signature) {
        NOTIFIER.notify(text, signature);
    }

    private static void cleanupBackup() {
        if (SeqClient.getModJar() == null) return;
        Path backup = SeqClient.getModJar().toPath().resolveSibling(SeqClient.getModJar().getName() + ".bak");
        try {
            Files.deleteIfExists(backup);
        } catch (IOException ignored) {}
    }

    private static void processAppliedMarker() {
        if (SeqClient.getModJar() == null) return;
        Path updatesDir = SeqClient.getModStorageDir("updates").toPath();
        Path applied = updatesDir.resolve("seq-update.applied");
        if (!Files.exists(applied)) return;
        try {
            var lines = Files.readAllLines(applied);
            if (lines.size() >= 3) {
                String channelName = lines.get(0).trim();
                String tag = lines.get(1).trim();
                String signature = lines.get(2).trim();
                UpdateChannel channel;
                try {
                    channel = UpdateChannel.valueOf(channelName);
                } catch (IllegalArgumentException ex) {
                    channel = UpdateChannel.STABLE;
                }
                UPDATE_STATE.save(channel, tag, signature);
                SeqClient.info("Detected completed update (" + channel.displayName() + " " + tag + ") applied externally.");
                NOTIFIER.notify(Text.literal("Applied update: " + channel.displayName() + " " + tag + " (external helper)").formatted(Formatting.GREEN));
            }
        } catch (Exception ex) {
            SeqClient.warn("Failed to read applied update marker: " + ex.getMessage(), ex);
        } finally {
            try {
                Files.deleteIfExists(applied);
            } catch (IOException ignored) {}
        }
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    public interface FabricClientCommandSourceBridge {
        void reply(String message, Formatting formatting);
    }
}
