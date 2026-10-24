/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * Colored totem pop particles.
 *
 * <p>
 * The upstream module cancels Meteor's totem particle event and recolors the
 * vanilla particles, which requires a mixin. This port cannot add mixins, so
 * it spawns an equivalent custom colored particle burst around the player who
 * popped the totem instead.
 */
@SearchTags({"img totem particle", "totem particle", "totem color",
	"totem pop"})
public final class ImgTotemParticleHack extends Hack
	implements UpdateListener, PacketInputListener
{
	private final SliderSetting velocityXZ = new SliderSetting(
		"Velocity XZ", "Horizontal velocity multiplier in percent.", 100, 0,
		500, 1, ValueDisplay.INTEGER.withSuffix("%"));

	private final SliderSetting velocityY = new SliderSetting(
		"Velocity Y", "Vertical velocity multiplier in percent.", 100, 0, 500,
		1, ValueDisplay.INTEGER.withSuffix("%"));

	private final ColorSetting color1 = new ColorSetting("Color 1",
		"First color of the gradient.", Color.WHITE);

	private final ColorSetting color2 = new ColorSetting("Color 2",
		"Second color of the gradient.", Color.BLACK);

	private final Random random = new Random();
	private final ConcurrentLinkedQueue<ClientboundEntityEventPacket> incoming =
		new ConcurrentLinkedQueue<>();

	public ImgTotemParticleHack()
	{
		super("ImgTotemParticle");
		setCategory(Category.RENDER);
		addSetting(velocityXZ);
		addSetting(velocityY);
		addSetting(color1);
		addSetting(color2);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		incoming.clear();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		incoming.clear();
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(!(event.getPacket() instanceof ClientboundEntityEventPacket packet))
			return;

		if(packet.getEventId() == 35)
			incoming.add(packet);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
		{
			incoming.clear();
			return;
		}

		ClientboundEntityEventPacket packet;
		while((packet = incoming.poll()) != null)
		{
			Entity entity = packet.getEntity(MC.level);
			if(entity instanceof Player player)
				spawnBurst(player);
		}
	}

	private void spawnBurst(Player player)
	{
		double multXZ = velocityXZ.getValue() / 100.0;
		double multY = velocityY.getValue() / 100.0;

		Color c1 = color1.getColor();
		Color c2 = color2.getColor();

		double x = player.getX();
		double y = player.getY() + player.getBbHeight() / 2.0;
		double z = player.getZ();

		for(int i = 0; i < 24; i++)
		{
			double t = random.nextDouble();
			float r = lerp(c1.getRed(), c2.getRed(), t) / 255F;
			float g = lerp(c1.getGreen(), c2.getGreen(), t) / 255F;
			float b = lerp(c1.getBlue(), c2.getBlue(), t) / 255F;

			ColorParticleOption particle = ColorParticleOption
				.create(ParticleTypes.ENTITY_EFFECT, r, g, b);

			double vx = (random.nextDouble() - 0.5) * 0.4 * multXZ;
			double vy = (0.2 + random.nextDouble() * 0.8) * multY;
			double vz = (random.nextDouble() - 0.5) * 0.4 * multXZ;

			MC.level.addParticle(particle, x, y, z, vx, vy, vz);
		}
	}

	private float lerp(int a, int b, double t)
	{
		return (float)(a + (b - a) * t);
	}
}
