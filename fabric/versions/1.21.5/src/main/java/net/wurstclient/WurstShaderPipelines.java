/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom render pipelines for Wurst's ESP/line rendering.
 *
 * <p>
 * 1.21.5 moved all depth test, cull and blend state out of
 * {@code RenderStateShard} and into {@link RenderPipeline}. The
 * {@code RenderType.CompositeState} that remains only carries layering, line
 * width and the output target, so the "with depth test" / "without depth test"
 * variants of each layer are built here instead.
 */
public enum WurstShaderPipelines
{
	;
	
	/**
	 * Similar to {@link RenderPipelines#LINES}, with the default depth test.
	 */
	public static final RenderPipeline LINES = register(
		RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation(location("wurst_lines"))
			.withBlend(BlendFunction.TRANSLUCENT)
			.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
			.build());
	
	/**
	 * Similar to {@link RenderPipelines#LINES}, but without a depth test.
	 */
	public static final RenderPipeline ESP_LINES = register(
		RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation(location("wurst_esp_lines"))
			.withBlend(BlendFunction.TRANSLUCENT)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false).build());
	
	/**
	 * Line strip variant of {@link #LINES}.
	 */
	public static final RenderPipeline LINE_STRIP = register(
		RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation(location("wurst_line_strip"))
			.withBlend(BlendFunction.TRANSLUCENT)
			.withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL,
				Mode.LINE_STRIP)
			.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
			.build());
	
	/**
	 * Line strip variant of {@link #ESP_LINES}.
	 */
	public static final RenderPipeline ESP_LINE_STRIP = register(
		RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation(location("wurst_esp_line_strip"))
			.withBlend(BlendFunction.TRANSLUCENT)
			.withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL,
				Mode.LINE_STRIP)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false).build());
	
	/**
	 * One-pixel debug lines using the vanilla {@code position_color} shader.
	 */
	public static final RenderPipeline ONE_PIXEL_LINES =
		positionColor("wurst_one_pixel_lines", Mode.DEBUG_LINES, true);
	
	/**
	 * One-pixel debug line strip using the vanilla {@code position_color}
	 * shader.
	 */
	public static final RenderPipeline ONE_PIXEL_LINE_STRIP = positionColor(
		"wurst_one_pixel_line_strip", Mode.DEBUG_LINE_STRIP, true);
	
	/**
	 * Similar to {@link RenderPipelines#DEBUG_QUADS}, with culling enabled.
	 */
	public static final RenderPipeline QUADS =
		positionColor("wurst_quads", Mode.QUADS, true);
	
	/**
	 * Similar to {@link RenderPipelines#DEBUG_QUADS}, but with culling enabled
	 * and no depth test.
	 */
	public static final RenderPipeline ESP_QUADS =
		positionColor("wurst_esp_quads", Mode.QUADS, false);
	
	/**
	 * Similar to {@link RenderPipelines#DEBUG_QUADS}, but with no depth test
	 * and no culling.
	 */
	public static final RenderPipeline ESP_QUADS_NO_CULLING = positionColorNoCull(
		"wurst_esp_quads_no_culling", Mode.QUADS);
	
	/**
	 * Position/colour triangles with no depth test and no culling.
	 */
	public static final RenderPipeline ESP_TRIANGLES =
		positionColorNoCull("wurst_esp_triangles", Mode.TRIANGLES);
	
	/**
	 * Position/colour triangle strip with no depth test and no culling.
	 */
	public static final RenderPipeline ESP_TRIANGLE_STRIP =
		positionColorNoCull("wurst_esp_triangle_strip", Mode.TRIANGLE_STRIP);
	
	/**
	 * Position/colour debug lines with no depth test and no culling.
	 */
	public static final RenderPipeline ESP_DEBUG_LINES =
		positionColorNoCull("wurst_esp_debug_lines", Mode.DEBUG_LINES);
	
	/**
	 * Position/colour debug line strip with no depth test and no culling.
	 */
	public static final RenderPipeline ESP_DEBUG_LINE_STRIP = positionColorNoCull(
		"wurst_esp_debug_line_strip", Mode.DEBUG_LINE_STRIP);
	
	private static RenderPipeline positionColor(String path, Mode mode,
		boolean depthTest)
	{
		RenderPipeline.Builder builder =
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
				.withLocation(location(path))
				.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, mode)
				.withBlend(BlendFunction.TRANSLUCENT);
		
		if(depthTest)
			builder.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST);
		else
			builder.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withDepthWrite(false);
		
		return register(builder.build());
	}
	
	private static RenderPipeline positionColorNoCull(String path, Mode mode)
	{
		return register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
				.withLocation(location(path))
				.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, mode)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withDepthWrite(false).withCull(false).build());
	}
	
	private static ResourceLocation location(String path)
	{
		return ResourceLocation.parse("wurst:pipeline/" + path);
	}
	
	private static RenderPipeline register(RenderPipeline pipeline)
	{
		RenderPipelines.PIPELINES_BY_LOCATION.put(pipeline.getLocation(),
			pipeline);
		return pipeline;
	}
}
