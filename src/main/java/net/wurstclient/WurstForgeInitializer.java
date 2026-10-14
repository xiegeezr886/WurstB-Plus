package net.wurstclient;

import java.io.IOException;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.GUIRenderListener.GUIRenderEvent;
import net.wurstclient.gui.glass.GlassButtons;

@Mod(WurstForgeInitializer.MOD_ID)
public final class WurstForgeInitializer
{
	public static final String MOD_ID = WurstClient.MOD_ID;
	private static boolean initialized;

	/**
	 * 玻璃按钮的两个着色器。资源是在 {@code assets/wurst/shaders/core/} 下，命名空间
	 * 是 {@code wurst} 而不是模组 ID，所以这里不能写 {@link #MOD_ID}。
	 */
	private ShaderInstance glassShader;
	private ShaderInstance blurShader;

	public WurstForgeInitializer()
	{
		if(FMLEnvironment.dist != Dist.CLIENT)
			return;

		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		modBus.addListener(this::onClientSetup);
		modBus.addListener(this::onRegisterShaders);
		MinecraftForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
		MinecraftForge.EVENT_BUS.addListener(this::onRenderGui);
		MinecraftForge.EVENT_BUS.addListener(this::onScreenRenderPre);
		MinecraftForge.EVENT_BUS.addListener(this::onRenderTick);
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
	 * 注册玻璃按钮的两个 core shader。Forge 在每次资源重载时都会重发这个事件，旧实例
	 * 同时被关掉：所以先把玻璃整体关掉（按钮回退扁平样式），等两个都装好再打开，免得
	 * 用一个已经被关掉的 shader 去画。
	 *
	 * <p>
	 * 读不到着色器只影响按钮外观，按钮会自己回退扁平样式，所以这里把 IOException 吞掉
	 * 并记一行日志——不能让一个纯装饰性的东西挡住启动。</p>
	 */
	private void onRegisterShaders(RegisterShadersEvent event)
	{
		glassShader = null;
		blurShader = null;
		GlassButtons.onShadersReloaded();

		try
		{
			event.registerShader(new ShaderInstance(event.getResourceProvider(),
				new ResourceLocation("wurst", "glass"),
				DefaultVertexFormat.POSITION_TEX), shader ->
				{
					glassShader = shader;
					installGlassShaders();
				});
			event.registerShader(new ShaderInstance(event.getResourceProvider(),
				new ResourceLocation("wurst", "blur"),
				DefaultVertexFormat.POSITION_TEX), shader ->
				{
					blurShader = shader;
					installGlassShaders();
				});
		}catch(IOException e)
		{
			System.err.println(
				"[Glass] 玻璃着色器加载失败，按钮回退扁平样式: " + e);
		}
	}

	private void installGlassShaders()
	{
		if(glassShader != null && blurShader != null)
			GlassButtons.installShaders(glassShader, blurShader);
	}

	/**
	 * 一趟屏幕渲染开始时重新抓一次背景。这个事件在 {@code Screen.render} 的最前面触发，
	 * 抓到的帧里已经有该界面的背景，之后画的按钮就都采样同一张快照。
	 */
	private void onScreenRenderPre(ScreenEvent.Render.Pre event)
	{
		GlassButtons.beginPass();
	}

	/** 兜底：万一某个界面没走 {@code Screen.render}，也不至于一直用上一帧的背景。 */
	private void onRenderTick(TickEvent.RenderTickEvent event)
	{
		if(event.phase == TickEvent.Phase.START)
			GlassButtons.beginPass();
	}

	private void onRenderGui(RenderGuiEvent.Post event)
	{

		if(!initialized || WurstClient.MC.options.renderDebug)
			return;
		EventManager.fire(new GUIRenderEvent(event.getGuiGraphics(),
			event.getPartialTick()));
	}

}
