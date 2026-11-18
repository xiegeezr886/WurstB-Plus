package net.wurstclient.test;

import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;

/** Empty discovery context for settings/behavior tests without ModLauncher.
 * Does not validate loader events or mixins; those require a game launch. */
public final class NativeTestBootstrap {
  private NativeTestBootstrap() {}
  public static synchronized void bootstrap() {

        if (LoadingModList.get() == null)
          LoadingModList.of(java.util.List.of(), java.util.List.of(),
              java.util.List.of(), java.util.List.of(), java.util.Map.of());

    Bootstrap.bootStrap();
  }
}
