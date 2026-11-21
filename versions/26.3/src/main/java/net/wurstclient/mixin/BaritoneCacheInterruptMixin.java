package net.wurstclient.mixin;

import java.util.concurrent.ExecutorService;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Both workers already return from their InterruptedException handler.
@Mixin(targets = {"baritone.cache.CachedWorld", "baritone.cache.CachedWorld$PackerThread"}, remap = false)
abstract class BaritoneCacheInterruptMixin
{
	@Redirect(method = {"lambda$new$0()V", "run()V"}, require = 1, allow = 1,
		at = @At(value = "INVOKE", target = "Ljava/lang/InterruptedException;printStackTrace()V"))
	private void wurst$onCacheInterrupted(InterruptedException interruption)
	{
		if(!(baritone.Baritone.getExecutor() instanceof ExecutorService pool)
			|| !pool.isShutdown())
			interruption.printStackTrace();
		Thread.currentThread().interrupt();
	}
}
