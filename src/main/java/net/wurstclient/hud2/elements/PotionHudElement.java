package net.wurstclient.hud2.elements;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HudElement;
import net.wurstclient.hud2.HudLayout.HudElementConfig;
import net.wurstclient.gui.visual.VisualTheme;

public final class PotionHudElement extends HudElement
{
	public PotionHudElement()
	{
		super("potions", "Potion Effects");
	}

	@Override
	public int getWidth()
	{
		Font font = WurstClient.MC.font;
		return getLines().stream().mapToInt(font::width).max().orElse(80) + 4;
	}

	@Override
	public int getHeight()
	{
		return Math.max(1, getLines().size()) * WurstClient.MC.font.lineHeight + 2;
	}

	@Override
	public void render(GuiGraphics graphics, int x, int y, float partialTicks)
	{
		Font font = WurstClient.MC.font;
		List<String> lines = getLines();
		if(lines.isEmpty())
			return;
		graphics.fill(x, y, x + getWidth(), y + getHeight(),
			VisualTheme.SURFACE_50);
		for(int index = 0; index < lines.size(); index++)
			graphics.drawString(font, lines.get(index), x + 2,
				y + 1 + index * font.lineHeight, VisualTheme.TEXT, false);
	}

	private List<String> getLines()
	{
		if(WurstClient.MC.player == null)
			return List.of();
		List<MobEffectInstance> effects = new ArrayList<>(
			WurstClient.MC.player.getActiveEffects());
		effects.sort(Comparator.comparing(
			effect -> effect.getEffect().getDisplayName().getString()));
		return effects.stream().map(this::format).toList();
	}

	private String format(MobEffectInstance effect)
	{
		String amplifier = effect.getAmplifier() > 0
			? " " + (effect.getAmplifier() + 1) : "";
		int seconds = Math.max(0, effect.getDuration() / 20);
		String duration = String.format("%d:%02d", seconds / 60, seconds % 60);
		return effect.getEffect().getDisplayName().getString() + amplifier + " "
			+ duration;
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		return new HudElementConfig(HudElementConfig.HORIZONTAL_RIGHT,
			HudElementConfig.VERTICAL_TOP, 3, 55);
	}
}
