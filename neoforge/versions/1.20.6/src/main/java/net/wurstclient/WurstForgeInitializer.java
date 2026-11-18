package net.wurstclient;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.GUIRenderListener.GUIRenderEvent;

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
		NeoForge.EVENT_BUS.addListener(this::onRenderGui);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderLevelStageEvent event) -> {
            if(initialized && event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL)
                try(var ignored = net.wurstclient.hud2.nanovg.GlStateGuard.capture()) {
                    kaptainwutax.seedcrackerX.finder.FinderQueue.get().renderFinders(event.getModelViewMatrix(), event.getCamera());
                }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> net.wurstclient.seed.SeedCrackerBridge.disconnect());
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

	private void onRenderGui(RenderGuiEvent.Post event)
	{
		if(!initialized || WurstClient.MC.getDebugOverlay().showDebugScreen())
			return;
		EventManager.fire(new GUIRenderEvent(event.getGuiGraphics(),
			event.getPartialTick()));
	}
}
