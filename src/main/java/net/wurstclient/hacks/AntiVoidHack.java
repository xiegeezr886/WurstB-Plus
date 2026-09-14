/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"anti void", "AntiVoid", "void"})
public final class AntiVoidHack extends Hack implements UpdateListener
{
	private final CheckboxSetting onlyHole = new CheckboxSetting(
		"Only in hole", "Only activates when standing in a hole.", true);

	public AntiVoidHack()
	{
		super("AntiVoid");
		setCategory(Category.MOVEMENT);
		addSetting(onlyHole);
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
		releaseJumpKey();
	}

	@Override
	public void onUpdate()
	{
		LocalPlayer player = MC.player;

		// 旧实现开头是：
		//     if(MC.player.getY() > MC.player.getBlockY() + 3) return;
		// 但 getBlockY() 就是 floor(getY())（1.20.2 Entity.getBlockY()），
		// 所以 getY() - getBlockY() 恒在 [0,1)，这个 return 永远不成立 ——
		// 是死代码，去掉后行为不变。
		double minY = MC.level.getMinBuildHeight();

		if(onlyHole.isChecked() && player.getBlockY() > minY + 10)
		{
			releaseJumpKey();
			return;
		}

		if(player.getY() >= minY + 3)
		{
			releaseJumpKey();
			return;
		}

		player.setDeltaMovement(0, 0.5, 0);

		// 旧实现在这里写死 getY() > -60（主世界最低点 -64 的近似值）。
		// 下界/末地的最低点是 0，于是"同样的相对高度"在主世界不按跳跃、
		// 在下界却按。改成按当前世界的最低点计算。
		if(player.getY() > minY + 4)
			MC.options.keyJump.setDown(true);
		else
			releaseJumpKey();
	}

	/**
	 * 松开被本模块强制按下的跳跃键。
	 *
	 * <p>
	 * 旧实现只 `setDown(true)`、从不还原：掉进虚空触发过一次之后，
	 * 跳跃键会一直保持按下（直到玩家自己按一次空格），被传送回地面后
	 * 会一直自动跳。这里按玩家真实按键状态还原。
	 */
	private void releaseJumpKey()
	{
		IKeyBinding.get(MC.options.keyJump).resetPressedState();
	}
}
