/* SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.leaves;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.BlockGetter;

/** Minecraft methods use mapped interfaces, not development names in reflection. */
public final class LitematicaBridge {
  private static Method getWorld;
  private static boolean attempted;

  private LitematicaBridge() {}

  public static BlockGetter getWorld() {
    if (!attempted) {
      attempted = true;
      if (!FabricLoader.getInstance().isModLoaded("litematica")) return null;
      try {
        getWorld =
            Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler")
                .getMethod("getSchematicWorld");
      } catch (ReflectiveOperationException | LinkageError e) {
        return null;
      }
    }
    if (getWorld == null) return null;
    try {
      Object world = getWorld.invoke(null);
      return world instanceof BlockGetter getter ? getter : null;
    } catch (ReflectiveOperationException | LinkageError e) {
      return null;
    }
  }
}
