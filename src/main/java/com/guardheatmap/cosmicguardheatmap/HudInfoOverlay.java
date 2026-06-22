package com.guardheatmap.cosmicguardheatmap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HudInfoOverlay {
    private static final int TITLE_COLOR = 0xFFFF66D9;
    private static final int LABEL_COLOR = 0xFFFF66D9;
    private static final int PAREN_COLOR = 0xFF67FFF3;
    private static final int VALUE_COLOR = 0xFFF3F3F3;
    private static final int ARMOR_TEXT_COLOR = 0xFF67FFF3;
    private static final int ARMOR_VALUE_COLOR = 0xFF39FF6F;
    private static final int ARMOR_WARNING_COLOR = 0xFFFFFF55;
    private static final int ARMOR_DANGER_COLOR = 0xFFFF5555;
    private static final int EDIT_TEXT_COLOR = 0xFFF4F4F4;
    private static final int EDIT_BOX_COLOR = 0x558AE7FF;
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int LINE_GAP = 1;
    private static final int ARMOR_ICON_SIZE = 16;
    private static final int ARMOR_ICON_GAP = 2;
    private static final int MIN_ARMOR_CONTENT_WIDTH = 132;
    private static final Pattern TRAILING_LEVEL_PATTERN = Pattern.compile("^(.*?)(?:\\s+(\\d+))$");
    private static final float MIN_SCALE = 0.75F;
    private static final float MAX_SCALE = 3.0F;
    private static final float SCALE_STEP = 0.25F;
    private static final int STABLE_ARMOR_ROWS = 5;

    private final HudInfoConfig config;
    private boolean editMode;
    private boolean dragging;
    private boolean previousMouseDown;
    private ActiveOverlay activeOverlay = ActiveOverlay.INFO;
    private int dragOffsetX;
    private int dragOffsetY;
    private Rect infoRect = new Rect(0, 0, 0, 0);
    private Rect armorRect = new Rect(0, 0, 0, 0);

    public HudInfoOverlay(HudInfoConfig config) {
        this.config = config;
    }

    public void toggleEditMode() {
        editMode = !editMode;
        dragging = false;
        config.save();
    }

    public void adjustScale(boolean increase) {
        if (!editMode) {
            return;
        }

        float delta = increase ? SCALE_STEP : -SCALE_STEP;
        if (activeOverlay == ActiveOverlay.ARMOR) {
            config.armorScale = MathHelper.clamp(config.armorScale + delta, MIN_SCALE, MAX_SCALE);
        } else {
            config.scale = MathHelper.clamp(config.scale + delta, MIN_SCALE, MAX_SCALE);
        }
        config.save();
    }

    public void toggleActiveOverlayVisible() {
        if (!editMode) {
            return;
        }
        if (activeOverlay == ActiveOverlay.ARMOR) {
            config.armorEnabled = !config.armorEnabled;
        } else {
            config.infoEnabled = !config.infoEnabled;
        }
        dragging = false;
        config.save();
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null
            || client.textRenderer == null
            || !IgnWhitelist.isCurrentPlayerWhitelisted(client)) {
            return;
        }

        List<HudLine> infoLines = buildInfoLines(client);
        List<ArmorLine> armorLines = buildArmorLines(client.player);

        infoRect = buildInfoRect(client, infoLines);
        armorRect = buildArmorRect(client, armorLines);

        handleDragging(client);
        clampToScreen(client);

        infoRect = buildInfoRect(client, infoLines);
        armorRect = buildArmorRect(client, armorLines);

        if (config.infoEnabled || editMode) {
            renderInfoOverlay(context, client, infoLines, infoRect);
        }
        if (config.armorEnabled || editMode) {
            renderArmorOverlay(context, client, armorLines, armorRect);
        }

        if (editMode) {
            renderEditHelper(context, client);
        }
    }

    private void renderInfoOverlay(DrawContext context, MinecraftClient client, List<HudLine> lines, Rect rect) {
        if (editMode && activeOverlay == ActiveOverlay.INFO) {
            drawEditOutline(context, rect);
        }
        if (!config.infoEnabled) {
            if (editMode && activeOverlay == ActiveOverlay.INFO) {
                drawHiddenPanelLabel(context, client, rect, "HUD Information hidden");
            }
            return;
        }

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(rect.x(), rect.y());
        context.getMatrices().scale(config.scale, config.scale);

        int drawY = PADDING_Y;
        context.drawText(client.textRenderer, Text.literal("HUD Information").formatted(Formatting.UNDERLINE, Formatting.BOLD), PADDING_X, drawY, TITLE_COLOR, true);
        drawY += client.textRenderer.fontHeight + LINE_GAP + 1;

        for (HudLine line : lines) {
            drawInfoLine(context, client, PADDING_X, drawY, line);
            drawY += client.textRenderer.fontHeight + LINE_GAP;
        }

        context.getMatrices().popMatrix();
    }

    private void renderArmorOverlay(DrawContext context, MinecraftClient client, List<ArmorLine> lines, Rect rect) {
        if (editMode && activeOverlay == ActiveOverlay.ARMOR) {
            drawEditOutline(context, rect);
        }
        if (!config.armorEnabled) {
            if (editMode && activeOverlay == ActiveOverlay.ARMOR) {
                drawHiddenPanelLabel(context, client, rect, "Armor Status hidden");
            }
            return;
        }

        int contentWidth = getArmorContentWidth(client, lines);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(rect.x(), rect.y());
        context.getMatrices().scale(config.armorScale, config.armorScale);

        int drawY = PADDING_Y;
        Text armorTitle = Text.literal("Armor Status").formatted(Formatting.UNDERLINE, Formatting.BOLD);
        int titleX = Math.max(PADDING_X, PADDING_X + contentWidth - client.textRenderer.getWidth(armorTitle));
        context.drawText(client.textRenderer, armorTitle, titleX, drawY, TITLE_COLOR, true);

        int lineHeight = Math.max(client.textRenderer.fontHeight, ARMOR_ICON_SIZE);
        drawY += lineHeight + LINE_GAP + 1;

        for (ArmorLine line : lines) {
            drawArmorLine(context, client, PADDING_X, drawY, contentWidth, line);
            drawY += lineHeight + LINE_GAP;
        }

        context.getMatrices().popMatrix();
    }

    private void renderEditHelper(DrawContext context, MinecraftClient client) {
        Rect rect = activeOverlay == ActiveOverlay.ARMOR ? armorRect : infoRect;
        String helper = dragging ? "Editing HUD: release left click to save" : "Editing HUD: click a panel, drag, [ ] resize, \\ hide/show, = close";
        int scale = Math.max(1, MathHelper.floor(getActiveScale()));
        int helperY = Math.max(4, rect.y() - (client.textRenderer.fontHeight * scale) - 4);
        context.drawText(client.textRenderer, helper, rect.x(), helperY, EDIT_TEXT_COLOR, true);
    }

    private void handleDragging(MinecraftClient client) {
        if (!editMode) {
            dragging = false;
            previousMouseDown = false;
            return;
        }

        Window window = client.getWindow();
        double scaleX = (double) window.getScaledWidth() / window.getWidth();
        double scaleY = (double) window.getScaledHeight() / window.getHeight();
        int mouseX = MathHelper.floor(client.mouse.getX() * scaleX);
        int mouseY = MathHelper.floor(client.mouse.getY() * scaleY);
        boolean mouseDown = GLFW.glfwGetMouseButton(window.getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (mouseDown && !previousMouseDown) {
            if (armorRect.contains(mouseX, mouseY)) {
                activeOverlay = ActiveOverlay.ARMOR;
                dragging = true;
                dragOffsetX = mouseX - config.armorX;
                dragOffsetY = mouseY - config.armorY;
            } else if (infoRect.contains(mouseX, mouseY)) {
                activeOverlay = ActiveOverlay.INFO;
                dragging = true;
                dragOffsetX = mouseX - config.x;
                dragOffsetY = mouseY - config.y;
            }
        }

        if (!mouseDown && dragging) {
            dragging = false;
            config.save();
        }

        if (dragging) {
            if (activeOverlay == ActiveOverlay.ARMOR) {
                config.armorX = MathHelper.clamp(mouseX - dragOffsetX, 0, Math.max(0, window.getScaledWidth() - armorRect.width()));
                config.armorY = MathHelper.clamp(mouseY - dragOffsetY, 0, Math.max(0, window.getScaledHeight() - armorRect.height()));
            } else {
                config.x = MathHelper.clamp(mouseX - dragOffsetX, 0, Math.max(0, window.getScaledWidth() - infoRect.width()));
                config.y = MathHelper.clamp(mouseY - dragOffsetY, 0, Math.max(0, window.getScaledHeight() - infoRect.height()));
            }
        }

        previousMouseDown = mouseDown;
    }

    private void clampToScreen(MinecraftClient client) {
        Window window = client.getWindow();
        config.x = MathHelper.clamp(config.x, 0, Math.max(0, window.getScaledWidth() - infoRect.width()));
        config.y = MathHelper.clamp(config.y, 0, Math.max(0, window.getScaledHeight() - infoRect.height()));

        config.armorX = MathHelper.clamp(config.armorX, 0, Math.max(0, window.getScaledWidth() - armorRect.width()));
        config.armorY = MathHelper.clamp(config.armorY, 0, Math.max(0, window.getScaledHeight() - armorRect.height()));
    }

    private Rect buildInfoRect(MinecraftClient client, List<HudLine> lines) {
        return new Rect(config.x, config.y, MathHelper.ceil(getInfoWidth(client, lines) * config.scale), MathHelper.ceil(getInfoHeight(client, lines.size()) * config.scale));
    }

    private Rect buildArmorRect(MinecraftClient client, List<ArmorLine> lines) {
        return new Rect(config.armorX, config.armorY, MathHelper.ceil(getArmorWidth(client, lines) * config.armorScale), MathHelper.ceil(getArmorHeight(client, STABLE_ARMOR_ROWS) * config.armorScale));
    }

    private List<HudLine> buildInfoLines(MinecraftClient client) {
        List<HudLine> lines = new ArrayList<>();
        lines.add(new HudLine("T5", "Vanilla"));
        lines.add(new HudLine("FPS", Integer.toString(client.getCurrentFps())));
        int entities = client.world != null ? client.world.getRegularEntityCount() : 0;
        lines.add(new HudLine("Entities", Integer.toString(entities)));
        PlayerEntity player = client.player;
        BlockPos pos = player.getBlockPos();
        lines.add(new HudLine("XYZ", pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
        lines.add(new HudLine("F", getFacingText(player.getHorizontalFacing())));
        lines.add(new HudLine("Ping", getPingText(client)));
        return lines;
    }

    private List<ArmorLine> buildArmorLines(PlayerEntity player) {
        List<ArmorLine> lines = new ArrayList<>();
        lines.add(createArmorLine(player.getEquippedStack(EquipmentSlot.HEAD), "Helmet"));
        lines.add(createArmorLine(player.getEquippedStack(EquipmentSlot.CHEST), "Chestplate"));
        lines.add(createArmorLine(player.getEquippedStack(EquipmentSlot.LEGS), "Leggings"));
        lines.add(createArmorLine(player.getEquippedStack(EquipmentSlot.FEET), "Boots"));
        lines.add(createWeaponLine(player.getMainHandStack()));
        return lines;
    }

    private ArmorLine createArmorLine(ItemStack stack, String fallbackName) {
        if (stack.isEmpty()) {
            return new ArmorLine("0%", "No " + fallbackName, "--", ItemStack.EMPTY, false, 0);
        }

        int percent = 100;
        if (stack.isDamageable()) {
            int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamage());
            percent = Math.round((remaining * 100.0F) / stack.getMaxDamage());
        }

        ParsedItemName parsed = parseItemName(stack.getName().getString());
        return new ArmorLine(percent + "%", parsed.baseName(), parsed.level(), stack, false, percent);
    }

    private ArmorLine createWeaponLine(ItemStack stack) {
        if (!isTrackedWeapon(stack)) {
            return new ArmorLine("", "", "--", ItemStack.EMPTY, true, 0);
        }
        ParsedItemName parsed = parseItemName(stack.getName().getString());
        return new ArmorLine("", parsed.baseName(), parsed.level(), stack, false, 100);
    }

    private boolean isTrackedWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        String key = stack.getItem().toString().toLowerCase();
        return stack.getItem() instanceof AxeItem || key.contains("sword");
    }

    private String getPingText(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) {
            return "0ms";
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return (entry == null ? 0 : entry.getLatency()) + "ms";
    }

    private String getFacingText(Direction direction) {
        return switch (direction) {
            case NORTH -> "North";
            case SOUTH -> "South";
            case EAST -> "East";
            case WEST -> "West";
            default -> direction.asString();
        };
    }

    private int getInfoWidth(MinecraftClient client, List<HudLine> lines) {
        int width = client.textRenderer.getWidth(Text.literal("HUD Information").formatted(Formatting.BOLD));
        for (HudLine line : lines) {
            width = Math.max(width, getInfoLineWidth(client, line));
        }
        return width + (PADDING_X * 2);
    }

    private int getInfoHeight(MinecraftClient client, int lineCount) {
        int textHeight = client.textRenderer.fontHeight;
        return (PADDING_Y * 2) + textHeight + 2 + ((textHeight + LINE_GAP) * lineCount);
    }

    private int getArmorWidth(MinecraftClient client, List<ArmorLine> lines) {
        return getArmorContentWidth(client, lines) + (PADDING_X * 2);
    }

    private int getArmorHeight(MinecraftClient client, int lineCount) {
        int lineHeight = Math.max(client.textRenderer.fontHeight, ARMOR_ICON_SIZE);
        return (PADDING_Y * 2) + lineHeight + 2 + ((lineHeight + LINE_GAP) * lineCount);
    }

    private int getInfoLineWidth(MinecraftClient client, HudLine line) {
        return client.textRenderer.getWidth(Text.literal("(").formatted(Formatting.BOLD))
            + client.textRenderer.getWidth(Text.literal(line.label()).formatted(Formatting.BOLD))
            + client.textRenderer.getWidth(Text.literal(") ").formatted(Formatting.BOLD))
            + client.textRenderer.getWidth(Text.literal(line.value()).formatted(Formatting.BOLD));
    }

    private int getArmorNameSectionWidth(MinecraftClient client, ArmorLine line) {
        int width = 0;
        if (!line.percent().isEmpty()) {
            width += client.textRenderer.getWidth(Text.literal(line.percent()).formatted(Formatting.BOLD));
            width += client.textRenderer.getWidth(Text.literal(" - ").formatted(Formatting.BOLD));
        }
        width += client.textRenderer.getWidth(Text.literal(line.name()).formatted(Formatting.BOLD));
        return width;
    }

    private int getArmorLineFlowWidth(MinecraftClient client, ArmorLine line) {
        if (line.hidden()) {
            return 0;
        }
        int width = getArmorNameSectionWidth(client, line);
        if (!line.level().equals("--")) {
            width += ARMOR_ICON_GAP + client.textRenderer.getWidth(Text.literal(line.level()).formatted(Formatting.BOLD));
        }
        if (!line.stack().isEmpty()) {
            width += ARMOR_ICON_GAP + ARMOR_ICON_SIZE;
        }
        return width;
    }

    private int getArmorContentWidth(MinecraftClient client, List<ArmorLine> lines) {
        int width = Math.max(MIN_ARMOR_CONTENT_WIDTH, client.textRenderer.getWidth(Text.literal("Armor Status").formatted(Formatting.BOLD)));
        for (ArmorLine line : lines) {
            width = Math.max(width, getArmorLineFlowWidth(client, line));
        }
        return width;
    }

    private void drawInfoLine(DrawContext context, MinecraftClient client, int x, int y, HudLine line) {
        int cursor = x;
        Text openParen = Text.literal("(").formatted(Formatting.BOLD);
        Text label = Text.literal(line.label()).formatted(Formatting.BOLD);
        Text closeParen = Text.literal(") ").formatted(Formatting.BOLD);
        Text value = Text.literal(line.value()).formatted(Formatting.BOLD);

        context.drawText(client.textRenderer, openParen, cursor, y, PAREN_COLOR, true);
        cursor += client.textRenderer.getWidth(openParen);
        context.drawText(client.textRenderer, label, cursor, y, LABEL_COLOR, true);
        cursor += client.textRenderer.getWidth(label);
        context.drawText(client.textRenderer, closeParen, cursor, y, PAREN_COLOR, true);
        cursor += client.textRenderer.getWidth(closeParen);
        context.drawText(client.textRenderer, value, cursor, y, VALUE_COLOR, true);
    }

    private void drawArmorLine(DrawContext context, MinecraftClient client, int x, int y, int contentWidth, ArmorLine line) {
        if (line.hidden()) {
            return;
        }

        Text percent = Text.literal(line.percent()).formatted(Formatting.BOLD);
        Text level = Text.literal(line.level()).formatted(Formatting.BOLD);
        Text separator = Text.literal(" - ").formatted(Formatting.BOLD);
        int percentColumnWidth = client.textRenderer.getWidth(Text.literal("100%").formatted(Formatting.BOLD));
        int separatorWidth = client.textRenderer.getWidth(separator);
        int cursor = line.percent().isEmpty() ? x + percentColumnWidth + separatorWidth : x;
        int levelWidth = line.level().equals("--") ? 0 : client.textRenderer.getWidth(level);
        Text name = Text.literal(line.name()).formatted(Formatting.BOLD);

        if (!line.percent().isEmpty()) {
            context.drawText(client.textRenderer, percent, cursor, y + 4, getDurabilityColor(line.percentValue()), true);
            cursor += percentColumnWidth;
            context.drawText(client.textRenderer, separator, cursor, y + 4, ARMOR_TEXT_COLOR, true);
            cursor += separatorWidth;
        }

        context.drawText(client.textRenderer, name, cursor, y + 4, ARMOR_TEXT_COLOR, true);
        cursor += client.textRenderer.getWidth(name);

        if (!line.level().equals("--")) {
            cursor += ARMOR_ICON_GAP;
            context.drawText(client.textRenderer, level, cursor, y + 4, ARMOR_VALUE_COLOR, true);
            cursor += levelWidth;
        }

        if (!line.stack().isEmpty()) {
            cursor += ARMOR_ICON_GAP;
            context.drawItem(line.stack(), cursor, y);
        }
    }

    private int getDurabilityColor(int percent) {
        if (percent >= 61) {
            return ARMOR_VALUE_COLOR;
        }
        if (percent >= 30) {
            return ARMOR_WARNING_COLOR;
        }
        return ARMOR_DANGER_COLOR;
    }

    private ParsedItemName parseItemName(String rawName) {
        Matcher matcher = TRAILING_LEVEL_PATTERN.matcher(rawName.trim());
        if (!matcher.matches()) {
            return new ParsedItemName(rawName, "--");
        }

        String level = matcher.group(2);
        try {
            if (Integer.parseInt(level) <= 0) {
                return new ParsedItemName(matcher.group(1).trim(), "--");
            }
        } catch (NumberFormatException ignored) {
            return new ParsedItemName(matcher.group(1).trim(), "--");
        }

        return new ParsedItemName(matcher.group(1).trim(), level);
    }

    private void drawEditOutline(DrawContext context, Rect rect) {
        int x = rect.x();
        int y = rect.y();
        int right = x + rect.width();
        int bottom = y + rect.height();
        context.fill(x - 1, y - 1, right + 1, y, EDIT_BOX_COLOR);
        context.fill(x - 1, bottom, right + 1, bottom + 1, EDIT_BOX_COLOR);
        context.fill(x - 1, y, x, bottom, EDIT_BOX_COLOR);
        context.fill(right, y, right + 1, bottom, EDIT_BOX_COLOR);
    }

    private void drawHiddenPanelLabel(DrawContext context, MinecraftClient client, Rect rect, String label) {
        context.drawText(client.textRenderer, label, rect.x() + 4, rect.y() + 4, EDIT_TEXT_COLOR, true);
    }

    private float getActiveScale() {
        return activeOverlay == ActiveOverlay.ARMOR ? config.armorScale : config.scale;
    }

    private enum ActiveOverlay {
        INFO,
        ARMOR
    }

    private record HudLine(String label, String value) {
    }

    private record ArmorLine(String percent, String name, String level, ItemStack stack, boolean hidden, int percentValue) {
    }

    private record ParsedItemName(String baseName, String level) {
    }

    private record Rect(int x, int y, int width, int height) {
        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }
}
