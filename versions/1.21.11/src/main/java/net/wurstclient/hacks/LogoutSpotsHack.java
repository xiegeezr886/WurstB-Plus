/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.RenderUtils;

@SearchTags({"logout spots", "log off", "disconnect marker"})
public final class LogoutSpotsHack extends Hack
	implements UpdateListener, RenderListener, WorldChangeListener
{
	private final SliderSetting maxSpots =
		new SliderSetting("Max spots", 50, 1, 200, 1, SliderSetting.ValueDisplay.INTEGER);
	private final CheckboxSetting showName =
		new CheckboxSetting("Show name", true);

	private final Map<UUID, Spot> spots = new HashMap<>();
	private final Map<UUID, Spot> trackedPlayers = new HashMap<>();

	public LogoutSpotsHack()
	{
		super("LogoutSpots");
		setCategory(Category.RENDER);
		addSetting(maxSpots);
		addSetting(showName);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		spots.clear();
		trackedPlayers.clear();
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		spots.clear();
		trackedPlayers.clear();
	}

	@Override
	public void onUpdate()
	{
		if(MC.level == null)
			return;

		Map<UUID, Spot> onlinePlayers = new HashMap<>();
		for(Player player : MC.level.players())
		{
			if(player == MC.player || !(player instanceof RemotePlayer))
				continue;

			UUID uuid = player.getUUID();
			onlinePlayers.put(uuid, new Spot(uuid,
				player.getName().getString(), player.position(), 0));
			spots.remove(uuid);
		}

		long now = System.currentTimeMillis();
		for(Spot tracked : trackedPlayers.values())
			if(!onlinePlayers.containsKey(tracked.uuid))
				spots.putIfAbsent(tracked.uuid, new Spot(tracked.uuid,
					tracked.name, tracked.pos, now));

		trackedPlayers.clear();
		trackedPlayers.putAll(onlinePlayers);

		int max = (int)maxSpots.getValueI();
		while(spots.size() > max)
		{
			Spot oldest = null;
			for(Spot s : spots.values())
				if(oldest == null || s.time < oldest.time)
					oldest = s;
			if(oldest != null)
				spots.remove(oldest.uuid);
		}

	}

	@Override
	public void onRender(PoseStack PoseStack, float partialTicks)
	{
		if(MC.level == null || MC.player == null || spots.isEmpty())
			return;

		Vec3 cam = MC.gameRenderer.getMainCamera().position();
		Matrix4f matrix = PoseStack.last().pose();

		RenderUtils.submit(PoseStack, WurstRenderLayers.getQuads(true), buf -> {
			for(Spot spot : spots.values())
			{
				float x = (float)(spot.pos.x - cam.x);
				float y = (float)(spot.pos.y - cam.y);
				float z = (float)(spot.pos.z - cam.z);
				float s = 0.4F;

				buf.addVertex(matrix, x - s, y + s, z)
					.setColor(1, 0.3F, 0.3F, 0.7F);
				buf.addVertex(matrix, x + s, y + s, z)
					.setColor(1, 0.3F, 0.3F, 0.7F);
				buf.addVertex(matrix, x + s, y - s, z)
					.setColor(1, 0.3F, 0.3F, 0.7F);
				buf.addVertex(matrix, x - s, y - s, z)
					.setColor(1, 0.3F, 0.3F, 0.7F);
			}
		});

		if(showName.isChecked())
			renderNames(PoseStack, cam);
	}

	private void renderNames(PoseStack PoseStack, Vec3 cameraPos)
	{
		Font font = MC.font;
		for(Spot spot : spots.values())
		{
			PoseStack.pushPose();
			PoseStack.translate(spot.pos.x - cameraPos.x,
				spot.pos.y + 2.2 - cameraPos.y, spot.pos.z - cameraPos.z);
			PoseStack.mulPose(new Quaternionf(MC.gameRenderer.getMainCamera().rotation()));
			PoseStack.scale(-0.025F, -0.025F, 0.025F);
			float textX = -font.width(spot.name) / 2F;
			RenderUtils.submitText(PoseStack, textX, 0,
				Component.literal(spot.name).getVisualOrderText(), false,
				Font.DisplayMode.SEE_THROUGH, 0xF000F0, 0xFFFFFFFF,
				0x60000000, 0);
			PoseStack.popPose();
		}
	}

	private record Spot(UUID uuid, String name, Vec3 pos, long time) {}
}
