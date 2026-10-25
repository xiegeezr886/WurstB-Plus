/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.waypoints.Waypoint;

/**
 * HUD list of saved waypoints with distance and compass direction, plus
 * optional world-space beams and base markers.
 *
 * <p>Simplification: Soar drew camera-facing name tags in the world and
 * opened its own waypoint editor GUI from a keybind. This port uses Wurst's
 * existing {@code /waypoints add|remove|list} command and the shared
 * {@link net.wurstclient.waypoints.WaypointsManager} instead. The manager's
 * small marker quads are always drawn; this hack adds the optional beams.
 */
@SearchTags({"waypoint", "waypoints", "beam", "marker", "navigation",
	"compass"})
public final class WaypointHack extends Hack
	implements GUIRenderListener, RenderListener
{
	private static final int PADDING = 4;
	private static final int SWATCH_WIDTH = 4;
	private static final int TEXT_X = 8;
	private static final float LINE_WIDTH = 2;

	private static final String[] DIRECTIONS =
		{"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the waypoint list.", 1, 0.5, 3, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the waypoint list.", 10, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the waypoint list.", 10, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the list.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the waypoint list text.", Color.WHITE);

	private final CheckboxSetting showBeams = new CheckboxSetting("Show beams",
		"Draws a vertical beam at each waypoint in the current dimension.",
		true);

	private final SliderSetting beamHeight = new SliderSetting("Beam height",
		"Height of the waypoint beams in blocks.", 32, 4, 128, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders the beams through solid blocks.", true);

	public WaypointHack()
	{
		super("Waypoint");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(background);
		addSetting(bgColor);
		addSetting(textColor);
		addSetting(showBeams);
		addSetting(beamHeight);
		addSetting(throughWalls);
		bgColor.visibleWhen(background::isChecked);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(GUIRenderListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
		EVENTS.remove(RenderListener.class, this);
	}

	@Override
	public void onRenderGUI(GuiGraphics context, float partialTicks)
	{
		List<Waypoint> waypoints = getVisibleWaypoints();
		if(waypoints.isEmpty())
			return;

		Font font = MC.font;
		int lineHeight = font.lineHeight + 2;
		String title = "Waypoints (" + waypoints.size() + ")";
		int width = font.width(title);

		for(Waypoint wp : waypoints)
			width = Math.max(width, TEXT_X + font.width(getEntryText(wp)));

		int height = lineHeight + waypoints.size() * lineHeight;
		PoseStack pose = context.pose();
		pose.pushPose();
		pose.translate(posX.getValueF(), posY.getValueF(), 0);
		pose.scale(scale.getValueF(), scale.getValueF(), 1);

		if(background.isChecked())
			context.fill(-PADDING, -PADDING, width + PADDING,
				height + PADDING, bgColor.getColorI());

		context.drawString(font, title, 0, 0, textColor.getColorI(), true);

		int y = lineHeight;
		for(Waypoint wp : waypoints)
		{
			context.fill(0, y + 1, SWATCH_WIDTH, y + 5, wp.getColor());
			context.drawString(font, getEntryText(wp), TEXT_X, y,
				textColor.getColorI(), true);
			y += lineHeight;
		}

		pose.popPose();
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(!showBeams.isChecked())
			return;

		List<Waypoint> waypoints = getVisibleWaypoints();
		if(waypoints.isEmpty())
			return;

		Vec3 cam = RenderUtils.getCameraPos();
		Matrix4f matrix = matrixStack.last().pose();
		RenderType layer = WurstRenderLayers.getLines(!throughWalls.isChecked());
		float height = beamHeight.getValueF();

		MultiBufferSource.BufferSource vcp = RenderUtils.getVCP();
		VertexConsumer buffer = vcp.getBuffer(layer);
		for(Waypoint wp : waypoints)
			drawBeam(buffer, matrix, cam, wp, height);
		vcp.endBatch(layer);
	}

	private void drawBeam(VertexConsumer buffer, Matrix4f matrix, Vec3 cam,
		Waypoint wp, float height)
	{
		float x = (float)(wp.getPos().getX() + 0.5 - cam.x);
		float y = (float)(wp.getPos().getY() - cam.y);
		float z = (float)(wp.getPos().getZ() + 0.5 - cam.z);

		int argb = wp.getColor();
		float r = ((argb >> 16) & 0xFF) / 255F;
		float g = ((argb >> 8) & 0xFF) / 255F;
		float b = (argb & 0xFF) / 255F;
		float a = 0.75F;

		// Vertical beam.
		addLine(buffer, matrix, x, y, z, x, y + height, z, r, g, b, a);

		// Cross-shaped marker at the base.
		addLine(buffer, matrix, x - 0.75F, y + 0.05F, z, x + 0.75F,
			y + 0.05F, z, r, g, b, a);
		addLine(buffer, matrix, x, y + 0.05F, z - 0.75F, x, y + 0.05F,
			z + 0.75F, r, g, b, a);
	}

	private void addLine(VertexConsumer buffer, Matrix4f matrix, float x1,
		float y1, float z1, float x2, float y2, float z2, float r, float g,
		float b, float a)
	{
		float dx = x2 - x1;
		float dy = y2 - y1;
		float dz = z2 - z1;
		float length = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
		if(length < 1.0E-5F)
		{
			dx = 0;
			dy = 1;
			dz = 0;
			length = 1;
		}

		float nx = dx / length;
		float ny = dy / length;
		float nz = dz / length;

		buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a)
			.normal(nx, ny, nz).endVertex();
		buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a)
			.normal(nx, ny, nz).endVertex();
	}

	private List<Waypoint> getVisibleWaypoints()
	{
		if(MC.player == null || MC.level == null
			|| WURST.getWaypointsManager() == null)
			return List.of();

		ResourceLocation dimension = MC.level.dimension().location();
		List<Waypoint> visible = new ArrayList<>();
		for(Waypoint wp : WURST.getWaypointsManager().getAllWaypoints())
			if(wp.getDimension().equals(dimension))
				visible.add(wp);

		visible.sort(Comparator.comparingDouble(this::getDistance));
		return visible;
	}

	private String getEntryText(Waypoint wp)
	{
		return String.format(Locale.ROOT, "%s  %dm  %s", wp.getName(),
			(int)Math.round(getDistance(wp)), getDirection(wp));
	}

	private double getDistance(Waypoint wp)
	{
		return Vec3.atCenterOf(wp.getPos()).distanceTo(MC.player.position());
	}

	private String getDirection(Waypoint wp)
	{
		Vec3 playerPos = MC.player.position();
		double dx = wp.getPos().getX() + 0.5 - playerPos.x;
		double dz = wp.getPos().getZ() + 0.5 - playerPos.z;
		double bearing = Math.toDegrees(Math.atan2(dx, -dz));
		int index = Math.floorMod((int)Math.round(bearing / 45.0), 8);
		return DIRECTIONS[index];
	}
}
