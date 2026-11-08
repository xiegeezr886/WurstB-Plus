/*
 * Direct port of OpenOpal ScaffoldModule's SkipTick recovery methods (GPL-3.0).
 * Source: reference/clients/OpenOpal; see META-INF/licenses/openopal/LICENSE.
 */
package net.wurstclient.hacks.scaffold;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.ScaffoldWalkHack;
import net.wurstclient.opal.SkipTickUtility;
import net.wurstclient.util.Rotation;

public final class OpenOpalScaffoldRecovery {
    private static final Direction[] DIRECTIONS = Direction.values();
    private final ScaffoldWalkHack hack;
    private boolean skipTickRecoveryActive, skipTickRecoveryFailed;
    private int skipTickRecoveryCandidateTicks;
    private BlockPos skipTickRecoveryBlockPos;
    private Direction skipTickRecoveryFace;
    public OpenOpalScaffoldRecovery(ScaffoldWalkHack hack) { this.hack = hack; }

    public void reset() {
        setSkipTickRecoveryActive(false);
        skipTickRecoveryFailed = false;
    }

    private boolean isSolidAndNonInteractive(BlockState state, BlockPos pos) {
        return WurstClient.MC.level != null
            && !state.getCollisionShape(WurstClient.MC.level, pos).isEmpty()
            && state.getMenuProvider(WurstClient.MC.level, pos) == null;
    }

    private Rotation rotationFromBlock(BlockPos pos, Direction face) {
        float xDiff = (float)(pos.getX() + 0.5 - WurstClient.MC.player.getX() + face.getStepX() * 0.5);
        float yDiff = (float)(WurstClient.MC.player.getEyeY() - pos.getY() - face.getStepY() * 0.5);
        float zDiff = (float)(pos.getZ() + 0.5 - WurstClient.MC.player.getZ() + face.getStepZ() * 0.5);
        double distance = Math.sqrt(xDiff * xDiff + zDiff * zDiff);
        return new Rotation((float)Math.toDegrees(-Math.atan2(xDiff, zDiff)),
            (float)Math.toDegrees(Math.atan(yDiff / distance)));
    }

    public void updateSkipTickRecoveryGroundState() {
        if (!this.skipTickRecoveryActive) {
            return;
        }

        boolean groundBelow = WurstClient.MC.player.onGround();
        if (!groundBelow) {
            for (double offset = 0.01; offset <= 1.5; offset += 0.5) {
                if (!WurstClient.MC.level.getBlockState(BlockPos.containing(WurstClient.MC.player.getX(), WurstClient.MC.player.getY() - offset, WurstClient.MC.player.getZ())).isAir()) {
                    groundBelow = true;
                    break;
                }
            }
        }

        if (groundBelow) {
            this.skipTickRecoveryActive = false;
            SkipTickUtility.reset();
            this.skipTickRecoveryFailed = false;
        }
    }

