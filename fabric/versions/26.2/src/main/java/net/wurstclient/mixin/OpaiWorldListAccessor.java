package net.wurstclient.mixin;
import java.util.List;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(WorldSelectionList.class)
public interface OpaiWorldListAccessor {
 @Invoker("pollLevelsIgnoreErrors") List<LevelSummary> opai$poll();
 @Invoker("handleNewLevels") void opai$update(List<LevelSummary> levels);
 @Accessor("currentlyDisplayedLevels") List<LevelSummary> opai$displayed();
}
