package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BaritoneUtils;

@SearchTags({"baritone walk", "smart walk", "baritone auto walk",
	"auto walk baritone", "path walk"})
@DontSaveState
public final class BaritoneWalkHack extends Hack implements UpdateListener
{
	private final SliderSetting distance =
		new SliderSetting("Distance",
			"How far to walk in the facing direction.", 100, 10, 500, 10,
			ValueDisplay.INTEGER);

	private int tickCounter;

	public BaritoneWalkHack()
	{
		super("BaritoneWalk");
		setCategory(Category.MOVEMENT);
		addSetting(distance);
	}

	@Override
	protected boolean canEnable()
	{
		return BaritoneUtils.IS_AVAILABLE && MC.player != null;
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		tickCounter = 0;
		startWalking();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		BaritoneUtils.stop();
	}

	@Override
	public void onUpdate()
	{
		if(!BaritoneUtils.IS_AVAILABLE)
		{
			setEnabled(false);
			return;
		}

		if(MC.player == null)
			return;

		tickCounter++;
		if(tickCounter < 20)
			return;

		tickCounter = 0;

		if(!BaritoneUtils.isPathing())
			startWalking();
	}

	private void startWalking()
	{
		if(MC.player == null)
			return;

		float yaw = MC.player.getYRot();
		BaritoneUtils.walkDirection(yaw, distance.getValue());
	}

	@Override
	public String getRenderName()
	{
		if(isEnabled() && BaritoneUtils.isPathing())
			return getName() + " [Walking]";
		return getName();
	}
}
