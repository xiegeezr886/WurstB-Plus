/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
// BufferUploader removed in MC 26.1.2
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;

@SearchTags({"light overlay", "spawn", "safe"})
public final class LightOverlayHack extends Hack implements RenderListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 8, 1, 32, 1, SliderSetting.ValueDisplay.INTEGER);

	public LightOverlayHack()
	{
		super("LightOverlay");
		setCategory(Category.RENDER);
		addSetting(range);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(RenderListener.class, this);
	}

	@Override
	public void onRender(PoseStack PoseStack, float partialTicks)
	{
		if(MC.level == null || MC.player == null)
			return;

		Vec3 cam = MC.gameRenderer.getMainCamera().position();
		Matrix4f matrix = PoseStack.last().pose();
		int r = (int)range.getValueI();

		// Blend state managed by render pipeline
		// Blend state managed by render pipeline
		// Depth state managed by render pipeline
		// Depth state managed by render pipeline
		// Shader managed by render pipeline
		try
		{
			Tesselator tess = Tesselator.getInstance();
			BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS,
				DefaultVertexFormat.POSITION_COLOR);

			for(int x = -r; x <= r; x++)
				for(int z = -r; z <= r; z++)
				{
					BlockPos pos = MC.player.blockPosition().offset(x, -1, z);
					while(pos.getY() > MC.level.getMinY() * 16
						&& !MC.level.getBlockState(pos).isSolid())
						pos = pos.below();

					int light = MC.level.getBrightness(LightLayer.BLOCK,
						pos.above());
					if(light >= 8)
						continue;

					float alpha = (8 - light) / 16F;
					float px = (float)(pos.getX() - cam.x);
					float py = (float)(pos.above().getY() - cam.y + 0.01);
					float pz = (float)(pos.getZ() - cam.z);
					float s = 0.5F;

					buf.addVertex(matrix, px - s, py, pz - s)
						.setColor(1, 1, 0, alpha);
					buf.addVertex(matrix, px - s, py, pz + s)
						.setColor(1, 1, 0, alpha);
					buf.addVertex(matrix, px + s, py, pz + s)
						.setColor(1, 1, 0, alpha);
					buf.addVertex(matrix, px + s, py, pz - s)
						.setColor(1, 1, 0, alpha);
				}

			com.mojang.blaze3d.vertex.MeshData rendered = buf.build();
			if(rendered != null)
			{
				// BufferUploader removed in MC 26.1.2
			}
		}finally
		{
		}
	}
}
