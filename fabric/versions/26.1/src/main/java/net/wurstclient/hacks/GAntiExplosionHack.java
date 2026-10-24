/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.DamageUtils;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"g anti explosion", "anti explosion", "anti crystal", "vclip",
	"anti anchor"})
public final class GAntiExplosionHack extends Hack implements PacketInputListener
{
	private final CheckboxSetting antiCrystal = new CheckboxSetting(
		"Anti crystal", "Dodges end crystal explosions with VClip packets.",
		true);

	private final SliderSetting crystalScanBorder = new SliderSetting(
		"Crystal scan border", "Radius searched for a safe spot.", 8, 1, 15, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting crystalHitRange = new SliderSetting(
		"Crystal hit range",
		"Maximum distance from the safe spot to the crystal.", 6, 0.1, 6, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting crystalMaxDamage = new SliderSetting(
		"Crystal max damage",
		"Triggers the dodge when the predicted damage is above this.", 6, 0.01,
		20, 0.01, ValueDisplay.DECIMAL);

	private final CheckboxSetting antiAnchor = new CheckboxSetting(
		"Anti anchor", "Dodges respawn anchor explosions with VClip packets.",
		true);

	private final SliderSetting anchorScanBorder = new SliderSetting(
		"Anchor scan border", "Radius searched for a safe spot.", 8, 1, 15, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting anchorClickRange = new SliderSetting(
		"Anchor click range",
		"Maximum distance from the safe spot to the anchor.", 6, 0.1, 6, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting anchorMaxDamage = new SliderSetting(
		"Anchor max damage",
		"Triggers the dodge when the predicted damage is above this.", 6, 0.01,
		20, 0.01, ValueDisplay.DECIMAL);

	private final SliderSetting vClipStep = new SliderSetting("VClip step",
		"Distance between spoofed movement packets.", 8, 1, 10, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting packetLimit = new SliderSetting("Max packets",
		"Maximum movement packets allowed per exploit.", 40, 10, 100, 5,
		ValueDisplay.INTEGER);

	private final CheckboxSetting returnBack = new CheckboxSetting(
		"Return to original position",
		"Sends the player back after the explosion.", true);

	private final CheckboxSetting notifyLimit = new CheckboxSetting(
		"Notify limit reached",
		"Shows a warning when the packet limit is exceeded.", true);

	public GAntiExplosionHack()
	{
		super("GAntiExplosion");
		setCategory(Category.COMBAT);
		addSetting(antiCrystal);
		addSetting(crystalScanBorder);
		addSetting(crystalHitRange);
		addSetting(crystalMaxDamage);
		addSetting(antiAnchor);
		addSetting(anchorScanBorder);
		addSetting(anchorClickRange);
		addSetting(anchorMaxDamage);
		addSetting(vClipStep);
		addSetting(packetLimit);
		addSetting(returnBack);
		addSetting(notifyLimit);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(PacketInputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketInputListener.class, this);
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(MC.player == null || MC.level == null)
			return;

		if(event.getPacket() instanceof ClientboundAddEntityPacket packet
			&& antiCrystal.isChecked()
			&& packet.getType() == EntityType.END_CRYSTAL)
		{
			MC.execute(() -> handleCrystal(packet));
			return;
		}

		if(event.getPacket() instanceof ClientboundBlockUpdatePacket packet
			&& antiAnchor.isChecked())
		{
			BlockState state = packet.getBlockState();
			if(state.getBlock() == Blocks.RESPAWN_ANCHOR
				&& state.getValue(RespawnAnchorBlock.CHARGE) == 0)
				MC.execute(() -> handleAnchor(packet));
		}
	}

	private void handleCrystal(ClientboundAddEntityPacket packet)
	{
		if(MC.player == null || MC.level == null || MC.getConnection() == null)
			return;

		Vec3 crystalPos = new Vec3(packet.getX(), packet.getY(), packet.getZ());
		if(DamageUtils.calculateDamage(crystalPos, MC.player) <= crystalMaxDamage
			.getValue())
			return;

		Vec3 safePos = findSafePos(crystalPos, crystalScanBorder.getValueI(),
			crystalHitRange.getValue(), crystalMaxDamage.getValue(), 6);
		if(safePos == null)
			return;

		Vec3 originalPos = MC.player.position();
		List<Vec3> toSafe = buildPath(originalPos, safePos, vClipStep.getValue());
		List<Vec3> back = returnBack.isChecked()
			? buildPath(safePos, originalPos, vClipStep.getValue())
			: new ArrayList<>();

		if(toSafe.size() + back.size() + 2 > packetLimit.getValueI())
		{
			if(notifyLimit.isChecked())
				ChatUtils.warning("GAntiExplosion: packet limit exceeded.");
			return;
		}

		sendPath(toSafe);
		sendPosition(safePos);

		EndCrystal dummy = new EndCrystal(MC.level, crystalPos.x, crystalPos.y,
			crystalPos.z);
		dummy.setId(packet.getId());
		MC.getConnection().send(new ServerboundAttackPacket(dummy.getId()));
		MC.getConnection()
			.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));

		if(returnBack.isChecked())
		{
			sendPath(back);
			sendPosition(originalPos);
		}else
			MC.player.setPos(safePos);

		ChatUtils.message("GAntiExplosion: crystal dodge complete.");
	}

	private void handleAnchor(ClientboundBlockUpdatePacket packet)
	{
		if(MC.player == null || MC.level == null || MC.getConnection() == null)
			return;

		BlockPos anchorPos = packet.getPos();
		Vec3 anchorCenter = Vec3.atCenterOf(anchorPos);

		int glowstoneSlot = InventoryUtils.indexOf(
			stack -> stack.is(Items.GLOWSTONE), 9);
		if(glowstoneSlot == -1)
		{
			ChatUtils.warning("GAntiExplosion: no glowstone in hotbar.");
			return;
		}

		int igniteSlot = InventoryUtils.indexOf(
			stack -> !stack.isEmpty() && !stack.is(Items.GLOWSTONE), 9);
		if(igniteSlot == -1)
			return;

		if(DamageUtils.calculateDamage(anchorCenter, MC.player, 5) <= anchorMaxDamage
			.getValue())
			return;

		Vec3 safePos = findSafePos(anchorCenter, anchorScanBorder.getValueI(),
			anchorClickRange.getValue(), anchorMaxDamage.getValue(), 5);
		if(safePos == null)
			return;

		Vec3 originalPos = MC.player.position();
		List<Vec3> toSafe = buildPath(originalPos, safePos, vClipStep.getValue());
		List<Vec3> back = returnBack.isChecked()
			? buildPath(safePos, originalPos, vClipStep.getValue())
			: new ArrayList<>();

		if(toSafe.size() + back.size() + 4 > packetLimit.getValueI())
		{
			if(notifyLimit.isChecked())
				ChatUtils.warning("GAntiExplosion: packet limit exceeded.");
			return;
		}

		sendPath(toSafe);
		sendPosition(safePos);

		swapAndInteract(glowstoneSlot, anchorPos);
		swapAndInteract(igniteSlot, anchorPos);

		if(returnBack.isChecked())
		{
			sendPath(back);
			sendPosition(originalPos);
		}else
			MC.player.setPos(safePos);

		ChatUtils.message("GAntiExplosion: anchor dodge complete.");
	}

	private Vec3 findSafePos(Vec3 explosionPos, int border, double maxDistance,
		double maxDamage, float power)
	{
		Vec3 bestPos = null;
		float lowestDamage = Float.MAX_VALUE;
		Vec3 origin = MC.player.position();

		for(int x = -border; x <= border; x++)
			for(int y = -border; y <= border; y++)
				for(int z = -border; z <= border; z++)
				{
					Vec3 testPos = origin.add(x, y, z);
					if(testPos.distanceTo(explosionPos) > maxDistance)
						continue;

					BlockPos testBlock = BlockPos.containing(testPos.x,
						testPos.y, testPos.z);
					if(!MC.level.getBlockState(testBlock)
						.getCollisionShape(MC.level, testBlock).isEmpty())
						continue;

					float damage = calculateDamageAt(testPos, explosionPos,
						power);
					if(damage < lowestDamage)
					{
						lowestDamage = damage;
						bestPos = testPos;
					}
				}

		if(bestPos != null && lowestDamage > maxDamage)
		{
			ChatUtils.warning("GAntiExplosion: no safe spot found ("
				+ String.format("%.1f", lowestDamage) + " damage).");
			return null;
		}

		return bestPos;
	}

	private float calculateDamageAt(Vec3 simulatedPlayerPos,
		Vec3 explosionPos, float power)
	{
		AABB originalBox = MC.player.getBoundingBox();
		Vec3 originalPos = MC.player.position();

		try
		{
			MC.player.setPos(simulatedPlayerPos);
			MC.player.setBoundingBox(
				originalBox.move(simulatedPlayerPos.subtract(originalPos)));
			return DamageUtils.calculateDamage(explosionPos, MC.player, power);
		}catch(Exception e)
		{
			return 100;
		}finally
		{
			MC.player.setPos(originalPos);
			MC.player.setBoundingBox(originalBox);
		}
	}

	private List<Vec3> buildPath(Vec3 start, Vec3 end, double step)
	{
		List<Vec3> path = new ArrayList<>();
		double distance = start.distanceTo(end);
		int segments = (int)Math.ceil(distance / step);

		for(int i = 1; i < segments; i++)
			path.add(start.lerp(end, (double)i / segments));

		return path;
	}

	private void sendPath(List<Vec3> path)
	{
		for(Vec3 pos : path)
			sendPosition(pos);
	}

	private void sendPosition(Vec3 pos)
	{
		MC.getConnection().send(new ServerboundMovePlayerPacket.Pos(pos.x,
			pos.y, pos.z, MC.player.onGround(), false));
	}

	private void swapAndInteract(int slot, BlockPos pos)
	{
		if(MC.player == null || MC.gameMode == null
			|| MC.getConnection() == null)
			return;

		int previous = MC.player.getInventory().getSelectedSlot();
		MC.player.getInventory().setSelectedSlot(slot);
		MC.getConnection().send(new ServerboundSetCarriedItemPacket(slot));

		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos),
			Direction.UP, pos, false);
		MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, hit);
		MC.player.swing(InteractionHand.MAIN_HAND);

		MC.player.getInventory().setSelectedSlot(previous);
		MC.getConnection()
			.send(new ServerboundSetCarriedItemPacket(previous));
	}
}
