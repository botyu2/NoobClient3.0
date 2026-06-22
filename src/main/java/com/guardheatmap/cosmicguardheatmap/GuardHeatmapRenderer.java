package com.guardheatmap.cosmicguardheatmap;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.util.math.Vec3d;

public final class GuardHeatmapRenderer {
    private static final int SAFE_GREEN = 0x5030D060;
    private static final int GUARD_RED = 0x70E04030;
    private static final int ENFORCER_RED = 0x90B01010;
    private static final int WARDEN_RED = 0xB0700010;
    private static final int SAFEZONE_RED = 0xB0300000;
    private static final double SURFACE_OFFSET = 0.035D;

    private GuardHeatmapRenderer() {
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!GuardHeatmapConfig.isEnabled()
            || client.world == null
            || client.player == null
            || !IgnWhitelist.isCurrentPlayerWhitelisted(client)) {
            return;
        }

        Vec3d camera = context.gameRenderer().getCamera().getCameraPos();
        context.commandQueue().submitCustom(
            context.matrices(),
            RenderLayers.debugQuads(),
            (entry, vertexConsumer) -> drawMeasuredBlocks(entry, vertexConsumer, client, camera));
    }

    private static void drawMeasuredBlocks(
        Entry entry,
        VertexConsumer vertexConsumer,
        MinecraftClient client,
        Vec3d camera
    ) {
        for (GuardRangeMath.PredictedBlock block : GuardRangeMath.predictedBlocks()) {
            drawTopFace(
                entry,
                vertexConsumer,
                block.x() - camera.x,
                block.y() + 1.0D + SURFACE_OFFSET - camera.y,
                block.z() - camera.z,
                colorFor(block.state()));
        }
    }

    private static int colorFor(GuardedStateTracker.ZoneState state) {
        return switch (state) {
            case NEUTRAL -> SAFE_GREEN;
            case GUARD -> GUARD_RED;
            case ENFORCER -> ENFORCER_RED;
            case WARDEN -> WARDEN_RED;
            case SAFEZONE -> SAFEZONE_RED;
        };
    }

    private static void drawTopFace(
        Entry entry,
        VertexConsumer vertexConsumer,
        double x,
        double y,
        double z,
        int color
    ) {
        int alpha = color >>> 24 & 0xFF;
        int red = color >>> 16 & 0xFF;
        int green = color >>> 8 & 0xFF;
        int blue = color & 0xFF;

        vertexConsumer.vertex(entry, (float) x, (float) y, (float) z).color(red, green, blue, alpha);
        vertexConsumer.vertex(entry, (float) (x + 1), (float) y, (float) z).color(red, green, blue, alpha);
        vertexConsumer.vertex(entry, (float) (x + 1), (float) y, (float) (z + 1)).color(red, green, blue, alpha);
        vertexConsumer.vertex(entry, (float) x, (float) y, (float) (z + 1)).color(red, green, blue, alpha);
    }

}
