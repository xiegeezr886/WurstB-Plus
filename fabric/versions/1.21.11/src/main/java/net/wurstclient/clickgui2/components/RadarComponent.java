/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.components;

import org.joml.Matrix3x2fStack;
import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.clickgui2.ClickGui;
import net.wurstclient.clickgui2.ClickGuiIcons;
import net.wurstclient.clickgui2.Component;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.theme.FlatTheme;
import net.wurstclient.hacks.RadarHack;
import net.wurstclient.util.EntityUtils;

public final class RadarComponent extends Component
{
	private static final int PANEL_INSET = 3;
	private static final int FOOTER_HEIGHT = 13;

	private final RadarHack hack;
	
	public RadarComponent(RadarHack hack)
	{
		this.hack = hack;
		setWidth(getDefaultWidth());
		setHeight(getDefaultHeight());
	}
	
	@Override
	public void render(GuiGraphicsExtractor context, int mouseX, int mouseY,
		float partialTicks)
	{
		// Can't make this a field because RadarComponent is initialized earlier
		// than ClickGui.
		ClickGui gui = WURST.getGui();
		FlatTheme theme = gui.getTheme();
		
		int x1 = getX();
		int x2 = x1 + getWidth();
		int y1 = getY();
		int y2 = y1 + getHeight();
		int mapLeft = x1 + PANEL_INSET;
		int mapTop = y1 + PANEL_INSET;
		int mapRight = x2 - PANEL_INSET;
		int mapBottom = y2 - FOOTER_HEIGHT;
		float middleX = (mapLeft + mapRight) / 2F;
		float middleY = (mapTop + mapBottom) / 2F;
		int radarRadius = Math.max(8,
			Math.min(mapRight - mapLeft, mapBottom - mapTop) / 2 - 5);
		
		if(isHovering(mouseX, mouseY))
			gui.setTooltip("");
		
		boolean hovering = isHovering(mouseX, mouseY);
		FlatRenderer.fillRoundedRect(context, x1, y1, x2, y2, 3,
			theme.mix(0.018F, 1));
		FlatRenderer.drawRoundedOutline(context, x1, y1, x2, y2, 3,
			theme.accent(hovering ? 0.42F : 0.22F));
		drawRadarField(context, theme, middleX, middleY, radarRadius);
		
		LocalPlayer player = MC.player;
		if(player == null)
		{
			drawFooter(context, theme, x1, x2, y2, 0);
			return;
		}

		Matrix3x2fStack matrixStack = context.pose();
		matrixStack.pushMatrix();
		matrixStack.translate(middleX, middleY);
		
		if(!hack.isRotateEnabled())
			matrixStack.rotate((180 + player.getYRot()) * Mth.DEG_TO_RAD);
		
		FlatRenderer.fillRoundedRect(context, -5, -5, 5, 5, 5,
			theme.background(0.92F));
		FlatRenderer.drawRoundedOutline(context, -5, -5, 5, 5, 5,
			theme.accent(0.42F));
		ClickGuiIcons.drawRadarArrow(context, -3, -4, 3, 4);
		
		matrixStack.popMatrix();
		Vec3 lerpedPlayerPos = EntityUtils.getLerpedPos(player, partialTicks);
		
		int entityCount = 0;
		for(Entity e : hack.getEntities())
		{
			Vec3 lerpedEntityPos = EntityUtils.getLerpedPos(e, partialTicks);
			double diffX = lerpedEntityPos.x - lerpedPlayerPos.x;
			double diffZ = lerpedEntityPos.z - lerpedPlayerPos.z;
			double scale = radarRadius / hack.getRadius();
			RadarPoint point = project(diffX, diffZ, player.getYRot(),
				hack.isRotateEnabled(), scale);
			
			if(!isInsideRadar(point, radarRadius - 2))
				continue;
			
			int pointX = Math.round(middleX + (float)point.x);
			int pointY = Math.round(middleY + (float)point.y());
			int color = getEntityColor(e);
			if(Math.abs(e.getY() - player.getY()) > 16)
				color = color & 0xFFFFFF | 0xA0000000;
			drawEntityPoint(context, theme, pointX, pointY, color,
				e instanceof Player);
			entityCount++;
		}

		drawFooter(context, theme, x1, x2, y2, entityCount);
	}

