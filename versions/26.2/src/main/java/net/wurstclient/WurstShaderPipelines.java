/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import java.util.Optional;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public enum WurstShaderPipelines
{
	;
	
	/**
	 * Similar to the RENDERTYPE_LINES Snippet, but without fog.
	 */
	public static final Snippet FOGLESS_LINES_SNIPPET = RenderPipeline
		.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
		.withVertexShader(Identifier.parse("wurst:core/fogless_lines"))
		.withFragmentShader(Identifier.parse("wurst:core/fogless_lines"))
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withCull(false)
		.withVertexBinding(0,
			DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
		.withPrimitiveTopology(PrimitiveTopology.LINES)
		.buildSnippet();
	
	public static final RenderPipeline DEPTH_TEST_LINES =
		register(RenderPipeline.builder(FOGLESS_LINES_SNIPPET)
			.withLocation(
				Identifier.parse("wurst:pipeline/wurst_depth_test_lines"))
			.withDepthStencilState(DepthStencilState.DEFAULT).build());
	
	public static final RenderPipeline ESP_LINES =
		register(RenderPipeline.builder(FOGLESS_LINES_SNIPPET)
			.withLocation(Identifier.parse("wurst:pipeline/wurst_esp_lines"))
			.withDepthStencilState(Optional.empty()).build());
	
	public static final RenderPipeline QUADS = register(
		RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.parse("wurst:pipeline/wurst_quads"))
			.withDepthStencilState(DepthStencilState.DEFAULT).build());
	
	public static final RenderPipeline ESP_QUADS = register(
		RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.parse("wurst:pipeline/wurst_esp_quads"))
			.withDepthStencilState(Optional.empty()).build());
	
	public static final RenderPipeline ESP_QUADS_NO_CULLING = register(
		RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(
				Identifier.parse("wurst:pipeline/wurst_esp_quads_no_culling"))
			.withDepthStencilState(Optional.empty()).withCull(false).build());
	
	private static RenderPipeline register(RenderPipeline pipeline)
	{
		RenderPipelines.PIPELINES_BY_LOCATION.put(pipeline.getLocation(), pipeline);
		return pipeline;
	}
}
