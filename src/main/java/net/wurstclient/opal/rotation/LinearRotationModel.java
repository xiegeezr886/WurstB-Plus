/*
 * Ported directly from OpenOpal: client/feature/helper/impl/player/rotation/model/impl/LinearRotationModel.java
 * Original source: GPL-3.0; see META-INF/licenses/openopal/LICENSE.
 * Minecraft names and host integration adapted for WurstB+ Plus.
 */
package net.wurstclient.opal.rotation;

import net.minecraft.util.Mth;
import net.wurstclient.util.Rotation;
import net.wurstclient.hacks.scaffold.ScaffoldRotationModel;
import net.wurstclient.opal.rotation.IRotationModel;

public final class LinearRotationModel implements IRotationModel {
    private final double speed;

    public LinearRotationModel(double speed) {
        this.speed = speed;
    }

    @Override
    public Rotation tick(Rotation from, Rotation to, float timeDelta) {
        final float deltaYaw = Mth.wrapDegrees(to.yaw() - from.yaw()) * timeDelta;
        final float deltaPitch = (to.pitch() - from.pitch()) * timeDelta;

        final double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (distance == 0.D) {
            return new Rotation(from.yaw() + deltaYaw, from.pitch() + deltaPitch);
        }
        final double distributionYaw = Math.abs(deltaYaw / distance);
        final double distributionPitch = Math.abs(deltaPitch / distance);

        final double maxYaw = this.speed * distributionYaw;
        final double maxPitch = this.speed * distributionPitch;

        final float moveYaw = (float) Math.max(Math.min(deltaYaw, maxYaw), -maxYaw);
        final float movePitch = (float) Math.max(Math.min(deltaPitch, maxPitch), -maxPitch);

        return new Rotation(from.yaw() + moveYaw, from.pitch() + movePitch);
    }

    @Override
    public ScaffoldRotationModel getEnum() {
        return ScaffoldRotationModel.LINEAR;
    }
}
