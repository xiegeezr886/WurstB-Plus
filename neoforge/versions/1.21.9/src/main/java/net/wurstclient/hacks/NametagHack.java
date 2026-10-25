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
import java.util.Locale;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.PlayerInfo;
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

/**
 * Custom nametags above living entities, showing name, health, ping and
 * distance.
 *
 * <p>Simplification: Soar cancelled the vanilla nametag rendering through a
 * renderer mixin. Without mixins the vanilla nameplate cannot be hidden, so
 * this port draws its own tags slightly above the vanilla ones.
 */
@SearchTags({"name tags", "nametag", "nameplate", "health tags", "entity info"})
public final class NametagHack extends Hack implements RenderListener
{
	private final CheckboxSetting showPlayers =
		new CheckboxSetting("Players", "Shows nametags above players.", true);

	private final CheckboxSetting showMobs = new CheckboxSetting("Mobs",
		"Shows nametags above living entities that are not players.", false);

	private final CheckboxSetting showHealth = new CheckboxSetting(
		"Show health", "Adds the entity's current health.", true);

	private final CheckboxSetting showDistance = new CheckboxSetting(
		"Show distance", "Adds the distance to the entity.", true);

	private final CheckboxSetting showPing = new CheckboxSetting("Show ping",
		"Adds the network latency to player nametags.", false);

	private final SliderSetting scale = new SliderSetting("Scale",
		"How large the nametags should be.", 1, 0.25, 3, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting maxDistance = new SliderSetting("Max distance",
		"Entities farther away than this are not tagged.", 64, 4, 256, 4,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final ColorSetting textColor =
		new ColorSetting("Text color", "The color of the nametag text.",
			Color.WHITE);

	private final CheckboxSetting background = new CheckboxSetting("Background",
		"Draws a background behind the nametag text.", true);

	private final ColorSetting backgroundColor = new ColorSetting(
		"Background color", "The color of the nametag background.",
		new Color(0, 0, 0, 120)).visibleWhen(background::isChecked);

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders the nametags through solid blocks.", true);

	public NametagHack()
	{
		super("Nametag");
		setCategory(Category.RENDER);
		addSetting(showPlayers);
		addSetting(showMobs);
		addSetting(showHealth);
		addSetting(showDistance);
		addSetting(showPing);
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

		Vec3 camPos = MC.gameRenderer.getMainCamera().position();
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

			String label = buildLabel(entity, living);
			Vec3 pos = entity.getPosition(partialTicks)
				.add(0, entity.getBbHeight() + 0.9, 0);

			matrixStack.pushPose();
			matrixStack.translate(pos.x - camPos.x, pos.y - camPos.y,
				pos.z - camPos.z);
			matrixStack.mulPose(cameraRotation);
			matrixStack.scale(-textScale, -textScale, textScale);

			float textX = -MC.font.width(label) / 2F;
			RenderUtils.submitText(matrixStack, textX, 0,
				Component.literal(label).getVisualOrderText(), false,
				displayMode, 0xF000F0, textColor.getColorI(), backgroundColor,
				0);
			matrixStack.popPose();
		}
	}

	private String buildLabel(Entity entity, LivingEntity living)
	{
		StringBuilder label = new StringBuilder(entity.getName().getString());

		if(showHealth.isChecked())
			label.append(' ')
				.append(Math.round(living.getHealth() + living.getAbsorptionAmount()));

		if(showPing.isChecked() && entity instanceof Player)
		{
			PlayerInfo info = MC.getConnection() == null ? null
				: MC.getConnection().getPlayerInfo(entity.getUUID());
			int latency = info == null ? -1 : info.getLatency();
			label.append(" [").append(latency).append("ms]");
		}

		if(showDistance.isChecked())
			label.append(' ').append(
				String.format(Locale.ROOT, "%.1fm", MC.player.distanceTo(entity)));

		return label.toString();
	}
}
