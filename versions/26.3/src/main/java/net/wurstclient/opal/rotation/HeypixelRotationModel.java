/*
 * Ported directly from OpenOpal: client/feature/helper/impl/player/rotation/model/impl/HeypixelRotationModel.java
 * Original source: GPL-3.0; see META-INF/licenses/openopal/LICENSE.
 * Minecraft names and host integration adapted for WurstB+ Plus.
 */
package net.wurstclient.opal.rotation;

import net.minecraft.util.Mth;
import net.wurstclient.util.Rotation;
import net.wurstclient.hacks.scaffold.ScaffoldRotationModel;
import net.wurstclient.opal.rotation.IRotationModel;
import net.wurstclient.hacks.scaffold.ScaffoldUtils;

public final class HeypixelRotationModel implements IRotationModel {

    private final float speed;

    public HeypixelRotationModel(final float speed) {
        this.speed = speed;
    }

    @Override
    public Rotation tick(final Rotation from, final Rotation to, final float timeDelta) {
        final float deltaYaw = Mth.wrapDegrees(to.yaw() - from.yaw());
        final float deltaPitch = to.pitch() - from.pitch();

        final double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (distance <= 1.0E-6D) {
            return from;
        }

        final double distributionYaw = Math.abs(deltaYaw / distance);
        final double distributionPitch = Math.abs(deltaPitch / distance);

        final double maxYaw = this.speed * distributionYaw;
        final double maxPitch = this.speed * distributionPitch;

        final float moveYaw = (float) Mth.clamp(deltaYaw, -maxYaw, maxYaw);
        final float movePitch = (float) Mth.clamp(deltaPitch, -maxPitch, maxPitch);
        final Rotation rotation = new Rotation(from.yaw() + moveYaw, Mth.clamp(from.pitch() + movePitch, -90.0F, 90.0F));
        return ScaffoldUtils.patchConstantRotation(rotation, from);
    }

    @Override
    public ScaffoldRotationModel getEnum() {
        return ScaffoldRotationModel.HEYPIXEL;
    }
}
