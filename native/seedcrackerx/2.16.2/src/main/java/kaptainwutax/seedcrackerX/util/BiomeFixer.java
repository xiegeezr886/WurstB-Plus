package kaptainwutax.seedcrackerX.util;

import com.seedfinding.mcbiome.biome.Biomes;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

public class BiomeFixer {
   private static final Map<String, com.seedfinding.mcbiome.biome.Biome> COMPATREGISTRY = new HashMap<>();

   public static com.seedfinding.mcbiome.biome.Biome swap(Biome biome) {
      ClientPacketListener clientPacketListener = Minecraft.getInstance().getConnection();
      if (clientPacketListener == null) {
         return Biomes.VOID;
      }

      Identifier biomeID = clientPacketListener.registryAccess().lookup(Registries.BIOME).map(reg -> reg.getKey(biome)).orElse(null);
      return biomeID == null ? Biomes.THE_VOID : COMPATREGISTRY.getOrDefault(biomeID.getPath(), Biomes.VOID);
   }

   public static Biome swap(com.seedfinding.mcbiome.biome.Biome biome) {
      RegistryLookup<Biome> biomeRegistries = VanillaRegistries.createWorldLookup().lookupOrThrow(Registries.BIOME);
      return (Biome)biomeRegistries.get(ResourceKey.create(Registries.BIOME, Identifier.withDefaultNamespace(biome.getName())))
         .orElse(biomeRegistries.getOrThrow(net.minecraft.world.level.biome.Biomes.THE_VOID))
         .value();
   }

   static {
      for (com.seedfinding.mcbiome.biome.Biome biome : Biomes.REGISTRY.values()) {
         COMPATREGISTRY.put(biome.getName(), biome);
      }

      COMPATREGISTRY.put("snowy_plains", Biomes.SNOWY_TUNDRA);
      COMPATREGISTRY.put("old_growth_birch_forest", Biomes.TALL_BIRCH_FOREST);
      COMPATREGISTRY.put("old_growth_pine_taiga", Biomes.GIANT_TREE_TAIGA);
      COMPATREGISTRY.put("old_growth_spruce_taiga", Biomes.GIANT_TREE_TAIGA);
      COMPATREGISTRY.put("windswept_hills", Biomes.EXTREME_HILLS);
      COMPATREGISTRY.put("windswept_forest", Biomes.WOODED_MOUNTAINS);
      COMPATREGISTRY.put("windswept_gravelly_hills", Biomes.GRAVELLY_MOUNTAINS);
      COMPATREGISTRY.put("windswept_savanna", Biomes.SHATTERED_SAVANNA);
      COMPATREGISTRY.put("sparse_jungle", Biomes.JUNGLE_EDGE);
      COMPATREGISTRY.put("stony_shore", Biomes.STONE_SHORE);
      COMPATREGISTRY.put("meadow", Biomes.PLAINS);
      COMPATREGISTRY.put("grove", Biomes.TAIGA);
      COMPATREGISTRY.put("snowy_slopes", Biomes.SNOWY_TUNDRA);
      COMPATREGISTRY.put("frozen_peaks", Biomes.TAIGA);
      COMPATREGISTRY.put("jagged_peaks", Biomes.TAIGA);
      COMPATREGISTRY.put("stony_peaks", Biomes.TAIGA);
      COMPATREGISTRY.put("mangrove_swamp", Biomes.SWAMP);
   }
}
