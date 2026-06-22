package com.guardheatmap.cosmicguardheatmap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;

public final class GuardHeatmapConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("cosmic-guard-heatmap.json");
    private static GuardHeatmapConfigData data = GuardHeatmapConfigData.defaults();

    private GuardHeatmapConfig() {
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            GuardHeatmapConfigData loaded = GSON.fromJson(reader, GuardHeatmapConfigData.class);
            if (loaded != null) {
                data = loaded.withDefaults();
            }
        } catch (IOException | JsonSyntaxException ignored) {
            data = GuardHeatmapConfigData.defaults();
            save();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public static boolean isEnabled() {
        return data.enabled;
    }

    public static boolean toggle() {
        data.enabled = !data.enabled;
        save();
        return data.enabled;
    }

    public static OptionalInt rangeForName(String entityName) {
        String normalized = entityName.toLowerCase();
        boolean mineGuard = normalized.contains("warden")
            || normalized.contains("enforcer")
            || normalized.contains("gold guard")
            || normalized.contains("diamond guard")
            || normalized.contains("emerald guard")
            || normalized.contains("guard");
        return mineGuard ? OptionalInt.of(data.guardRange) : OptionalInt.empty();
    }

    public static int maximumRange() {
        return data.guardRange;
    }

    private static final class GuardHeatmapConfigData {
        boolean enabled = true;
        int guardRange = 12;

        static GuardHeatmapConfigData defaults() {
            return new GuardHeatmapConfigData();
        }

        GuardHeatmapConfigData withDefaults() {
            GuardHeatmapConfigData defaults = defaults();
            defaults.enabled = enabled;
            defaults.guardRange = guardRange > 0 ? guardRange : defaults.guardRange;
            return defaults;
        }
    }
}
