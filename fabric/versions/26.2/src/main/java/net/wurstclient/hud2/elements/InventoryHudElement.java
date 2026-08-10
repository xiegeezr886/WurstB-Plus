package net.wurstclient.hud2.elements;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.hud2.HudElement;
import net.wurstclient.hud2.HudLayout.HudElementConfig;
import net.wurstclient.util.RenderUtils;

public final class InventoryHudElement extends HudElement
{
	static final int COLUMNS = 9;
	static final int ROWS = 3;
	static final int FIRST_INVENTORY_SLOT = 9;
	static final int WIDTH = 164;
	static final int HEIGHT = 68;
	private static final int SLOT_SIZE = 16;
	private static final int SLOT_STEP = 17;
	private static final int SLOT_START_X = 6;
	private static final int SLOT_START_Y = 14;
	private static final int CARD_FILL = 0xAD050505;
	private static final int CARD_OUTLINE = 0x10FFFFFF;
	private static final int SLOT_FILL = 0x78101010;
	private static final int SLOT_OUTLINE = 0x0FFFFFFF;
	private static final int TEXT = 0xFFF2F4F7;
	private static final WurstClient WURST = WurstClient.INSTANCE;

	public InventoryHudElement()
	{
		super("inventory", "背包");
	}

	@Override
	public int getWidth()
	{
		return WIDTH;
	}

	@Override
	public int getHeight()
	{
		return HEIGHT;
	}

	@Override
	public boolean renderEditorPreview()
	{
		return true;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int x, int y, float partialTicks)
	{
		WURST.getGui().updateColors();
		int accent = WURST.getGui().getTheme().accent(1);
		FlatRenderer.fillRoundedRect(graphics, x, y, x + WIDTH, y + HEIGHT,
			4, CARD_FILL);
		FlatRenderer.drawRoundedOutline(graphics, x, y, x + WIDTH, y + HEIGHT,
			4, CARD_OUTLINE);
		FlatRenderer.fillRoundedRect(graphics, x + 1, y + 2, x + 3,
			y + HEIGHT - 2, 1, accent);
		graphics.text(WurstClient.MC.font, getName(), x + 7, y + 3, TEXT,
			false);

		for(int row = 0; row < ROWS; row++)
			for(int column = 0; column < COLUMNS; column++)
			{
				int slotX = x + slotX(column);
				int slotY = y + slotY(row);
				FlatRenderer.fillRoundedRect(graphics, slotX, slotY,
					slotX + SLOT_SIZE, slotY + SLOT_SIZE, 2, SLOT_FILL);
				FlatRenderer.drawRoundedOutline(graphics, slotX, slotY,
					slotX + SLOT_SIZE, slotY + SLOT_SIZE, 2, SLOT_OUTLINE);
				ItemStack stack = getStack(row, column);
				if(stack.isEmpty())
					continue;
				RenderUtils.drawItem(graphics, stack, slotX, slotY, false);
			}
	}

	private ItemStack getStack(int row, int column)
	{
		if(WurstClient.MC.player == null)
			return ItemStack.EMPTY;
		return WurstClient.MC.player.getInventory().getItem(slotIndex(row,
			column));
	}

	static int slotIndex(int row, int column)
	{
		return FIRST_INVENTORY_SLOT + row * COLUMNS + column;
	}

	static int slotX(int column)
	{
		return SLOT_START_X + column * SLOT_STEP;
	}

	static int slotY(int row)
	{
		return SLOT_START_Y + row * SLOT_STEP;
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		HudElementConfig config = new HudElementConfig(
			HudElementConfig.HORIZONTAL_RIGHT,
			HudElementConfig.VERTICAL_BOTTOM, 8, 50);
		config.setEnabled(false);
		return config;
	}
}
