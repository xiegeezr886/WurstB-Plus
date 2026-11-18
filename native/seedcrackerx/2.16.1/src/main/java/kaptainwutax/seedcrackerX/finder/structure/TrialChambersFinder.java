package kaptainwutax.seedcrackerX.finder.structure;

import com.seedfinding.mcfeature.structure.RegionStructure.Data;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kaptainwutax.seedcrackerX.Features;
import kaptainwutax.seedcrackerX.SeedCracker;
import kaptainwutax.seedcrackerX.cracker.DataAddedEvent;
import kaptainwutax.seedcrackerX.finder.Finder;
import kaptainwutax.seedcrackerX.render.Cuboid;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.Direction.Plane;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.dimension.DimensionType;

public class TrialChambersFinder extends Finder {
   protected static Map<Direction, List<BlockPos>> SEARCH_POSITIONS;
   protected static final Vec3i size = new Vec3i(19, 20, 19);
   protected List<JigsawFinder> finders = new ArrayList<>();

   public TrialChambersFinder(Level world, ChunkPos chunkPos) {
      super(world, chunkPos);
      Plane.HORIZONTAL.forEach(direction -> {
         JigsawFinder finder1 = new JigsawFinder(world, chunkPos, direction, size);
         finder1.searchPositions = SEARCH_POSITIONS.get(direction);
         buildEnd1(finder1);
         this.finders.add(finder1);
         JigsawFinder finder2 = new JigsawFinder(world, chunkPos, direction, size);
         finder2.searchPositions = SEARCH_POSITIONS.get(direction);
         buildEnd2(finder2);
         this.finders.add(finder2);
      });
   }

   public static void reloadSearchPositions() {
      SEARCH_POSITIONS = JigsawFinder.getSearchPositions(0, 0, 0, 0, size);
   }

   public static List<Finder> create(Level world, ChunkPos chunkPos) {
      List<Finder> finders = new ArrayList<>();
      finders.add(new TrialChambersFinder(world, chunkPos));
      return finders;
   }

   public static void buildOminousVault(JigsawFinder finder, int x, int y, int z) {
      BlockState air = Blocks.AIR.defaultBlockState();
      BlockState polishedTuff = Blocks.POLISHED_TUFF.defaultBlockState();
      BlockState polishedTuffSlabTop = (BlockState)Blocks.POLISHED_TUFF_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
      BlockState ominousVault = (BlockState)((BlockState)((BlockState)Blocks.VAULT.defaultBlockState().setValue(VaultBlock.FACING, Direction.NORTH))
            .setValue(VaultBlock.OMINOUS, true))
         .setValue(VaultBlock.STATE, VaultState.INACTIVE);
      BlockState tuffBricks = Blocks.TUFF_BRICKS.defaultBlockState();
      BlockState chiseledTuff = Blocks.CHISELED_TUFF.defaultBlockState();
      BlockState redGlazedTerracotta = ((Block)Blocks.GLAZED_TERRACOTTA.red()).defaultBlockState();
      BlockState waxedCopperBulbOn = (BlockState)((Block)Blocks.COPPER_BULB.waxed().unaffected()).defaultBlockState().setValue(CopperBulbBlock.LIT, true);
      BlockState redCandle3 = (BlockState)((BlockState)((Block)Blocks.DYED_CANDLE.red()).defaultBlockState().setValue(CandleBlock.CANDLES, 3))
         .setValue(CandleBlock.LIT, true);
      BlockState redCandle4 = (BlockState)((BlockState)((Block)Blocks.DYED_CANDLE.red()).defaultBlockState().setValue(CandleBlock.CANDLES, 4))
         .setValue(CandleBlock.LIT, true);
      BlockState waxedOxidizedCopperGrate = ((Block)Blocks.COPPER_GRATE.waxed().oxidized()).defaultBlockState();
      finder.fillWithOutline(x, y, z, x + 2, y, z + 1, waxedOxidizedCopperGrate, waxedOxidizedCopperGrate, false);
      finder.addBlock(redCandle3, x, y + 1, z);
      finder.addBlock(redCandle4, x + 2, y + 1, z);
      finder.fillWithOutline(x, y + 1, z + 1, x + 2, y + 3, z + 1, polishedTuff, polishedTuff, false);
      finder.fillWithOutline(x, y + 2, z + 1, x + 2, y + 2, z + 1, polishedTuffSlabTop, polishedTuffSlabTop, false);
      finder.addBlock(ominousVault, x + 1, y + 1, z + 1);
      finder.addBlock(air, x + 1, y + 2, z + 1);
      finder.addBlock(waxedCopperBulbOn, x + 1, y + 3, z + 1);
      finder.fillWithOutline(x, y, z + 2, x + 2, y, z + 2, polishedTuff, polishedTuff, false);
      finder.fillWithOutline(x, y + 1, z + 2, x + 2, y + 3, z + 2, tuffBricks, tuffBricks, false);
      finder.addBlock(waxedCopperBulbOn, x + 1, y + 1, z + 2);
      finder.fillWithOutline(x, y + 2, z + 2, x + 2, y + 2, z + 2, redGlazedTerracotta, redGlazedTerracotta, false);
      finder.addBlock(chiseledTuff, x + 1, y + 2, z + 2);
   }

