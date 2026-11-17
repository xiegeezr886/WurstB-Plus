/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun v1.9.0 (GPL-3.0).
package net.wurstclient.hacks;

import java.util.Locale;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.ServerEquivTp;

@SearchTags({"vclip", "v clip", "vertical clip", "super vclip", "超级vclip"})
public final class GSuperVClipHack extends Hack implements UpdateListener
{
	private static final double VOID_FLOOR_Y = -128;
	private static final double FALL_DEBT_DELTA = 9E-8;

	private final SliderSetting moveDistance = new SliderSetting(
		"Packet distance",
		"Distance covered by each movement packet, in blocks.", 10, 0.1, 100,
		0.1, ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final SliderSetting steps = new SliderSetting("Steps",
		"Total displacement = steps x packet distance.", 8, 1, 20, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting protectY = new SliderSetting("Protect Y",
		"The teleport never ends below this Y level.", -63, -128, 0, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting sneakDown = new CheckboxSetting(
		"Down while sneaking",
		"Clip downwards while sneaking, upwards otherwise.", true);

	private final CheckboxSetting voidProtect = new CheckboxSetting(
		"Void protection", "Clamp the destination to Y -128.", true);

	public GSuperVClipHack()
	{
		super("GSuperVClip");
		setCategory(Category.MOVEMENT);
		addSetting(moveDistance);
		addSetting(steps);
		addSetting(protectY);
		addSetting(sneakDown);
		addSetting(voidProtect);
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
		if(MC.player == null || MC.level == null)
		{
			setEnabled(false);
			return;
		}

		// This is a one-shot hack: run once, then turn itself off.
		setEnabled(false);
		doVClip();
	}

	private void doVClip()
	{
		LocalPlayer player = MC.player;
		Vec3 start = player.position();
		double clipY = steps.getValueI() * moveDistance.getValue();
		boolean down = sneakDown.isChecked() && player.isShiftKeyDown();

		double finalY = start.y;
		double limit = Math.floor(clipY);
		for(double offset = 0; offset <= limit; offset += 1)
		{
			double candidate = down ? start.y - (clipY - offset)
				: start.y + clipY - offset;

			if(candidate > protectY.getValue()
				&& ServerEquivTp.canStand(new Vec3(start.x, candidate, start.z)))
			{
				finalY = candidate;
				break;
			}
		}

		if(voidProtect.isChecked() && finalY <= VOID_FLOOR_Y)
			finalY = VOID_FLOOR_Y;

		Vec3 target = new Vec3(start.x, finalY, start.z);
		sendPath(start, target);
		player.setPos(target.x, target.y, target.z);
		player.fallDistance = 0;
		ServerEquivTp.sendPos(target.x, target.y - FALL_DEBT_DELTA, target.z,
			false);

		ChatUtils.message(String.format(Locale.ROOT, "GSuperVClip: %.1f m",
			target.y - start.y));
	}

	private void sendPath(Vec3 from, Vec3 to)
	{
		double distance = from.distanceTo(to);
		int packets =
			Math.max(1, (int)Math.ceil(distance / moveDistance.getValue()));

		for(int i = 1; i <= packets; i++)
		{
			Vec3 pos = from.lerp(to, (double)i / packets);
			ServerEquivTp.sendPos(pos.x, pos.y, pos.z, false);
		}
	}
}
