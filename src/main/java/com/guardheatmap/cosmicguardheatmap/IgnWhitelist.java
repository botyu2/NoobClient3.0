package com.guardheatmap.cosmicguardheatmap;

import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class IgnWhitelist {
    private static final URI WHITELIST_URI = URI.create("https://pastebin.com/raw/MB3veEjr");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private static final long REFRESH_INTERVAL_MS = 30_000L;
    private static final long RETRY_INTERVAL_MS = 5_000L;
    private static final Object REFRESH_LOCK = new Object();
    private static volatile Set<String> whitelistedIgns = Set.of();
    private static volatile long nextRefreshAtMs;
    private static volatile boolean refreshInFlight;

    private IgnWhitelist() {
    }

    public static void refreshNow() {
        nextRefreshAtMs = 0L;
        refreshIfNeeded();
    }

    public static boolean isCurrentPlayerWhitelisted(MinecraftClient client) {
        refreshIfNeeded();
        if (client.player == null) {
            return false;
        }
        return whitelistedIgns.contains(client.player.getName().getString().toLowerCase(Locale.ROOT));
    }

    private static void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        if (refreshInFlight || now < nextRefreshAtMs) {
            return;
        }

        synchronized (REFRESH_LOCK) {
            now = System.currentTimeMillis();
            if (refreshInFlight || now < nextRefreshAtMs) {
                return;
            }
            refreshInFlight = true;
            nextRefreshAtMs = now + RETRY_INTERVAL_MS;
        }

        HttpRequest request = HttpRequest.newBuilder(WHITELIST_URI)
            .timeout(Duration.ofSeconds(8))
            .GET()
            .build();
        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete((response, throwable) -> {
                try {
                    if (throwable == null && response.statusCode() >= 200 && response.statusCode() < 300) {
                        whitelistedIgns = parseCsv(response.body());
                        nextRefreshAtMs = System.currentTimeMillis() + REFRESH_INTERVAL_MS;
                    } else {
                        nextRefreshAtMs = System.currentTimeMillis() + RETRY_INTERVAL_MS;
                    }
                } finally {
                    refreshInFlight = false;
                }
            });
    }

    private static Set<String> parseCsv(String csv) {
        return Collections.unmodifiableSet(Arrays.stream(csv.split(","))
            .map(name -> name.trim().toLowerCase(Locale.ROOT))
            .filter(name -> !name.isEmpty())
            .collect(Collectors.toSet()));
    }
}
