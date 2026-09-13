/*
 * Copyright (c) 2025 Penguin
 */
package net.wurstclient.hacks;

import net.minecraft.core.BlockPos;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.BlockUtils;

@SearchTags({"anchor", "hole anchor", "AntiKB hole"})
public final class AnchorHack extends Hack implements UpdateListener
{
	private final CheckboxSetting pullback = new CheckboxSetting(
		"Pullback", "Pulls you back into the hole when pushed out.", true);

	private int holeCheck;

	public AnchorHack()
	{
		super("Anchor");
		setCategory(Category.MOVEMENT);
		addSetting(pullback);
	}

	@Override
	protected void onEnable()
	{
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
		if(!MC.player.onGround())
			return;

		BlockPos pp = BlockPos.containing(MC.player.position());

		boolean inHole = !BlockUtils.getState(pp.offset(1, 0, 0)).canBeReplaced()
			&& !BlockUtils.getState(pp.offset(-1, 0, 0)).canBeReplaced()
			&& !BlockUtils.getState(pp.offset(0, 0, 1)).canBeReplaced()
			&& !BlockUtils.getState(pp.offset(0, 0, -1)).canBeReplaced();

		if(inHole)
		{
			MC.player.setDeltaMovement(0, MC.player.getDeltaMovement().y, 0);
			MC.player.setPosRaw(pp.getX() + 0.5, MC.player.getY(), pp.getZ() + 0.5);
		}
		else if(pullback.isChecked())
		{
			double dx = MC.player.getX() - (pp.getX() + 0.5);
			double dz = MC.player.getZ() - (pp.getZ() + 0.5);
			if(Math.abs(dx) > 0.3 || Math.abs(dz) > 0.3)
				MC.player.setDeltaMovement(-dx * 0.3, MC.player.getDeltaMovement().y,
					-dz * 0.3);
		}
	}
}
