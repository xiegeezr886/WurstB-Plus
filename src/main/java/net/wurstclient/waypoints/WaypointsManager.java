/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.waypoints;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.events.RenderListener;
import net.wurstclient.util.json.JsonException;
import net.wurstclient.util.json.JsonUtils;
import net.wurstclient.util.json.WsonArray;
import net.wurstclient.util.json.WsonObject;

public final class WaypointsManager implements RenderListener
{
	private final List<Waypoint> waypoints = new ArrayList<>();
	private final Path file;

	public WaypointsManager(Path wurstFolder)
	{
		file = wurstFolder.resolve("waypoints.json");
		load();
	}

	private void load()
	{
		try
		{
			WsonArray wson = JsonUtils.parseFileToArray(file);
			for(int i = 0; i < wson.size(); i++)
			{
				WsonObject obj = wson.getObject(i);
				String name = obj.getString("name");
				ResourceLocation dim = ResourceLocation
					.parse(obj.getString("dimension"));
				int x = obj.getInt("x");
				int y = obj.getInt("y");
				int z = obj.getInt("z");
				int color = obj.getInt("color", 0xFF00FFFF);
				waypoints.add(new Waypoint(name, dim, new BlockPos(x, y, z),
					color));
			}
		}catch(NoSuchFileException e)
		{}catch(IOException | JsonException e)
		{
			System.err.println("Couldn't load waypoints.json");
			e.printStackTrace();
		}
	}

	public void save()
	{
		JsonArray array = new JsonArray();
		for(Waypoint wp : waypoints)
		{
			JsonObject obj = new JsonObject();
			obj.addProperty("name", wp.getName());
			obj.addProperty("dimension",
				wp.getDimension().toString());
			obj.addProperty("x", wp.getPos().getX());
			obj.addProperty("y", wp.getPos().getY());
			obj.addProperty("z", wp.getPos().getZ());
			obj.addProperty("color", wp.getColor());
			array.add(obj);
		}
		try
		{
			JsonUtils.toJson(array, file);
		}catch(IOException | JsonException e)
		{
			System.err.println("Couldn't save waypoints.json");
			e.printStackTrace();
		}
	}

	public List<Waypoint> getAllWaypoints()
	{
		return Collections.unmodifiableList(waypoints);
	}

	public void add(Waypoint wp)
	{
		waypoints.removeIf(w -> w.getName().equals(wp.getName()));
		waypoints.add(wp);
		save();
	}

	public void remove(String name)
	{
		waypoints.removeIf(w -> w.getName().equals(name));
		save();
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks)
	{
		Minecraft mc = WurstClient.MC;
		if(waypoints.isEmpty() || mc.level == null || mc.player == null)
			return;

		ResourceLocation currentDim = mc.level.dimension().location();

		Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		try
		{
			Tesselator tesselator = Tesselator.getInstance();
			BufferBuilder builder = tesselator.getBuilder();
			builder.begin(VertexFormat.Mode.QUADS,
				DefaultVertexFormat.POSITION_COLOR);

			org.joml.Matrix4f matrix = poseStack.last().pose();
			for(Waypoint wp : waypoints)
			{
				if(!wp.getDimension().equals(currentDim))
					continue;

				Vec3 pos = Vec3.atCenterOf(wp.getPos());
				int argb = wp.getColor();
				float r = ((argb >> 16) & 0xFF) / 255F;
				float g = ((argb >> 8) & 0xFF) / 255F;
				float b = (argb & 0xFF) / 255F;
				float a = 0.6F;

				float x = (float)(pos.x - camPos.x);
				float y = (float)(pos.y - camPos.y);
				float z = (float)(pos.z - camPos.z);
				float hs = 0.3F;

				builder.vertex(matrix, x - hs, y + hs, z)
					.color(r, g, b, a).endVertex();
				builder.vertex(matrix, x + hs, y + hs, z)
					.color(r, g, b, a).endVertex();
				builder.vertex(matrix, x + hs, y - hs, z)
					.color(r, g, b, a).endVertex();
				builder.vertex(matrix, x - hs, y - hs, z)
					.color(r, g, b, a).endVertex();

				builder.vertex(matrix, x, y + hs, z - hs)
					.color(r, g, b, a).endVertex();
				builder.vertex(matrix, x, y + hs, z + hs)
					.color(r, g, b, a).endVertex();
				builder.vertex(matrix, x, y - hs, z + hs)
					.color(r, g, b, a).endVertex();
				builder.vertex(matrix, x, y - hs, z - hs)
					.color(r, g, b, a).endVertex();
			}

			BufferBuilder.RenderedBuffer rendered =
				builder.endOrDiscardIfEmpty();
			if(rendered != null)
				BufferUploader.drawWithShader(rendered);
		}finally
		{
			RenderSystem.depthMask(true);
			RenderSystem.enableDepthTest();
			RenderSystem.disableBlend();
		}
	}
}
