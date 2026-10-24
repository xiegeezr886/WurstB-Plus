/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

@SearchTags({"break esp", "breaking esp", "block break esp", "mining esp"})
public final class BreakEspHack extends Hack
	implements PacketInputListener, UpdateListener, RenderListener,
	WorldChangeListener
{
	private final CheckboxSetting progress = new CheckboxSetting("Progress",
		"Shows the break progress percentage.", true);

	private final CheckboxSetting name = new CheckboxSetting("Name",
		"Shows the name of the player who is breaking the block.", true);

	private final CheckboxSetting boxToggle =
		new CheckboxSetting("Box", "Shows a box outline.", true);

	private final ColorSetting boxColor =
		new ColorSetting("Box color", new Color(198, 176, 12));

	private final CheckboxSetting fillToggle =
		new CheckboxSetting("Fill", "Shows a filled box.", true);

	private final ColorSetting fillColor =
		new ColorSetting("Fill color", new Color(198, 176, 12, 78));

	private final CheckboxSetting friendBoxToggle =
		new CheckboxSetting("Friend box", true);

	private final ColorSetting friendBoxColor =
		new ColorSetting("Friend box color", new Color(30, 45, 169));

	private final CheckboxSetting friendFillToggle =
		new CheckboxSetting("Friend fill", true);

	private final ColorSetting friendFillColor =
		new ColorSetting("Friend fill color", new Color(30, 45, 169, 78));

	private final CheckboxSetting secondToggle = new CheckboxSetting("Second",
		"Uses separate colors when multiple players break the same block.",
		true);

	private final ColorSetting secondBoxColor =
		new ColorSetting("Second box color", Color.WHITE);

	private final ColorSetting secondFillColor =
		new ColorSetting("Second fill color", new Color(255, 255, 255, 100));

	private final SliderSetting textScale = new SliderSetting("Text scale",
		"How large the name and progress text are.", 1, 0.25, 3, 0.05,
		ValueDisplay.DECIMAL);

	private final ConcurrentHashMap<BlockPos, BreakData> breaking =
		new ConcurrentHashMap<>();

	public BreakEspHack()
	{
		super("BreakESP");
		setCategory(Category.RENDER);
		addSetting(progress);
		addSetting(name);
		addSetting(boxToggle);
		addSetting(boxColor);
		addSetting(fillToggle);
		addSetting(fillColor);
		addSetting(friendBoxToggle);
		addSetting(friendBoxColor);
		addSetting(friendFillToggle);
		addSetting(friendFillColor);
		addSetting(secondToggle);
		addSetting(secondBoxColor);
		addSetting(secondFillColor);
		addSetting(textScale);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		breaking.clear();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		breaking.clear();
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		breaking.clear();
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(!(event.getPacket() instanceof ClientboundBlockDestructionPacket packet)
			|| MC.player == null || packet.getId() == MC.player.getId())
			return;

		BlockPos pos = packet.getPos().immutable();
		int stage = packet.getProgress();
		if(stage < 0 || stage > 9)
		{
			breaking.remove(pos);
			return;
		}

		long now = System.currentTimeMillis();
		BreakData data = breaking.get(pos);
		if(data == null)
		{
			boolean second = false;
			for(BreakData other : breaking.values())
				if(other.entityId != packet.getId() && other.pos.equals(pos))
				{
					other.second = true;
					second = true;
					break;
				}

			breaking.put(pos,
				new BreakData(pos, packet.getId(), now, stage, second));
			return;
		}

		if(data.entityId != packet.getId())
			data.second = true;
		data.entityId = packet.getId();
		data.stage = stage;
		data.lastUpdate = now;
	}

	@Override
	public void onUpdate()
	{
		if(MC.level == null)
		{
			breaking.clear();
			return;
		}

		long now = System.currentTimeMillis();
		Iterator<BreakData> it = breaking.values().iterator();
		while(it.hasNext())
		{
			BreakData data = it.next();
			if(now - data.lastUpdate > 4000
				|| now - data.started > 10000)
				it.remove();
		}
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(MC.level == null || MC.player == null || breaking.isEmpty())
			return;

		ArrayList<AABB> fills = new ArrayList<>();
		ArrayList<Integer> fillColors = new ArrayList<>();
		ArrayList<AABB> outlines = new ArrayList<>();
		ArrayList<Integer> lineColors = new ArrayList<>();
		Vec3 camPos = MC.gameRenderer.getMainCamera().getPosition();
		Quaternionf cameraRotation =
			new Quaternionf(MC.gameRenderer.getMainCamera().rotation());
		float scale = 0.025F * textScale.getValueF();

		long now = System.currentTimeMillis();
		for(BreakData data : new ArrayList<>(breaking.values()))
		{
			double age = Math.min((now - data.started) / 300.0, 1);
			double ease = age < 0.5 ? 4 * age * age * age
				: 1 - Math.pow(-2 * age + 2, 3) / 2;
			double shrink = 0.5 * (1 - ease);
			AABB box = new AABB(data.pos).deflate(shrink);

			Player player = getPlayer(data.entityId);
			boolean friend = player != null
				&& WURST.getFriends().isFriend(player);
			boolean second = data.second && secondToggle.isChecked();

			int fillRgb = second ? secondFillColor.getColorI()
				: friend ? friendFillColor.getColorI() : fillColor.getColorI();
			int lineRgb = second ? secondBoxColor.getColorI()
				: friend ? friendBoxColor.getColorI() : boxColor.getColorI();
			int fillAlpha = second ? secondFillColor.getColor().getAlpha()
				: friend ? friendFillColor.getColor().getAlpha()
					: fillColor.getColor().getAlpha();

			if(second ? true
				: friend ? friendFillToggle.isChecked() : fillToggle.isChecked())
			{
				fills.add(box);
				fillColors.add(withAlpha(fillRgb, fillAlpha));
			}
			if(second ? true
				: friend ? friendBoxToggle.isChecked() : boxToggle.isChecked())
			{
				outlines.add(box);
				lineColors.add(withAlpha(lineRgb, 255));
			}

			if(!name.isChecked() && !progress.isChecked())
				continue;

			String label = getLabel(data, player);
			if(label.isEmpty())
				continue;

			int color = getProgressColor(data.stage);
			matrixStack.pushPose();
			matrixStack.translate(data.pos.getX() + 0.5 - camPos.x,
				data.pos.getY() + 1.15 - camPos.y,
				data.pos.getZ() + 0.5 - camPos.z);
			matrixStack.mulPose(cameraRotation);
			matrixStack.scale(-scale, -scale, scale);
			RenderUtils.submitText(matrixStack, -MC.font.width(label) / 2F, 0,
				Component.literal(label).getVisualOrderText(), false,
				Font.DisplayMode.SEE_THROUGH, 0xF000F0, color, 0x66000000, 0);
			matrixStack.popPose();
		}

		if(!fills.isEmpty())
			RenderUtils.drawSolidBoxes(matrixStack, zipBoxes(fills, fillColors),
				false);
		if(!outlines.isEmpty())
			RenderUtils.drawOutlinedBoxes(matrixStack,
				zipBoxes(outlines, lineColors), false);
	}

	private List<RenderUtils.ColoredBox> zipBoxes(List<AABB> boxes,
		List<Integer> colors)
	{
		ArrayList<RenderUtils.ColoredBox> result =
			new ArrayList<>(boxes.size());
		for(int i = 0; i < boxes.size(); i++)
			result.add(new RenderUtils.ColoredBox(boxes.get(i), colors.get(i)));
		return result;
	}

	private String getLabel(BreakData data, Player player)
	{
		StringBuilder label = new StringBuilder();
		if(name.isChecked())
		{
			if(player != null)
				label.append(player.getName().getString());
			else if(data.second)
				label.append("Second");
		}
		if(progress.isChecked())
		{
			if(!label.isEmpty())
				label.append(' ');
			label.append((data.stage + 1) * 10).append('%');
		}
		return label.toString();
	}

	private Player getPlayer(int entityId)
	{
		if(MC.level == null)
			return null;
		Entity entity = MC.level.getEntity(entityId);
		return entity instanceof Player player ? player : null;
	}

	private static int getProgressColor(int stage)
	{
		float progress = Math.min(stage + 1, 10) / 10F;
		int red = (int)(255 * (1 - progress));
		int green = (int)(255 * progress);
		return 0xFF000000 | red << 16 | green << 8 | 12;
	}

	private static int withAlpha(int color, int alpha)
	{
		return color & 0x00FFFFFF | alpha << 24;
	}

	private static final class BreakData
	{
		private final BlockPos pos;
		private final long started;
		private int entityId;
		private int stage;
		private long lastUpdate;
		private boolean second;

		private BreakData(BlockPos pos, int entityId, long started, int stage,
			boolean second)
		{
			this.pos = pos;
			this.entityId = entityId;
			this.started = started;
			this.stage = stage;
			this.lastUpdate = started;
			this.second = second;
		}
	}
}
