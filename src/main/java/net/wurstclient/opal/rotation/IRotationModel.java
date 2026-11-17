/*
 * Ported directly from OpenOpal: client/feature/helper/impl/player/rotation/model/IRotationModel.java
 * Original source: GPL-3.0; see META-INF/licenses/openopal/LICENSE.
 * Minecraft names and host integration adapted for WurstB+ Plus.
 */
package net.wurstclient.opal.rotation;

import net.wurstclient.util.Rotation;
import net.wurstclient.hacks.scaffold.ScaffoldRotationModel;

public interface IRotationModel {
    Rotation tick(Rotation from, Rotation to, float timeDelta);
    ScaffoldRotationModel getEnum();
}
