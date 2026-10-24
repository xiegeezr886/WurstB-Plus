/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;

/**
 * Finds nearby trial vaults, selects the matching trial key and right-clicks
 * the vault.
 *
 * <p>
 * The vault API is available in this Minecraft version, so the ominous flag is
 * read directly from {@link VaultBlock#OMINOUS} instead of guessing from the
 * block name.
 */
@SearchTags({"auto vault", "trial vault", "vault key", "ominous vault",
	"trial key", "auto key"})
public final class AutoVaultHack extends Hack implements UpdateListener
{
	public enum Mode
	{
		ALWAYS("Always"),
		KEY_HELD("Key held");

		private final String name;

		private Mode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"Always: opens any vault in range.\n"
			+ "Key held: only opens a vault while the use key is held.",
		Mode.values(), Mode.KEY_HELD);

	private final SliderSetting range = new SliderSetting("Radius",
		"How far to search for vaults.", 4, 1, 6, 0.5,
		ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final SliderSetting delay = new SliderSetting("Delay",
		"Ticks to wait between vault interactions.", 10, 1, 40, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private int timer;

	public AutoVaultHack()
	{
		super("AutoVault");
		setCategory(Category.ITEMS);
		addSetting(mode);
		addSetting(range);
		addSetting(delay);
	}

	@Override
	protected void onEnable()
	{
		timer = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.player.isSpectator())
			return;

		if(MC.gui.screen() != null)
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		if(mode.getSelected() == Mode.KEY_HELD
			&& !MC.options.keyUse.isDown())
			return;

		BlockPos vaultPos = findVault();
		if(vaultPos == null)
			return;

		BlockState state = MC.level.getBlockState(vaultPos);
		boolean ominous = state.getValue(VaultBlock.OMINOUS);
		Item requiredKey = ominous ? Items.OMINOUS_TRIAL_KEY : Items.TRIAL_KEY;

		if(!MC.player.getMainHandItem().is(requiredKey))
		{
			if(!InventoryUtils.selectItem(requiredKey))
				return;

			// Selecting a key from the main inventory takes a tick to arrive.
			timer = delay.getValueI();
			return;
		}

		IMC.getInteractionManager().rightClickBlock(vaultPos, Direction.UP,
			Vec3.atCenterOf(vaultPos));
		MC.player.swing(InteractionHand.MAIN_HAND);
		timer = delay.getValueI();
	}

	private BlockPos findVault()
	{
		double rangeSq = range.getValueSq();
		int r = range.getValueCeil();
		BlockPos center = MC.player.blockPosition();
		BlockPos min = center.offset(-r, -r, -r);
		BlockPos max = center.offset(r, r, r);

		BlockPos best = null;
		double bestDistSq = Double.MAX_VALUE;

		for(BlockPos pos : BlockPos.betweenClosed(min, max))
		{
			BlockState state = MC.level.getBlockState(pos);
			if(!state.is(Blocks.VAULT))
				continue;

			double distSq = MC.player.distanceToSqr(Vec3.atCenterOf(pos));
			if(distSq > rangeSq || distSq >= bestDistSq)
				continue;

			best = pos.immutable();
			bestDistSq = distSq;
		}

		return best;
	}
}
