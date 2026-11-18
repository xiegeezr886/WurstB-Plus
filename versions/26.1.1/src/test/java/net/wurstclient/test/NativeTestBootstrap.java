package net.wurstclient.test;

import net.minecraft.server.Bootstrap;
import net.minecraftforge.fml.loading.LoadingModList;

/** Empty discovery context for settings/behavior tests without ModLauncher.
 * Does not validate loader events or mixins; those require a game launch. */
public final class NativeTestBootstrap {
  private NativeTestBootstrap() {}
  public static synchronized void bootstrap() {
    try {

        var stateClass = Class.forName("net.minecraftforge.fml.loading.ModSorter$State");
        var constructor = stateClass.getDeclaredConstructor(java.util.List.class, java.util.List.class);
        constructor.setAccessible(true);
        var state = constructor.newInstance(java.util.List.of(), java.util.List.of());
        var listClass = Class.forName("net.minecraftforge.fml.loading.LoadingModListImpl");
        var temporary = listClass.getDeclaredField("temp");
        temporary.setAccessible(true);
        temporary.set(null, state);
        net.minecraftforge.fml.loading.LoadingModList.getMods();
        temporary.set(null, null);
        var layers = net.minecraftforge.fml.loading.FMLLoader.class.getDeclaredField("moduleLayerManager");
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
