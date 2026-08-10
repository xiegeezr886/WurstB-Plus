package net.wurstclient.hud2.elements;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HudElement;
import net.wurstclient.hud2.HudLayout.HudElementConfig;
import net.wurstclient.hud2.render.RiseFrostedGlass;
import net.wurstclient.hud2.render.RiseHudFont;
import net.wurstclient.util.ScreenRegistry;

/** Rise 6.1.30 scoreboard layout, adapted to the 1.20.1 score API. */
public final class ScoreboardHudElement extends HudElement
{
	public static final String ID = "scoreboard";
	private static final int PADDING = 3;
	private static final int FONT_HEIGHT = 9;
	private static final int RADIUS = 10;
	private static final int MAX_ENTRIES = 15;

	public ScoreboardHudElement()
	{
		super(ID, "计分板");
	}

	@Override
	public int getWidth()
	{
		Snapshot snapshot = currentSnapshot();
		Font font = WurstClient.MC.font;
		int contentWidth = RiseHudFont.width(font, snapshot.title());
		for(Entry entry : snapshot.entries())
			contentWidth = Math.max(contentWidth,
				RiseHudFont.width(font, entry.name()));
		return panelWidth(contentWidth);
	}

	@Override
	public int getHeight()
	{
		return panelHeightForEntries(currentSnapshot().entries().size());
	}

	@Override
	public boolean renderEditorPreview()
	{
		return true;
	}

	@Override
	public void render(GuiGraphics graphics, int x, int y, float partialTicks)
	{
		Snapshot snapshot = currentSnapshot();
		if(snapshot.entries().isEmpty() && !ScreenRegistry.HUD_EDITOR.isOpen())
			return;

		Font font = WurstClient.MC.font;
		int width = getWidth();
		int maxTextWidth = width - PADDING * 4;
		int height = panelHeightForEntries(snapshot.entries().size());
		RiseFrostedGlass.draw(graphics, x, y, x + width, y + height, RADIUS,
			1, 0x78000000);

		int textColor = 0xFFFFFFFF;
		RiseHudFont.drawCentered(graphics, font, snapshot.title(),
			x + PADDING * 2 + maxTextWidth / 2, y + PADDING + 1, textColor);

		int rowY = y + PADDING + 1 + FONT_HEIGHT;
		for(Entry entry : snapshot.entries())
		{
			RiseHudFont.draw(graphics, font, entry.name(), x + PADDING * 2,
				rowY, textColor, false);
			rowY += FONT_HEIGHT;
		}
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		return new HudElementConfig(HudElementConfig.HORIZONTAL_RIGHT,
			HudElementConfig.VERTICAL_CENTER, 6, 0);
	}

	static int panelWidth(int contentWidth)
	{
		return Math.max(1, contentWidth + 2 + PADDING * 4);
	}

	static int panelHeightForEntries(int entryCount)
	{
		return FONT_HEIGHT * Math.max(0, entryCount) + PADDING + FONT_HEIGHT
			+ PADDING;
	}

	private Snapshot currentSnapshot()
	{
		Objective objective = getSidebarObjective();
		if(objective == null)
		{
			if(ScreenRegistry.HUD_EDITOR.isOpen())
				return previewSnapshot();
			return new Snapshot(Component.empty(), List.of());
		}

		Scoreboard scoreboard = objective.getScoreboard();
		Collection<Score> rawScores = scoreboard.getPlayerScores(objective);
		List<Score> scores = rawScores.stream()
			.filter(score -> score.getOwner() != null
				&& !score.getOwner().startsWith("#"))
			.toList();
		if(scores.size() > MAX_ENTRIES)
			scores = scores.subList(scores.size() - MAX_ENTRIES, scores.size());

		List<Entry> entries = new ArrayList<>(scores.size());
		for(Score score : scores)
		{
			PlayerTeam team = scoreboard.getPlayersTeam(score.getOwner());
			Component name = PlayerTeam.formatNameForTeam(team,
				Component.literal(score.getOwner()));
			entries.add(new Entry(name));
		}
		return new Snapshot(objective.getDisplayName(), List.copyOf(entries));
	}

	private Objective getSidebarObjective()
	{
		if(WurstClient.MC.level == null || WurstClient.MC.player == null)
			return null;

		Scoreboard scoreboard = WurstClient.MC.level.getScoreboard();
		PlayerTeam team = scoreboard
			.getPlayersTeam(WurstClient.MC.player.getScoreboardName());
		if(team != null && team.getColor() != ChatFormatting.RESET)
		{
			int colorId = team.getColor().getId();
			if(colorId >= 0)
			{
				Objective teamObjective = scoreboard
					.getDisplayObjective(3 + colorId);
				if(teamObjective != null)
					return teamObjective;
			}
		}
		return scoreboard.getDisplayObjective(1);
	}

	private static Snapshot previewSnapshot()
	{
		return new Snapshot(Component.literal("RISE SCOREBOARD"), List.of(
			new Entry(Component.literal("Player  WurstB+")),
			new Entry(Component.literal("Kills   7")),
			new Entry(Component.literal("Ping    32 ms"))));
	}

	private record Entry(Component name)
	{}

	private record Snapshot(Component title, List<Entry> entries)
	{}
}
