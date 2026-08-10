/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util.render;

import java.util.List;

import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * Drop-in replacement for the 26.x {@code GuiGraphicsExtractor} that wraps a
 * 1.21.11 {@link GuiGraphics}. Method names follow the 26.x API so that the
 * Wurst source can stay identical across versions.
 */
public final class GuiGraphicsExtractor
{
	private final GuiGraphics inner;
	
	public GuiGraphicsExtractor(GuiGraphics inner)
	{
		this.inner = inner;
	}
	
	public GuiGraphics getInner()
	{
		return inner;
	}
	
	public void requestCursor(CursorType type)
	{
		// not available in 1.21.11, no-op
	}
	
	public void applyCursor(Window window)
	{
		// not available in 1.21.11, no-op
	}
	
	public int guiWidth()
	{
		return Minecraft.getInstance().getWindow().getGuiScaledWidth();
	}
	
	public int guiHeight()
	{
		return Minecraft.getInstance().getWindow().getGuiScaledHeight();
	}
	
	public Matrix3x2fStack pose()
	{
		return inner.pose();
	}
	
	public void nextStratum()
	{
		// not available in 1.21.11, no-op
	}
	
	public void blurBeforeThisStratum()
	{
		// not available in 1.21.11, no-op
	}
	
	public void enableScissor(int x1, int y1, int x2, int y2)
	{
		inner.enableScissor(x1, y1, x2, y2);
	}
	
	public void disableScissor()
	{
		inner.disableScissor();
	}
	
	public boolean containsPointInScissor(int x, int y)
	{
		return inner.containsPointInScissor(x, y);
	}
	
	public void horizontalLine(int x1, int x2, int y, int color)
	{
		inner.hLine(x1, x2, y, color);
	}
	
	public void verticalLine(int x, int y1, int y2, int color)
	{
		inner.vLine(x, y1, y2, color);
	}
	
	public void fill(int x1, int y1, int x2, int y2, int color)
	{
		inner.fill(x1, y1, x2, y2, color);
	}
	
	public void fill(RenderPipeline pipeline, int x1, int y1, int x2,
		int y2, int color)
	{
		inner.fill(pipeline, x1, y1, x2, y2, color);
	}
	
	public void fillGradient(int x1, int y1, int x2, int y2, int colorTop,
		int colorBottom)
	{
		inner.fillGradient(x1, y1, x2, y2, colorTop, colorBottom);
	}
	
	public void fill(RenderPipeline pipeline, TextureSetup setup, int x1,
		int y1, int x2, int y2)
	{
		inner.fill(pipeline, setup, x1, y1, x2, y2);
	}
	
	public void outline(int x1, int y1, int x2, int y2, int color)
	{
		inner.renderOutline(x1, y1, x2, y2, color);
	}
	
	public void textHighlight(int x, int y, int width, int height,
		boolean focus)
	{
		inner.textHighlight(x, y, width, height, focus);
	}
	
	public void text(Font font, String text, int x, int y, int color)
	{
		inner.drawString(font, text, x, y, color);
	}
	
	public void text(Font font, String text, int x, int y, int color,
		boolean shadow)
	{
		inner.drawString(font, text, x, y, color, shadow);
	}
	
	public void text(Font font, FormattedCharSequence text, int x, int y,
		int color)
	{
		inner.drawString(font, text, x, y, color);
	}
	
	public void text(Font font, FormattedCharSequence text, int x, int y,
		int color, boolean shadow)
	{
		inner.drawString(font, text, x, y, color, shadow);
	}
	
	public void text(Font font, Component text, int x, int y, int color)
	{
		inner.drawString(font, text, x, y, color);
	}
	
	public void text(Font font, Component text, int x, int y, int color,
		boolean shadow)
	{
		inner.drawString(font, text, x, y, color, shadow);
	}
	
	public void centeredText(Font font, String text, int x, int y,
		int color)
	{
		inner.drawCenteredString(font, text, x, y, color);
	}
	
	public void centeredText(Font font, Component text, int x, int y,
		int color)
	{
		inner.drawCenteredString(font, text, x, y, color);
	}
	
	public void centeredText(Font font, FormattedCharSequence text, int x,
		int y, int color)
	{
		inner.drawCenteredString(font, text, x, y, color);
	}
	
	public void textWithWordWrap(Font font, FormattedText text, int x,
		int y, int width, int color)
	{
		inner.drawWordWrap(font, text, x, y, width, color);
	}
	
	public void textWithWordWrap(Font font, FormattedText text, int x,
		int y, int width, int color, boolean shadow)
	{
		inner.drawWordWrap(font, text, x, y, width, color, shadow);
	}
	
	public void textWithBackdrop(Font font, Component text, int x, int y,
		int width, int color)
	{
		inner.drawStringWithBackdrop(font, text, x, y, width, color);
	}
	
	public void blit(RenderPipeline pipeline, Identifier texture, int x,
		int y, float u, float v, int width, int height, int textureWidth,
		int textureHeight)
	{
		inner.blit(pipeline, texture, x, y, u, v, width, height,
			textureWidth, textureHeight);
	}
	
	public void blit(RenderPipeline pipeline, Identifier texture, int x,
		int y, float u, float v, int width, int height, int textureWidth,
		int textureHeight, int color)
	{
		inner.blit(pipeline, texture, x, y, u, v, width, height,
			textureWidth, textureHeight, color);
	}
	
