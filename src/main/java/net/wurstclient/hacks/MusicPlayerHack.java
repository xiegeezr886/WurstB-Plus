package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.DontBlock;
import net.wurstclient.SearchTags;
import net.wurstclient.twilight.TwilightShellScreen;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;

@DontSaveState
@DontBlock
@SearchTags({"music", "netease", "网易云", "播放器"})
public final class MusicPlayerHack extends Hack
{
	public MusicPlayerHack()
	{
		super("MusicPlayer");
		setCategory(Category.OTHER);
	}

	@Override
	protected void onEnable()
	{
		MC.setScreen(new TwilightShellScreen(MC.screen));
		setEnabled(false);
	}
}
