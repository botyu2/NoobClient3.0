package com.guardheatmap.cosmicguardheatmap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class GuardedStateTracker {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATABASE_TYPE = new TypeToken<Map<String, Map<String, ZoneState>>>() { }.getType();
    private static final Path DATABASE_PATH = FabricLoader.getInstance().getConfigDir().resolve("cosmic-guard-heatmap-blocks-v3.json");
    private static final Map<String, Map<String, ZoneState>> DATABASE = loadDatabase();
    private static Map<String, ZoneState> currentBlocks = Map.of();
    private static String currentContext = "";
    private static boolean dirty;
    private static int saveCountdown = 100;

    private GuardedStateTracker() {
    }

    public static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            flush();
            currentContext = "";
            currentBlocks = Map.of();
            return;
        }

        String context = contextKey(client);
        if (!context.equals(currentContext)) {
            flush();
            currentContext = context;
            currentBlocks = DATABASE.computeIfAbsent(context, ignored -> new HashMap<>());
        }

        readZoneState(client).ifPresent(state -> {
            BlockPos floor = findRecordableFloor(client);
            if (floor != null && currentBlocks.put(blockKey(floor), state) != state) {
                dirty = true;
            }
        });

        if (dirty && --saveCountdown <= 0) {
            flush();
        }
    }

    public static Iterable<MeasuredBlock> measuredBlocks() {
        return currentBlocks.entrySet().stream()
            .map(entry -> measuredBlock(entry.getKey(), entry.getValue()))
            .toList();
    }

    public static int measuredBlockCount() {
        return currentBlocks.size();
    }

    public static boolean isRecordableBlock(BlockState state) {
        return !state.isAir();
    }

    public static Optional<ZoneState> readCurrentZoneState(MinecraftClient client) {
        return readZoneState(client);
    }

    private static Optional<ZoneState> readZoneState(MinecraftClient client) {
        Scoreboard scoreboard = client.world.getScoreboard();
        ScoreboardObjective sidebar = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return Optional.empty();
        }

        boolean cosmicSidebar = false;
        boolean safezone = false;
        for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(sidebar)) {
            String display = entry.display() == null ? "" : entry.display().getString();
            Team team = scoreboard.getScoreHolderTeam(entry.owner());
            String decorated = Team.decorateName(team, Text.literal(entry.owner())).getString();
            String line = (entry.name().getString() + " " + display + " " + entry.owner() + " " + decorated)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z]", "");
            if (line.contains("currentzone")) {
                cosmicSidebar = true;
            }
            if (line.contains("safezone")) {
                safezone = true;
            }
            if (line.contains("guardedw")) {
                return Optional.of(ZoneState.WARDEN);
            }
            if (line.contains("guardede")) {
                return Optional.of(ZoneState.ENFORCER);
            }
            if (line.contains("guardedg")) {
                return Optional.of(ZoneState.GUARD);
            }
        }

        if (safezone) {
            return Optional.of(ZoneState.SAFEZONE);
        }
        return cosmicSidebar ? Optional.of(ZoneState.NEUTRAL) : Optional.empty();
    }

    private static String contextKey(MinecraftClient client) {
        String server = client.getCurrentServerEntry() == null ? "singleplayer" : client.getCurrentServerEntry().address;
        return server + "|" + client.world.getRegistryKey().getValue();
    }

    private static String blockKey(BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
    }

    private static BlockPos findRecordableFloor(MinecraftClient client) {
        BlockPos playerPosition = client.player.getBlockPos();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int y = playerPosition.getY(); y >= playerPosition.getY() - 6; y--) {
            mutable.set(playerPosition.getX(), y, playerPosition.getZ());
            if (isRecordableBlock(client.world.getBlockState(mutable))) {
                return mutable.toImmutable();
            }
        }

        return null;
    }

    private static MeasuredBlock measuredBlock(String key, ZoneState state) {
        String[] coordinates = key.split(",", 3);
        return new MeasuredBlock(
            Integer.parseInt(coordinates[0]),
            Integer.parseInt(coordinates[1]),
            Integer.parseInt(coordinates[2]),
            state);
    }

    private static Map<String, Map<String, ZoneState>> loadDatabase() {
        if (!Files.exists(DATABASE_PATH)) {
            return new HashMap<>();
        }

        try (Reader reader = Files.newBufferedReader(DATABASE_PATH)) {
            Map<String, Map<String, ZoneState>> loaded = GSON.fromJson(reader, DATABASE_TYPE);
            return loaded == null ? new HashMap<>() : loaded;
        } catch (IOException | RuntimeException ignored) {
            return new HashMap<>();
        }
    }

    private static void flush() {
        if (!dirty) {
            return;
        }

        try {
            Files.createDirectories(DATABASE_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(DATABASE_PATH)) {
                GSON.toJson(DATABASE, DATABASE_TYPE, writer);
            }
            dirty = false;
            saveCountdown = 100;
        } catch (IOException ignored) {
        }
    }

    public enum ZoneState {
        NEUTRAL,
        GUARD,
        ENFORCER,
        WARDEN,
        SAFEZONE
    }

    public record MeasuredBlock(int x, int y, int z, ZoneState state) {
    }
}
