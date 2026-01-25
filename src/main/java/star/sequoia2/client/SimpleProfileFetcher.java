package star.sequoia2.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.util.ApiServices;
import net.minecraft.util.Util;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static star.sequoia2.client.SeqClient.mc;

public class SimpleProfileFetcher {
    private static final int MAX_CACHE_ENTRIES = 512;
    private static final ConcurrentHashMap<UUID, CompletableFuture<Optional<GameProfile>>> BY_ID = new ConcurrentHashMap<>();
    private static final CompletableFuture<Optional<GameProfile>> EMPTY = CompletableFuture.completedFuture(Optional.empty());

    ApiServices services;

    public SimpleProfileFetcher() {
        services = mc.getApiServices();
    }

    public CompletableFuture<Optional<GameProfile>> fetchByUUID(UUID id) {
        if (services == null || id == null)
            return EMPTY;
        CompletableFuture<Optional<GameProfile>> future = BY_ID.computeIfAbsent(id, uuid ->
                CompletableFuture.supplyAsync(() -> {
                            ProfileResult res = services.sessionService().fetchProfile(uuid, true);
                            return Optional.ofNullable(res).map(ProfileResult::profile);
                        }, Util.getMainWorkerExecutor())
                        .whenComplete((profile, throwable) -> trimCache()));
        return future;
    }

    private static void trimCache() {
        if (BY_ID.size() <= MAX_CACHE_ENTRIES) return;
        BY_ID.keySet().iterator().forEachRemaining(key -> {
            if (BY_ID.size() <= MAX_CACHE_ENTRIES) return;
            BY_ID.remove(key);
        });
    }
}
