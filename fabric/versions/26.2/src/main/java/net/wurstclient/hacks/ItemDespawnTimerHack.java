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
import net.minecraft.world.entity.item.ItemEntity;
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

@SearchTags({"item despawn", "despawn timer", "item timer"})
public final class ItemDespawnTimerHack extends Hack implements RenderListener
{
	private static final int ITEM_LIFETIME = 6000;
	private static final int BLINK_TIME = 200;

	private final SliderSetting scale = new SliderSetting("Scale",
		"How large the timer text should be.", 1, 0.5, 3, 0.05,
		ValueDisplay.DECIMAL);

	private final SliderSetting heightOffset = new SliderSetting(
		"Height offset", "How far above the item the timer is shown.", 0.75, 0,
		3, 0.05, ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final SliderSetting maxDistance = new SliderSetting("Max distance",
		"Items farther away than this are not rendered.", 64, 4, 256, 4,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"The color of the timer text.", Color.WHITE);

	private final CheckboxSetting blink = new CheckboxSetting("Blink",
		"Blinks the timer red during the last 10 seconds.", true);

	private final CheckboxSetting background = new CheckboxSetting("Background",
		"Draws a background behind the timer text.", true);

	private final ColorSetting backgroundColor = new ColorSetting(
		"Background color", "The color of the timer background.",
		new Color(0, 0, 0, 120)).visibleWhen(background::isChecked);

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders the timers through solid blocks.", true);

	public ItemDespawnTimerHack()
	{
		super("ItemDespawnTimer");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(heightOffset);
		addSetting(maxDistance);
		addSetting(textColor);
		addSetting(blink);
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

		Vec3 camPos = MC.gameRenderer.mainCamera().position();
		Quaternionf cameraRotation =
			new Quaternionf(MC.gameRenderer.mainCamera().rotation());
		float textScale = 0.025F * scale.getValueF();
		double maxDistanceSq = maxDistance.getValueSq();
		int backgroundColor = background.isChecked()
			? this.backgroundColor.getColor().getRGB() : 0;
		Font.DisplayMode displayMode = throughWalls.isChecked()
			? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;

		for(ItemEntity item : WURST.getEntitySnapshotManager().getCurrent()
			.items())
		{
			if(item == null || item.isRemoved()
				|| MC.player.distanceToSqr(item) > maxDistanceSq)
				continue;

			int age = item.getAge();
			if(age < 0 || age >= ITEM_LIFETIME)
				continue;

			int ticksLeft = ITEM_LIFETIME - age;
			String text = formatTime(ticksLeft / 20.0);
			int color = textColor.getColorI();
			if(blink.isChecked() && ticksLeft <= BLINK_TIME
				&& (ticksLeft / 10) % 2 == 0)
				color = 0xFFFF3030;

			Vec3 pos = item.getPosition(partialTicks)
				.add(0, item.getBbHeight() + heightOffset.getValue(), 0);

			matrixStack.pushPose();
			matrixStack.translate(pos.x - camPos.x, pos.y - camPos.y,
				pos.z - camPos.z);
			matrixStack.mulPose(cameraRotation);
			matrixStack.scale(-textScale, -textScale, textScale);

			float textX = -MC.font.width(text) / 2F;
			RenderUtils.submitText(matrixStack, textX, 0,
				Component.literal(text).getVisualOrderText(), false,
				displayMode, 0xF000F0, color, backgroundColor, 0);
			matrixStack.popPose();
		}
	}

	private String formatTime(double secondsLeft)
	{
		if(secondsLeft >= 60)
			return String.format(Locale.ROOT, "%d:%04.1f",
				(int)secondsLeft / 60, secondsLeft % 60);

		return String.format(Locale.ROOT, "%.1fs", secondsLeft);
	}
}
