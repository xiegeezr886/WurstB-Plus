package net.wurstclient.hud2.elements;

import net.minecraft.world.item.ItemStack;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.util.RenderUtils;

public final class InventoryHudElement extends HUDMod
{
	private static final int COLUMNS = 9;
	private static final int ROWS = 3;
	private static final int FIRST_INVENTORY_SLOT = 9;
	private static final int WIDTH = 188;
	private static final int HEIGHT = 82;
	private static final int SLOT_STEP = 20;
	private static final int SLOT_START_X = 6;
	private static final int SLOT_START_Y = 22;

	public InventoryHudElement()
	{
		super("inventory", "Backpack");
		setDefaultPosition(600, 200);
		setDefaultEnabled(false);
	}

	@Override
	protected void draw()
	{
		drawBackground(WIDTH, HEIGHT);
		drawText("Inventory", 5.5F, 6F, 10.5F);
		drawRect(0, 17.5F, WIDTH, 0.5F, getFontColor(50));

		graphics.pose().pushMatrix();
		graphics.pose().translate(getX(), getY());
		graphics.pose().scale(getScale(), getScale());
		for(int row = 0; row < ROWS; row++)
			for(int column = 0; column < COLUMNS; column++)
			{
				ItemStack stack = getStack(row, column);
				if(stack.isEmpty())
					continue;
				int slotX = SLOT_START_X + column * SLOT_STEP;
				int slotY = SLOT_START_Y + row * SLOT_STEP;
				RenderUtils.drawItem(graphics, stack, slotX, slotY, false);
			}
		graphics.pose().popMatrix();

		setSize(WIDTH, HEIGHT);
	}

	private ItemStack getStack(int row, int column)
	{
		if(WurstClient.MC.player == null)
			return ItemStack.EMPTY;
		return WurstClient.MC.player.getInventory().getItem(FIRST_INVENTORY_SLOT
			+ row * COLUMNS + column);
	}
}
