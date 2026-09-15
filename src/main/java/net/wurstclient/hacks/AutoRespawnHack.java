/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstClient;
import net.wurstclient.events.DeathListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"auto respawn", "AutoRevive", "auto revive"})
public final class AutoRespawnHack extends Hack implements DeathListener
{
	private final CheckboxSetting respawn = new CheckboxSetting("Respawn",
		"Automatically respawn after dying.", true);
	private final CheckboxSetting antiGlitchScreen = new CheckboxSetting(
		"Anti glitch screen", "Closes the death screen if it shows up while"
			+ " you're still alive.",
		true);
	private final CheckboxSetting button =
		new CheckboxSetting("Death screen button", "Shows a button on the death"
			+ " screen that lets you quickly enable AutoRespawn.", true);
	
	public AutoRespawnHack()
	{
		super("AutoRespawn");
		setCategory(Category.COMBAT);
		addSetting(respawn);
		addSetting(antiGlitchScreen);
		addSetting(button);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(DeathListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(DeathListener.class, this);
	}
	
	/**
	 * 与参考 {@code module/misc/AutoRespawn.kt:29-32} 的条件一致：
	 * <pre>
	 * if (respawn || antiGlitchScreen &amp;&amp; player.health &gt; 0.0f) { ... }
	 * </pre>
	 * 即"关掉自动重生、但死亡界面出现在还活着的时候（假死/回滚）仍然关掉它"。
	 */
	@Override
	public void onDeath()
	{
		if(!respawn.isChecked() && !(antiGlitchScreen.isChecked()
			&& MC.player.getHealth() > 0))
			return;
		
		respawnNow();
	}
	
	/**
	 * 无条件重生。死亡界面上的那个按钮走这条路径：用户既然点了按钮，就是要重生，
	 * 不应该被 {@code Respawn} 的设置挡住。
	 */
	public void respawnNow()
	{
		MC.player.respawn();
		MC.setScreen(null);
	}
	
	public boolean shouldShowButton()
	{
		return WurstClient.INSTANCE.isEnabled() && !isEnabled()
			&& button.isChecked();
	}
}
