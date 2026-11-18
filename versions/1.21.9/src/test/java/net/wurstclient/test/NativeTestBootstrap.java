package net.wurstclient.test;

import net.minecraft.server.Bootstrap;
import net.minecraftforge.fml.loading.LoadingModList;

/** Empty discovery context for settings/behavior tests without ModLauncher.
 * Does not validate loader events or mixins; those require a game launch. */
public final class NativeTestBootstrap {
  private NativeTestBootstrap() {}
  public static synchronized void bootstrap() {
    try {

        if (net.minecraftforge.fml.loading.FMLLoader.getLoadingModList() == null) {
          var list = LoadingModList.of(java.util.List.of(), java.util.List.of(), null);
          list.setBrokenFiles(java.util.List.of());
          var field = net.minecraftforge.fml.loading.FMLLoader.class
              .getDeclaredField("loadingModList");
          field.setAccessible(true);
          field.set(null, list);
        }
        var layers = net.minecraftforge.fml.loading.FMLLoader.class
            .getDeclaredField("moduleLayerManager");
        layers.setAccessible(true);
        if (layers.get(null) == null)
          layers.set(null, (cpw.mods.modlauncher.api.IModuleLayerManager)
              layer -> java.util.Optional.of(ModuleLayer.boot()));

    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Native unit-test bootstrap failed", e);
    }
    Bootstrap.bootStrap();
  }
}
