package com.guardheatmap.cosmicguardheatmap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public final class EnchantProcHudRenderer {
    private static final int BACKGROUND_COLOR = 0x88000000;
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 3;
    private static final int Y_OFFSET = 72;

    private final EnchantProcTracker tracker;

    public EnchantProcHudRenderer(EnchantProcTracker tracker) {
        this.tracker = tracker;
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.textRenderer == null || !IgnWhitelist.isCurrentPlayerWhitelisted(client)) {
            return;
        }

        List<EnchantProcTracker.ProcEntry> activeProcs = tracker.getActiveProcs();
        if (activeProcs.isEmpty()) {
            return;
        }

        EnchantProcTracker.ProcEntry proc = activeProcs.getLast();
        Text text = Text.literal(proc.text()).formatted(Formatting.BOLD);
        int textWidth = client.textRenderer.getWidth(text);
        int x = (context.getScaledWindowWidth() - textWidth) / 2;
        int y = context.getScaledWindowHeight() / 2 - Y_OFFSET;

        context.fill(
            x - PADDING_X,
            y - PADDING_Y,
            x + textWidth + PADDING_X,
            y + client.textRenderer.fontHeight + PADDING_Y,
            BACKGROUND_COLOR);
        context.drawText(client.textRenderer, text, x, y, proc.color(), true);
    }
}
