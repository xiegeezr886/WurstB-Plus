/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Pose;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstClient;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.FakePlayerEntity;

@SearchTags({"fake player", "fakeplayer", "test dummy", "client side player"})
public final class FakePlayerHack extends Hack implements UpdateListener
{
	private final SliderSetting spinSpeed = new SliderSetting("Spin speed",
		"Degrees to rotate the fake player per tick. 0 disables spinning.", 0,
		0, 30, 1, ValueDisplay.DECIMAL);

	private final CheckboxSetting sneak =
		new CheckboxSetting("Sneak", "Makes the fake player sneak.", false);

	private FakePlayerEntity fakePlayer;

	public FakePlayerHack()
	{
		super("FakePlayer");
		setCategory(Category.OTHER);
		addSetting(spinSpeed);
		addSetting(sneak);
	}

	@Override
	protected boolean canEnable()
	{
		return MC.player != null && MC.level != null;
	}

	@Override
	protected void onEnable()
	{
		spawnFakePlayer();
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		removeFakePlayer();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
		{
			removeFakePlayer();
			return;
		}

		if(fakePlayer != null && (fakePlayer.isRemoved()
			|| fakePlayer.level() != MC.level))
			removeFakePlayer();

		if(fakePlayer == null)
			spawnFakePlayer();

		if(fakePlayer == null)
			return;

		float speed = spinSpeed.getValueF();
		if(speed > 0)
		{
			float yaw = fakePlayer.getYRot() + speed;
			fakePlayer.setYRot(yaw);
			fakePlayer.setYHeadRot(yaw);
			fakePlayer.setYBodyRot(yaw);
		}

		boolean shouldSneak = sneak.isChecked();
		fakePlayer.setShiftKeyDown(shouldSneak);
		fakePlayer.setPose(shouldSneak ? Pose.CROUCHING : Pose.STANDING);
	}

	private void spawnFakePlayer()
	{
		if(fakePlayer != null || MC.player == null || MC.level == null)
			return;

		fakePlayer = new SkinFakePlayer();
	}

	private void removeFakePlayer()
	{
		if(fakePlayer == null)
			return;

		fakePlayer.despawn();
		fakePlayer = null;
	}

	/**
	 * Uses the real player's player info so the fake player renders with your
	 * skin instead of a random default skin.
	 */
	private static final class SkinFakePlayer extends FakePlayerEntity
	{
		@Override
		protected PlayerInfo getPlayerInfo()
		{
			if(WurstClient.MC.getConnection() == null
				|| WurstClient.MC.player == null)
				return null;

			return WurstClient.MC.getConnection()
				.getPlayerInfo(WurstClient.MC.player.getUUID());
		}
	}
}
