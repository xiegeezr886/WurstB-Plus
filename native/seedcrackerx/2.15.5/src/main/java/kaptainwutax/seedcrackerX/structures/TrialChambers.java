package kaptainwutax.seedcrackerX.structures;

import com.seedfinding.mcbiome.biome.Biome;
import com.seedfinding.mccore.state.Dimension;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mccore.version.VersionMap;
import com.seedfinding.mcfeature.structure.UniformStructure;
import com.seedfinding.mcfeature.structure.RegionStructure.Config;

public class TrialChambers extends UniformStructure<TrialChambers> {
   public static final VersionMap<Config> CONFIGS = new VersionMap().add(MCVersion.v1_21, new Config(34, 12, 94251327));

   public TrialChambers(MCVersion version) {
      this((Config)CONFIGS.getAsOf(version), version);
   }

   public TrialChambers(Config config, MCVersion version) {
      super(config, version);
   }

   public static String name() {
      return "trial_chambers";
   }

   public Dimension getValidDimension() {
      return Dimension.OVERWORLD;
   }

   public boolean isValidBiome(Biome biome) {
      return true;
   }
}
