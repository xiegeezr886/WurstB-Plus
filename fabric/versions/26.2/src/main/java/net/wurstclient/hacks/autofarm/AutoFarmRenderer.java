/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.autofarm;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.PrimitiveTopology;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.util.EasyVertexBuffer;
import net.wurstclient.util.RegionPos;
import net.wurstclient.util.RenderUtils;

public final class AutoFarmRenderer
{
	private static final AABB BLOCK_BOX =
		new AABB(BlockPos.ZERO).deflate(1 / 16.0);
	private static final AABB NODE_BOX = new AABB(BlockPos.ZERO).deflate(0.25);
	
	private EasyVertexBuffer vertexBuffer;
	private RegionPos region;
	
	public void reset()
	{
		if(vertexBuffer != null)
		{
			vertexBuffer.close();
			vertexBuffer = null;
		}
	}
	
	public void render(PoseStack matrixStack)
	{
		if(vertexBuffer == null || region == null)
			return;
		
		matrixStack.pushPose();
		RenderUtils.applyRegionalRenderOffset(matrixStack, region);
		
		vertexBuffer.draw(matrixStack, WurstRenderLayers.ESP_LINES);
		
		matrixStack.popPose();
	}
	
	public void updateVertexBuffers(List<BlockPos> blocksToHarvest,
		Set<BlockPos> plants, List<BlockPos> blocksToReplant)
	{
		reset();
		
		if(blocksToHarvest.isEmpty() && plants.isEmpty()
			&& blocksToReplant.isEmpty())
			return;
		
		vertexBuffer = EasyVertexBuffer.createAndUpload(PrimitiveTopology.LINES,
			DefaultVertexFormat.POSITION_COLOR_NORMAL, buffer -> buildBuffer(buffer, blocksToHarvest,
				plants, blocksToReplant));
	}
	
	private void buildBuffer(VertexConsumer buffer,
		List<BlockPos> blocksToHarvest, Set<BlockPos> plants,
		List<BlockPos> blocksToReplant)
	{
		region = RenderUtils.getCameraRegion();
		Vec3 regionOffset = region.negate().toVec3d();
		
		for(BlockPos pos : blocksToHarvest)
		{
			AABB box = BLOCK_BOX.move(pos).move(regionOffset);
			RenderUtils.drawOutlinedBox(buffer, box, 0x8000FF00);
		}
		
		for(BlockPos pos : plants)
		{
			AABB renderNode = NODE_BOX.move(pos).move(regionOffset);
			RenderUtils.drawNode(buffer, renderNode, 0x8000FFFF);
		}
		
		for(BlockPos pos : blocksToReplant)
		{
			AABB renderBox = BLOCK_BOX.move(pos).move(regionOffset);
			RenderUtils.drawOutlinedBox(buffer, renderBox, 0x80FF0000);
		}
	}
}
