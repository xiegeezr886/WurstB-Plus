package net.wurstclient.hud2.island;

/** Last rendered container geometry, in Minecraft GUI coordinates. */
public record IslandContainerView(Object menu, float x, float y, float width,
	float height, float scale, int rows, long renderedAt)
{
	public boolean accepts(Object currentMenu, long now)
	{
		return menu != null && menu == currentMenu && rows >= 1 && rows <= 6
			&& scale > 0 && now >= renderedAt && now - renderedAt <= 250;
	}

	public boolean contains(double mouseX, double mouseY)
	{
		return mouseX >= x && mouseX < x + width
			&& mouseY >= y && mouseY < y + height;
	}

	public int slotAt(double mouseX, double mouseY)
	{
		if(!contains(mouseX, mouseY) || scale <= 0) return -1;
		double localX = (mouseX - x) / scale, localY = (mouseY - y) / scale;
		// Match both the centered grid and the animated shell's scissor.
		if(localX < 2 || localX >= width / scale - 2
			|| localY < 1 || localY >= height / scale - 1) return -1;
		double gridX = localX - ((width / scale - 190) / 2 + 5);
		double gridY = localY - 6;
		if(gridX < 0 || gridX >= 180 || gridY < 0 || gridY >= rows * 20) return -1;
		return (int)(gridY / 20) * 9 + (int)(gridX / 20);
	}
}