   public static void buildEnd1(JigsawFinder finder) {
      BlockState air = Blocks.AIR.defaultBlockState();
      BlockState tuffBricks = Blocks.TUFF_BRICKS.defaultBlockState();
      BlockState waxedOxidizedCutCopper = ((Block)Blocks.CUT_COPPER.waxed().oxidized()).defaultBlockState();
      BlockState waxedCopperBulbOn = (BlockState)((Block)Blocks.COPPER_BULB.waxed().unaffected()).defaultBlockState().setValue(CopperBulbBlock.LIT, true);
      BlockState waxedCopperBlock = ((Block)Blocks.COPPER_BLOCK.waxed().unaffected()).defaultBlockState();
      BlockState chiseledTuff = Blocks.CHISELED_TUFF.defaultBlockState();
      BlockState chiseledTuffBricks = Blocks.CHISELED_TUFF_BRICKS.defaultBlockState();
      BlockState polishedTuff = Blocks.POLISHED_TUFF.defaultBlockState();
      finder.fillWithOutline(0, 0, 0, 0, 18, 18, tuffBricks, tuffBricks, false);
      finder.fillWithOutline(18, 0, 0, 18, 18, 18, tuffBricks, tuffBricks, false);
      finder.fillWithOutline(0, 19, 0, 18, 19, 18, tuffBricks, tuffBricks, false);
      finder.fillWithOutline(0, 0, 0, 18, 1, 18, tuffBricks, tuffBricks, false);
      finder.fillWithOutline(6, 6, 11, 12, 18, 12, tuffBricks, tuffBricks, false);
      finder.fillWithOutline(6, 3, 11, 12, 3, 12, waxedOxidizedCutCopper, waxedOxidizedCutCopper, false);
      finder.fillWithOutline(6, 10, 11, 12, 10, 12, waxedCopperBlock, waxedCopperBlock, false);
      finder.fillWithOutline(6, 15, 11, 12, 15, 12, waxedCopperBlock, waxedCopperBlock, false);
      finder.fillWithOutline(6, 5, 12, 12, 5, 12, chiseledTuff, chiseledTuff, false);
      finder.fillWithOutline(6, 5, 11, 7, 5, 12, waxedCopperBlock, waxedCopperBlock, false);
      finder.fillWithOutline(11, 5, 11, 12, 5, 11, waxedCopperBlock, waxedCopperBlock, false);
      finder.fillWithOutline(7, 5, 12, 12, 5, 12, chiseledTuffBricks, chiseledTuffBricks, false);
      finder.addBlock(chiseledTuff, 8, 5, 12);
      finder.addBlock(chiseledTuff, 10, 5, 12);
      finder.fillWithOutline(6, 7, 11, 12, 7, 12, chiseledTuffBricks, chiseledTuffBricks, false);
      finder.fillWithOutline(6, 12, 11, 12, 12, 12, chiseledTuffBricks, chiseledTuffBricks, false);
      finder.fillWithOutline(6, 17, 11, 12, 17, 12, chiseledTuffBricks, chiseledTuffBricks, false);
      finder.fillWithOutline(8, 3, 11, 10, 17, 11, air, air, false);
      finder.fillWithOutline(8, 18, 11, 10, 18, 12, polishedTuff, polishedTuff, false);
      buildOminousVault(finder, 8, 13, 10);
   }