	public void blit(RenderPipeline pipeline, Identifier texture, int x,
		int y, float u, float v, int width, int height, int textureWidth,
		int textureHeight, int color, int color2)
	{
		inner.blit(pipeline, texture, x, y, u, v, width, height,
			textureWidth, textureHeight, color, color2);
	}
	
	public void blit(RenderPipeline pipeline, Identifier texture, int x,
		int y, float u, float v, int width, int height, int textureWidth,
		int textureHeight, int color, int color2, int color3)
	{
		inner.blit(pipeline, texture, x, y, u, v, width, height,
			textureWidth, textureHeight, color, color2, color3);
	}
	
	public void blit(Identifier texture, int x, int y, int width,
		int height, float u, float v, float uWidth, float vHeight)
	{
		inner.blit(texture, x, y, width, height, u, v, uWidth, vHeight);
	}
	
	public void blit(GpuTextureView texture, GpuSampler sampler, int x,
		int y, int width, int height, float u, float v, float uWidth,
		float vHeight)
	{
		// not available in 1.21.11, no-op
	}
	
	public void blitSprite(RenderPipeline pipeline, Identifier texture,
		int x, int y, int width, int height)
	{
		inner.blitSprite(pipeline, texture, x, y, width, height);
	}
	
	public void blitSprite(RenderPipeline pipeline, Identifier texture,
		int x, int y, int width, int height, float color)
	{
		inner.blitSprite(pipeline, texture, x, y, width, height, color);
	}
	
	public void blitSprite(RenderPipeline pipeline, Identifier texture,
		int x, int y, int width, int height, int color)
	{
		inner.blitSprite(pipeline, texture, x, y, width, height, color);
	}
	
	public void blitSprite(RenderPipeline pipeline, Identifier texture,
		int x, int y, int width, int height, int uOffset, int vOffset,
		int uWidth, int vHeight)
	{
		inner.blitSprite(pipeline, texture, x, y, width, height, uOffset,
			vOffset, uWidth, vHeight);
	}
	
	public void blitSprite(RenderPipeline pipeline, Identifier texture,
		int x, int y, int width, int height, int uOffset, int vOffset,
		int uWidth, int vHeight, int color)
	{
		inner.blitSprite(pipeline, texture, x, y, width, height,
			Math.round(uOffset), Math.round(vOffset), uWidth, vHeight, color);
	}
	
	public void blitSprite(RenderPipeline pipeline, Identifier texture,
		int x, int y, int width, int height, float uOffset, float vOffset,
		int uWidth, int vHeight, int color)
	{
		inner.blitSprite(pipeline, texture, x, y, width, height,
			Math.round(uOffset), Math.round(vOffset), uWidth, vHeight, color);
	}
	
	public void item(ItemStack stack, int x, int y)
	{
		inner.renderItem(stack, x, y);
	}
	
	public void item(ItemStack stack, int x, int y, int seed)
	{
		inner.renderItem(stack, x, y, seed);
	}
	
	public void item(Entity entity, int x, int y, int size)
	{
		// not available in 1.21.11, no-op
	}
	
	public void fakeItem(ItemStack stack, int x, int y)
	{
		inner.renderFakeItem(stack, x, y);
	}
	
	public void fakeItem(ItemStack stack, int x, int y, int seed)
	{
		inner.renderFakeItem(stack, x, y, seed);
	}
	
	public void itemDecorations(Font font, ItemStack stack, int x, int y)
	{
		inner.renderItemDecorations(font, stack, x, y);
	}
	
	public void itemDecorations(Font font, ItemStack stack, int x, int y,
		String count)
	{
		inner.renderItemDecorations(font, stack, x, y, count);
	}
	
	public void map()
	{
		// requires MapRenderState in 1.21.11, no-op
	}
	
	public void entity(Entity entity, float partialTicks, Vector3fc offset,
		Quaternionf rotation, Quaternionf scale, int x, int y, int width,
		int height)
	{
		// requires EntityRenderState in 1.21.11, no-op
	}
	
	public void skin(Object model, Entity entity, float headYaw,
		float headPitch, float yaw, float roll, int x, int y, int width,
		int height)
	{
		// requires PlayerModel in 1.21.11, no-op
	}
	
	public void book(Component text, int page, float partialTicks,
		float scale, int x, int y, int width, int height)
	{
		// requires BookModel in 1.21.11, no-op
	}
	
	public void bannerPattern(Identifier bannerTexture, int color,
		boolean transparent, int x, int y, int width, int height)
	{
		// requires BannerFlagModel in 1.21.11, no-op
	}
	
	public void profilerChart(List<Object> profileResults, int x, int y,
		int width, int height)
	{
		// requires ResultField list in 1.21.11, no-op
	}
	
	public void setComponentTooltipForNextFrame(Font font,
		List<Component> lines, int mouseX, int mouseY)
	{
		inner.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
	}
	
	public void setPreeditOverlay()
	{
		// not available in 1.21.11, no-op
	}
	
	public void extractDeferredElements(int mouseX, int mouseY,
		float partialTicks)
	{
		inner.renderDeferredElements();
	}
	
	public GuiRenderState getRenderState()
	{
		return inner.getRenderState();
	}
	
	public void nextStratumBlur()
	{
		// not available in 1.21.11, no-op
	}
	
	public void requestFocus()
	{
		// not available in 1.21.11, no-op
	}
}
