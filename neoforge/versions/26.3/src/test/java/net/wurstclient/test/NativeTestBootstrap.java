package net.wurstclient.test;

import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;

/** Empty discovery context for settings/behavior tests without ModLauncher.
 * Does not validate loader events or mixins; those require a game launch. */
public final class NativeTestBootstrap {
  private NativeTestBootstrap() {}
  public static synchronized void bootstrap() {
    try {

        var loaderClass = net.neoforged.fml.loading.FMLLoader.class;
        if (net.neoforged.fml.loading.FMLLoader.getCurrentOrNull() == null) {
          var constructor = loaderClass.getDeclaredConstructor(ClassLoader.class,
              String[].class, net.neoforged.api.distmarker.Dist.class,
              boolean.class, java.nio.file.Path.class);
          constructor.setAccessible(true);
          constructor.newInstance(Thread.currentThread().getContextClassLoader(),
              new String[]{"--fml.neoForgeVersion", "test", "--fml.mcVersion", "26.3",
                  "--fml.neoFormVersion", "test"},
              net.neoforged.api.distmarker.Dist.CLIENT, false, java.nio.file.Path.of("."));
        }
        var loadingList = loaderClass.getDeclaredField("loadingModList");
        loadingList.setAccessible(true);
        var loader = net.neoforged.fml.loading.FMLLoader.getCurrent();
        if (loadingList.get(loader) == null)
          loadingList.set(loader, LoadingModList.of(java.util.List.of(), java.util.List.of(),
              java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.Map.of()));

    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Native unit-test bootstrap failed", e);
    }
    net.minecraft.SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
  }
}
