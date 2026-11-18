package kaptainwutax.seedcrackerX.finder.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kaptainwutax.seedcrackerX.finder.Finder;
import kaptainwutax.seedcrackerX.render.Cuboid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;

public abstract class AbstractTempleFinder extends Finder {
   protected static List<BlockPos> SEARCH_POSITIONS;
   protected final Vec3i size;
   protected List<PieceFinder> finders = new ArrayList<>();

   public AbstractTempleFinder(Level world, ChunkPos chunkPos, Vec3i size) {
      super(world, chunkPos);
      Plane.HORIZONTAL.forEach(direction -> {
         PieceFinder finder = new PieceFinder(world, chunkPos, direction, size);
         finder.searchPositions = SEARCH_POSITIONS;
         this.buildStructure(finder);
         this.finders.add(finder);
      });
      this.size = size;
   }

   public static void reloadSearchPositions() {
      SEARCH_POSITIONS = buildSearchPositions(CHUNK_POSITIONS, pos -> {
         if (pos.getX() != 0) {
            return true;
         } else if (pos.getY() < 0) {
            return true;
         } else {
            return pos.getY() > 200 ? true : pos.getZ() != 0;
         }
      });
   }

   public List<BlockPos> findInChunkPiece(PieceFinder pieceFinder) {
      Biome biome = (Biome)this.world.getNoiseBiome((this.chunkPos.x() << 2) + 2, 64, (this.chunkPos.z() << 2) + 2).value();
      return !this.isValidBiome(biome) ? new ArrayList<>() : pieceFinder.findInChunk();
   }

   protected abstract boolean isValidBiome(Biome var1);

   public void addRenderers(PieceFinder pieceFinder, BlockPos origin, int argb) {
      this.cuboids.add(new Cuboid(origin, pieceFinder.getLayout(), argb));
      BlockPos chunkStart = new BlockPos(origin.getX() & -16, origin.getY(), origin.getZ() & -16);
      this.cuboids.add(new Cuboid(chunkStart, argb));
   }

   public Map<PieceFinder, List<BlockPos>> findInChunkPieces() {
      Map<PieceFinder, List<BlockPos>> result = new HashMap<>();
      this.finders.forEach(pieceFinder -> result.put(pieceFinder, this.findInChunkPiece(pieceFinder)));
      return result;
   }

   public abstract void buildStructure(PieceFinder var1);

   @Override
   public boolean isValidDimension(DimensionType dimension) {
      return this.isOverworld(dimension);
   }
}
