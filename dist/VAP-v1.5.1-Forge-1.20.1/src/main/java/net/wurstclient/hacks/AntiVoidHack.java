/*
 * Copyright (c) 2025 Penguin
 */
package net.wurstclient.hacks;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;

@SearchTags({"anti void", "AntiVoid", "void"})
public final class AntiVoidHack extends Hack implements UpdateListener, WorldChangeListener
{
	private final CheckboxSetting onlyHole = new CheckboxSetting(
		"Only in hole", "Restricts Bounce mode to the bottom ten blocks of the world.", true);
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"Bounce near the world bottom, or use Jump/Flight in the first 15 blocks of the void.",
		Mode.values(), Mode.BOUNCE);
	private boolean ownsFlight;

	public AntiVoidHack()
	{
		super("AntiVoid");
		setCategory(Category.MOVEMENT);
		addSetting(onlyHole);
		addSetting(mode);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		releaseFlight();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.player.isSpectator()
			|| MC.player.isPassenger() || WURST.getHax().freecamHack.isEnabled())
		{
			releaseFlight();
			return;
		}

		double y = MC.player.getY();
		int minY = MC.level.getMinBuildHeight();
		if(mode.getSelected() != Mode.FLIGHT)
			releaseFlight();

		if(mode.getSelected() == Mode.BOUNCE)
		{
			if(onlyHole.isChecked() && MC.player.getBlockY() > minY + 10)
				return;
			if(y < minY + 3)
			{
				Vec3 velocity = MC.player.getDeltaMovement();
				MC.player.setDeltaMovement(velocity.x, 0.5, velocity.z);
			}
			return;
		}

		if(y > minY || y < minY - 15)
		{
			releaseFlight();
			return;
		}
		if(mode.getSelected() == Mode.JUMP)
			MC.player.jumpFromGround();
		else if(!WURST.getHax().flightHack.isEnabled())
		{
			WURST.getHax().flightHack.setEnabled(true);
			ownsFlight = WURST.getHax().flightHack.isEnabled();
		}
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		releaseFlight();
	}

	private void releaseFlight()
	{
		if(!ownsFlight)
			return;
		ownsFlight = false;
		WURST.getHax().flightHack.setEnabled(false);
	}

	private enum Mode
	{
		BOUNCE, JUMP, FLIGHT
	}
}
