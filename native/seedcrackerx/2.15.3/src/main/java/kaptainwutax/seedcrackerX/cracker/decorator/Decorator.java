package kaptainwutax.seedcrackerX.cracker.decorator;

import com.seedfinding.mcbiome.biome.Biome;
import com.seedfinding.mcbiome.source.BiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.Feature;
import com.seedfinding.mcterrain.TerrainGenerator;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.levelgen.WorldgenRandom;

public abstract class Decorator<C extends Decorator.Config, D extends Decorator.Data<?>> extends Feature<C, D> {
   public Decorator(C config, MCVersion version) {
      super(config, version);
   }

   public int getIndex(Biome biome) {
      return ((Decorator.Config)this.getConfig()).getSalt(biome) % 10000;
   }

   public int getStep(Biome biome) {
      return ((Decorator.Config)this.getConfig()).getSalt(biome) / 10000;
   }

   public boolean canStart(D data, long structureSeed, ChunkRand rand) {
      rand.setDecoratorSeed(structureSeed, data.chunkX << 4, data.chunkZ << 4, this.getIndex(data.biome), this.getStep(data.biome), this.getVersion());
      return true;
   }

   public boolean canStart(D data, long worldSeed, WorldgenRandom rand) {
      long l = rand.setDecorationSeed(worldSeed, data.chunkX << 4, data.chunkZ << 4);
      rand.setFeatureSeed(l, this.getIndex(data.biome), this.getStep(data.biome));
      return true;
   }

   public boolean canGenerate(D data, TerrainGenerator generator) {
      return true;
   }

   public final boolean canSpawn(D data, BiomeSource source) {
      return this.canSpawn(data.chunkX, data.chunkZ, source);
   }

   public boolean canSpawn(int chunkX, int chunkZ, BiomeSource source) {
      return this.getVersion().isOlderThan(MCVersion.v1_16)
         ? this.isValidBiome(source.getBiome((chunkX << 4) + 8, 0, (chunkZ << 4) + 8))
         : this.isValidBiome(source.getBiomeForNoiseGen((chunkX << 2) + 2, 0, (chunkZ << 2) + 2));
   }

   public abstract boolean isValidBiome(Biome var1);

   public static class Config extends com.seedfinding.mcfeature.Feature.Config {
      public final int defaultSalt;
      public final Map<Biome, Integer> salts = new HashMap<>();

      public Config(int step, int index) {
         this.defaultSalt = step * 10000 + index;
      }

      public Decorator.Config add(int step, int index, Biome... biomes) {
         for (Biome biome : biomes) {
            this.salts.put(biome, step * 10000 + index);
         }

         return this;
      }

      public int getSalt(Biome biome) {
         return this.salts.getOrDefault(biome, this.defaultSalt);
      }
   }

   public static class Data<T extends Decorator<?, ?>> extends com.seedfinding.mcfeature.Feature.Data<T> {
      public final Biome biome;

      public Data(T feature, int chunkX, int chunkZ, Biome biome) {
         super(feature, chunkX, chunkZ);
         this.biome = biome;
      }

      public boolean testStart(long worldSeed, WorldgenRandom rand) {
         return ((Decorator)this.feature).canStart(this, worldSeed, rand);
      }
   }
}
