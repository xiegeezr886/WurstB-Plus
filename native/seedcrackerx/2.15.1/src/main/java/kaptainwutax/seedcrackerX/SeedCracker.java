package kaptainwutax.seedcrackerX;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import kaptainwutax.seedcrackerX.api.SeedCrackerAPI;
import kaptainwutax.seedcrackerX.config.Config;
import kaptainwutax.seedcrackerX.cracker.storage.DataStorage;
import kaptainwutax.seedcrackerX.finder.FinderQueue;
import kaptainwutax.seedcrackerX.init.ClientCommands;
import net.wurstclient.seed.SeedPlatform;
import org.slf4j.Logger;

public class SeedCracker {
   public static final Logger LOGGER = LogUtils.getLogger();
   public static final ArrayList<SeedCrackerAPI> entrypoints = new ArrayList<>();
   private static SeedCracker INSTANCE;
   private final DataStorage dataStorage = new DataStorage();

   public static SeedCracker get() {
      return INSTANCE;
   }

   public void onInitialize() {
      INSTANCE = this;
      Config.load();
      Features.init(Config.get().getVersion());
      entrypoints.add(new net.wurstclient.seed.SeedCrackerBridge());
   }

   public DataStorage getDataStorage() {
      return this.dataStorage;
   }

   public void reset() {
      get().getDataStorage().clear();
      FinderQueue.get().finderControl.deleteFinders();
   }
}
