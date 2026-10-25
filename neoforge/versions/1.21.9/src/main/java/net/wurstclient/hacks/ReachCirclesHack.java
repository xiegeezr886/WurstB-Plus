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

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;

/**
 * Draws circles around the player at their attack and block interaction
 * reach.
 *
 * <p>Simplification: Soar drew reach circles around every visible player
 * (using hardcoded 3.4 / 4.7 radii). This port draws the local player's two
 * reach values using the vanilla interaction-range attributes instead.
 */
@SearchTags({"reach circles", "reach", "interaction range", "attack range",
	"circles"})
public final class ReachCirclesHack extends Hack implements RenderListener
{
	private static final int SEGMENTS = 90;

	private final SliderSetting lineWidth = new SliderSetting("Line width",
		"Width of the reach circles.", 2, 1, 5, 1, ValueDisplay.INTEGER);

	private final ColorSetting attackColor = new ColorSetting("Attack color",
		"Color of the entity attack reach circle.",
		new Color(255, 90, 90, 180));

	private final ColorSetting interactColor =
		new ColorSetting("Interact color",
			"Color of the block interaction reach circle.",
			new Color(90, 200, 255, 180));

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders the circles through solid blocks.", true);

	public ReachCirclesHack()
	{
		super("ReachCircles");
		setCategory(Category.RENDER);
		addSetting(lineWidth);
		addSetting(attackColor);
		addSetting(interactColor);
		addSetting(throughWalls);
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
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(MC.player == null || MC.level == null)
			return;

		var player = MC.player;
		double attackReach = Math.max(1,
			player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE));
		double interactReach = Math.max(1,
			player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE));

		Vec3 center = EntityUtils.getLerpedPos(player, partialTicks);
		Vec3 cam = RenderUtils.getCameraPos();
		Matrix4f matrix = matrixStack.last().pose();
		RenderType layer =
			WurstRenderLayers.getLines(!throughWalls.isChecked());
		float width = lineWidth.getValueF();

		RenderUtils.submit(matrixStack, layer, buffer -> {
			drawCircle(buffer, matrix, center, cam, attackReach,
				attackColor.getColor(), width);
			drawCircle(buffer, matrix, center, cam, interactReach,
				interactColor.getColor(), width);
		});
	}

	private void drawCircle(VertexConsumer buffer, Matrix4f matrix,
		Vec3 center, Vec3 cam, double radius, Color color, float width)
	{
		float red = color.getRed() / 255F;
		float green = color.getGreen() / 255F;
		float blue = color.getBlue() / 255F;
		float alpha = color.getAlpha() / 255F;

		for(int i = 0; i < SEGMENTS; i++)
		{
			double angle1 = Math.PI * 2 * i / SEGMENTS;
			double angle2 = Math.PI * 2 * (i + 1) / SEGMENTS;
			float cos1 = (float)Math.cos(angle1);
			float sin1 = (float)Math.sin(angle1);
			float cos2 = (float)Math.cos(angle2);
			float sin2 = (float)Math.sin(angle2);

			buffer
				.addVertex(matrix,
					(float)(center.x + cos1 * radius - cam.x),
					(float)(center.y + 0.03 - cam.y),
					(float)(center.z + sin1 * radius - cam.z))
				.setColor(red, green, blue, alpha)
				.setNormal(sin1, 0, -cos1);
			buffer
				.addVertex(matrix,
					(float)(center.x + cos2 * radius - cam.x),
					(float)(center.y + 0.03 - cam.y),
					(float)(center.z + sin2 * radius - cam.z))
				.setColor(red, green, blue, alpha)
				.setNormal(sin2, 0, -cos2);
		}
	}
}
