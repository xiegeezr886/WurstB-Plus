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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BaritoneUtils;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RenderUtils.ColoredBox;
import net.wurstclient.util.RenderUtils.ColoredPoint;

@SearchTags({"anti anti xray", "anti xray", "ore scanner", "ore esp"})
public final class AntiAntiXrayHack extends Hack
	implements UpdateListener, RenderListener, PacketInputListener,
	WorldChangeListener
{
	private final BlockListSetting targetBlocks = new BlockListSetting(
		"Target blocks", "Blocks that are reported as ores.",
		"minecraft:ancient_debris", "minecraft:coal_ore",
		"minecraft:deepslate_coal_ore", "minecraft:copper_ore",
		"minecraft:deepslate_copper_ore", "minecraft:diamond_ore",
		"minecraft:deepslate_diamond_ore", "minecraft:emerald_ore",
		"minecraft:deepslate_emerald_ore", "minecraft:gold_ore",
		"minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore",
		"minecraft:iron_ore", "minecraft:deepslate_iron_ore",
		"minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
		"minecraft:nether_quartz_ore", "minecraft:redstone_ore",
		"minecraft:deepslate_redstone_ore");

	private final SliderSetting radius = new SliderSetting("Radius",
		"How many blocks around the player are scanned.", 5, 1, 8, 1,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting blocksPerTick = new SliderSetting(
		"Blocks per tick", "How many blocks are probed each tick.", 3, 1, 10, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting maxDistance = new SliderSetting("Max distance",
		"Found ores farther away than this are not rendered.", 128, 16, 512, 8,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting fillOpacity = new SliderSetting("Fill opacity",
		"Opacity of the filled ore boxes.", 0.2, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final SliderSetting lineOpacity = new SliderSetting("Line opacity",
		"Opacity of the ore box outlines.", 0.75, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final CheckboxSetting tracers = new CheckboxSetting("Tracers",
		"Draws lines from the camera to found ores.", true);

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders found ores through solid blocks.", true);

	private final CheckboxSetting autoMine = new CheckboxSetting("Auto mine",
		"Uses Baritone to walk to the closest found ore.", false);

	private final ColorSetting diamondColor =
		new ColorSetting("Diamond color", new Color(0, 255, 255));
	private final ColorSetting debrisColor =
		new ColorSetting("Debris color", new Color(160, 32, 240));
	private final ColorSetting goldColor =
		new ColorSetting("Gold color", new Color(255, 215, 0));
	private final ColorSetting ironColor =
		new ColorSetting("Iron color", new Color(210, 180, 140));
	private final ColorSetting emeraldColor =
		new ColorSetting("Emerald color", new Color(0, 255, 0));
	private final ColorSetting lapisColor =
		new ColorSetting("Lapis color", new Color(0, 0, 170));
	private final ColorSetting redstoneColor =
		new ColorSetting("Redstone color", new Color(255, 0, 0));
	private final ColorSetting coalColor =
		new ColorSetting("Coal color", new Color(20, 20, 20));
	private final ColorSetting copperColor =
		new ColorSetting("Copper color", new Color(230, 115, 0));
	private final ColorSetting quartzColor =
		new ColorSetting("Quartz color", new Color(230, 230, 230));
	private final ColorSetting otherColor =
		new ColorSetting("Other color", new Color(255, 255, 255));

	private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
	private final Set<BlockPos> scanned = new HashSet<>();
	private final ConcurrentHashMap<BlockPos, Block> foundOres =
		new ConcurrentHashMap<>();
	private BlockPos lastRefillPos;
	private int timer;

	public AntiAntiXrayHack()
	{
		super("AntiAntiXray");
		setCategory(Category.RENDER);
		addSetting(targetBlocks);
		addSetting(radius);
		addSetting(blocksPerTick);
		addSetting(maxDistance);
		addSetting(fillOpacity);
		addSetting(lineOpacity);
		addSetting(tracers);
		addSetting(throughWalls);
		addSetting(autoMine);
		addSetting(diamondColor);
		addSetting(debrisColor);
		addSetting(goldColor);
		addSetting(ironColor);
		addSetting(emeraldColor);
		addSetting(lapisColor);
		addSetting(redstoneColor);
		addSetting(coalColor);
		addSetting(copperColor);
		addSetting(quartzColor);
		addSetting(otherColor);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		clear();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		clear();
	}

	private void clear()
	{
		queue.clear();
		scanned.clear();
		foundOres.clear();
		lastRefillPos = null;
		timer = 0;
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		clear();
	}

	@Override
	public void onUpdate()
	{
		if(MC.level == null || MC.player == null || MC.getConnection() == null)
			return;

		timer++;
		BlockPos playerPos = MC.player.blockPosition();

		if(timer % 20 == 0)
		{
			foundOres.entrySet().removeIf(
				e -> MC.level.getBlockState(e.getKey()).getBlock() != e.getValue());

			int clearRadius = radius.getValueI() + 8;
			scanned.removeIf(p -> distanceSq(p, playerPos) > clearRadius
				* clearRadius);
		}

		boolean moved = lastRefillPos == null
			|| distanceSq(lastRefillPos, playerPos) > 2.25;
		if(moved || (queue.isEmpty() && timer % 10 == 0))
			refillQueue(playerPos);

		int limit = blocksPerTick.getValueI();
		for(int i = 0; i < limit && !queue.isEmpty(); i++)
		{
			BlockPos pos = queue.poll();
			if(!scanned.add(pos))
				continue;

			MC.getConnection().send(new ServerboundPlayerActionPacket(
				Action.START_DESTROY_BLOCK, pos, Direction.UP));
			MC.getConnection().send(new ServerboundPlayerActionPacket(
				Action.ABORT_DESTROY_BLOCK, pos, Direction.UP));
		}

		if(autoMine.isChecked() && !foundOres.isEmpty() && timer % 40 == 0
			&& BaritoneUtils.IS_AVAILABLE && !BaritoneUtils.isPathing())
		{
			BlockPos closest = null;
			double best = Double.MAX_VALUE;
			for(BlockPos pos : foundOres.keySet())
			{
				double d = distanceSq(pos, playerPos);
				if(d < best)
				{
					best = d;
					closest = pos;
				}
			}
			if(closest != null)
				BaritoneUtils.walkTo(closest);
		}
	}

	private void refillQueue(BlockPos playerPos)
	{
		int r = radius.getValueI();
		int rSq = r * r;
		queue.clear();

		for(int x = -r; x <= r; x++)
			for(int y = -r; y <= r; y++)
				for(int z = -r; z <= r; z++)
				{
					if(x * x + y * y + z * z > rSq)
						continue;

					BlockPos pos =
						playerPos.offset(x, y, z).immutable();
					if(MC.level.getBlockState(pos).isAir()
						|| scanned.contains(pos) || foundOres.containsKey(pos))
						continue;

					queue.add(pos);
				}

		lastRefillPos = playerPos;
	}

	private Set<Block> buildTargets()
	{
		Set<Block> targets = new HashSet<>();
		for(String name : targetBlocks.getBlockNames())
		{
			Block block = BlockUtils.getBlockFromName(name);
			if(block != Blocks.AIR)
				targets.add(block);
		}
		return targets;
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(!(event.getPacket() instanceof ClientboundBlockUpdatePacket packet))
			return;

		BlockPos pos = packet.getPos();
		Block block = packet.getBlockState().getBlock();
		if(buildTargets().contains(block))
			foundOres.put(pos.immutable(), block);
		else
			foundOres.remove(pos);
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(foundOres.isEmpty())
			return;

		double maxDistanceSq = maxDistance.getValueSq();
		boolean depthTest = !throughWalls.isChecked();
		int fillAlpha = (int)(fillOpacity.getValue() * 255);
		int lineAlpha = (int)(lineOpacity.getValue() * 255);

		ArrayList<ColoredBox> fills = new ArrayList<>();
		ArrayList<ColoredBox> lines = new ArrayList<>();
		ArrayList<ColoredPoint> tracerEnds = new ArrayList<>();

		for(var entry : foundOres.entrySet())
		{
			BlockPos pos = entry.getKey();
			if(MC.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5,
				pos.getZ() + 0.5) > maxDistanceSq)
				continue;

			int rgb = getColor(entry.getValue()).getColorI();
			AABB box = new AABB(pos).inflate(0.002);
			if(fillAlpha > 0)
				fills.add(new ColoredBox(box, withAlpha(rgb, fillAlpha)));
			if(lineAlpha > 0)
			{
				lines.add(new ColoredBox(box, withAlpha(rgb, lineAlpha)));
				tracerEnds.add(new ColoredPoint(box.getCenter(),
					withAlpha(rgb, lineAlpha)));
			}
		}

		if(!fills.isEmpty())
			RenderUtils.drawSolidBoxes(matrixStack, fills, depthTest);
		if(!lines.isEmpty())
			RenderUtils.drawOutlinedBoxes(matrixStack, lines, depthTest);
		if(tracers.isChecked() && !tracerEnds.isEmpty())
			RenderUtils.drawTracers(matrixStack, partialTicks, tracerEnds,
				depthTest);
	}

	private ColorSetting getColor(Block block)
	{
		if(block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE)
			return diamondColor;
		if(block == Blocks.ANCIENT_DEBRIS)
			return debrisColor;
		if(block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE
			|| block == Blocks.NETHER_GOLD_ORE)
			return goldColor;
		if(block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE)
			return ironColor;
		if(block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE)
			return emeraldColor;
		if(block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE)
			return lapisColor;
		if(block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE)
			return redstoneColor;
		if(block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE)
			return coalColor;
		if(block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE)
			return copperColor;
		if(block == Blocks.NETHER_QUARTZ_ORE)
			return quartzColor;
		return otherColor;
	}

	private static int withAlpha(int color, int alpha)
	{
		return color & 0x00FFFFFF | alpha << 24;
	}

	private static double distanceSq(BlockPos a, BlockPos b)
	{
		double dx = a.getX() - b.getX();
		double dy = a.getY() - b.getY();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dy * dy + dz * dz;
	}
}
