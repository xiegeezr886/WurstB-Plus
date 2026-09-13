/*
 * Copyright (c) 2026 Penguin
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
 * Minecraft 1.21.6 does not expose the {@link RenderPipeline} that a
 * {@link RenderType} was built with (1.21.9 added {@code RenderType.pipeline()}
 * and 1.21.11 removed {@code RenderType.setupRenderState()}), so we read the
 * private field directly.
 */
@Mixin(RenderType.CompositeRenderType.class)
public interface RenderTypeAccessor
{
	@Accessor("renderPipeline")
	RenderPipeline wurst$getRenderPipeline();
}
