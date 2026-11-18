package kaptainwutax.seedcrackerX.finder.decorator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import kaptainwutax.seedcrackerX.Features;
import kaptainwutax.seedcrackerX.SeedCracker;
import kaptainwutax.seedcrackerX.cracker.decorator.FullFungusData;
import kaptainwutax.seedcrackerX.cracker.decorator.WarpedFungus;
import kaptainwutax.seedcrackerX.finder.BlockFinder;
import kaptainwutax.seedcrackerX.finder.Finder;
import kaptainwutax.seedcrackerX.render.Cuboid;
import kaptainwutax.seedcrackerX.util.BiomeFixer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WarpedFungusFinder extends BlockFinder {
   private static final Logger logger = LoggerFactory.getLogger("warpedFungusFinder");
   private static final Predicate<Block> prdc = block -> block == Blocks.SHROOMLIGHT || block == Blocks.WARPED_WART_BLOCK;

   public WarpedFungusFinder(Level world, ChunkPos chunkPos) {
      super(world, chunkPos, Blocks.WARPED_STEM);
      this.searchPositions = CHUNK_POSITIONS;
   }

   public static List<Finder> create(Level world, ChunkPos chunkPos) {
      List<Finder> finders = new ArrayList<>();
      finders.add(new WarpedFungusFinder(world, chunkPos));
      finders.add(new WarpedFungusFinder(world, new ChunkPos(chunkPos.x() - 1, chunkPos.z())));
      finders.add(new WarpedFungusFinder(world, new ChunkPos(chunkPos.x(), chunkPos.z() - 1)));
      finders.add(new WarpedFungusFinder(world, new ChunkPos(chunkPos.x() - 1, chunkPos.z() - 1)));
      finders.add(new WarpedFungusFinder(world, new ChunkPos(chunkPos.x() + 1, chunkPos.z())));
      finders.add(new WarpedFungusFinder(world, new ChunkPos(chunkPos.x(), chunkPos.z() + 1)));
      finders.add(new WarpedFungusFinder(world, new ChunkPos(chunkPos.x() + 1, chunkPos.z() + 1)));
      finders.add(new WarpedFungusFinder(world, new ChunkPos(chunkPos.x() + 1, chunkPos.z() - 1)));
      finders.add(new WarpedFungusFinder(world, new ChunkPos(chunkPos.x() - 1, chunkPos.z() + 1)));
      return finders;
   }

   @Override
   public List<BlockPos> findInChunk() {
      Biome biome = (Biome)this.world.getNoiseBiome((this.chunkPos.x() << 2) + 2, 64, (this.chunkPos.z() << 2) + 2).value();
      List<BlockPos> newResult = new ArrayList<>();
      if (!Features.WARPED_FUNGUS.isValidBiome(BiomeFixer.swap(biome))) {
         return new ArrayList<>();
      }

      List<BlockPos> result = super.findInChunk();
      List<FullFungusData> fullFungusData = new ArrayList<>();
      List<BoundingBox> renderBox = new ArrayList<>();
      result.removeIf(posx -> {
         Block soil = this.world.getBlockState(posx.below()).getBlock();
         if (soil != Blocks.NETHERRACK && soil != Blocks.WARPED_NYLIUM) {
            return true;
         }

         int bigCount = 0;
         if (this.world.getBlockState(posx.north()).getBlock() == Blocks.WARPED_STEM) {
            bigCount++;
         }

         if (this.world.getBlockState(posx.east()).getBlock() == Blocks.WARPED_STEM) {
            bigCount++;
         }

         if (this.world.getBlockState(posx.west()).getBlock() == Blocks.WARPED_STEM) {
            bigCount++;
         }

         if (this.world.getBlockState(posx.south()).getBlock() == Blocks.WARPED_STEM) {
            bigCount++;
         }

         if (bigCount != 0 && bigCount != 4) {
            return true;
         }

         boolean big = bigCount == 4;
         newResult.add(posx);
         MutableBlockPos trunk = posx.mutable();
         int i = 0;

         while (this.world.getBlockState(trunk.offset(0, i, 0)).getBlock() == Blocks.WARPED_STEM) {
            i++;
         }

         if (i >= 4 && i <= 26 && (i <= 14 || i % 2 != 1)) {
            int dataCounter = 0;
            ArrayList<Integer> bigTrunkData = new ArrayList<>();
            if (big) {
               dataCounter++;

               for (int x = -1; x < 2; x += 2) {
                  for (int z = -1; z < 2; z += 2) {
                     for (int height = 0; height < i; height++) {
                        Block block = this.world.getBlockState(trunk.offset(x, height, z)).getBlock();
                        if (block == Blocks.WARPED_STEM) {
                           dataCounter++;
                           bigTrunkData.add(1);
                        } else if (this.isNetherPlant(block) || block == Blocks.WARPED_WART_BLOCK || block == Blocks.SHROOMLIGHT || block == Blocks.AIR) {
                           bigTrunkData.add(0);
                        }
                     }
                  }
               }
            }

            int height = i;
            ArrayList<Integer> layerSizes = new ArrayList<>();
            int upperLayerSize = 1;

            boolean validLayer;
            do {
               validLayer = false;

               for (int layerSize = upperLayerSize + 1; upperLayerSize - 1 <= layerSize && layerSize >= 1; layerSize--) {
                  int countRing = this.countRing(posx.offset(0, i, 0), layerSize);
                  if (countRing == 0) {
                     i--;
                     layerSizes.add(layerSize);
                     upperLayerSize = layerSize;
                     validLayer = true;
                     break;
                  }

                  if (countRing == 3) {
                     return false;
                  }

                  if (countRing == 2) {
                     break;
                  }
               }
            } while (validLayer);

            if (layerSizes.size() < 2) {
               return false;
            }

            int heeeight = height;
            int ring = this.countRing(posx.offset(0, i, 0), upperLayerSize + 1);
            int vineRingSize = upperLayerSize;
            if (ring != 0 && ring != 2) {
               if (ring == 3) {
                  return false;
               }
            } else {
               vineRingSize++;
            }

            for (int y = i - 2; y <= i; y++) {
               for (int x = -vineRingSize + 1; x < vineRingSize; x++) {
                  for (int z = -vineRingSize + 1; z < vineRingSize; z++) {
                     if ((!big || -2 >= x || x >= 2 || -2 >= z || z >= 2) && (x != 0 || z != 0)) {
                        Block block = this.world.getBlockState(posx.offset(x, y, z)).getBlock();
                        if (this.isNetherPlant(block) && block != Blocks.AIR) {
                           return false;
                        }
                     }
                  }
               }
            }

            ArrayList<Integer> vine = new ArrayList<>();

            for (int x = -vineRingSize; x <= vineRingSize; x++) {
               for (int z = -vineRingSize; z <= vineRingSize; z++) {
                  if (Math.abs(x) == vineRingSize || Math.abs(z) == vineRingSize) {
                     boolean isVine = false;

                     for (int y = i - 2; y <= i; y++) {
                        Block block = this.world.getBlockState(posx.offset(x, y, z)).getBlock();
                        if (block == Blocks.WARPED_WART_BLOCK) {
                           if (!isVine) {
                              vine.add(i - y + 1);
                              isVine = true;
                              dataCounter++;
                           }
                        } else {
                           if (this.isNetherPlant(block) && block != Blocks.AIR || isVine) {
                              return false;
                           }

                           if (y == i) {
                              vine.add(0);
                           }
                        }
                     }
                  }
               }
            }

            Collections.reverse(layerSizes);
            int counter = 0;
            int[][][] layers = new int[layerSizes.size()][11][11];

            for (int y = i + 1; y <= height; y++) {
               int layerSize = layerSizes.get(counter);

               for (int x = -layerSize; x <= layerSize; x++) {
                  for (int z = -layerSize; z <= layerSize; z++) {
                     Block block = this.world.getBlockState(posx.offset(x, y, z)).getBlock();
                     int blocktype = -1;
                     byte var46;
                     if (block == Blocks.AIR) {
                        var46 = 0;
                     } else if (block == Blocks.WARPED_WART_BLOCK) {
                        var46 = 1;
                     } else if (block == Blocks.SHROOMLIGHT) {
                        dataCounter++;
                        var46 = 2;
                     } else if (block == Blocks.WARPED_STEM) {
                        var46 = 3;
                     } else {
                        var46 = 0;
                        logger.error("error found illegal Block: " + block.getName() + " at " + posx.offset(x, y, z).toShortString());
                     }

                     layers[counter][x + layerSize][z + layerSize] = var46;
                  }
               }

               counter++;
            }

            if (dataCounter < 13) {
               return true;
            }

            FullFungusData fungusData = new FullFungusData(layerSizes, layers, vine, big, height, vineRingSize, bigTrunkData, dataCounter);
            fullFungusData.add(fungusData);
            Collections.reverse(layerSizes);

            for (int layerSize : layerSizes) {
               renderBox.add(BoundingBox.fromCorners(posx.offset(layerSize + 1, heeeight, layerSize + 1), posx.offset(-layerSize, heeeight + 1, -layerSize)));
               heeeight--;
            }

            renderBox.add(BoundingBox.fromCorners(posx.offset(-vineRingSize + 1, i - 2, -vineRingSize + 1), posx.offset(vineRingSize, i + 1, vineRingSize)));
            renderBox.add(BoundingBox.fromCorners(posx.offset(-vineRingSize, i - 2, -vineRingSize), posx.offset(vineRingSize + 1, i + 1, vineRingSize + 1)));
            return false;
         } else {
            return false;
         }
      });
      if (fullFungusData.isEmpty()) {
         return new ArrayList<>();
      }

      if (newResult.size() < 5) {
         return new ArrayList<>();
      }

      FullFungusData bestFungus = FullFungusData.getBestFungus(fullFungusData);
      if (bestFungus == null) {
         return new ArrayList<>();
      }

      WarpedFungus.Data data = Features.WARPED_FUNGUS
         .at(this.chunkPos.getMinBlockX(), this.chunkPos.getMinBlockZ(), BiomeFixer.swap(biome), newResult, bestFungus);
      if (SeedCracker.get().getDataStorage().addBaseData(data, data::onDataAdded)) {
         for (BoundingBox box : renderBox) {
            this.cuboids.add(new Cuboid(box, ARGB.color(0, 255, 255)));
         }

         for (BlockPos pos : newResult) {
            this.cuboids.add(new Cuboid(pos, ARGB.color(255, 30, 0)));
         }
      }

      return newResult;
   }

   private int countRing(BlockPos pos, int ringSize) {
      int counter = 0;
      int deco = 0;

      for (int x = -ringSize; x <= ringSize; x++) {
         for (int z = -ringSize; z <= ringSize; z++) {
            if (Math.abs(x) == ringSize || Math.abs(z) == ringSize) {
               counter++;
               Block block = this.world.getBlockState(pos.offset(x, 0, z)).getBlock();
               if (prdc.test(block)) {
                  deco++;
               } else if (block != Blocks.AIR) {
                  return 3;
               }
            }
         }
      }

      if (counter / 2 < deco) {
         return 0;
      } else {
         return deco == 0 ? 1 : 2;
      }
   }

   private boolean isNetherPlant(Block block) {
      return block == Blocks.TWISTING_VINES
         || block == Blocks.TWISTING_VINES_PLANT
         || block == Blocks.NETHER_SPROUTS
         || block == Blocks.WARPED_ROOTS
         || block == Blocks.WARPED_FUNGUS
         || block == Blocks.CRIMSON_FUNGUS
         || block == Blocks.CRIMSON_ROOTS;
   }

   @Override
   public boolean isValidDimension(DimensionType dimension) {
      return this.isNether(dimension);
   }
}
