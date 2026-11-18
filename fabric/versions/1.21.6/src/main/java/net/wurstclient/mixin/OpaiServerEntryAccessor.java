package net.wurstclient.mixin;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.FaviconTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ServerSelectionList.OnlineServerEntry.class)
public interface OpaiServerEntryAccessor {
 @Accessor("icon") FaviconTexture opai$icon();
}