    private BlockPos findRecoveryBlock() {
        if (WurstClient.MC.player == null || WurstClient.MC.level == null) return null;

        BlockPos playerPos = WurstClient.MC.player.blockPosition();
        for (int y = 0; y >= -3; y--) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos target = playerPos.offset(x, y, z);
                    if (WurstClient.MC.level.getBlockState(target).canBeReplaced()) {
                        for (Direction dir : DIRECTIONS) {
                            BlockPos neighbor = target.relative(dir);
                            if (!WurstClient.MC.level.getBlockState(neighbor).isAir() && !WurstClient.MC.level.getBlockState(neighbor).canBeReplaced()) {
                                return target;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private SkipTickRecoveryTarget findReadySkipTickRecoveryTarget() {
        if (WurstClient.MC.player == null || WurstClient.MC.level == null) {
            return null;
        }

        final Rotation currentRotation = getCurrentClientRotation();
        if (currentRotation == null) {
            return null;
        }

        final Vec3 eyePos = WurstClient.MC.player.getEyePosition();
        final BlockPos playerPos = WurstClient.MC.player.blockPosition();
        SkipTickRecoveryTarget bestTarget = null;

        for (int y = 0; y >= -3; y--) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    final BlockPos targetPos = playerPos.offset(x, y, z);
                    if (!WurstClient.MC.level.getBlockState(targetPos).canBeReplaced()) {
                        continue;
                    }

                    for (Direction direction : DIRECTIONS) {
                        final BlockPos supportPos = targetPos.relative(direction);
                        if (!isSolidAndNonInteractive(WurstClient.MC.level.getBlockState(supportPos), supportPos)) {
                            continue;
                        }

                        final Direction face = direction.getOpposite();
                        final Rotation rotation = rotationFromBlock(supportPos, face);
                        final float rotationDifference = (float)currentRotation.getAngleTo(rotation);
                        if (rotationDifference > 16.0F) {
                            continue;
                        }

                        final Vec3 supportCenter = new Vec3(
                                supportPos.getX() + 0.5D,
                                supportPos.getY() + 0.5D,
                                supportPos.getZ() + 0.5D
                        );
                        final double dx = eyePos.x - supportCenter.x;
                        final double dy = eyePos.y - supportCenter.y;
                        final double dz = eyePos.z - supportCenter.z;
                        final double distanceSq = dx * dx + dy * dy + dz * dz;
                        if (distanceSq > 4.85D * 4.85D) {
                            continue;
                        }

                        if (bestTarget == null
                                || distanceSq < bestTarget.distanceSq
                                || (Math.abs(distanceSq - bestTarget.distanceSq) < 1.0E-4D
                                && rotationDifference < bestTarget.rotationDifference)) {
                            bestTarget = new SkipTickRecoveryTarget(targetPos, supportPos, face, rotation, rotationDifference, distanceSq);
                        }
                    }
                }
            }
        }

        return bestTarget;
    }

    private Rotation getCurrentClientRotation() {
        if (WurstClient.MC.player == null) {
            return null;
        }
        return ScaffoldUtils.getRotation();
    }

    private boolean isFallingIntoVoid() {
        if (WurstClient.MC.player == null || WurstClient.MC.level == null) return false;
        if (WurstClient.MC.player.getY() < -5) return true;

        BlockPos pos = WurstClient.MC.player.blockPosition();
        for (int y = (int) WurstClient.MC.player.getY(); y > -64; y--) {
            if (!WurstClient.MC.level.getBlockState(new BlockPos(pos.getX(), y, pos.getZ())).isAir()) {
                return false;
            }
        }
        return true;
    }

    private boolean shouldTriggerSkipTickRecovery() {
        if (WurstClient.MC.player == null || WurstClient.MC.level == null || this.skipTickRecoveryActive || this.skipTickRecoveryFailed) {
            return false;
        }

        if (hack.selfRescueMode.getSelected() != SelfRescueMode.SKIP_TICK) {
            return false;
        }

        if (hack.getEffectiveMode() == ScaffoldWalkHack.Mode.HEYPIXEL || hack.getEffectiveMode() == ScaffoldWalkHack.Mode.HYPIXEL) {
            final var activeMode = hack.getScaffoldMode();
            if (activeMode instanceof HeypixelScaffoldMode heypixelScaffold && heypixelScaffold.shouldSuppressSkipTickRecoveryTrigger()) {
                return false;
            }
        }

        final boolean overVoid = isFallingIntoVoid();
        final boolean fallingFast = WurstClient.MC.player.getDeltaMovement().y < -0.35D;
        final boolean hardFall = WurstClient.MC.player.fallDistance > 2.0F;
        if (!overVoid && !fallingFast && !hardFall) {
            return false;
        }
        return hack.hasHotbarPlaceableBlock() || WurstClient.MC.player.getMainHandItem().getItem() instanceof BlockItem;
    }

    private Object trackedPlayer, trackedWorld;
    public void tryArmSkipTickRecovery() {
        if(trackedPlayer != WurstClient.MC.player || trackedWorld != WurstClient.MC.level
            || hack.selfRescueMode.getSelected() == SelfRescueMode.DISABLED) {
            reset();
            trackedPlayer = WurstClient.MC.player;
            trackedWorld = WurstClient.MC.level;
        }
        if (!shouldTriggerSkipTickRecovery()) {
            this.skipTickRecoveryCandidateTicks = 0;
            this.skipTickRecoveryBlockPos = null;
            this.skipTickRecoveryFace = null;
            return;
        }

        if (hack.mode.getSelected() == ScaffoldWalkHack.Mode.HEYPIXEL && hack.uitemsTelly.isChecked() && WurstClient.MC.player != null && !WurstClient.MC.player.onGround() && WurstClient.MC.player.getDeltaMovement().y > -0.16D) {
            this.skipTickRecoveryCandidateTicks = 0;
            this.skipTickRecoveryBlockPos = null;
            this.skipTickRecoveryFace = null;
            return;
        }

        if (findRecoveryBlock() == null) {
            this.skipTickRecoveryCandidateTicks = 0;
            this.skipTickRecoveryBlockPos = null;
            this.skipTickRecoveryFace = null;
            return;
        }

        final SkipTickRecoveryTarget recoveryTarget = findReadySkipTickRecoveryTarget();
        if (recoveryTarget == null) {
            this.skipTickRecoveryCandidateTicks = 0;
            this.skipTickRecoveryBlockPos = null;
            this.skipTickRecoveryFace = null;
            return;
        }

        this.skipTickRecoveryBlockPos = recoveryTarget.supportPos();
        this.skipTickRecoveryFace = recoveryTarget.face();

        this.skipTickRecoveryCandidateTicks = Math.min(this.skipTickRecoveryCandidateTicks + 1, 2);
        if (this.skipTickRecoveryCandidateTicks < 2) {
            return;
        }

        if (this.skipTickRecoveryActive) {
            return;
        }

        this.skipTickRecoveryActive = true;
        this.skipTickRecoveryCandidateTicks = 0;
        SkipTickUtility.reset();
        SkipTickUtility.addSkipTicks(12);
    }

    public BlockPos getSkipTickRecoveryBlockPos() {
        return skipTickRecoveryBlockPos;
    }

    public Direction getSkipTickRecoveryFace() {
        return skipTickRecoveryFace;
    }

    public boolean isSkipTickRecoveryActive() {
        return skipTickRecoveryActive;
    }

    public void setSkipTickRecoveryActive(final boolean skipTickRecoveryActive) {
        this.skipTickRecoveryActive = skipTickRecoveryActive;
        if (!skipTickRecoveryActive) {
            SkipTickUtility.reset();
        }
        this.skipTickRecoveryCandidateTicks = 0;
        this.skipTickRecoveryBlockPos = null;
        this.skipTickRecoveryFace = null;
    }

    public void markSkipTickRecoveryFailed() {
        this.skipTickRecoveryFailed = true;
        this.skipTickRecoveryActive = false;
        SkipTickUtility.reset();
        this.skipTickRecoveryCandidateTicks = 0;
        this.skipTickRecoveryBlockPos = null;
        this.skipTickRecoveryFace = null;
    }
    private record SkipTickRecoveryTarget(BlockPos targetPos, BlockPos supportPos,
        Direction face, Rotation rotation, float rotationDifference, double distanceSq) {}

    public enum SelfRescueMode {
        DISABLED("Disabled"), SKIP_TICK("SkipTick");
        private final String name;
        SelfRescueMode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }
}
