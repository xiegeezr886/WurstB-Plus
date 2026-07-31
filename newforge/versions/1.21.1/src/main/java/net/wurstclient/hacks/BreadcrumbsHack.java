/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.ColorSetting;

@SearchTags({"breadcrumbs", "trail", "path", "footprints"})
public final class BreadcrumbsHack extends Hack
	implements UpdateListener, RenderListener, WorldChangeListener
{
	private final SliderSetting maxPoints =
		new SliderSetting("Max points", 500, 50, 5000, 50, SliderSetting.ValueDisplay.INTEGER);
	private final ColorSetting color =
		new ColorSetting("Color", "Trail color", java.awt.Color.CYAN);

	private final List<Vec3> points = new ArrayList<>();
	private Vec3 lastPos;

	public BreadcrumbsHack()
	{
		super("Breadcrumbs");
		setCategory(Category.RENDER);
		addSetting(maxPoints);
		addSetting(color);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		lastPos = null;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		points.clear();
		lastPos = null;
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		points.clear();
		lastPos = null;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;

		Vec3 pos = MC.player.position();
		if(lastPos != null && pos.distanceTo(lastPos) > 0.1)
			points.add(pos);

		lastPos = pos;

		int max = (int)maxPoints.getValueI();
		while(points.size() > max)
			points.remove(0);
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks)
	{
		if(MC.level == null || MC.player == null || points.size() < 2)
			return;

		Vec3 cam = MC.gameRenderer.getMainCamera().getPosition();
		Matrix4f matrix = poseStack.last().pose();
		int argb = color.getColorI();
		float r = ((argb >> 16) & 0xFF) / 255F;
		float g = ((argb >> 8) & 0xFF) / 255F;
		float b = (argb & 0xFF) / 255F;

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		try
		{
			Tesselator tess = Tesselator.getInstance();
			BufferBuilder buf = tess.begin(VertexFormat.Mode.DEBUG_LINE_STRIP,
				DefaultVertexFormat.POSITION_COLOR);
			RenderSystem.lineWidth(2);

			for(int i = 0; i < points.size(); i++)
			{
				Vec3 point = points.get(i);
				float progress = i / (float)points.size();
				float alpha = progress * 0.8F;

				float x = (float)(point.x - cam.x);
				float y = (float)(point.y - cam.y + 0.1);
				float z = (float)(point.z - cam.z);
				buf.addVertex(matrix, x, y, z).setColor(r, g, b, alpha)
					;
			}

			com.mojang.blaze3d.vertex.MeshData rendered = buf.build();
			if(rendered != null)
				BufferUploader.drawWithShader(rendered);
		}finally
		{
			RenderSystem.depthMask(true);
			RenderSystem.enableDepthTest();
			RenderSystem.lineWidth(1);
			RenderSystem.disableBlend();
		}
	}
}
