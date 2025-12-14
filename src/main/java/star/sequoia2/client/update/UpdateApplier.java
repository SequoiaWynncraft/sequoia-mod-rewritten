package star.sequoia2.client.update;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Small helper invoked in a separate JVM on Windows to replace the mod jar after exit.
 * Arguments: modJar pending meta applied helperJar
 */
public final class UpdateApplier {

    private UpdateApplier() {}

    public static void main(String[] args) {
        if (args.length < 4) return;
        Path modJar = Path.of(args[0]);
        Path pending = Path.of(args[1]);
        Path meta = Path.of(args[2]);
        Path applied = Path.of(args[3]);
        Path helper = args.length >= 5 ? Path.of(args[4]) : null;

        boolean success = applyWithRetries(modJar, pending);
        if (success) {
            persistMeta(meta, applied);
            deleteQuietly(pending);
            deleteQuietly(modJar.resolveSibling(modJar.getFileName().toString() + ".bak"));
        }
        if (helper != null) {
            deleteQuietly(helper);
        }
    }

    private static boolean applyWithRetries(Path modJar, Path pending) {
        for (int i = 0; i < 40; i++) {
            if (copyReplace(pending, modJar)) {
                return true;
            }
            sleep(1500);
        }
        return false;
    }

    private static boolean copyReplace(Path src, Path dst) {
        try {
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void persistMeta(Path meta, Path applied) {
        try {
            if (Files.exists(meta)) {
                Files.copy(meta, applied, StandardCopyOption.REPLACE_EXISTING);
                deleteQuietly(meta);
            }
        } catch (Exception ignored) {
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
