/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from LeavesHack (AGPL-3.0).
package net.wurstclient.hacks;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatInputListener;
import net.wurstclient.events.DeathListener;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

/**
 * Draws markers where players died. Remote deaths are detected through the
 * death animation packet, the local player's death through the death event,
 * and (optionally) any death coordinates mentioned in chat.
 */
@SearchTags({"death render", "death marker", "death coords", "death position",
	"death esp"})
public final class DeathRenderHack extends Hack implements UpdateListener,
	RenderListener, PacketInputListener, ChatInputListener, DeathListener,
	WorldChangeListener
{
	private static final Pattern SIMPLE_COORDS = Pattern.compile(
		"(-?\\d+(?:\\.\\d+)?)\\s*[,;/ ]\\s*(-?\\d+(?:\\.\\d+)?)"
			+ "\\s*[,;/ ]\\s*(-?\\d+(?:\\.\\d+)?)");

	private static final Pattern LABELED_COORDS = Pattern.compile(
		"[xX]\\s*[:=]?\\s*(-?\\d+(?:\\.\\d+)?).{0,16}?"
			+ "[yY]\\s*[:=]?\\s*(-?\\d+(?:\\.\\d+)?).{0,16}?"
			+ "[zZ]\\s*[:=]?\\s*(-?\\d+(?:\\.\\d+)?)");

	private final SliderSetting range = new SliderSetting("Range",
		"How far away a player death is recorded.", 32, 4, 128, 1,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting maxMarkers = new SliderSetting("Max markers",
		"Maximum number of death markers to keep.", 20, 1, 100, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting showNames = new CheckboxSetting("Show names",
		"Shows a name above every marker.", true);

	private final CheckboxSetting chatCoords = new CheckboxSetting(
		"Chat coordinates",
		"Also read death coordinates from chat messages.\n\n"
			+ "This is a heuristic and may produce false positives.",
		true);

	private final CheckboxSetting depthTest = new CheckboxSetting("Depth test",
		"Hide markers behind blocks.", true);

	private final Map<String, Marker> markers = new ConcurrentHashMap<>();
	private final ConcurrentLinkedQueue<ClientboundEntityEventPacket> incoming =
		new ConcurrentLinkedQueue<>();

	public DeathRenderHack()
	{
		super("DeathRender");
		setCategory(Category.RENDER);
		addSetting(range);
		addSetting(maxMarkers);
		addSetting(showNames);
		addSetting(chatCoords);
		addSetting(depthTest);
	}

	@Override
	protected void onEnable()
	{
		markers.clear();
		incoming.clear();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(ChatInputListener.class, this);
		EVENTS.add(DeathListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(ChatInputListener.class, this);
		EVENTS.remove(DeathListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		markers.clear();
		incoming.clear();
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		markers.clear();
		incoming.clear();
	}

	@Override
	public void onDeath()
	{
		if(MC.player == null)
			return;

		addMarker("self", "You", MC.player.position(), 0xFFFF4040);
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		// Packet handlers run on the netty thread; defer the world lookup.
		if(event.getPacket() instanceof ClientboundEntityEventPacket packet
			&& packet.getEventId() == EntityEvent.DEATH)
			incoming.add(packet);
	}

	@Override
	public void onReceivedMessage(ChatInputEvent event)
	{
		if(!chatCoords.isChecked())
			return;

		String text = event.getComponent().getString();
		if(!looksLikeDeathMessage(text.toLowerCase(Locale.ROOT)))
			return;

		Matcher matcher = LABELED_COORDS.matcher(text);
		if(!matcher.find())
		{
			matcher = SIMPLE_COORDS.matcher(text);
			if(!matcher.find())
				return;
		}

		try
		{
			double x = Double.parseDouble(matcher.group(1));
			double y = Double.parseDouble(matcher.group(2));
			double z = Double.parseDouble(matcher.group(3));
			String key = "chat:" + x + "," + y + "," + z;
			addMarker(key, "Chat death", new Vec3(x, y, z), 0xFFFF8040);
		}catch(NumberFormatException e)
		{
			// Some other mod's chat message; ignore it.
		}
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
		{
			markers.clear();
			incoming.clear();
			return;
		}

		ClientboundEntityEventPacket packet;
		while((packet = incoming.poll()) != null)
		{
			Entity entity = packet.getEntity(MC.level);
			if(!(entity instanceof Player player) || player == MC.player)
				continue;

			if(MC.player.distanceToSqr(player) > range.getValueSq())
				continue;

			addMarker(player.getUUID().toString(),
				player.getName().getString(), player.position(), 0xFFFFAA00);
		}
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(MC.level == null || MC.player == null || markers.isEmpty())
			return;

		boolean depth = depthTest.isChecked();
		for(Marker marker : markers.values())
		{
			Vec3 pos = marker.pos();
			AABB box = new AABB(pos.x - 0.4, pos.y, pos.z - 0.4, pos.x + 0.4,
				pos.y + 0.8, pos.z + 0.4);
			int fill = (marker.color() & 0x00FFFFFF) | 0x40000000;
			RenderUtils.drawSolidBox(matrixStack, box, fill, depth);
			RenderUtils.drawOutlinedBox(matrixStack, box, marker.color(),
				depth);
		}

		if(showNames.isChecked())
			renderNames(matrixStack);
	}

	private void renderNames(PoseStack matrixStack)
	{
		Font font = MC.font;
		Vec3 camera = MC.gameRenderer.getMainCamera().position();

		for(Marker marker : markers.values())
		{
			matrixStack.pushPose();
			matrixStack.translate(marker.pos().x - camera.x,
				marker.pos().y + 1.3 - camera.y, marker.pos().z - camera.z);
			matrixStack
				.mulPose(new Quaternionf(MC.gameRenderer.getMainCamera().rotation()));
			matrixStack.scale(-0.025F, -0.025F, 0.025F);
			float textX = -font.width(marker.name()) / 2F;
			RenderUtils.submitText(matrixStack, textX, 0,
				Component.literal(marker.name()).getVisualOrderText(), false,
				Font.DisplayMode.SEE_THROUGH, 0xF000F0,
				marker.color() | 0xFF000000, 0x60000000, 0);
			matrixStack.popPose();
		}
	}

	private void addMarker(String key, String name, Vec3 pos, int color)
	{
		markers.put(key, new Marker(name, pos, color,
			System.currentTimeMillis()));

		int max = maxMarkers.getValueI();
		while(markers.size() > max)
		{
			String oldestKey = null;
			long oldestTime = Long.MAX_VALUE;
			for(Map.Entry<String, Marker> entry : markers.entrySet())
				if(entry.getValue().time() < oldestTime)
				{
					oldestTime = entry.getValue().time();
					oldestKey = entry.getKey();
				}

			if(oldestKey == null)
				break;
			markers.remove(oldestKey);
		}
	}

	private boolean looksLikeDeathMessage(String message)
	{
		return message.contains("died") || message.contains("death")
			|| message.contains("slain") || message.contains("was killed")
			|| message.contains("killed by") || message.contains("死亡")
			|| message.contains("阵亡") || message.contains("被杀")
			|| message.contains("死在了");
	}

	private record Marker(String name, Vec3 pos, int color, long time)
	{}
}
