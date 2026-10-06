/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.wurstclient.WurstClient;

/**
 * 不透明度模式（Opacity X-Ray）：把方块整体归到半透明层。
 *
 * <p>
 * 1.21.6 起 {@code ItemBlockRenderTypes.getChunkRenderType} 的返回类型由
 * {@code RenderType} 变成了 {@code ChunkSectionLayer}（已用 javap 对着
 * 1.21.6 / 1.21.7 两个版本的真实映射 jar 核实）。所以**不能沿用 1.21.5 那份** ——
 * 注入签名会与方法实际签名对不上，而 mixin 注入失败是硬错误（启动崩）。
 * </p>
 */
@Mixin(ItemBlockRenderTypes.class)
public abstract class RenderLayersMixin
{
	/**
	 * Puts all blocks on the translucent layer if Opacity X-Ray is enabled.
	 */
	@Inject(at = @At("HEAD"),
		method = "getChunkRenderType(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;",
		cancellable = true)
	private static void onGetBlockLayer(BlockState state,
		CallbackInfoReturnable<ChunkSectionLayer> cir)
	{
		if(!WurstClient.INSTANCE.getHax().xRayHack.isOpacityMode())
			return;

		cir.setReturnValue(ChunkSectionLayer.TRANSLUCENT);
	}
}