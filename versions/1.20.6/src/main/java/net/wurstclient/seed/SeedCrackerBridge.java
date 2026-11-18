package net.wurstclient.seed;

import kaptainwutax.seedcrackerX.SeedCracker;
import kaptainwutax.seedcrackerX.api.SeedCrackerAPI;
import kaptainwutax.seedcrackerX.config.Config;
import kaptainwutax.seedcrackerX.config.StructureSave;
import kaptainwutax.seedcrackerX.cracker.HashedSeedData;
import kaptainwutax.seedcrackerX.cracker.DataAddedEvent;
import kaptainwutax.seedcrackerX.finder.ReloadFinders;
import net.minecraft.client.Minecraft;
import net.wurstclient.WurstClient;
import net.wurstclient.util.ChatUtils;

/** Native loader integration; cracking runs entirely on the local machine. */
public final class SeedCrackerBridge implements SeedCrackerAPI {
  public static final SeedSession SESSION = new SeedSession();
  public static void initialize() {
    new SeedCracker().onInitialize();
    Config.get().active = false;
    Config.get().render = Config.RenderType.OFF;
    Config.get().databaseSubmits = false;
  }
  public static void dimension(long hash) {
    var mc = Minecraft.getInstance();
    if (mc.level == null) return;
    StructureSave.beginSession();
    var dimension = mc.level.dimensionType();
    ReloadFinders.reloadHeight(dimension.minY(), dimension.minY() + dimension.logicalHeight());
    SeedCracker.get().getDataStorage().addHashedSeedData(new HashedSeedData(hash), DataAddedEvent.POKE_BIOMES);
  }
  public static void disconnect() {
    var hax = WurstClient.INSTANCE.getHax();
    if (SeedCracker.get() != null)
      StructureSave.saveStructures(SeedCracker.get().getDataStorage().baseSeedData);
    StructureSave.endSession();
    SESSION.clear();
    if (hax != null) {
      if (hax.seedCrackerHack.isEnabled()) hax.seedCrackerHack.setEnabled(false);
      if (hax.seedMineHack.isEnabled()) hax.seedMineHack.setEnabled(false);
    }
    if (SeedCracker.get() != null) {
      SeedCracker.get().reset();
      SeedCracker.get().getDataStorage().hashedSeedData = null;
    }
  }
  @Override
  public void pushWorldSeed(long seed) {
    long generation = SESSION.generation();
    Minecraft.getInstance().execute(() -> {
      var mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null || !SESSION.accept(generation, seed)) return;
      ChatUtils.message("SeedCrackerX: " + seed);
      WurstClient.INSTANCE.getHax().seedCrackerHack.onVerifiedSeed();
    });
  }
}
