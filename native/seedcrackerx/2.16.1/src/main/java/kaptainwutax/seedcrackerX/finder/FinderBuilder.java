package kaptainwutax.seedcrackerX.finder;

import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

@FunctionalInterface
public interface FinderBuilder {
   List<Finder> build(Level var1, ChunkPos var2);
}
