package net.wurstclient.seed;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** OpenSeed's ore replay, using this game's registries/feature order instead of 26.x salts. */
public final class SeedOrePredictor {
  public record Ore(BlockPos pos, Block block) {}

  private record Spec(
      int step, int index, PlacedFeature feature, OreConfiguration config, boolean scattered) {}

  private record Model(
      HolderLookup.Provider registries, ChunkGenerator generator, List<Spec> specs) {}

  private record Job(int x, int z, Spec spec) {}

  private static CompletableFuture<HolderLookup.Provider> vanilla;
  private final ClientLevel level;
  private final long seed;
  private final Set<Block> wanted;
  private final ArrayDeque<Job> jobs = new ArrayDeque<>();
  private final LinkedHashMap<BlockPos, Ore> results = new LinkedHashMap<>();
  private CompletableFuture<Model> modelFuture;
  private Model model;
  private Job current;
  private WorldgenRandom random;
  private PlacementCursor<BlockPos> origins;
  private String error;
  private final int cx, cz, radius;

  public SeedOrePredictor(
      ClientLevel level, long seed, Set<Block> wanted, BlockPos center, int radius) {
    this.level = level;
    this.seed = seed;
    this.wanted = Set.copyOf(wanted);
    cx = center.getX() >> 4;
    cz = center.getZ() >> 4;
    this.radius = Math.min(4, Math.max(1, radius));
    if (!level.dimension().equals(Level.OVERWORLD) && !level.dimension().equals(Level.NETHER)) {
      error = "Only vanilla Overworld and Nether generation is supported";
      return;
    }
    synchronized (SeedOrePredictor.class) {
      if (vanilla == null) vanilla = CompletableFuture.supplyAsync(VanillaRegistries::createLookup);
    }
    ResourceKey<LevelStem> dimension =
        level.dimension().equals(Level.NETHER) ? LevelStem.NETHER : LevelStem.OVERWORLD;
    modelFuture =
        vanilla.thenApplyAsync(
            registries -> {
              var dimensions =
                  registries
                      .lookupOrThrow(Registries.WORLD_PRESET)
                      .getOrThrow(WorldPresets.NORMAL)
                      .value()
                      .createWorldDimensions();
              var generator = dimensions.dimensions().get(dimension).generator();
              var steps =
                  FeatureSorter.buildFeaturesPerStep(
                      new ArrayList<>(generator.getBiomeSource().possibleBiomes()),
                      biome -> generator.getBiomeGenerationSettings(biome).features(),
                      true);
              List<Spec> specs = new ArrayList<>();
              for (int step = 0; step < steps.size(); step++) {
                var features = steps.get(step).features();
                for (int index = 0; index < features.size(); index++) {
                  var placed = features.get(index);
                  var feature = placed.feature().value();
                  if (feature.config() instanceof OreConfiguration ore
                      && ore.targetStates.stream()
                          .anyMatch(t -> wanted.contains(t.state.getBlock())))
                    specs.add(
                        new Spec(
                            step,
                            index,
                            placed,
                            ore,
                            feature.feature() instanceof ScatteredOreFeature));
                }
              }
              return new Model(registries, generator, List.copyOf(specs));
            });
  }

  public List<Ore> results() {
    return List.copyOf(results.values());
  }

  public String error() {
    return error;
  }

  public boolean finished() {
    return error != null || (model != null && jobs.isEmpty() && origins == null);
  }

  /** At most five veins / ~3ms per tick. World reads stay on the client thread. */
  public void tick() {
    if (error != null) return;
    try {
      if (model == null) {
        if (!modelFuture.isDone()) return;
        model = modelFuture.join();
        for (int ring = 0; ring <= radius; ring++)
          for (int dx = -ring; dx <= ring; dx++)
            for (int dz = -ring; dz <= ring; dz++) {
              if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || !level.hasChunk(cx + dx, cz + dz))
                continue;
              for (Spec spec : model.specs) jobs.add(new Job(cx + dx, cz + dz, spec));
            }
      }
      long until = System.nanoTime() + 3_000_000;
      for (int i = 0; i < 5 && System.nanoTime() < until && results.size() < 8192; i++) {
        if (origins == null) {
          current = jobs.poll();
          if (current == null) return;
          random = new WorldgenRandom(new XoroshiroRandomSource(0));
          long decoration = random.setDecorationSeed(seed, current.x << 4, current.z << 4);
          random.setFeatureSeed(decoration, current.spec.index, current.spec.step);
          var context =
              new PlacementContext(
                  placementWorld(), model.generator, Optional.of(current.spec.feature));
          List<java.util.function.Function<BlockPos, Iterator<BlockPos>>> modifiers =
              new ArrayList<>();
          for (PlacementModifier modifier : current.spec.feature.placement())
            modifiers.add(pos -> modifier.getPositions(context, random, pos).iterator());
          origins =
              new PlacementCursor<>(
                  new BlockPos(current.x << 4, level.getMinBuildHeight(), current.z << 4),
                  modifiers);
        }
        BlockPos origin = origins.nextOrNull();
        if (origin == null) {
          origins = null;
          continue;
        }
        OpenSeedVeins.OreSink sink =
            (pos, block, color) -> {
              if (wanted.contains(block) && results.size() < 8192)
                results.putIfAbsent(pos, new Ore(pos, block));
            };
        if (current.spec.scattered)
          OpenSeedVeins.simulateScattered(level, current.spec.config, origin, random, 0, sink);
        else OpenSeedVeins.simulateOreVein(level, current.spec.config, origin, random, 0, sink);
      }
      if (results.size() >= 8192) {
        jobs.clear();
        origins = null;
      }
    } catch (Exception e) {
      error = e.toString();
      jobs.clear();
      origins = null;
      System.err.println("[SeedMine] Prediction failed: " + e);
    }
  }

  /** Read-only placement context. Generation never writes blocks into the actual client world. */
  private WorldGenLevel placementWorld() {
    return (WorldGenLevel)
        Proxy.newProxyInstance(
            WorldGenLevel.class.getClassLoader(),
            new Class<?>[] {WorldGenLevel.class},
            (proxy, method, args) -> {
              // Match descriptors, not Mojang method-name strings: these names change in release
              // jars.
              if (method.getReturnType() == Holder.class
                  && args != null
                  && args.length == 1
                  && args[0] instanceof BlockPos pos)
                return model
                    .registries
                    .lookupOrThrow(Registries.BIOME)
                    .getOrThrow(level.getBiome(pos).unwrapKey().orElseThrow());
              if (method.getReturnType() == long.class && method.getParameterCount() == 0)
                return seed;
              if (method.getDeclaringClass().isInstance(level)) return method.invoke(level, args);
              throw new UnsupportedOperationException("Unexpected worldgen read: " + method);
            });
  }
}
