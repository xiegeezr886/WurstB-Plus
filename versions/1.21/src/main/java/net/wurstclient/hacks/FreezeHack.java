/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;

@SearchTags({"freeze", "stationary", "queue", "cancel"})
public final class FreezeHack extends Hack
	implements UpdateListener, PacketInputListener, PacketOutputListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lQueue\u00a7r - Queues every outgoing packet.\n"
			+ "\u00a7lCancel\u00a7r - Cancels outgoing movement packets.\n"
			+ "\u00a7lStationary\u00a7r - Replaces movement packets with frozen "
			+ "coordinates.",
		Mode.values(), Mode.STATIONARY);

	private final CheckboxSetting disableOnFlag = new CheckboxSetting(
		"Disable on flag", "Disables when the server teleports you.", false);

	private final CheckboxSetting notification = new CheckboxSetting(
		"Notification", "Shows a chat message when the server teleports you.",
		false);

	private final CheckboxSetting balance = new CheckboxSetting("Balance",
		"Runs the missed player ticks when disabling.", false);

	private final CheckboxSetting bypassNegativeTimer = new CheckboxSetting(
		"Bypass negative timer", "Interacts with an item when disabling.", true);

	private final CheckboxSetting cancelC0B = new CheckboxSetting(
		"Cancel C0B", "Cancels outgoing pong packets.", false);

	private final CheckboxSetting freezeLook = new CheckboxSetting(
		"Freeze look", "Also freezes the server-side rotation.", false);

	private final CheckboxSetting freezeLookSilent = new CheckboxSetting(
		"Freeze look silently",
		"Freezes the server-side rotation without changing the client camera.",
		true);

	private final CheckboxSetting maceMode = new CheckboxSetting("Mace mode",
		"Slowly drifts downward to accumulate fall distance.", true);

	private final SliderSetting driftSpeed = new SliderSetting("Drift speed",
		"Blocks to sink per tick in mace mode.", 0.05, 0.01, 0.5, 0.01,
		ValueDisplay.DECIMAL);

	private final SliderSetting maxDrift = new SliderSetting("Max drift",
		"Maximum total distance to sink in mace mode.", 5, 1, 20, 0.1,
		ValueDisplay.DECIMAL);

	private final List<Packet<?>> queuedPackets = new ArrayList<>();

	private int missedOutTick;
	private boolean warpInProgress;
	private boolean isFlushing;
	private double frozenX;
	private double frozenY;
	private double frozenZ;
	private float frozenYaw;
	private float frozenPitch;
	private double accumulatedDrift;
	private boolean pendingDisable;

	public FreezeHack()
	{
		super("Freeze");
		setCategory(Category.MOVEMENT);
		addSetting(mode);
		addSetting(disableOnFlag);
		addSetting(notification);
		addSetting(balance);
		addSetting(bypassNegativeTimer);
		addSetting(cancelC0B);
		addSetting(freezeLook);
		addSetting(freezeLookSilent);
		addSetting(maceMode);
		addSetting(driftSpeed);
		addSetting(maxDrift);
	}

	@Override
	protected boolean canEnable()
	{
		return MC.player != null && MC.level != null;
	}

	@Override
	protected void onEnable()
	{
		missedOutTick = 0;
		queuedPackets.clear();
		warpInProgress = false;
		isFlushing = false;
		accumulatedDrift = 0;
		pendingDisable = false;

		frozenX = MC.player.getX();
		frozenY = MC.player.getY();
		frozenZ = MC.player.getZ();
		frozenYaw = MC.player.getYRot();
		frozenPitch = MC.player.getXRot();

		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);

		if(balance.isChecked() && MC.player != null)
		{
			warpInProgress = true;
			while(missedOutTick > 0)
			{
				MC.player.tick();
				missedOutTick--;
			}
			warpInProgress = false;
		}

		if(mode.getSelected() == Mode.QUEUE && !queuedPackets.isEmpty()
			&& MC.getConnection() != null)
		{
			isFlushing = true;
			try
			{
				for(Packet<?> packet : queuedPackets)
					MC.getConnection().send(packet);
			}finally
			{
				queuedPackets.clear();
				isFlushing = false;
			}
		}

		missedOutTick = 0;

		if(bypassNegativeTimer.isChecked())
			interact();
	}

	@Override
	public void onUpdate()
	{
		if(pendingDisable)
		{
			pendingDisable = false;

			if(notification.isChecked())
				ChatUtils.message("Freeze: flagged, disabling.");

			setEnabled(false);
			return;
		}

		if(warpInProgress || MC.player == null || MC.level == null)
			return;

		missedOutTick++;
		MC.player.setDeltaMovement(Vec3.ZERO);

		if(mode.getSelected() == Mode.STATIONARY && maceMode.isChecked()
			&& accumulatedDrift < maxDrift.getValue())
		{
			double nextY = frozenY - driftSpeed.getValue();
			AABB nextBox =
				MC.player.getBoundingBox().move(0, nextY - frozenY, 0);

			if(MC.level.noCollision(nextBox))
			{
				frozenY = nextY;
				accumulatedDrift += driftSpeed.getValue();
			}else
				accumulatedDrift = maxDrift.getValue();
		}

		MC.player.setPos(frozenX, frozenY, frozenZ);

		if(freezeLook.isChecked() && !freezeLookSilent.isChecked())
		{
			MC.player.setYRot(frozenYaw);
			MC.player.setXRot(frozenPitch);
		}
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(!(event.getPacket() instanceof ClientboundPlayerPositionPacket))
			return;

		missedOutTick = 0;
		accumulatedDrift = 0;

		if(MC.player != null)
		{
			frozenX = MC.player.getX();
			frozenY = MC.player.getY();
			frozenZ = MC.player.getZ();
			frozenYaw = MC.player.getYRot();
			frozenPitch = MC.player.getXRot();
		}

		if(disableOnFlag.isChecked())
			pendingDisable = true;
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(warpInProgress || isFlushing)
			return;

		Packet<?> packet = event.getPacket();

		switch(mode.getSelected())
		{
			case QUEUE ->
			{
				queuedPackets.add(packet);
				event.cancel();
			}
			case CANCEL ->
			{
				if(packet instanceof ServerboundMovePlayerPacket)
					event.cancel();
			}
			case STATIONARY -> handleStationary(event, packet);
		}
	}

	private void handleStationary(PacketOutputEvent event, Packet<?> packet)
	{
		if(packet instanceof ServerboundMovePlayerPacket movePacket)
		{
			ServerboundMovePlayerPacket replacement =
				replaceMovePacket(movePacket);
			if(replacement == movePacket)
				return;

			event.cancel();
			isFlushing = true;
			try
			{
				if(MC.getConnection() != null)
					MC.getConnection().send(replacement);
			}finally
			{
				isFlushing = false;
			}
			return;
		}

		if(packet instanceof ServerboundPongPacket && cancelC0B.isChecked())
			event.cancel();
	}

	private ServerboundMovePlayerPacket replaceMovePacket(
		ServerboundMovePlayerPacket original)
	{
		boolean onGround = MC.player != null && MC.player.onGround();
		boolean horizontalCollision =
			MC.player != null && MC.player.horizontalCollision;

		if(original.hasPosition())
			return new ServerboundMovePlayerPacket.Pos(frozenX, frozenY,
				frozenZ, onGround);

		if(original.hasRotation() && freezeLook.isChecked())
			return new ServerboundMovePlayerPacket.Rot(frozenYaw, frozenPitch,
				onGround);

		return original;
	}

	private void interact()
	{
		if(MC.player == null || MC.gameMode == null || MC.level == null)
			return;

		InteractionHand hand = InteractionHand.OFF_HAND;
		int previousSlot = -1;

		if(!isInteractable(MC.player.getOffhandItem()))
		{
			hand = null;

			for(int i = 0; i < 9; i++)
			{
				if(!isInteractable(MC.player.getInventory().getItem(i)))
					continue;

				hand = InteractionHand.MAIN_HAND;
				int selected =
					MC.player.getInventory().selected;
				if(i != selected)
				{
					previousSlot = selected;
					MC.player.getInventory().selected = (i);
					MC.player.connection.send(
						new ServerboundSetCarriedItemPacket(i));
				}
				break;
			}
		}

		if(hand == null)
			return;

		MC.gameMode.useItem(MC.player, hand);

		if(previousSlot != -1)
		{
			MC.player.getInventory().selected = (previousSlot);
			MC.player.connection.send(
				new ServerboundSetCarriedItemPacket(previousSlot));
		}
	}

	private boolean isInteractable(ItemStack stack)
	{
		if(stack.is(Items.ENDER_PEARL) || stack.is(Items.TNT)
			|| stack.is(Items.FIRE_CHARGE) || stack.is(Items.WIND_CHARGE))
			return false;

		return stack.getUseAnimation() == UseAnim.NONE;
	}

	private enum Mode
	{
		QUEUE("Queue"),
		CANCEL("Cancel"),
		STATIONARY("Stationary");

		private final String name;

		Mode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
