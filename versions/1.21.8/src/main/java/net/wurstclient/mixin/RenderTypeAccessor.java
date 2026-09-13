/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.renderer.RenderType;

/**
 * 1.21.9+ exposes {@code RenderType#pipeline()}. 1.21.8 keeps the pipeline in a
 * private field of {@code CompositeRenderType}, so it has to be accessed
 * through a mixin.
 */
@Mixin(RenderType.CompositeRenderType.class)
public interface RenderTypeAccessor
{
	@Accessor("renderPipeline")
	RenderPipeline getRenderPipeline();
}
