/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.TextFieldSetting;

/**
 * Attempts to hide client mods from server-side client checks: spoofs the
 * client brand, drops outgoing Fabric/Meteor payloads and applies cautious
 * physics during the first seconds after enabling.
 *
 * <p>
 * The brand packet is normally sent during configuration, before a hack can
 * be enabled; this port only rewrites brand payloads sent afterwards.
 */
@SearchTags({"sonar bypass", "sonar", "brand spoof", "fabric payload",
	"client brand"})
public final class SonarBypassHack extends Hack
	implements PacketOutputListener, UpdateListener
{
	private final TextFieldSetting brandName = new TextFieldSetting(
		"Brand name", "Client brand reported to the server.", "vanilla");

	private final CheckboxSetting blockFabric = new CheckboxSetting(
		"Block fabric payloads",
		"Cancels outgoing custom payloads that look like Fabric or Meteor "
			+ "mod messages.",
		true);

	private final CheckboxSetting strictPhysics = new CheckboxSetting(
		"Strict physics on join",
		"Disables elytra gliding and sneak input during the first 60 ticks.",
		true);

	private int joinTicks;

	public SonarBypassHack()
	{
		super("SonarBypass");
		setCategory(Category.OTHER);
		addSetting(brandName);
		addSetting(blockFabric);
		addSetting(strictPhysics);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(PacketOutputListener.class, this);
		EVENTS.add(UpdateListener.class, this);
		joinTicks = 0;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketOutputListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(!(event.getPacket() instanceof ServerboundCustomPayloadPacket packet))
			return;

		ResourceLocation id = packet.getIdentifier();

		if(id.equals(ServerboundCustomPayloadPacket.BRAND))
		{
			String brand = brandName.getValue();
			if(!brand.isBlank())
			{
				FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
				buf.writeUtf(brand);
				event.setPacket(new ServerboundCustomPayloadPacket(
					ServerboundCustomPayloadPacket.BRAND, buf));
			}
			return;
		}

		if(!blockFabric.isChecked())
			return;

		String namespace = id.getNamespace().toLowerCase();
		String path = id.getPath().toLowerCase();

		if(namespace.contains("fabric") || path.contains("fabric")
			|| namespace.contains("meteor") || path.contains("meteor"))
			event.cancel();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(!strictPhysics.isChecked() || joinTicks >= 60)
			return;

		joinTicks++;

		if(MC.player.isFallFlying())
			MC.player.stopFallFlying();

		ItemStack chest = MC.player.getItemBySlot(EquipmentSlot.CHEST);
		if(chest != null && chest.getItem().toString().contains("elytra")
			&& MC.options.keyShift.isDown())
			MC.options.keyShift.setDown(false);
	}
}
