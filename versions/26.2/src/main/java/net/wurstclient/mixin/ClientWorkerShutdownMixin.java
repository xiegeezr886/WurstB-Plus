package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import kaptainwutax.seedcrackerX.cracker.storage.TimeMachine;
import net.minecraft.client.Minecraft;
import net.wurstclient.util.BaritoneCacheWorkers;

@Mixin(Minecraft.class)
abstract class ClientWorkerShutdownMixin
{
	@Inject(method = "close()V", at = @At("RETURN"))
	private void wurst$closeBundledWorkers(CallbackInfo ci)
	{
		TimeMachine.SERVICE.shutdown();
		BaritoneCacheWorkers.close();
	}
}
