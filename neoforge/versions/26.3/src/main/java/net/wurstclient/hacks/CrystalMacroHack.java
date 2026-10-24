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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;

@SearchTags({"crystal macro", "legit crystal", "CrystalMacro",
	"auto crystal"})
public final class CrystalMacroHack extends Hack implements UpdateListener
{
	private final SliderSetting placeDelay = new SliderSetting("Place delay",
		"Ticks between crystal placements.", 0.3, 0, 20, 0.05,
		ValueDisplay.DECIMAL.withSuffix(" ticks"));

	private final SliderSetting breakDelay = new SliderSetting("Break delay",
		"Ticks between crystal attacks.", 0.03, 0, 20, 0.01,
		ValueDisplay.DECIMAL.withSuffix(" ticks"));

	private final CheckboxSetting stopOnKill = new CheckboxSetting(
		"Stop on kill",
		"Pauses the macro for 5 seconds when a nearby player dies.", true);

	private final CheckboxSetting requireUseKey = new CheckboxSetting(
		"Require use key", "Only crystal while the use key is held.", true);

	private final Set<Player> deadPlayers = new HashSet<>();
	private long lastPlaceTime;
	private long lastBreakTime;
	private boolean paused;
	private long resumeTime;

	public CrystalMacroHack()
	{
		super("CrystalMacro");
		setCategory(Category.COMBAT);
		addSetting(placeDelay);
		addSetting(breakDelay);
		addSetting(stopOnKill);
		addSetting(requireUseKey);
	}

	@Override
	protected void onEnable()
	{
		lastPlaceTime = 0;
		lastBreakTime = 0;
		deadPlayers.clear();
		paused = false;
		resumeTime = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		deadPlayers.clear();
		paused = false;
		resumeTime = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gui.screen() != null)
			return;

		long now = System.currentTimeMillis();

		if(paused && now >= resumeTime)
			paused = false;
		if(paused)
			return;

		if(requireUseKey.isChecked() && !MC.options.keyUse.isDown())
			return;
		if(MC.player.isUsingItem())
			return;
		if(!MC.player.getMainHandItem().is(Items.END_CRYSTAL))
			return;

		if(stopOnKill.isChecked() && checkForDeadPlayers())
		{
			paused = true;
			resumeTime = now + 5000;
			return;
		}

		handleInteraction(now);
	}

	private void handleInteraction(long now)
	{
		HitResult hitResult = MC.hitResult;
		if(hitResult instanceof BlockHitResult blockHit)
			handleBlock(blockHit, now);
		else if(hitResult instanceof EntityHitResult entityHit)
			handleEntity(entityHit, now);
	}

	private void handleBlock(BlockHitResult blockHit, long now)
	{
		if(blockHit.getType() != HitResult.Type.BLOCK)
			return;

		long delayMs = (long)(placeDelay.getValue() * 50.0);
		if(now - lastPlaceTime < delayMs)
			return;

		BlockPos pos = blockHit.getBlockPos();
		if(BlockUtils.getBlock(pos) != Blocks.OBSIDIAN
			&& BlockUtils.getBlock(pos) != Blocks.BEDROCK)
			return;
		if(!isValidPlacement(pos))
			return;

		MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, blockHit);
		MC.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
		lastPlaceTime = now;
	}

	private void handleEntity(EntityHitResult entityHit, long now)
	{
		long delayMs = (long)(breakDelay.getValue() * 50.0);
		if(now - lastBreakTime < delayMs)
			return;

		Entity entity = entityHit.getEntity();
		if(!(entity instanceof EndCrystal))
			return;

		MC.gameMode.attack(MC.player, entity);
		MC.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
		entity.discard();
		lastBreakTime = now;
	}

	private boolean isValidPlacement(BlockPos pos)
	{
		BlockPos above = pos.above();
		if(!MC.level.isEmptyBlock(above))
			return false;

		AABB box = new AABB(above.getX(), above.getY(), above.getZ(),
			above.getX() + 1.0, above.getY() + 2.0, above.getZ() + 1.0);
		return MC.level.getEntities((Entity)null, box, entity -> true)
			.isEmpty();
	}

	private boolean checkForDeadPlayers()
	{
		for(Player player : MC.level.players())
		{
			if(player == MC.player)
				continue;

			if(!player.isAlive() || player.getHealth() <= 0)
			{
				if(deadPlayers.add(player))
					return true;
			}
		}

		deadPlayers.removeIf(player -> player.isAlive());
		return false;
	}
}
