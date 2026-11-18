package kaptainwutax.seedcrackerX.finder.structure;

import com.seedfinding.mcfeature.structure.RegionStructure.Data;
import java.util.ArrayList;
import java.util.List;
import kaptainwutax.seedcrackerX.Features;
import kaptainwutax.seedcrackerX.SeedCracker;
import kaptainwutax.seedcrackerX.cracker.DataAddedEvent;
import kaptainwutax.seedcrackerX.finder.BlockFinder;
import kaptainwutax.seedcrackerX.finder.Finder;
import kaptainwutax.seedcrackerX.render.Cuboid;
import kaptainwutax.seedcrackerX.util.BiomeFixer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;

public class BuriedTreasureFinder extends BlockFinder {
   protected static final List<BlockState> CHEST_HOLDERS = new ArrayList<>();
   protected static List<BlockPos> SEARCH_POSITIONS;

   public BuriedTreasureFinder(Level world, ChunkPos chunkPos) {
      super(world, chunkPos, Blocks.CHEST);
      this.searchPositions = SEARCH_POSITIONS;
   }

   public static void reloadSearchPositions() {
      SEARCH_POSITIONS = buildSearchPositions(CHUNK_POSITIONS, pos -> {
         int localX = pos.getX() & 15;
         int localZ = pos.getZ() & 15;
         return localX == 9 && localZ == 9 ? pos.getY() > 90 && pos.getY() < 0 : true;
      });
   }

   public static List<Finder> create(Level world, ChunkPos chunkPos) {
      List<Finder> finders = new ArrayList<>();
      finders.add(new BuriedTreasureFinder(world, chunkPos));
      return finders;
   }

   @Override
   public List<BlockPos> findInChunk() {
      Biome biome = (Biome)this.world.getNoiseBiome((this.chunkPos.x() << 2) + 2, 64, (this.chunkPos.z() << 2) + 2).value();
      if (!Features.BURIED_TREASURE.isValidBiome(BiomeFixer.swap(biome))) {
         return new ArrayList<>();
      }

      List<BlockPos> result = super.findInChunk();
      result.removeIf(pos -> {
         BlockState chest = this.world.getBlockState(pos);
         if ((Boolean)chest.getValue(ChestBlock.WATERLOGGED)) {
            return true;
         }

         BlockState chestHolder = this.world.getBlockState(pos.below());
         return !CHEST_HOLDERS.contains(chestHolder);
      });
      result.forEach(pos -> {
         Data<?> data = Features.BURIED_TREASURE.at(this.chunkPos.x(), this.chunkPos.z());
         if (SeedCracker.get().getDataStorage().addBaseData(data, DataAddedEvent.POKE_STRUCTURES)) {
            this.cuboids.add(new Cuboid(pos, ARGB.color(255, 255, 0)));
         }
      });
      return result;
   }

   @Override
   public boolean isValidDimension(DimensionType dimension) {
      return this.isOverworld(dimension);
   }

   static {
      CHEST_HOLDERS.add(Blocks.SANDSTONE.defaultBlockState());
      CHEST_HOLDERS.add(Blocks.STONE.defaultBlockState());
      CHEST_HOLDERS.add(Blocks.ANDESITE.defaultBlockState());
      CHEST_HOLDERS.add(Blocks.GRANITE.defaultBlockState());
      CHEST_HOLDERS.add(Blocks.DIORITE.defaultBlockState());
      CHEST_HOLDERS.add(Blocks.COAL_ORE.defaultBlockState());
      CHEST_HOLDERS.add(Blocks.IRON_ORE.defaultBlockState());
      CHEST_HOLDERS.add(Blocks.GOLD_ORE.defaultBlockState());
      CHEST_HOLDERS.add(Blocks.GRAVEL.defaultBlockState());
   }
}
