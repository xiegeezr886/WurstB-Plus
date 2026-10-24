/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;

import org.joml.Quaternionf;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

@SearchTags({"entity owner", "pet owner", "tamed", "owner nametag"})
public final class EntityOwnerHack extends Hack implements RenderListener
{
	private final SliderSetting scale =
		new SliderSetting("Scale", "How large the owner nametags should be.", 1,
			0.5, 2, 0.05, ValueDisplay.DECIMAL);

	public EntityOwnerHack()
	{
		super("EntityOwner");
		setCategory(Category.RENDER);
		addSetting(scale);
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

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof OwnableEntity ownable))
				continue;

			EntityReference<LivingEntity> ownerRef =
				ownable.getOwnerReference();
			if(ownerRef == null)
				continue;

			String ownerName = getOwnerName(ownerRef);

			Vec3 pos = entity.getPosition(partialTicks)
				.add(0, entity.getBbHeight() + 0.5, 0);

			matrixStack.pushPose();
			matrixStack.translate(pos.x - camPos.x, pos.y - camPos.y,
				pos.z - camPos.z);
			matrixStack.rotate(cameraRotation);
			matrixStack.scale(-textScale, -textScale, textScale);

			float textX = -MC.font.width(ownerName) / 2F;
			RenderUtils.submitText(matrixStack, textX, 0,
				Component.literal(ownerName).getVisualOrderText(), false,
				Font.DisplayMode.SEE_THROUGH, 0xF000F0, 0xFFFFFFFF,
				0x60000000, 0);
			matrixStack.popPose();
		}
	}

	private String getOwnerName(EntityReference<LivingEntity> ownerRef)
	{
		LivingEntity owner =
			EntityReference.getLivingEntity(ownerRef, MC.level);
		if(owner != null)
			return owner.getName().getString();

		// Offline owners are not looked up over the network.
		String uuid = ownerRef.getUUID().toString();
		return uuid.substring(0, 8);
	}
}
