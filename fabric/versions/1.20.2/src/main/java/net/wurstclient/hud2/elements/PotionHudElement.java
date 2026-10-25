package net.wurstclient.hud2.elements;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.world.effect.MobEffectInstance;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HUDMod;

public final class PotionHudElement extends HUDMod
{
	public PotionHudElement()
	{
		super("potions", "Potion Effects");
		setDefaultPosition(620, 55);
	}

	@Override
	protected void draw()
	{
		List<String> lines = getLines();
		if(lines.isEmpty())
			return;

		int contentWidth = lines.stream()
			.mapToInt(line -> WurstClient.MC.font.width(line)).max().orElse(80);
		int width = contentWidth + 11;
		int height = lines.size() * 10 + 8;
		drawBackground(width, height);
		for(int index = 0; index < lines.size(); index++)
			drawText(lines.get(index), 5.5F, 4.5F + index * 10, 9);
		setSize(width, height);
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
		return effect.getEffect().getDisplayName().getString()
			+ amplifier + " " + duration;
	}
}