	private void drawRadarField(GuiGraphicsExtractor context, FlatTheme theme,
		float middleX, float middleY, int radius)
	{
		int centerX = Math.round(middleX);
		int centerY = Math.round(middleY);
		FlatRenderer.fillRoundedRect(context, centerX - radius,
			centerY - radius, centerX + radius, centerY + radius, radius,
			theme.background(1));
		FlatRenderer.drawRoundedOutline(context, centerX - radius,
			centerY - radius, centerX + radius, centerY + radius, radius,
			theme.accent(0.28F));

		for(int ring = 1; ring <= 2; ring++)
		{
			int ringRadius = radius * ring / 3;
			FlatRenderer.drawRoundedOutline(context, centerX - ringRadius,
				centerY - ringRadius, centerX + ringRadius,
				centerY + ringRadius, ringRadius, theme.accent(0.09F));
		}

		context.fill(centerX, centerY - radius + 3, centerX + 1,
			centerY + radius - 3, theme.accent(0.1F));
		context.fill(centerX - radius + 3, centerY,
			centerX + radius - 3, centerY + 1, theme.accent(0.1F));
		FlatRenderer.fillRoundedRect(context, centerX - 1,
			centerY - radius - 1, centerX + 2, centerY + 3 - radius, 1,
			theme.accent(0.9F));
	}

	private void drawEntityPoint(GuiGraphicsExtractor context, FlatTheme theme, int x,
		int y, int color, boolean player)
	{
		int outerRadius = player ? 3 : 2;
		int innerRadius = player ? 2 : 1;
		FlatRenderer.fillRoundedRect(context, x - outerRadius,
			y - outerRadius, x + outerRadius + 1, y + outerRadius + 1,
			outerRadius, theme.shadow(0.85F));
		FlatRenderer.fillRoundedRect(context, x - innerRadius,
			y - innerRadius, x + innerRadius + 1, y + innerRadius + 1,
			innerRadius, color);
	}

	private void drawFooter(GuiGraphicsExtractor context, FlatTheme theme, int x1,
		int x2, int y2, int entityCount)
	{
		int dividerY = y2 - FOOTER_HEIGHT;
		context.fill(x1 + 5, dividerY, x2 - 5, dividerY + 1,
			theme.accent(0.24F));
		FlatRenderer.fillRoundedRect(context, x1 + 6, dividerY + 5,
			x1 + 9, dividerY + 8, 1, theme.accent(1));
		String radiusText = (int)Math.round(hack.getRadius()) + "m";
		context.text(MC.font, radiusText, x1 + 12, dividerY + 2,
			theme.text(0.62F), false);
		String countText = Integer.toString(entityCount);
		context.text(MC.font, countText,
			x2 - 6 - MC.font.width(countText), dividerY + 2,
			theme.text(0.82F), false);
	}
	
	private int getEntityColor(Entity e)
	{
		if(WURST.getFriends().isFriend(e))
			return 0xFF4DA3FF;
		if(e instanceof Player)
			return 0xFFFF4D5E;
		if(e instanceof Enemy)
			return 0xFFFF9A3D;
		if(e instanceof Animal || e instanceof AmbientCreature
			|| e instanceof WaterAnimal)
			return 0xFF58C878;
		return 0xFFB8C0CA;
	}

	static RadarPoint project(double deltaX, double deltaZ, float yawDegrees,
		boolean rotateWithPlayer, double scale)
	{
		if(!rotateWithPlayer)
			return new RadarPoint(deltaX * scale, deltaZ * scale);

		double yaw = Math.toRadians(yawDegrees);
		double x = deltaX * Math.cos(yaw) + deltaZ * Math.sin(yaw);
		double y = deltaX * Math.sin(yaw) - deltaZ * Math.cos(yaw);
		return new RadarPoint(x * scale, y * scale);
	}

	static boolean isInsideRadar(RadarPoint point, double radius)
	{
		return point.x * point.x + point.y() * point.y()
			<= radius * radius;
	}

	record RadarPoint(double x, double y)
	{
	}
	
	@Override
	public int getDefaultWidth()
	{
		return 96;
	}
	
	@Override
	public int getDefaultHeight()
	{
		return 96;
	}
}
