/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.Locale;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

@SearchTags({"entity tags", "nametags", "health tags", "entity info"})
public final class EntityTagsHack extends Hack implements RenderListener
{
	private final CheckboxSetting showPlayers =
		new CheckboxSetting("Players", "Shows tags above players.", true);

	private final CheckboxSetting showMobs =
		new CheckboxSetting("Mobs", "Shows tags above mobs.", true);

	private final CheckboxSetting showHealth =
		new CheckboxSetting("Show health", "Adds the entity's health.", true);

	private final CheckboxSetting showDistance =
		new CheckboxSetting("Show distance", "Adds the distance to you.", true);

	private final SliderSetting scale = new SliderSetting("Scale",
		"How large the tags should be.", 1, 0.25, 3, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting maxDistance = new SliderSetting("Max distance",
		"Entities farther away than this are not tagged.", 64, 4, 256, 4,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final ColorSetting textColor =
		new ColorSetting("Text color", "The color of the tag text.", Color.WHITE);

	private final CheckboxSetting background = new CheckboxSetting("Background",
		"Draws a background behind the tag text.", true);

	private final ColorSetting backgroundColor = new ColorSetting(
		"Background color", "The color of the tag background.",
		new Color(0, 0, 0, 120)).visibleWhen(background::isChecked);

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders the tags through solid blocks.", true);

	public EntityTagsHack()
	{
		super("EntityTags");
		setCategory(Category.RENDER);
		addSetting(showPlayers);
		addSetting(showMobs);
		addSetting(showHealth);
		addSetting(showDistance);
		addSetting(scale);
		addSetting(maxDistance);
		addSetting(textColor);
		addSetting(background);
		addSetting(backgroundColor);
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
		if(MC.level == null || MC.player == null)
			return;

		Vec3 camPos = MC.gameRenderer.getMainCamera().getPosition();
		Quaternionf cameraRotation =
			new Quaternionf(MC.gameRenderer.getMainCamera().rotation());
		float textScale = 0.025F * scale.getValueF();
		double maxDistanceSq = maxDistance.getValueSq();
		int backgroundColor = background.isChecked()
			? this.backgroundColor.getColor().getRGB() : 0;
		Font.DisplayMode displayMode = throughWalls.isChecked()
			? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(entity == MC.player || entity.isRemoved()
				|| !(entity instanceof LivingEntity living)
				|| living.getHealth() <= 0)
				continue;

			boolean player = entity instanceof Player;
			if(player && !showPlayers.isChecked())
				continue;
			if(!player && !showMobs.isChecked())
				continue;

			if(MC.player.distanceToSqr(entity) > maxDistanceSq)
				continue;

			String text = entity.getName().getString();
			if(showHealth.isChecked())
			{
				float health =
					living.getHealth() + living.getAbsorptionAmount();
				text += " " + Math.round(health);
			}
			if(showDistance.isChecked())
				text += " "
					+ String.format(Locale.ROOT, "%.1f", MC.player.distanceTo(entity))
					+ "m";

			Vec3 pos = entity.getPosition(partialTicks)
				.add(0, entity.getBbHeight() + 0.5, 0);

			matrixStack.pushPose();
			matrixStack.translate(pos.x - camPos.x, pos.y - camPos.y,
				pos.z - camPos.z);
			matrixStack.mulPose(cameraRotation);
			matrixStack.scale(-textScale, -textScale, textScale);

			float textX = -MC.font.width(text) / 2F;
			RenderUtils.submitText(matrixStack, textX, 0,
				Component.literal(text).getVisualOrderText(), false,
				displayMode, 0xF000F0, textColor.getColorI(), backgroundColor,
				0);
			matrixStack.popPose();
		}
	}
}
