package com.guardheatmap.cosmicguardheatmap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HudInfoConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("cosmic-guard-hudinfo.json");

    public int x = 8;
    public int y = 8;
    public float scale = 1.0F;
    public int armorX = 170;
    public int armorY = 8;
    public float armorScale = 0.6F;
    public boolean enabled = true;
    public boolean infoEnabled = true;
    public boolean armorEnabled = true;

    public static HudInfoConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            return new HudInfoConfig();
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            HudInfoConfig config = GSON.fromJson(reader, HudInfoConfig.class);
            if (config == null) {
                return new HudInfoConfig();
            }
            config.normalize();
            return config;
        } catch (IOException ignored) {
            return new HudInfoConfig();
        }
    }

    private void normalize() {
        enabled = true;
        if (!infoEnabled && !armorEnabled) {
            infoEnabled = true;
            armorEnabled = true;
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException ignored) {
        }
    }
}
