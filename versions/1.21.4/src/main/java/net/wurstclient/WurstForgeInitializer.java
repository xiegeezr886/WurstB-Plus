package net.wurstclient;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge 54.x (MC 1.21.4) entry point.
 *
 * <p>
 * Unlike the Forge 61.x (MC 1.21.11) tree, this Forge line still uses the
 * classic {@link FMLJavaModLoadingContext} / {@link MinecraftForge#EVENT_BUS}
 * scheme instead of {@code getModBusGroup()} and per-event {@code BUS} fields,
 * and it has no {@code RenderLevelStageEvent}. The render hook is therefore
 * fired from {@code LevelRendererMixin} instead.
 */
@Mod(WurstForgeInitializer.MOD_ID)
public final class WurstForgeInitializer
{
	public static final String MOD_ID = WurstClient.MOD_ID;
	private static boolean initialized;

	public WurstForgeInitializer(FMLJavaModLoadingContext context)
	{
		if(FMLEnvironment.dist != Dist.CLIENT)
			return;

		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		modBus.addListener(this::onClientSetup);
		MinecraftForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
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
}
