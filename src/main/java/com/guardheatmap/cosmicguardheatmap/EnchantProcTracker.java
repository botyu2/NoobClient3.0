package com.guardheatmap.cosmicguardheatmap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EnchantProcTracker {
    private static final Pattern PROC_PATTERN = Pattern.compile("^(.+?)\\s+(?:[IVXLCDM]+|\\d+)\\s+proc!$", Pattern.CASE_INSENSITIVE);
    private static final long PROC_DURATION_MS = 3500L;

    private static final int LEGENDARY_COLOR = 0xFFFFAA00;
    private static final int ULTIMATE_COLOR = 0xFFFFFF55;
    private static final int ELITE_COLOR = 0xFF5555FF;
    private static final int UNCOMMON_COLOR = 0xFF55FF55;
    private static final int COMMON_COLOR = 0xFFF3F3F3;

    private static final Map<String, Integer> ENCHANT_COLORS = Map.ofEntries(
        Map.entry("lifesteal", LEGENDARY_COLOR),
        Map.entry("trap", LEGENDARY_COLOR),
        Map.entry("perfect strike", LEGENDARY_COLOR),
        Map.entry("lucky", LEGENDARY_COLOR),
        Map.entry("lightning", LEGENDARY_COLOR),
        Map.entry("frenzy", LEGENDARY_COLOR),
        Map.entry("crown of blades", LEGENDARY_COLOR),
        Map.entry("weakness", LEGENDARY_COLOR),
        Map.entry("berserk", LEGENDARY_COLOR),
        Map.entry("bloodlust", LEGENDARY_COLOR),
        Map.entry("outbreak", LEGENDARY_COLOR),
        Map.entry("leadership", LEGENDARY_COLOR),
        Map.entry("thousand cuts", ULTIMATE_COLOR),
        Map.entry("pummel", ULTIMATE_COLOR),
        Map.entry("execute", ULTIMATE_COLOR),
        Map.entry("enrage", ULTIMATE_COLOR),
        Map.entry("anti gank", ULTIMATE_COLOR),
        Map.entry("demon forged", ULTIMATE_COLOR),
        Map.entry("impact", ULTIMATE_COLOR),
        Map.entry("cleave", ULTIMATE_COLOR),
        Map.entry("bleed", ULTIMATE_COLOR),
        Map.entry("obliterate", ELITE_COLOR),
        Map.entry("fling", ELITE_COLOR),
        Map.entry("famine", ELITE_COLOR),
        Map.entry("electrocution", ELITE_COLOR),
        Map.entry("blaze", ELITE_COLOR),
        Map.entry("deep wounds", ELITE_COLOR),
        Map.entry("defiance", ELITE_COLOR),
        Map.entry("poison", UNCOMMON_COLOR),
        Map.entry("cannibalism", UNCOMMON_COLOR),
        Map.entry("scorch", UNCOMMON_COLOR),
        Map.entry("daze", COMMON_COLOR)
    );

    private final List<ProcEntry> activeProcs = new ArrayList<>();

    public void handleMessage(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!IgnWhitelist.isCurrentPlayerWhitelisted(client)) {
            activeProcs.clear();
            return;
        }

        String raw = message.getString().trim();
        Matcher matcher = PROC_PATTERN.matcher(raw);
        if (!matcher.matches()) {
            return;
        }

        String enchantName = matcher.group(1).trim();
        int color = ENCHANT_COLORS.getOrDefault(enchantName.toLowerCase(Locale.ROOT), COMMON_COLOR);

        long now = System.currentTimeMillis();
        activeProcs.removeIf(proc -> proc.expiresAt() <= now || proc.text().equalsIgnoreCase(enchantName));
        activeProcs.add(new ProcEntry(enchantName, color, now + PROC_DURATION_MS));
        while (activeProcs.size() > 2) {
            activeProcs.removeFirst();
        }
    }

    public void tick(MinecraftClient client) {
        long now = System.currentTimeMillis();
        activeProcs.removeIf(proc -> proc.expiresAt() <= now);
        ClientPlayerEntity player = client.player;
        if (player == null || player.isRemoved() || !IgnWhitelist.isCurrentPlayerWhitelisted(client)) {
            activeProcs.clear();
        }
    }

    public List<ProcEntry> getActiveProcs() {
        return List.copyOf(activeProcs);
    }

    public record ProcEntry(String text, int color, long expiresAt) {
    }
}
