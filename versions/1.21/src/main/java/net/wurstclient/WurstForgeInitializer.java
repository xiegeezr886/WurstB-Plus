package net.wurstclient;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
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
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> net.wurstclient.seed.SeedCrackerBridge.disconnect());
		MinecraftForge.EVENT_BUS.addListener(this::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderLevelStageEvent event) -> {
            if(initialized && event.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL)
                try(var ignored = net.wurstclient.hud2.nanovg.GlStateGuard.capture()) {
                    kaptainwutax.seedcrackerX.finder.FinderQueue.get().renderFinders(new org.joml.Matrix4f().rotate(event.getCamera().rotation().conjugate(new org.joml.Quaternionf())), event.getCamera());
                }
        });
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
		float partialTick = event.getPartialTick();

		WurstClient.MC.getBlockEntityRenderDispatcher().camera = camera;

		Quaternionf cameraRotation = camera.rotation()
			.conjugate(new Quaternionf());
		Matrix4f viewMatrix = new Matrix4f().rotate(cameraRotation);

		PoseStack poseStack = new PoseStack();
		poseStack.mulPose(viewMatrix);

		EventManager.fire(new RenderEvent(poseStack, partialTick));
		WurstClient.INSTANCE.getPostEffectQueue().flush(poseStack, partialTick);
        net.wurstclient.util.render.MoonEntityGlow.INSTANCE.flush(poseStack, partialTick);
	}

}
