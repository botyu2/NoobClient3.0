package com.guardheatmap.cosmicguardheatmap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CosmicForensics {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Path CAPTURE_PATH = FabricLoader.getInstance().getConfigDir()
        .resolve("cosmic-guard-forensics.jsonl");
    private static final int CAPTURE_INTERVAL_TICKS = 5;
    private static int countdown;
    private static int capturedSamples;

    private CosmicForensics() {
    }

    public static void tick(MinecraftClient client) {
        if (!GuardHeatmapConfig.isEnabled() || client.world == null || client.player == null) {
            countdown = 0;
            return;
        }
        if (--countdown > 0) {
            return;
        }
        countdown = CAPTURE_INTERVAL_TICKS;

        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("time", System.currentTimeMillis());
        sample.put("player", List.of(client.player.getX(), client.player.getY(), client.player.getZ()));
        sample.put("scoreboard", scoreboardLines(client));
        sample.put("entities", nearbyEntities(client));

        try {
            Files.createDirectories(CAPTURE_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(
                CAPTURE_PATH,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
                GSON.toJson(sample, writer);
                writer.write(System.lineSeparator());
                capturedSamples++;
            }
        } catch (IOException ignored) {
        }
    }

    public static int capturedSamples() {
        return capturedSamples;
    }

    public static Path capturePath() {
        return CAPTURE_PATH;
    }

    private static List<String> scoreboardLines(MinecraftClient client) {
        Scoreboard scoreboard = client.world.getScoreboard();
        ScoreboardObjective sidebar = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(sidebar)) {
            Team team = scoreboard.getScoreHolderTeam(entry.owner());
            lines.add(Team.decorateName(team, Text.literal(entry.owner())).getString());
        }
        return lines;
    }

    private static List<Map<String, Object>> nearbyEntities(MinecraftClient client) {
        List<Map<String, Object>> entities = new ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player || entity.squaredDistanceTo(client.player) > 80.0D * 80.0D) {
                continue;
            }

            String name = entity.getName().getString();
            String display = entity.getDisplayName().getString();
            String combined = (name + " " + display).toLowerCase(Locale.ROOT);
            if (!combined.contains("guard")
                && !combined.contains("warden")
                && !combined.contains("enforcer")
                && !combined.contains("100")
                && !entity.hasCustomName()) {
                continue;
            }

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("type", entity.getType().toString());
            details.put("class", entity.getClass().getSimpleName());
            details.put("name", name);
            details.put("display", display);
            details.put("customName", entity.hasCustomName());
            details.put("position", List.of(entity.getX(), entity.getY(), entity.getZ()));
            details.put("yaw", entity.getYaw());
            details.put("pitch", entity.getPitch());
            if (entity instanceof LivingEntity livingEntity) {
                details.put("headYaw", livingEntity.headYaw);
                details.put("bodyYaw", livingEntity.bodyYaw);
            }
            details.put("playerBehind", isPlayerBehindEntity(client, entity));
            entities.add(details);
        }
        return entities;
    }

    private static boolean isPlayerBehindEntity(MinecraftClient client, Entity entity) {
        double yawRadians = Math.toRadians(entity.getYaw());
        double forwardX = -Math.sin(yawRadians);
        double forwardZ = Math.cos(yawRadians);
        double toPlayerX = client.player.getX() - entity.getX();
        double toPlayerZ = client.player.getZ() - entity.getZ();
        return forwardX * toPlayerX + forwardZ * toPlayerZ < 0.0D;
    }
}
