package net.wurstclient.hud2;

/**
 * Ported from Soar Client v7.1.2
 * ({@code me.eldodebug.soar.management.mods.SimpleHUDMod}).
 *
 * <p>
 * Renders a single line of text in an 18px tall rounded background. Soar's
 * icon font is not available in Wurst, so the icon slot is optional and
 * currently unused by the built-in modules.
 */
public abstract class SimpleHUDMod extends HUDMod
{
	protected SimpleHUDMod(String id, String name)
	{
		super(id, name);
	}

	@Override
	protected void draw()
	{
		String text = getText();
		if(text == null)
			return;
		String icon = getIcon();
		boolean hasIcon = icon != null && !icon.isEmpty();
		float iconWidth = hasIcon ? getTextWidth(icon, 9.5F) + 4 : 0;

		float backgroundWidth = getTextWidth(text, 9) + 10 + iconWidth;
		drawBackground(backgroundWidth, 18);
		drawText(text, 5.5F + iconWidth, 5.5F, 9);
		if(hasIcon)
			drawText(icon, 5.5F, 4F, 10.4F);
		setSize(Math.round(backgroundWidth), 18);
	}

	public abstract String getText();

	public String getIcon()
	{
		return null;
	}
}
