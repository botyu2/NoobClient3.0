package com.guardheatmap.cosmicguardheatmap;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class PayloadMain {
    private static final Identifier PROC_HUD_ID = Identifier.of(CosmicGuardHeatmapClient.MOD_ID, "proc_overlay");
    private static final Identifier INFO_HUD_ID = Identifier.of(CosmicGuardHeatmapClient.MOD_ID, "info_overlay");

    private PayloadMain() {
    }

    public static void init() {
        GuardHeatmapConfig.load();
        IgnWhitelist.refreshNow();
        HudInfoConfig hudInfoConfig = HudInfoConfig.load();
        HudInfoOverlay hudInfoOverlay = new HudInfoOverlay(hudInfoConfig);
        EnchantProcTracker procTracker = new EnchantProcTracker();
        EnchantProcRenderer procRenderer = new EnchantProcRenderer(procTracker);
        EnchantProcHudRenderer procHudRenderer = new EnchantProcHudRenderer(procTracker);
        CosmicApiClient.init(procTracker);
        boolean[] previousEditKeyDown = new boolean[1];
        boolean[] previousToggleKeyDown = new boolean[1];
        KeyBinding editHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cosmic_guard_heatmap.edit_hud",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_EQUAL,
            KeyBinding.Category.MISC
        ));
        KeyBinding toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cosmic_guard_heatmap.toggle_info_hud",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_BACKSLASH,
            KeyBinding.Category.MISC
        ));
        KeyBinding increaseSizeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cosmic_guard_heatmap.increase_size",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            KeyBinding.Category.MISC
        ));
        KeyBinding decreaseSizeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cosmic_guard_heatmap.decrease_size",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_BRACKET,
            KeyBinding.Category.MISC
        ));
        ClientTickEvents.END_CLIENT_TICK.register(GuardRangeMath::tick);
        ClientTickEvents.END_CLIENT_TICK.register(CosmicForensics::tick);
        ClientTickEvents.END_CLIENT_TICK.register(procTracker::tick);
        ClientTickEvents.END_CLIENT_TICK.register(CosmicApiClient::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!IgnWhitelist.isCurrentPlayerWhitelisted(client)) {
                return;
            }
            boolean editPressed = false;
            boolean togglePressed = false;
            while (editHudKey.wasPressed()) {
                editPressed = true;
            }
            while (toggleHudKey.wasPressed()) {
                togglePressed = true;
            }
            if (client.getWindow() != null) {
                long handle = client.getWindow().getHandle();
                boolean editKeyDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_EQUAL) == GLFW.GLFW_PRESS;
                boolean toggleKeyDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_BACKSLASH) == GLFW.GLFW_PRESS;
                editPressed = editPressed || (editKeyDown && !previousEditKeyDown[0]);
                togglePressed = togglePressed || (toggleKeyDown && !previousToggleKeyDown[0]);
                previousEditKeyDown[0] = editKeyDown;
                previousToggleKeyDown[0] = toggleKeyDown;
            }
            if (editPressed) {
                hudInfoOverlay.toggleEditMode();
            }
            if (togglePressed) {
                hudInfoOverlay.toggleActiveOverlayVisible();
            }
            while (increaseSizeKey.wasPressed()) {
                hudInfoOverlay.adjustScale(true);
            }
            while (decreaseSizeKey.wasPressed()) {
                hudInfoOverlay.adjustScale(false);
            }
        });
        ClientReceiveMessageEvents.GAME.register((message, overlayMessage) -> procTracker.handleMessage(message));
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, time) -> procTracker.handleMessage(message));
        WorldRenderEvents.END_MAIN.register(GuardHeatmapRenderer::render);
        WorldRenderEvents.AFTER_ENTITIES.register(procRenderer::render);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, INFO_HUD_ID, hudInfoOverlay::render);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, PROC_HUD_ID, procHudRenderer::render);
        registerCommands();
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            literal("guardheatmap")
                .executes(context -> {
                    if (!IgnWhitelist.isCurrentPlayerWhitelisted(net.minecraft.client.MinecraftClient.getInstance())) {
                        sendRaw(context.getSource(), Text.literal("Not whitelisted."));
                        return 0;
                    }
                    boolean enabled = GuardHeatmapConfig.toggle();
                    sendRaw(context.getSource(), Text.translatable(enabled
                        ? "cosmic_guard_heatmap.command.enabled"
                        : "cosmic_guard_heatmap.command.disabled")
                        .append(Text.literal(" (math-only heatmap)")));
                    return 1;
                })
        ));
    }

    private static void sendRaw(FabricClientCommandSource source, Text text) {
        source.sendFeedback(Text.literal("[GuardHeatmap] ").append(text));
    }
}
