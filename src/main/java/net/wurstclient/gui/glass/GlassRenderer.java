package net.wurstclient.gui.glass;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * 液态玻璃的绘制器：先抓一帧背景，再用 {@code wurst:glass} 着色器画面板。
 *
 * <p>
 * 移植自 LumaGlass 0.2.9 的 {@code dev.lumaglass.client.GlassRenderer} 与
 * {@code dev.lumaglass.api.client.GlassCanvas}/{@code GlassFrame} 的取帧逻辑
 * （MIT，Copyright © 2026 Sunny5349；许可正文见
 * {@code META-INF/licenses/lumaglass/}）。着色器源码与上游逐字节相同，只把命名空间
 * 从 {@code lumaglass} 换成 {@code wurst}；本类相对上游的差异有四处，都在注释里标了
 * 出处：</p>
 *
 * <ul>
 * <li>泛光（glow）恒为 0——上游由 {@code GlassConfig} 写入，默认就是关的，本工程没有
 * 调色盘；</li>
 * <li>贴图面板（{@code Artwork}）不参与：按钮不需要把原版贴图玻璃化，因此不暴露
 * 该参数，采样器仍按上游的 "无贴图" 分支绑到背景纹理上；</li>
 * <li>不再每帧新建/关闭 {@code GlassCanvas}/{@code GlassFrame} 对象——按钮的取帧
 * 时机由 {@link GlassButtons} 按屏幕渲染趟次管理；</li>
 * <li>{@link #capture} 返回是否成功，调用方据此回退到扁平样式。</li>
 * </ul>
 *
 * <p>
 * 只在客户端渲染线程上使用，采样的是主 framebuffer。</p>
 */
final class GlassRenderer
{
	private static ShaderInstance glassShader;
	private static ShaderInstance blurShader;

	private RenderTarget scene;
	private RenderTarget ping;
	private RenderTarget blurred;
	private final List<RenderTarget> pyramid = new ArrayList<>();
	private boolean captured;
	private boolean hasBlur;

	static boolean available()
	{
		return glassShader != null && blurShader != null;
	}

	static void setShaders(ShaderInstance glass, ShaderInstance blur)
	{
		glassShader = glass;
		blurShader = blur;
	}

	/**
	 * 抓取当前 framebuffer 作为玻璃的背景，并按半径建一条逐级降采样的模糊链。
	 *
	 * <p>
	 * 必须在背景画完、玻璃之前调用；{@code radius} 与 {@code pixelScale} 用面板所在的
	 * 同一套局部坐标。</p>
	 */
	boolean capture(GuiGraphics gui, float radius, float pixelScale)
	{
		RenderSystem.assertOnRenderThread();
		gui.flush();
		captured = false;
		if(!available())
			return false;

		Minecraft minecraft = Minecraft.getInstance();
		RenderTarget main = minecraft.getMainRenderTarget();
		if(main == null || main.width <= 0 || main.height <= 0)
			return false;

		State state = new State();
		try
		{
			if(scene == null || scene.width != main.width
				|| scene.height != main.height)
			{
				release();
				scene = target(main.width, main.height);
			}

			RenderSystem.disableScissor();
			RenderSystem.disableDepthTest();
			RenderSystem.depthMask(false);
			RenderSystem.disableCull();
			RenderSystem.disableBlend();
			// 先拷贝再采样：不能一边读一边写同一张纹理。
			GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
			GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,
				scene.frameBufferId);
			GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0,
				scene.width, scene.height, GL11.GL_COLOR_BUFFER_BIT,
				GL11.GL_NEAREST);

			hasBlur = radius > 0;
			if(hasBlur)
			{
				float physicalRadius = radius * pixelScale;
				int levels = 0;
				// 相邻高斯采样最多隔一个纹素。每一级先对两个方向低通再降采样，
				// 不会跳过像素细节。
				while(physicalRadius / (1 << levels) > 16 && levels < 6)
					levels++;

				RenderTarget source = scene;
				for(int i = 0; i < levels; i++)
				{
					if(pyramid.size() <= i)
						pyramid.add(target(Math.max(1, source.width / 2),
							Math.max(1, source.height / 2)));
					RenderTarget reduced = pyramid.get(i);
					pass(source, reduced, 0, 0, true);
					source = reduced;
				}
				if(ping == null || ping.width != source.width
					|| ping.height != source.height)
				{
					if(ping != null)
						ping.destroyBuffers();
					if(blurred != null)
						blurred.destroyBuffers();
					ping = target(source.width, source.height);
					blurred = target(source.width, source.height);
				}
				pass(source, ping, physicalRadius / scene.width, 0, false);
				pass(ping, blurred, 0, physicalRadius / scene.height, false);
			}
			captured = true;
			return true;
		}finally
		{
			main.bindWrite(true);
			state.restore();
		}
	}

	/**
	 * 在 {@code gui.pose()} 的局部坐标里画一块玻璃面板。
	 *
	 * <p>
	 * {@code mouseX}/{@code mouseY} 与面板同一套局部坐标，{@code pixelScale} =
	 * 窗口 GUI scale × 局部 pose 缩放。</p>
	 */
	void panel(GuiGraphics gui, float x, float y, float width, float height,
		GlassStyle style, float mouseX, float mouseY, float pixelScale)
	{
		if(width <= 0 || height <= 0 || style.opacity() <= 0)
			return;
		RenderSystem.assertOnRenderThread();
		gui.flush();
		if(!captured || !available() || scene == null)
			return;

		State state = new State();
		try
		{
			RenderSystem.disableDepthTest();
			RenderSystem.depthMask(false);
			RenderSystem.disableCull();
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.setShader(() -> glassShader);
			glassShader.setSampler("Scene", scene.getColorTextureId());
			glassShader.setSampler("Blurred",
				(hasBlur ? blurred : scene).getColorTextureId());
			// 本移植不画贴图面板：采样器仍绑一张有效纹理（上游 artwork==null 的那条
			// 分支），shader JSON 里 UseArtwork 的默认值就是 0。
			glassShader.setSampler("Artwork", scene.getColorTextureId());
			glassShader.safeGetUniform("Opacity").set(style.opacity());
			glassShader.safeGetUniform("FrameSize").set((float)scene.width,
				(float)scene.height);
			glassShader.safeGetUniform("PanelSize").set(width, height);
			glassShader.safeGetUniform("Mouse").set(mouseX - x, mouseY - y);
			glassShader.safeGetUniform("Radius")
				.set(Math.min(style.radius(), Math.min(width, height) * 0.5F));
			glassShader.safeGetUniform("Refraction").set(style.refraction());
			glassShader.safeGetUniform("Frost").set(style.frost());
			glassShader.safeGetUniform("Tint").set(style.tint());
			glassShader.safeGetUniform("Highlight").set(style.highlight());
			glassShader.safeGetUniform("PixelScale").set(pixelScale);
			// 上游由配置写这个 uniform（GlassConfig 默认 glowEnabled=false），但
			// shader JSON 的 GlowStrength 默认值是 1，所以必须显式关掉。
			glassShader.safeGetUniform("GlowStrength").set(0F);

			float pad = 12;
			Matrix4f matrix = gui.pose().last().pose();
			BufferBuilder buffer = Tesselator.getInstance().getBuilder();
			buffer.begin(VertexFormat.Mode.QUADS,
				DefaultVertexFormat.POSITION_TEX);
			buffer.vertex(matrix, x - pad, y + height + pad, 0)
				.uv(-pad, height + pad).endVertex();
			buffer.vertex(matrix, x + width + pad, y + height + pad, 0)
				.uv(width + pad, height + pad).endVertex();
			buffer.vertex(matrix, x + width + pad, y - pad, 0)
				.uv(width + pad, -pad).endVertex();
			buffer.vertex(matrix, x - pad, y - pad, 0).uv(-pad, -pad)
				.endVertex();
			BufferUploader.drawWithShader(buffer.end());
		}finally
		{
			state.restore();
		}
	}

	void release()
	{
		RenderSystem.assertOnRenderThread();
		if(scene != null)
			scene.destroyBuffers();
		if(ping != null)
			ping.destroyBuffers();
		if(blurred != null)
			blurred.destroyBuffers();
		for(RenderTarget level : pyramid)
			level.destroyBuffers();
		pyramid.clear();
		scene = ping = blurred = null;
		captured = false;
	}

	private static RenderTarget target(int width, int height)
	{
		RenderTarget target = new TextureTarget(width, height, false,
			Minecraft.ON_OSX);
		target.setFilterMode(GL11.GL_LINEAR);
		return target;
	}

	private static void pass(RenderTarget source, RenderTarget destination,
		float dx, float dy, boolean downsample)
	{
		// 两边的 JSON 混合声明与上游、也与原版 core shader 一致。不同的声明会污染
		// BlendMode 的全局缓存，把暗角那次的显式 ZERO 混合覆盖掉。
		destination.bindWrite(true);
		RenderSystem.setShader(() -> blurShader);
		blurShader.setSampler("Source", source.getColorTextureId());
		blurShader.safeGetUniform("Direction").set(dx, dy);
		blurShader.safeGetUniform("SourceTexel")
			.set(1F / source.width, 1F / source.height);
		blurShader.safeGetUniform("Downsample").set(downsample ? 1F : 0F);
		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		buffer.begin(VertexFormat.Mode.QUADS,
			DefaultVertexFormat.POSITION_TEX);
		buffer.vertex(-1, -1, 0).uv(0, 0).endVertex();
		buffer.vertex(1, -1, 0).uv(1, 0).endVertex();
		buffer.vertex(1, 1, 0).uv(1, 1).endVertex();
		buffer.vertex(-1, 1, 0).uv(0, 1).endVertex();
		BufferUploader.drawWithShader(buffer.end());
	}

	/** 保住调用方的 GUI 状态，包括混合因子与裁剪框。 */
	private static final class State
	{
		private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
		private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
		private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
		private final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
		private final boolean depthMask =
			GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
		private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
		private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
		private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
		private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
		private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		private final ShaderInstance shader = RenderSystem.getShader();

		void restore()
		{
			if(depth)
				RenderSystem.enableDepthTest();
			else
				RenderSystem.disableDepthTest();
			if(blend)
				RenderSystem.enableBlend();
			else
				RenderSystem.disableBlend();
			if(cull)
				RenderSystem.enableCull();
			else
				RenderSystem.disableCull();
			// 抓帧只关了裁剪测试，并没有改裁剪矩形。
			if(scissor)
				GlStateManager._enableScissorTest();
			else
				RenderSystem.disableScissor();
			RenderSystem.depthMask(depthMask);
			GlStateManager._blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
			RenderSystem.activeTexture(activeTexture);
			RenderSystem.setShader(() -> shader);
		}
	}
}
