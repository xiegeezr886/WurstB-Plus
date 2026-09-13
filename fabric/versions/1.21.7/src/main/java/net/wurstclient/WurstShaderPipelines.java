/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

public enum WurstShaderPipelines
{
	;
	
	/**
	 * Similar to the RENDERTYPE_LINES Snippet, but without fog.
	 *
	 * <p>
	 * 1.21.9 has no {@code POSITION_COLOR_NORMAL_LINE_WIDTH} vertex format, so
	 * the custom shader takes a plain {@code POSITION_COLOR_NORMAL} stream and
	 * uses a fixed line width of 2.
	 */
	public static final Snippet FOGLESS_LINES_SNIPPET = RenderPipeline
		.builder(RenderPipelines.LINES_SNIPPET,
			RenderPipelines.GLOBALS_SNIPPET)
		.withVertexShader(ResourceLocation.parse("wurst:core/fogless_lines"))
		.withFragmentShader(ResourceLocation.parse("wurst:core/fogless_lines"))
		.withBlend(BlendFunction.TRANSLUCENT)
		.withCull(false)
		.withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, Mode.LINES)
		.buildSnippet();
	
	public static final RenderPipeline DEPTH_TEST_LINES =
		register(RenderPipeline.builder(FOGLESS_LINES_SNIPPET)
			.withLocation(
				ResourceLocation.parse("wurst:pipeline/wurst_depth_test_lines"))
			.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).build());
	
	public static final RenderPipeline ESP_LINES =
		register(RenderPipeline.builder(FOGLESS_LINES_SNIPPET)
			.withLocation(ResourceLocation.parse("wurst:pipeline/wurst_esp_lines"))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false).build());
	
	public static final RenderPipeline QUADS = register(
		RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(ResourceLocation.parse("wurst:pipeline/wurst_quads"))
			.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).build());
	
	public static final RenderPipeline ESP_QUADS = register(
		RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(ResourceLocation.parse("wurst:pipeline/wurst_esp_quads"))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false).build());
	
	public static final RenderPipeline ESP_QUADS_NO_CULLING = register(
		RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(
				ResourceLocation.parse("wurst:pipeline/wurst_esp_quads_no_culling"))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false).withCull(false).build());
	
	/**
	 * 1.21.7's {@code RenderType} has no {@code pipeline()} accessor (that
	 * arrives in 1.21.9), so the Wurst render layers are mapped back to their
	 * pipelines here.
	 */
	public static RenderPipeline pipelineFor(
		net.minecraft.client.renderer.RenderType layer)
	{
		if(layer == net.wurstclient.WurstRenderLayers.LINES)
			return DEPTH_TEST_LINES;
		if(layer == net.wurstclient.WurstRenderLayers.ESP_LINES)
			return ESP_LINES;
		if(layer == net.wurstclient.WurstRenderLayers.QUADS)
			return QUADS;
		if(layer == net.wurstclient.WurstRenderLayers.ESP_QUADS)
			return ESP_QUADS;
		if(layer == net.wurstclient.WurstRenderLayers.ESP_QUADS_NO_CULLING)
			return ESP_QUADS_NO_CULLING;
		throw new IllegalArgumentException("Unknown Wurst render layer: " + layer);
	}
	
	private static RenderPipeline register(RenderPipeline pipeline)
	{
		return RenderPipelines.register(pipeline);
	}
}
