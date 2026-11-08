/*
 * Ported directly from OpenOpal: client/feature/helper/impl/player/rotation/model/impl/HypixelRotationModel.java
 * Original source: GPL-3.0; see META-INF/licenses/openopal/LICENSE.
 * Minecraft names and host integration adapted for WurstB+ Plus.
 */
package net.wurstclient.opal.rotation;

import net.minecraft.util.Mth;
import net.wurstclient.util.Rotation;
import net.wurstclient.hacks.scaffold.ScaffoldRotationModel;
import net.wurstclient.opal.rotation.IRotationModel;
import net.wurstclient.opal.RandomUtility;



public class HypixelRotationModel implements IRotationModel {
    private final boolean diagonalYaw;
    private final int airTicks;

    public HypixelRotationModel(boolean diagonalYaw, int airTicks) {
        this.diagonalYaw = diagonalYaw;
        this.airTicks = airTicks;
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

        final double maxYaw = this.getSpeed() * distributionYaw;
        final double maxPitch = this.getSpeed() * distributionPitch;

        final float moveYaw = (float) Math.max(Math.min(deltaYaw, maxYaw), -maxYaw);
        final float movePitch = (float) Math.max(Math.min(deltaPitch, maxPitch), -maxPitch);

        return new Rotation(from.yaw() + moveYaw, from.pitch() + movePitch);
    }

    private float getSpeed() {

        return isYawDiagonal() ? (airTicks == 1 ? 65f : 36f)  : 35f;
    }

    private boolean isYawDiagonal() {
        return diagonalYaw;
    }

    @Override
    public ScaffoldRotationModel getEnum() {
        return null;
    }
}
