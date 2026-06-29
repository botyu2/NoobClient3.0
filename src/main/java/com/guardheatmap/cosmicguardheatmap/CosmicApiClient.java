package com.guardheatmap.cosmicguardheatmap;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class CosmicApiClient {
    private static final String CLIENT_ID = "client_mqo12agnhaofbxsgdk";
    private static final String MOD_ID = "noobclient6-7";
    private static final String MOD_VERSION = "1.0.0";
    private static final String MINECRAFT_VERSION = "1.21.11";
    private static final String ENCHANT_PROC_HOOK = "player.enchant_proc";
    private static final String GUARDS_SNAPSHOT_HOOK = "server.guards.snapshot.changed";
    private static final String ENCHANT_PROC_SCOPE = "hooks.player.enchant_proc:read";
    private static final String GUARDS_SCOPE = "server.guards:read";
    private static final Path INSTALL_ID_PATH = FabricLoader.getInstance().getConfigDir()
        .resolve("noobclient-cosmic-install-id.txt");
    private static final Gson GSON = new Gson();
    private static final int RETRY_TICKS = 20;

    private static EnchantProcTracker procTracker;
    private static String installId;
    private static String sessionId;
    private static Set<String> allowedScopes = Set.of();
    private static Set<String> allowedHooks = Set.of();
    private static boolean connected;
    private static boolean helloSent;
    private static int retryCountdown;

    private CosmicApiClient() {
    }

    public static void init(EnchantProcTracker tracker) {
        procTracker = tracker;
        installId = loadInstallId();
        PayloadTypeRegistry.playC2S().register(CosmicApiPayload.ID, CosmicApiPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CosmicApiPayload.ID, CosmicApiPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(CosmicApiPayload.ID, (payload, context) ->
            handleIncoming(payload.json()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            connected = true;
            helloSent = false;
            retryCountdown = 0;
            sessionId = null;
            allowedScopes = Set.of();
            allowedHooks = Set.of();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static void tick(MinecraftClient client) {
        if (!connected || helloSent || client.getNetworkHandler() == null) {
            return;
        }
        if (--retryCountdown > 0) {
            return;
        }
        retryCountdown = RETRY_TICKS;
        if (!ClientPlayNetworking.canSend(CosmicApiPayload.ID)) {
            return;
        }
        ClientPlayNetworking.send(new CosmicApiPayload(GSON.toJson(createHello())));
        helloSent = true;
    }

    public static boolean hasSession() {
        return sessionId != null && !sessionId.isBlank();
    }

    public static Set<String> allowedScopes() {
        return allowedScopes;
    }

    public static Set<String> allowedHooks() {
        return allowedHooks;
    }

    private static JsonObject createHello() {
        JsonObject hello = new JsonObject();
        hello.addProperty("v", 1);
        hello.addProperty("kind", "client_hello");
        hello.addProperty("clientId", CLIENT_ID);
        hello.addProperty("modId", MOD_ID);
        hello.addProperty("installId", installId);
        hello.addProperty("modVersion", MOD_VERSION);
        hello.addProperty("minecraftVersion", MINECRAFT_VERSION);
        JsonArray hooks = new JsonArray();
        hooks.add(ENCHANT_PROC_HOOK);
        hooks.add(GUARDS_SNAPSHOT_HOOK);
        hello.add("requestedHooks", hooks);
        JsonArray scopes = new JsonArray();
        scopes.add(ENCHANT_PROC_SCOPE);
        scopes.add(GUARDS_SCOPE);
        hello.add("requestedScopes", scopes);
        return hello;
    }

    private static void handleIncoming(String rawJson) {
        try {
            JsonElement parsed = JsonParser.parseString(rawJson);
            if (!parsed.isJsonObject()) {
                return;
            }
            JsonObject message = parsed.getAsJsonObject();
            if (message.has("sessionId") && !isHookEvent(message)) {
                handleSession(message);
                return;
            }
            if (isHookEvent(message)) {
                handleHook(message);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static boolean isHookEvent(JsonObject message) {
        return "event".equals(readString(message, "type"))
            && "hook".equals(readString(message, "event"));
    }

    private static void handleSession(JsonObject message) {
        sessionId = readString(message, "sessionId");
        allowedScopes = readStringSet(message.get("allowedScopes"));
        allowedHooks = readStringSet(message.get("allowedHooks"));
    }

    private static void handleHook(JsonObject message) {
        String eventType = readString(message, "eventType");
        if (eventType.isBlank() && message.has("payload") && message.get("payload").isJsonObject()) {
            eventType = readString(message.getAsJsonObject("payload"), "eventType");
        }
        if (ENCHANT_PROC_HOOK.equals(eventType) && procTracker != null) {
            procTracker.handleApiEvent(message);
        } else if (GUARDS_SNAPSHOT_HOOK.equals(eventType)) {
            GuardRangeMath.requestRefresh();
        }
    }

    private static Set<String> readStringSet(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                values.add(entry.getAsString());
            }
        }
        return Set.copyOf(values);
    }

    private static String readString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static String loadInstallId() {
        try {
            if (Files.exists(INSTALL_ID_PATH)) {
                String existing = Files.readString(INSTALL_ID_PATH).trim();
                if (!existing.isBlank()) {
                    return existing;
                }
            }
            Files.createDirectories(INSTALL_ID_PATH.getParent());
            String generated = "ins_" + UUID.randomUUID().toString().replace("-", "");
            Files.writeString(
                INSTALL_ID_PATH,
                generated,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
            return generated;
        } catch (IOException ignored) {
            return "ins_" + UUID.randomUUID().toString().replace("-", "");
        }
    }

    private static void reset() {
        connected = false;
        helloSent = false;
        retryCountdown = 0;
        sessionId = null;
        allowedScopes = Set.of();
        allowedHooks = Set.of();
    }

    private record CosmicApiPayload(String json) implements CustomPayload {
        private static final CustomPayload.Id<CosmicApiPayload> ID =
            new CustomPayload.Id<>(Identifier.of("cosmicapi", "main"));
        private static final PacketCodec<RegistryByteBuf, CosmicApiPayload> CODEC = CustomPayload.codecOf(
            (payload, buffer) -> buffer.writeBytes(payload.json().getBytes(StandardCharsets.UTF_8)),
            buffer -> {
                byte[] bytes = new byte[buffer.readableBytes()];
                buffer.readBytes(bytes);
                return new CosmicApiPayload(new String(bytes, StandardCharsets.UTF_8));
            });

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
