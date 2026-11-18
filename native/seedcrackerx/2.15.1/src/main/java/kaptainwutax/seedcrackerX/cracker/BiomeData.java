package kaptainwutax.seedcrackerX.cracker;

import com.seedfinding.mcbiome.biome.Biome;
import com.seedfinding.mcbiome.source.BiomeSource;
import com.seedfinding.mccore.version.MCVersion;
import kaptainwutax.seedcrackerX.config.Config;

public class BiomeData {
   public final Biome biome;
   public final int x;
   public final int z;

   public BiomeData(Biome biome, int x, int z) {
      this.biome = biome;
      this.x = x;
      this.z = z;
   }

   public boolean test(BiomeSource source) {
      return Config.get().getVersion().isNewerOrEqualTo(MCVersion.v1_15)
         ? source.getBiomeForNoiseGen(this.x, 0, this.z) == this.biome
         : source.getBiome(this.x, 0, this.z) == this.biome;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else {
         return !(o instanceof BiomeData data) ? false : this.biome == data.biome;
      }
   }

   @Override
   public int hashCode() {
      return this.biome.getName().hashCode();
   }
}
