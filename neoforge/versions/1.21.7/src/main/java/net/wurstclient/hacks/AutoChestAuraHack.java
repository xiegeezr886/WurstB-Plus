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

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RotationUtils;
import net.wurstclient.util.chunk.ChunkUtils;

@SearchTags({"auto chest aura", "chest aura", "open chests", "AutoChestAura",
	"container aura"})
public final class AutoChestAuraHack extends Hack
	implements UpdateListener, PacketInputListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Maximum distance to containers.", 5, 0, 6, 0.1,
		ValueDisplay.DECIMAL);

	private final CheckboxSetting openChests = new CheckboxSetting("Chests",
		"Open chests and trapped chests.", true);

	private final CheckboxSetting openBarrels = new CheckboxSetting("Barrels",
		"Open barrels.", true);

	private final CheckboxSetting openShulkers = new CheckboxSetting(
		"Shulker boxes", "Open shulker boxes.", true);

	private final SliderSetting delay = new SliderSetting("Open delay",
		"Ticks between opening containers.", 3, 1, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting waitTime = new SliderSetting("Read wait",
		"Ticks to wait after the container contents arrive.", 2, 1, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting timeout = new SliderSetting("Force timeout",
		"Ticks before closing a container that seems stuck.", 15, 5, 60, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting rotate = new CheckboxSetting("Rotate",
		"Face containers before opening them.", true);

	private final Set<BlockPos> opened = new HashSet<>();
	private int timer;
	private int packetTimer;
	private int stuckTimer;
	private boolean pending;

	public AutoChestAuraHack()
	{
		super("AutoChestAura");
		setCategory(Category.COMBAT);
		addSetting(range);
		addSetting(openChests);
		addSetting(openBarrels);
		addSetting(openShulkers);
		addSetting(delay);
		addSetting(waitTime);
		addSetting(timeout);
		addSetting(rotate);
	}

	@Override
	protected void onEnable()
	{
		opened.clear();
		timer = 0;
		packetTimer = 0;
		stuckTimer = 0;
		pending = false;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		opened.clear();
		if(pending)
			forceClose();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(pending)
		{
			stuckTimer++;

			if(MC.screen == null && stuckTimer > 5)
			{
				resetState();
				return;
			}

			if(packetTimer > 0 && --packetTimer <= 0)
			{
				forceClose();
				return;
			}

			if(stuckTimer >= timeout.getValueI())
				forceClose();
			return;
		}

		if(timer > 0)
		{
			timer--;
			return;
		}

		if(MC.screen != null)
			return;

		for(BlockEntity blockEntity : ChunkUtils.getLoadedBlockEntities()
			.toList())
		{
			if(!isEnabledContainer(blockEntity))
				continue;

			BlockPos pos = blockEntity.getBlockPos();
			if(opened.contains(pos))
				continue;

			if(RotationUtils.getEyesPos().distanceToSqr(Vec3.atCenterOf(pos))
				>= range.getValueSq())
				continue;

			Vec3 hitVec = Vec3.atCenterOf(pos);
			if(!BlockUtils.hasLineOfSight(hitVec))
				continue;

			if(rotate.isChecked())
				WURST.getRotationFaker().faceVectorClient(hitVec);

			IMC.getInteractionManager().rightClickBlock(pos, Direction.UP,
				hitVec);

			opened.add(pos.immutable());
			pending = true;
			stuckTimer = 0;
			packetTimer = 0;
			timer = delay.getValueI();
			break;
		}
	}

	private boolean isEnabledContainer(BlockEntity blockEntity)
	{
		if(blockEntity instanceof ChestBlockEntity)
			return openChests.isChecked();
		if(blockEntity instanceof BarrelBlockEntity)
			return openBarrels.isChecked();
		if(blockEntity instanceof ShulkerBoxBlockEntity)
			return openShulkers.isChecked();
		return false;
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(!pending || MC.player == null)
			return;

		int menuId = MC.player.containerMenu.containerId;

		if(event.getPacket() instanceof ClientboundContainerSetContentPacket packet)
		{
			if(packet.containerId() == menuId)
				packetTimer = waitTime.getValueI();
		}else if(event.getPacket() instanceof ClientboundOpenScreenPacket packet)
		{
			if(packetTimer == 0)
				packetTimer = Math.max(1, timeout.getValueI() - 5);
		}
	}

	private void forceClose()
	{
		if(MC.player == null)
			return;

		int menuId = MC.player.containerMenu.containerId;
		MC.player.closeContainer();
		if(menuId != 0)
			MC.player.connection
				.send(new ServerboundContainerClosePacket(menuId));

		resetState();
	}

	private void resetState()
	{
		pending = false;
		stuckTimer = 0;
		packetTimer = 0;
	}
}
