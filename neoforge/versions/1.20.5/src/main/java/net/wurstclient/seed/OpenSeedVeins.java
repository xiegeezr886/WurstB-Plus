/* Ore vein replay adapted from kuxiaole/OpenSeed OreXrayManager.
 * Provenance and upstream licensing limits: META-INF/licenses/openseed/NOTICE.txt. */
package net.wurstclient.seed;

import java.util.BitSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

public final class OpenSeedVeins {
  private OpenSeedVeins() {}

  public interface OreSink {
    void accept(BlockPos pos, Block block, int color);
  }

  public static void simulateOreVein(
      Level level,
      OreConfiguration config,
      BlockPos origin,
      RandomSource rand,
      int color,
      OreSink sink) {
    int size = config.size;
    float g = (float) size / 8.0f;
    int yRadius = Mth.ceil((float) (((float) size / 16.0f * 2.0f + 1.0f) / 2.0f));
    float f = rand.nextFloat() * (float) Math.PI;
    double sin = Mth.sin(f);
    double cos = Mth.cos(f);
    double x1 = (double) origin.getX() + sin * (double) g;
    double x2 = (double) origin.getX() - sin * (double) g;
    double z1 = (double) origin.getZ() + cos * (double) g;
    double z2 = (double) origin.getZ() - cos * (double) g;
    double y1 = origin.getY() + rand.nextInt(3) - 2;
    double y2 = origin.getY() + rand.nextInt(3) - 2;
    int xMin = origin.getX() - Mth.ceil((float) g) - yRadius;
    int yMin = origin.getY() - 2 - yRadius;
    int zMin = origin.getZ() - Mth.ceil((float) g) - yRadius;
    int rangeXZ = 2 * (Mth.ceil((float) g) + yRadius);
    int rangeY = 2 * (2 + yRadius);
    boolean doPlace = false;
    BlockPos.MutableBlockPos gatePos = new BlockPos.MutableBlockPos();
    int worldMinYGate = level.getMinBuildHeight();
    int worldMaxYGate = level.getMaxBuildHeight();
    int gateFloor = Math.max(yMin, worldMinYGate);
    for (int lx = 0; lx <= rangeXZ && !doPlace; ++lx) {
      for (int lz = 0; lz <= rangeXZ && !doPlace; ++lz) {
        int wx = xMin + lx;
        int wz = zMin + lz;
        if (level.getChunk(wx >> 4, wz >> 4, ChunkStatus.FULL, false) == null) continue;
        int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
        if (h <= worldMinYGate) {
          h = worldMinYGate;
          for (int wy = worldMaxYGate - 1; wy >= gateFloor; --wy) {
            gatePos.set(wx, wy, wz);
            BlockState st = level.getBlockState((BlockPos) gatePos);
            if (st.isAir() || st.is(Blocks.WATER)) continue;
            h = wy + 1;
            break;
          }
        } else {
          while (h - 1 >= gateFloor) {
            gatePos.set(wx, h - 1, wz);
            if (level.getBlockState((BlockPos) gatePos).is(Blocks.WATER)) {
              --h;
              continue;
            }
            break;
          }
        }
        if (yMin > h) continue;
        doPlace = true;
      }
    }
    if (!doPlace) {
      return;
    }
    double[] px = new double[size];
    double[] py = new double[size];
    double[] pz = new double[size];
    double[] pr = new double[size];
    for (int j = 0; j < size; ++j) {
      double t = (double) j / (double) size;
      px[j] = Mth.lerp((double) t, (double) x1, (double) x2);
      py[j] = Mth.lerp((double) t, (double) y1, (double) y2);
      pz[j] = Mth.lerp((double) t, (double) z1, (double) z2);
      double d3 = rand.nextDouble() * (double) size / 16.0;
      pr[j] =
          ((double) (Mth.sin((float) ((float) (3.1415927410125732 * t))) + 1.0f) * d3 + 1.0) / 2.0;
    }
    for (int k = 0; k < size - 1; ++k) {
      if (pr[k] <= 0.0) continue;
      for (int l = k + 1; l < size; ++l) {
        double dz;
        double dy;
        double dx;
        double dw;
        if (pr[l] <= 0.0
            || !((dw = pr[k] - pr[l]) * dw
                > (dx = px[k] - px[l]) * dx
                    + (dy = py[k] - py[l]) * dy
                    + (dz = pz[k] - pz[l]) * dz)) continue;
        if (dw > 0.0) {
          pr[l] = -1.0;
          continue;
        }
        pr[k] = -1.0;
      }
    }
    BitSet placed = new BitSet(rangeXZ * rangeXZ * rangeY);
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    int worldMinY = level.getMinBuildHeight();
    int worldMaxY = level.getMaxBuildHeight();
    float discard = config.discardChanceOnAirExposure;
    for (int s = 0; s < size; ++s) {
      double r = pr[s];
      if (r < 0.0) continue;
      int x0 = Math.max(Mth.floor((double) (px[s] - r)), xMin);
      int y0 = Math.max(Mth.floor((double) (py[s] - r)), yMin);
      int z0 = Math.max(Mth.floor((double) (pz[s] - r)), zMin);
      int x1b = Math.max(Mth.floor((double) (px[s] + r)), x0);
      int y1b = Math.max(Mth.floor((double) (py[s] + r)), y0);
      int z1b = Math.max(Mth.floor((double) (pz[s] + r)), z0);
      for (int bx = x0; bx <= x1b; ++bx) {
        double dx = ((double) bx + 0.5 - px[s]) / r;
        double dx2 = dx * dx;
        if (dx2 >= 1.0) continue;
        for (int by = y0; by <= y1b; ++by) {
          double dy;
          double dxy2;
          if (by < worldMinY
              || by >= worldMaxY
              || (dxy2 = dx2 + (dy = ((double) by + 0.5 - py[s]) / r) * dy) >= 1.0) continue;
          block10:
          for (int bz = z0; bz <= z1b; ++bz) {
            int bit;
            double dz = ((double) bz + 0.5 - pz[s]) / r;
            if (dxy2 + dz * dz >= 1.0
                || placed.get(
                    bit = bx - xMin + (by - yMin) * rangeXZ + (bz - zMin) * rangeXZ * rangeY))
              continue;
            placed.set(bit);
            if (level.getChunk(bx >> 4, bz >> 4, ChunkStatus.FULL, false) == null) continue;
            pos.set(bx, by, bz);
            BlockState state = level.getBlockState((BlockPos) pos);
            for (OreConfiguration.TargetBlockState target : config.targetStates) {
              boolean isOre = state.getBlock() == target.state.getBlock();
              boolean replaceable = target.target.test(state, (RandomSource) rand);
              if (!isOre && !replaceable) continue;
              if (discard > 0.0f && discard < 1.0f) {
                rand.nextFloat();
              }
              sink.accept(pos.immutable(), target.state.getBlock(), color);
              continue block10;
            }
          }
        }
      }
    }
  }

  public static void simulateScattered(
      Level level,
      OreConfiguration config,
      BlockPos origin,
      RandomSource rand,
      int color,
      OreSink sink) {
    int veinCount = rand.nextInt(config.size + 1);
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    float discard = config.discardChanceOnAirExposure;
    block0:
    for (int i = 0; i < veinCount; ++i) {
      int dist = Math.min(i, 7);
      int dx = Math.round((rand.nextFloat() - rand.nextFloat()) * (float) dist);
      int dy = Math.round((rand.nextFloat() - rand.nextFloat()) * (float) dist);
      int dz = Math.round((rand.nextFloat() - rand.nextFloat()) * (float) dist);
      pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
      if (level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) == null)
        continue;
      BlockState state = level.getBlockState((BlockPos) pos);
      for (OreConfiguration.TargetBlockState target : config.targetStates) {
        boolean isOre = state.getBlock() == target.state.getBlock();
        boolean replaceable = target.target.test(state, (RandomSource) rand);
        if (!isOre && !replaceable) continue;
        if (discard > 0.0f && discard < 1.0f) {
          rand.nextFloat();
        }
        sink.accept(pos.immutable(), target.state.getBlock(), color);
        continue block0;
      }
    }
  }
}
