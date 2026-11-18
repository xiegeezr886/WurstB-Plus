package kaptainwutax.seedcrackerX.cracker.storage;

import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.Feature;
import com.seedfinding.mcfeature.Feature.Data;
import com.seedfinding.mcfeature.decorator.DesertWell;
import com.seedfinding.mcfeature.decorator.EndGateway;
import com.seedfinding.mcfeature.structure.BuriedTreasure;
import com.seedfinding.mcfeature.structure.OldStructure;
import com.seedfinding.mcfeature.structure.PillagerOutpost;
import com.seedfinding.mcfeature.structure.Shipwreck;
import com.seedfinding.mcfeature.structure.Structure;
import com.seedfinding.mcfeature.structure.TriangularStructure;
import com.seedfinding.mcfeature.structure.UniformStructure;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import kaptainwutax.seedcrackerX.config.ConfigScreen;
import kaptainwutax.seedcrackerX.cracker.BiomeData;
import kaptainwutax.seedcrackerX.cracker.DataAddedEvent;
import kaptainwutax.seedcrackerX.cracker.HashedSeedData;
import kaptainwutax.seedcrackerX.cracker.PillarData;
import kaptainwutax.seedcrackerX.cracker.decorator.Decorator;
import kaptainwutax.seedcrackerX.cracker.decorator.DeepDungeon;
import kaptainwutax.seedcrackerX.cracker.decorator.Dungeon;
import kaptainwutax.seedcrackerX.cracker.decorator.EmeraldOre;
import kaptainwutax.seedcrackerX.cracker.decorator.WarpedFungus;
import kaptainwutax.seedcrackerX.finder.BlockUpdateQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public class DataStorage {
   public static final Comparator<DataStorage.Entry<Data<?>>> SEED_DATA_COMPARATOR = (s1, s2) -> {
      boolean isStructure1 = s1.data.feature instanceof Structure;
      boolean isStructure2 = s2.data.feature instanceof Structure;
      if (isStructure1 != isStructure2) {
         return isStructure2 ? 1 : -1;
      }

      if (s1.equals(s2)) {
         return 0;
      }

      double diff = getBits(s2.data.feature, false) - getBits(s1.data.feature, false);
      return diff == 0.0 ? 1 : (int)Math.signum(diff);
   };
   public ScheduledSet<DataStorage.Entry<Data<?>>> baseSeedData = new ScheduledSet<>(SEED_DATA_COMPARATOR);
   public HashedSeedData hashedSeedData = null;
   public BlockUpdateQueue blockUpdateQueue = new BlockUpdateQueue();
   public boolean openGui = false;
   protected TimeMachine timeMachine = new TimeMachine(this);
   protected Set<Consumer<DataStorage>> scheduledData = ConcurrentHashMap.newKeySet();
   protected PillarData pillarData = null;
   protected ScheduledSet<DataStorage.Entry<BiomeData>> biomeSeedData = new ScheduledSet<>(null);

   public static double getBits(Feature<?, ?> feature, boolean decorators18) {
      if (feature instanceof UniformStructure<?> s) {
         return Math.log(s.getOffset() * s.getOffset()) / Math.log(2.0);
      } else if (feature instanceof TriangularStructure<?> s) {
         return Math.log(s.getPeak() * s.getPeak()) / Math.log(2.0);
      } else if (!decorators18 && feature instanceof Decorator && feature.getVersion().isNewerThan(MCVersion.v1_17_1)) {
         return 0.0;
      } else if (feature instanceof BuriedTreasure) {
         return Math.log(100.0) / Math.log(2.0);
      } else if (feature instanceof DesertWell) {
         return Math.log(256000.0) / Math.log(2.0);
      } else if (feature instanceof Dungeon) {
         return Math.log(8192.0) / Math.log(2.0);
      } else if (feature instanceof DeepDungeon) {
         return Math.log(3712.0) / Math.log(2.0);
      } else if (feature instanceof EmeraldOre) {
         return Math.log(3584.0) / Math.log(2.0);
      } else if (feature instanceof EndGateway) {
         return Math.log(1254400.0) / Math.log(2.0);
      } else if (feature instanceof WarpedFungus) {
         return 0.0;
      } else {
         throw new UnsupportedOperationException("go do implement bits count for " + feature.getName() + " you fool");
      }
   }

   public void tick() {
      if (this.openGui) {
         ConfigScreen configscreen = new ConfigScreen();
         Screen screen = configscreen.getConfigScreenByCloth(Minecraft.getInstance().gui.screen());
         Minecraft.getInstance().gui.setScreen(screen);
         this.openGui = false;
      }

      if (!this.timeMachine.isRunning) {
         this.baseSeedData.dump();
         this.biomeSeedData.dump();
         this.blockUpdateQueue.tick();
         this.timeMachine.isRunning = true;
         this.timeMachine.SERVICE.submit(() -> {
            try {
               this.scheduledData.removeIf(c -> {
                  c.accept(this);
                  return true;
               });
            } catch (Exception e) {
               e.printStackTrace();
            }

            this.timeMachine.isRunning = false;
         });
      }
   }

   public synchronized boolean addPillarData(PillarData data, DataAddedEvent event) {
      boolean isAdded = this.pillarData == null;
      if (isAdded && data != null) {
         this.pillarData = data;
         this.schedule(event::onDataAdded);
      }

      return isAdded;
   }

   public synchronized boolean addBaseData(Data<?> data, DataAddedEvent event) {
      DataStorage.Entry<Data<?>> e = new DataStorage.Entry<>(data, event);
      if (this.baseSeedData.contains(e)) {
         return false;
      }

      this.baseSeedData.scheduleAdd(e);
      this.schedule(event::onDataAdded);
      return true;
   }

   public synchronized boolean addBiomeData(BiomeData data, DataAddedEvent event) {
      DataStorage.Entry<BiomeData> e = new DataStorage.Entry<>(data, event);
      if (this.biomeSeedData.contains(e)) {
         return false;
      }

      this.biomeSeedData.scheduleAdd(e);
      this.schedule(event::onDataAdded);
      return true;
   }

   public synchronized boolean addHashedSeedData(HashedSeedData data, DataAddedEvent event) {
      if (this.hashedSeedData != null && this.hashedSeedData.getHashedSeed() == data.getHashedSeed()) {
         return false;
      }

      this.hashedSeedData = data;
      this.schedule(event::onDataAdded);
      return true;
   }

   public void schedule(Consumer<DataStorage> consumer) {
      this.scheduledData.add(consumer);
   }

   public TimeMachine getTimeMachine() {
      return this.timeMachine;
   }

   public double getBaseBits() {
      double bits = 0.0;

      for (DataStorage.Entry<Data<?>> e : this.baseSeedData) {
         if (!(e.data.feature instanceof PillagerOutpost)) {
            bits += getBits(e.data.feature, false);
         }
      }

      return bits;
   }

   public double getLiftingBits() {
      double bits = 0.0;

      for (DataStorage.Entry<Data<?>> e : this.baseSeedData) {
         if (e.data.feature instanceof OldStructure structure) {
            bits += Math.log(structure.getOffset() * structure.getOffset()) / Math.log(2.0);
         } else if (e.data.feature instanceof Shipwreck shipwreck) {
            bits += Math.log(shipwreck.getOffset() * shipwreck.getOffset()) / Math.log(2.0);
         }
      }

      return bits;
   }

   public double getDecoratorBits() {
      double bits = 0.0;

      for (DataStorage.Entry<Data<?>> e : this.baseSeedData) {
         if (e.data.feature instanceof Decorator decorator) {
            bits += getBits(decorator, true);
         }
      }

      return bits;
   }

   public double getWantedBits() {
      return 32.0;
   }

   public boolean notEnoughBiomeData() {
      return this.biomeSeedData.size() < 7;
   }

   public void clear() {
      this.scheduledData = ConcurrentHashMap.newKeySet();
      this.pillarData = null;
      this.baseSeedData = new ScheduledSet<>(SEED_DATA_COMPARATOR);
      this.biomeSeedData = new ScheduledSet<>(null);
      this.timeMachine.shouldTerminate = true;
      this.timeMachine = new TimeMachine(this);
      this.blockUpdateQueue = new BlockUpdateQueue();
   }

   public static class Entry<T> {
      public final T data;
      public final DataAddedEvent event;

      public Entry(T data, DataAddedEvent event) {
         this.data = data;
         this.event = event;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (!(o instanceof DataStorage.Entry<?> entry)) {
            return false;
         } else if (this.data instanceof Data<?> d1 && entry.data instanceof Data<?> d2) {
            return d1.feature == d2.feature && d1.chunkX == d2.chunkX && d1.chunkZ == d2.chunkZ;
         } else {
            return this.data instanceof BiomeData && entry.data instanceof BiomeData ? this.data.equals(entry.data) : false;
         }
      }

      @Override
      public int hashCode() {
         if (this.data instanceof Data) {
            return ((Data)this.data).chunkX * 31 + ((Data)this.data).chunkZ;
         } else {
            return this.data instanceof BiomeData ? this.data.hashCode() : super.hashCode();
         }
      }
   }
}
