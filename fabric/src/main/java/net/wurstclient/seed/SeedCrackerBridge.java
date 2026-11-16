package net.wurstclient.seed;

import kaptainwutax.seedcrackerX.SeedCracker;
import kaptainwutax.seedcrackerX.api.SeedCrackerAPI;
import kaptainwutax.seedcrackerX.config.Config;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.wurstclient.WurstClient;
import net.wurstclient.util.ChatUtils;

/** Receives actual upstream cracking results, then returns to the Minecraft thread. */
public final class SeedCrackerBridge implements SeedCrackerAPI {
  public static final SeedSession SESSION = new SeedSession();

  public static void initialize() {
    // This integration is local. Do not opt the user into upstream database submissions.
    Config.get().databaseSubmits = false;
    Config.get().active = false;
    Config.get().render = Config.RenderType.OFF;
    ClientPlayConnectionEvents.DISCONNECT.register(
        (handler, client) -> {
          SESSION.clear();
          if (WurstClient.INSTANCE.getHax().seedCrackerHack.isEnabled())
            WurstClient.INSTANCE.getHax().seedCrackerHack.setEnabled(false);
          SeedCracker.get().reset();
          SeedCracker.get().getDataStorage().hashedSeedData = null;
        });
  }

  @Override
  public void pushWorldSeed(long seed) {
    long generation = SESSION.generation();
    Minecraft.getInstance()
        .execute(
            () -> {
              var mc = Minecraft.getInstance();
              if (mc.player == null || mc.level == null || !SESSION.accept(generation, seed))
                return;
              ChatUtils.message("SeedCrackerX: " + seed);
              WurstClient.INSTANCE.getHax().seedCrackerHack.onVerifiedSeed();
            });
  }
}
