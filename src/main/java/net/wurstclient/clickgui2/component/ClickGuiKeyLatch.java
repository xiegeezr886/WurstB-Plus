package net.wurstclient.clickgui2.component;

/** Prevents the key press that opens ClickGUI from immediately closing it. */
public final class ClickGuiKeyLatch
{
	private boolean closeArmed;

	public boolean shouldCloseOnPress(boolean clickGuiBinding)
	{
		return clickGuiBinding && closeArmed;
	}

	public boolean armOnRelease(boolean clickGuiBinding)
	{
		if(!clickGuiBinding)
			return false;
		closeArmed = true;
		return true;
	}
}