   public static void buildEnd2(JigsawFinder finder) {
      BlockState water = Blocks.WATER.defaultBlockState();
      BlockState tuffBricks = Blocks.TUFF_BRICKS.defaultBlockState();
      BlockState waxedOxidizedCopper = ((Block)Blocks.COPPER_BLOCK.waxed().oxidized()).defaultBlockState();
      BlockState waxedCopperGrate = ((Block)Blocks.COPPER_GRATE.waxed().unaffected()).defaultBlockState();
      BlockState diamondChest = (BlockState)((BlockState)Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH))
         .setValue(ChestBlock.TYPE, ChestType.SINGLE);
      finder.fillWithOutline(0, 0, 0, 0, 18, 18, tuffBricks, tuffBricks, false);
      finder.fillWithOutline(18, 0, 0, 18, 18, 18, tuffBricks, tuffBricks, false);
      finder.fillWithOutline(0, 19, 0, 18, 19, 18, tuffBricks, tuffBricks, false);
      finder.fillWithOutline(0, 0, 0, 18, 0, 18, tuffBricks, tuffBricks, false);
      finder.fillWithOutline(8, 0, 13, 10, 0, 17, waxedOxidizedCopper, waxedOxidizedCopper, false);
      finder.fillWithOutline(4, 2, 8, 14, 2, 9, water, water, false);
      finder.fillWithOutline(3, 2, 11, 4, 2, 15, water, water, false);
      finder.fillWithOutline(14, 2, 11, 15, 2, 15, water, water, false);
      finder.fillWithOutline(8, 3, 11, 10, 5, 11, waxedCopperGrate, waxedCopperGrate, false);
      finder.fillWithOutline(7, 3, 13, 7, 5, 16, waxedCopperGrate, waxedCopperGrate, false);
      finder.fillWithOutline(11, 3, 13, 11, 5, 16, waxedCopperGrate, waxedCopperGrate, false);
      finder.addBlock(diamondChest, 9, 3, 6);
      buildOminousVault(finder, 8, 1, 15);
   }

   @Override
   public List<BlockPos> findInChunk() {
      Biome biome = (Biome)this.world.getNoiseBiome((this.chunkPos.x() << 2) + 2, 64, (this.chunkPos.z() << 2) + 2).value();
      if (((Registry)Minecraft.getInstance().level.registryAccess().lookup(Registries.BIOME).get()).getKey(biome).equals(Biomes.DEEP_DARK)) {
         return new ArrayList<>();
      }

      Map<JigsawFinder, List<BlockPos>> result = this.findInChunkPieces();
      List<BlockPos> combinedResult = new ArrayList<>();
      result.forEach((pieceFinder, positions) -> {
         combinedResult.addAll((Collection<? extends BlockPos>)positions);
         positions.forEach(pos -> {
            Data<?> data = Features.TRIAL_CHAMBERS.at(this.chunkPos.x(), this.chunkPos.z());
            if (SeedCracker.get().getDataStorage().addBaseData(data, DataAddedEvent.POKE_STRUCTURES)) {
               this.cuboids.add(new Cuboid(pos, pieceFinder.getLayout(), ARGB.color(170, 84, 3)));
               this.cuboids.add(new Cuboid(this.chunkPos.getWorldPosition().offset(0, pos.getY(), 0), ARGB.color(170, 84, 3)));
            }
         });
      });
      return combinedResult;
   }

   public Map<JigsawFinder, List<BlockPos>> findInChunkPieces() {
      Map<JigsawFinder, List<BlockPos>> result = new HashMap<>();
      this.finders.forEach(pieceFinder -> result.put(pieceFinder, pieceFinder.findInChunk()));
      return result;
   }

   @Override
   public boolean isValidDimension(DimensionType dimension) {
      return this.isOverworld(dimension);
   }
}
