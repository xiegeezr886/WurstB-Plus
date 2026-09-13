package net.wurstclient;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.RenderListener.RenderEvent;

@Mod(WurstForgeInitializer.MOD_ID)
public final class WurstForgeInitializer
{
	public static final String MOD_ID = WurstClient.MOD_ID;
	private static boolean initialized;

	public WurstForgeInitializer()
	{
		if(FMLEnvironment.dist != Dist.CLIENT)
			return;

		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		modBus.addListener(this::onClientSetup);
		MinecraftForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
		MinecraftForge.EVENT_BUS.addListener(this::onRenderLevel);
	}

	private void onClientSetup(FMLClientSetupEvent event)
	{
		event.enqueueWork(() ->
		{
			if(initialized)
				return;

			initialized = true;
			WurstClient.INSTANCE.initialize();
		});
	}

	private void onRegisterClientCommands(RegisterClientCommandsEvent event)
	{
		if(initialized && WurstClient.INSTANCE.getCmds() != null)
			WurstClient.INSTANCE.getCmds()
				.buildBrigadierDispatcher(event.getDispatcher());
	}

	/**
	 * Forge 1.20.5+ no longer offers a RenderGuiEvent, so the GUIRenderEvent is
	 * fired from IngameHudMixin instead. This listener replaces the old
	 * RenderEvent injection into GameRenderer.renderLevel(), which no longer
	 * receives a PoseStack in 1.20.5+.
	 */
	private void onRenderLevel(RenderLevelStageEvent event)
	{
		if(!initialized
			|| event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL)
			return;

		Camera camera = event.getCamera();
		float partialTick = event.getPartialTick();

		WurstClient.MC.getBlockEntityRenderDispatcher().camera = camera;

		Quaternionf cameraRotation =
			camera.rotation().conjugate(new Quaternionf());
		Matrix4f viewMatrix = new Matrix4f().rotate(cameraRotation);

		PoseStack poseStack = new PoseStack();
		poseStack.mulPose(viewMatrix);

		EventManager.fire(new RenderEvent(poseStack, partialTick));
		WurstClient.INSTANCE.getPostEffectQueue().flush(poseStack, partialTick);
	}

}
