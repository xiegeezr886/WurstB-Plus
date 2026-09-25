/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.util.RenderUtils;

@SearchTags({"pop cham", "totem pop", "death effect"})
public final class PopChamsHack extends Hack
	implements UpdateListener, RenderListener, PacketInputListener,
	WorldChangeListener
{
	private final SliderSetting duration =
		new SliderSetting("Duration (sec)", 3, 1, 10, 1, SliderSetting.ValueDisplay.INTEGER);
	private final ColorSetting color =
		new ColorSetting("Color", "Pop cham color", java.awt.Color.CYAN);

	private final List<PopData> pops = new ArrayList<>();

	public PopChamsHack()
	{
		super("PopChams");
		setCategory(Category.RENDER);
		addSetting(duration);
		addSetting(color);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		pops.clear();
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		pops.clear();
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(event.getPacket() instanceof ClientboundSoundPacket sound)
		{
			if(sound.getSound().value() == SoundEvents.TOTEM_USE)
				MC.execute(() -> recordPop(sound.getX(), sound.getY(), sound.getZ()));
		}
	}

	private void recordPop(double soundX, double soundY, double soundZ)
	{
		if(!isEnabled() || MC.level == null)
			return;
		for(Player player : MC.level.players())
		{
			double dx = player.getX() - soundX;
			double dy = player.getY() - soundY;
			double dz = player.getZ() - soundZ;
			if(dx * dx + dy * dy + dz * dz >= 4)
				continue;
			pops.add(new PopData(player.position(), player.getBbHeight(),
				System.currentTimeMillis()));
			break;
		}
	}

	@Override
	public void onUpdate()
	{
		long now = System.currentTimeMillis();
		long maxAge = (long)(duration.getValueI() * 1000);
		pops.removeIf(p -> now - p.time > maxAge);
	}

	@Override
	public void onRender(PoseStack PoseStack, float partialTicks)
	{
		if(MC.level == null || MC.player == null || pops.isEmpty())
			return;

		Matrix4f matrix = PoseStack.last().pose();
		Vec3 cam = MC.gameRenderer.mainCamera().position();
		int argb = color.getColorI();
		float r = ((argb >> 16) & 0xFF) / 255F;
		float g = ((argb >> 8) & 0xFF) / 255F;
		float b = (argb & 0xFF) / 255F;

		RenderUtils.submit(PoseStack, WurstRenderLayers.getQuads(true), buf -> {
			for(PopData pop : pops)
			{
				long age = System.currentTimeMillis() - pop.time;
				float alpha = 1
					- age / (float)(duration.getValueI() * 1000);
				if(alpha < 0)
					continue;

				float x = (float)(pop.position.x - cam.x);
				float y = (float)(pop.position.y
					+ pop.height * 0.6 * (1 - alpha) - cam.y);
				float z = (float)(pop.position.z - cam.z);
				float s = 0.4F * (1 + alpha);

				buf.addVertex(matrix, x - s, y + s, z)
					.setColor(r, g, b, alpha);
				buf.addVertex(matrix, x + s, y + s, z)
					.setColor(r, g, b, alpha);
				buf.addVertex(matrix, x + s, y - s, z)
					.setColor(r, g, b, alpha);
				buf.addVertex(matrix, x - s, y - s, z)
					.setColor(r, g, b, alpha);
			}
		});
	}

	private record PopData(Vec3 position, float height, long time) {}
}
