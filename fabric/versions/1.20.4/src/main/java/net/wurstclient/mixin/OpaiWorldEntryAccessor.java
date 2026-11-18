package net.wurstclient.mixin;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(WorldSelectionList.WorldListEntry.class)
public interface OpaiWorldEntryAccessor {
 @Accessor("summary") LevelSummary opai$summary();
 @Accessor("icon") FaviconTexture opai$icon();
}
