package net.wurstclient.mixin;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.wurstclient.util.BaritoneCacheWorkers;

// baritone.o is CachedWorld in the bundled Forge 1.19.0 (MC 26.2) jar.
@Pseudo
@Mixin(targets = "baritone.o", remap = false)
abstract class BaritoneCacheWorkersMixin
{
	@Redirect(method = "<init>", require = 2, allow = 2, at = @At(value = "INVOKE",
		target = "Ljava/util/concurrent/Executor;execute(Ljava/lang/Runnable;)V"))
	private void wurst$trackCacheWorker(Executor executor, Runnable task)
	{
		BaritoneCacheWorkers.submit((ExecutorService)executor, task);
	}
}
