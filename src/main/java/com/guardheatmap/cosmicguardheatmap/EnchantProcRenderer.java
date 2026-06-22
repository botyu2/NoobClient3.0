package com.guardheatmap.cosmicguardheatmap;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public final class EnchantProcRenderer {
    private static final float LABEL_SCALE = 0.025F;
    private static final float BASE_HEIGHT = 2.35F;
    private static final double MAX_RENDER_DISTANCE_SQUARED = 36.0D * 36.0D;

    private final EnchantProcTracker tracker;

    public EnchantProcRenderer(EnchantProcTracker tracker) {
        this.tracker = tracker;
    }

    public void render(WorldRenderContext context) {
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (matrices == null
            || consumers == null
            || player == null
            || client.textRenderer == null
            || !IgnWhitelist.isCurrentPlayerWhitelisted(client)) {
            return;
        }

        List<EnchantProcTracker.ProcEntry> activeProcs = tracker.getActiveProcs();
        if (activeProcs.isEmpty()) {
            return;
        }

        Vec3d cameraPos = client.gameRenderer.getCamera().getCameraPos();
        Vec3d playerPos = player.getLerpedPos(1.0F);
        if (cameraPos.squaredDistanceTo(playerPos) > MAX_RENDER_DISTANCE_SQUARED) {
            return;
        }

        renderProcLabel(matrices, consumers, client, client.textRenderer, activeProcs.getLast(),
            playerPos.x - cameraPos.x, playerPos.y - cameraPos.y + BASE_HEIGHT, playerPos.z - cameraPos.z);
    }

    private void renderProcLabel(
        MatrixStack matrices,
        VertexConsumerProvider consumers,
        MinecraftClient client,
        TextRenderer textRenderer,
        EnchantProcTracker.ProcEntry proc,
        double x,
        double y,
        double z
    ) {
        Text text = Text.literal(proc.text()).formatted(Formatting.BOLD);
        float drawX = -textRenderer.getWidth(text) / 2.0F;

        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(client.gameRenderer.getCamera().getRotation());
        matrices.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
        textRenderer.draw(
            text,
            drawX,
            0,
            proc.color(),
            false,
            matrices.peek().getPositionMatrix(),
            consumers,
            TextLayerType.NORMAL,
            0,
            LightmapTextureManager.MAX_LIGHT_COORDINATE
        );
        matrices.pop();
    }
}
