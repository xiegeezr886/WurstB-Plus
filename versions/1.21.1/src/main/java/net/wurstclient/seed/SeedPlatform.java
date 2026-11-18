package net.wurstclient.seed;

public final class SeedPlatform {
  private SeedPlatform() {}
  public static java.nio.file.Path configDir() {
    return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();
  }
}
