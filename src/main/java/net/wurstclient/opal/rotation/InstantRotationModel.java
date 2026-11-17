/*
 * Ported directly from OpenOpal: client/feature/helper/impl/player/rotation/model/impl/InstantRotationModel.java
 * Original source: GPL-3.0; see META-INF/licenses/openopal/LICENSE.
 * Minecraft names and host integration adapted for WurstB+ Plus.
 */
package net.wurstclient.opal.rotation;

import net.minecraft.util.Mth;
import net.wurstclient.util.Rotation;
import net.wurstclient.hacks.scaffold.ScaffoldRotationModel;
import net.wurstclient.opal.rotation.IRotationModel;

public final class InstantRotationModel implements IRotationModel {
    public static final InstantRotationModel INSTANCE = new InstantRotationModel();

    private InstantRotationModel() {
    }

    @Override
    public Rotation tick(Rotation from, Rotation to, float timeDelta) { // Interpolates between ticks, finishes rotation instantly tick-wise
        final float deltaYaw = Mth.wrapDegrees(to.yaw() - from.yaw()) * timeDelta;
        final float deltaPitch = (to.pitch() - from.pitch()) * timeDelta;
        return new Rotation(from.yaw() + deltaYaw, from.pitch() + deltaPitch);
    }

    @Override
    public ScaffoldRotationModel getEnum() {
        return ScaffoldRotationModel.INSTANT;
    }
}
