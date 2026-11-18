package net.wurstclient;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.RenderListener.RenderEvent;

@Mod(WurstForgeInitializer.MOD_ID)
public final class WurstForgeInitializer
{
	public static final String MOD_ID = WurstClient.MOD_ID;
	private static boolean initialized;

	public WurstForgeInitializer(IEventBus modBus)
	{
		if(FMLEnvironment.dist != Dist.CLIENT)
			return;

		modBus.addListener(this::onClientSetup);
		NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> net.wurstclient.seed.SeedCrackerBridge.disconnect());
		NeoForge.EVENT_BUS.addListener(this::onRenderLevel);
	}

	private void onClientSetup(FMLClientSetupEvent event)
	{
		event.enqueueWork(() ->
		{
			if(initialized)
				return;

			initialized = true;
			WurstClient.INSTANCE.initialize();
            net.wurstclient.seed.SeedCrackerBridge.initialize();
		});
	}

	private void onRegisterClientCommands(RegisterClientCommandsEvent event)
	{
		if(initialized && WurstClient.INSTANCE.getCmds() != null)
			WurstClient.INSTANCE.getCmds()
				.buildBrigadierDispatcher(event.getDispatcher());
        kaptainwutax.seedcrackerX.init.ClientCommands.registerCommands(event.getDispatcher());
	}

	private void onRenderLevel(RenderLevelStageEvent event)
	{
		if(!initialized
			|| event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL)
			return;

		net.minecraft.client.Camera camera = event.getCamera();
		float partialTick = event.getPartialTick()
			.getGameTimeDeltaPartialTick(false);

		WurstClient.MC.getBlockEntityRenderDispatcher().camera = camera;

		Quaternionf cameraRotation = camera.rotation()
			.conjugate(new Quaternionf());
		Matrix4f viewMatrix = new Matrix4f().rotate(cameraRotation);

		PoseStack poseStack = new PoseStack();
		poseStack.mulPose(viewMatrix);

		EventManager.fire(new RenderEvent(poseStack, partialTick));
        kaptainwutax.seedcrackerX.finder.FinderQueue.get().renderNative(poseStack, camera);
		WurstClient.INSTANCE.getPostEffectQueue().flush(poseStack, partialTick);
        net.wurstclient.util.render.MoonEntityGlow.INSTANCE.flush(poseStack, partialTick);
	}

}
