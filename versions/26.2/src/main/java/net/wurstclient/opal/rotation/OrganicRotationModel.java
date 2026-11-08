/*
 * Ported directly from OpenOpal: client/feature/helper/impl/player/rotation/model/impl/OrganicRotationModel.java
 * Original source: GPL-3.0; see META-INF/licenses/openopal/LICENSE.
 * Minecraft names and host integration adapted for WurstB+ Plus.
 */
package net.wurstclient.opal.rotation;

import net.minecraft.util.Mth;
import net.wurstclient.util.Rotation;
import net.wurstclient.hacks.scaffold.ScaffoldRotationModel;
import net.wurstclient.opal.rotation.IRotationModel;
import net.wurstclient.opal.RandomUtility;
import net.wurstclient.hacks.scaffold.ScaffoldUtils;

import java.util.Random;

public final class OrganicRotationModel implements IRotationModel {

    private final double speed;
    private final double driftIntensity;
    private final double jitterIntensity;

    private final double freqYaw1, freqYaw2, freqPitch1, freqPitch2;
    private final double phaseYaw1, phaseYaw2, phasePitch1, phasePitch2;
    private double timeAccumulator;

    private final Random random;

    public OrganicRotationModel(final double speed, final double driftIntensity, final double jitterIntensity) {
        this.speed = speed;
        this.driftIntensity = driftIntensity;
        this.jitterIntensity = jitterIntensity;

        random = new Random(System.nanoTime());
        this.freqYaw1 = random.nextDouble() * 0.3 + 0.1;
        this.freqYaw2 = random.nextDouble() * 0.5 + 0.5;
        this.freqPitch1 = random.nextDouble() * 0.3 + 0.1;
        this.freqPitch2 = random.nextDouble() * 0.5 + 0.5;
        this.phaseYaw1 = random.nextDouble() * Math.PI * 2;
        this.phaseYaw2 = random.nextDouble() * Math.PI * 2;
        this.phasePitch1 = random.nextDouble() * Math.PI * 2;
        this.phasePitch2 = random.nextDouble() * Math.PI * 2;
        this.timeAccumulator = 0.0;
    }

    @Override
    public Rotation tick(Rotation from, Rotation to, float timeDelta) {
        final float rawYaw = Mth.wrapDegrees(to.yaw() - from.yaw());
        final float rawPitch = to.pitch() - from.pitch();
        float deltaYaw = rawYaw * timeDelta;
        float deltaPitch = rawPitch * timeDelta;

        final double distance = Math.hypot(deltaYaw, deltaPitch);
        if (distance < driftIntensity) {
            return new Rotation(from.yaw() + deltaYaw, from.pitch() + deltaPitch);
        }

        if (distance > 0) {
            final double ratioYaw = Math.abs(deltaYaw) / distance;
            final double ratioPitch = Math.abs(deltaPitch) / distance;
            final double maxYaw = speed * ratioYaw * timeDelta;
            final double maxPitch = speed * ratioPitch * timeDelta;
            deltaYaw = Mth.clamp(deltaYaw, (float)-maxYaw, (float)maxYaw);
            deltaPitch = Mth.clamp(deltaPitch, (float)-maxPitch, (float)maxPitch);
        }

        timeAccumulator += timeDelta;

        final double sinYaw = Math.sin(timeAccumulator * freqYaw1 + phaseYaw1)
                + RandomUtility.getRandomDouble(0.45, 0.55) * Math.sin(timeAccumulator * freqYaw2 + phaseYaw2);
        final double sinPitch = Math.sin(timeAccumulator * freqPitch1 + phasePitch1)
                + RandomUtility.getRandomDouble(0.45, 0.55) * Math.sin(timeAccumulator * freqPitch2 + phasePitch2);
        final double driftYaw = sinYaw * driftIntensity * timeDelta;
        final double driftPitch = sinPitch * driftIntensity * timeDelta;

        final double jitterYaw = (random.nextDouble() * 2 - 1) * jitterIntensity * timeDelta;
        final double jitterPitch = (random.nextDouble() * 2 - 1) * jitterIntensity * timeDelta;

        final float moveYaw = deltaYaw + (float) driftYaw + (float) jitterYaw;
        final float movePitch = deltaPitch + (float) driftPitch + (float) jitterPitch;

        final Rotation rotation = new Rotation(from.yaw() + moveYaw, from.pitch() + movePitch);

        return ScaffoldUtils.patchConstantRotation(rotation, from);
    }

    @Override
    public ScaffoldRotationModel getEnum() {
        return ScaffoldRotationModel.ORGANIC;
    }
}
