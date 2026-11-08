/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks.scaffold;

import net.wurstclient.util.Rotation;
import net.wurstclient.opal.rotation.*;

/** OpenOpal model names backed by its original per-instance implementations. */
public enum ScaffoldRotationModel
{
	INSTANT("Instant"), HEYPIXEL("Heypixel"), LINEAR("Linear"),
	ORGANIC("Organic"), SIDEWAYS("Sideways");
	private final String name;
	ScaffoldRotationModel(String name) { this.name = name; }
	@Override public String toString() { return name; }

	public IRotationModel create(float speed, double drift, double jitter)
	{
		return switch(this) {
			case INSTANT -> InstantRotationModel.INSTANCE;
			case HEYPIXEL -> new HeypixelRotationModel(speed);
			case LINEAR -> new LinearRotationModel(speed);
			case ORGANIC -> new OrganicRotationModel(speed, drift, jitter);
			case SIDEWAYS -> new SidewaysRotationModel(speed);
		};
	}

	public Rotation tick(Rotation from, Rotation to, float speed)
	{
		return create(speed, 1.2D, 0.12D).tick(from, to, 1.0F);
	}

	public Rotation tick(Rotation from, Rotation to, float speed,
		float timeDelta, double drift, double jitter)
	{
		return create(speed, drift, jitter).tick(from, to, timeDelta);
	}

	public static Rotation tickHypixel(Rotation from, Rotation to,
		float timeDelta, boolean diagonalYaw, int airTicks)
	{
		return new HypixelRotationModel(diagonalYaw, airTicks).tick(from, to, timeDelta);
	}
}
