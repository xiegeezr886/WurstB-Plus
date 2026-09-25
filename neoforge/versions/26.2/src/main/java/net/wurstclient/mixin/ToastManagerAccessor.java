package net.wurstclient.mixin;

import java.util.Deque;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;

@Mixin(ToastManager.class)
public interface ToastManagerAccessor
{
	@Accessor("queued")
	Deque<Toast> getQueued();
}
