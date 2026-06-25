package com.guardheatmap.cosmicguardheatmap;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GuardRangeMath {
    private static final double WARDEN_RADIUS_SQUARED = 25.0D * 25.0D;
    private static final double WARDEN_REAR_RADIUS_SQUARED = 14.0D * 14.0D;
    private static final double ENFORCER_RADIUS_SQUARED = 29.0D * 29.0D;
    private static final double ENFORCER_REAR_RADIUS_SQUARED = 24.0D * 24.0D;
    private static final double GUARD_RADIUS_SQUARED = 28.0D * 28.0D;
    private static final int MAP_RADIUS = 50;
    private static final int MAP_RADIUS_SQUARED = MAP_RADIUS * MAP_RADIUS;
    private static final int REFRESH_TICKS = 5;
    private static List<PredictedBlock> predictedBlocks = List.of();
    private static int refreshCountdown;

    private GuardRangeMath() {
    }

    public static void tick(MinecraftClient client) {
        if (client.world == null
            || client.player == null
            || !IgnWhitelist.isCurrentPlayerWhitelisted(client)
            || !GuardHeatmapConfig.isEnabled()) {
            predictedBlocks = List.of();
            refreshCountdown = 0;
            return;
        }
        if (--refreshCountdown > 0) {
            return;
        }
        refreshCountdown = REFRESH_TICKS;

        List<GuardSource> sources = guardSources(client);
        if (sources.isEmpty()) {
            predictedBlocks = List.of();
            return;
        }

        BlockPos player = client.player.getBlockPos();
        GuardedStateTracker.ZoneState currentServerState = GuardedStateTracker.readCurrentZoneState(client)
            .orElse(null);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        List<PredictedBlock> predictions = new ArrayList<>();
        for (int x = player.getX() - MAP_RADIUS; x <= player.getX() + MAP_RADIUS; x++) {
            for (int z = player.getZ() - MAP_RADIUS; z <= player.getZ() + MAP_RADIUS; z++) {
                int dx = x - player.getX();
                int dz = z - player.getZ();
                if (dx * dx + dz * dz > MAP_RADIUS_SQUARED) {
                    continue;
                }
                for (int y = player.getY() + 4; y >= player.getY() - 10; y--) {
                    mutable.set(x, y, z);
                    if (!isVisibleTopSurface(client, mutable)) {
                        continue;
                    }
                    BlockPos position = mutable.toImmutable();
                    GuardedStateTracker.ZoneState protection = strongestProtection(position, sources);
                    protection = applyCurrentServerCorrection(player, position, protection, currentServerState);
                    predictions.add(new PredictedBlock(
                        position.getX(),
                        position.getY(),
                        position.getZ(),
                        protection));
                }
            }
        }
        predictedBlocks = List.copyOf(predictions);
    }

    public static Iterable<PredictedBlock> predictedBlocks() {
        return predictedBlocks;
    }

    private static List<GuardSource> guardSources(MinecraftClient client) {
        List<GuardSource> sources = new ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            String name = entity.getName().getString().toLowerCase(Locale.ROOT);
            GuardedStateTracker.ZoneState tier = tierForName(name);
            if (tier != null) {
                sources.add(new GuardSource(entity.getX(), entity.getY(), entity.getZ(), entity.getYaw(), tier));
            }
        }
        return sources;
    }

    private static GuardedStateTracker.ZoneState tierForName(String name) {
        if (name.startsWith("warden_")) {
            return GuardedStateTracker.ZoneState.WARDEN;
        }
        if (name.startsWith("enforcer_")) {
            return GuardedStateTracker.ZoneState.ENFORCER;
        }
        if (name.startsWith("guard_")) {
            return GuardedStateTracker.ZoneState.GUARD;
        }
        return null;
    }

    private static GuardedStateTracker.ZoneState strongestProtection(BlockPos floor, List<GuardSource> sources) {
        GuardedStateTracker.ZoneState strongest = GuardedStateTracker.ZoneState.NEUTRAL;
        double targetX = floor.getX() + 0.5D;
        double targetY = floor.getY() + 1.0D;
        double targetZ = floor.getZ() + 0.5D;

        for (GuardSource source : sources) {
            double dx = targetX - source.x();
            double dy = targetY - source.y();
            double dz = targetZ - source.z();
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (isInsideProtection(source, dx, dy, dz, distanceSquared)
                && strength(source.tier()) > strength(strongest)) {
                strongest = source.tier();
            }
        }
        return strongest;
    }

    private static GuardedStateTracker.ZoneState applyCurrentServerCorrection(
        BlockPos player,
        BlockPos position,
        GuardedStateTracker.ZoneState predicted,
        GuardedStateTracker.ZoneState currentServerState
    ) {
        if (currentServerState == null || currentServerState == GuardedStateTracker.ZoneState.NEUTRAL) {
            return predicted;
        }

        int dx = position.getX() - player.getX();
        int dy = position.getY() - player.getY();
        int dz = position.getZ() - player.getZ();
        if (dx * dx + dy * dy + dz * dz <= 9 && strength(currentServerState) > strength(predicted)) {
            return currentServerState;
        }
        return predicted;
    }

    private static boolean isInsideProtection(GuardSource source, double dx, double dy, double dz, double distanceSquared) {
        if (source.tier() == GuardedStateTracker.ZoneState.WARDEN) {
            return distanceSquared <= (isBehind(source, dx, dz) ? WARDEN_REAR_RADIUS_SQUARED : WARDEN_RADIUS_SQUARED);
        }
        if (source.tier() == GuardedStateTracker.ZoneState.ENFORCER) {
            return distanceSquared <= (isBehind(source, dx, dz) ? ENFORCER_REAR_RADIUS_SQUARED : ENFORCER_RADIUS_SQUARED);
        }
        return distanceSquared <= radiusSquared(source.tier());
    }

    private static boolean isBehind(GuardSource source, double dx, double dz) {
        double yawRadians = Math.toRadians(source.yaw());
        double forwardX = -Math.sin(yawRadians);
        double forwardZ = Math.cos(yawRadians);
        return forwardX * dx + forwardZ * dz < 0.0D;
    }

    private static double radiusSquared(GuardedStateTracker.ZoneState tier) {
        return switch (tier) {
            case WARDEN -> WARDEN_RADIUS_SQUARED;
            case ENFORCER -> ENFORCER_RADIUS_SQUARED;
            case GUARD -> GUARD_RADIUS_SQUARED;
            default -> 0.0D;
        };
    }

    private static int strength(GuardedStateTracker.ZoneState tier) {
        return switch (tier) {
            case WARDEN -> 3;
            case ENFORCER -> 2;
            case GUARD -> 1;
            default -> 0;
        };
    }

    private static boolean isVisibleTopSurface(MinecraftClient client, BlockPos position) {
        BlockState state = client.world.getBlockState(position);
        BlockPos above = position.up();
        BlockState aboveState = client.world.getBlockState(above);
        return !state.isAir()
            && !state.getCollisionShape(client.world, position).isEmpty()
            && (aboveState.isAir() || aboveState.getCollisionShape(client.world, above).isEmpty());
    }

    private record GuardSource(double x, double y, double z, float yaw, GuardedStateTracker.ZoneState tier) {
    }

    public record PredictedBlock(int x, int y, int z, GuardedStateTracker.ZoneState state) {
    }
}
