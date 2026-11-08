/*
 * Ported directly from OpenOpal: client/feature/helper/impl/player/rotation/model/impl/SidewaysRotationModel.java
 * Original source: GPL-3.0; see META-INF/licenses/openopal/LICENSE.
 * Minecraft names and host integration adapted for WurstB+ Plus.
 */
package net.wurstclient.opal.rotation;

import net.minecraft.util.Mth;
import net.wurstclient.util.Rotation;
import net.wurstclient.hacks.scaffold.ScaffoldRotationModel;
import net.wurstclient.opal.rotation.IRotationModel;
import net.wurstclient.hacks.scaffold.ScaffoldUtils;

public final class SidewaysRotationModel implements IRotationModel {

    private final float speed;

    public SidewaysRotationModel(final float speed) {
        this.speed = speed;
    }

    @Override
    public Rotation tick(final Rotation from, final Rotation to, final float timeDelta) {
        final float targetYaw = to.yaw();
        final float targetPitch = to.pitch();
        final float lastYaw = from.yaw();
        final float lastPitch = from.pitch();

        final float deltaYaw = Mth.wrapDegrees(targetYaw - lastYaw);
        final float deltaPitch = targetPitch - lastPitch;

        final double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (distance <= 1.0E-6D) {
            return from;
        }

        final double distributionYaw = Math.abs(deltaYaw / distance);
        final double distributionPitch = Math.abs(deltaPitch / distance);

        final double maxYaw = speed * distributionYaw;
        final double maxPitch = speed * distributionPitch;

        final float moveYaw = (float) Math.max(Math.min(deltaYaw, maxYaw), -maxYaw);
        final float movePitch = (float) Math.max(Math.min(deltaPitch, maxPitch), -maxPitch);

        final Rotation rotation = new Rotation(lastYaw + moveYaw, Mth.clamp(lastPitch + movePitch, -90.0F, 90.0F));
        return ScaffoldUtils.patchConstantRotation(rotation, from);
    }

    @Override
    public ScaffoldRotationModel getEnum() {
        return ScaffoldRotationModel.SIDEWAYS;
    }
}
