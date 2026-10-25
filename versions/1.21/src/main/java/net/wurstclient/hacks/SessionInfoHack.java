/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.Locale;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatInputListener;
import net.wurstclient.events.DeathListener;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD overlay showing session playtime, server, join count, kills and deaths.
 *
 * <p>Simplification: Soar counted kills on Hypixel by inspecting chat packets.
 * This port listens to incoming chat through the existing chat listener and
 * uses the same English/Spanish death-message triggers. A death counter is
 * shown as well because the local API has no direct kill event.
 */
@SearchTags({"session info", "session", "playtime", "joins", "kills",
	"deaths", "server info"})
public final class SessionInfoHack extends Hack
	implements GUIRenderListener, WorldChangeListener, DeathListener,
	ChatInputListener
{
	private static final String[] KILL_TRIGGERS = {"by *", "para *",
		"fue destrozado a manos de *"};
	private static final int PADDING = 4;
	private static final int LINE_GAP = 2;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the session info overlay.", 1.0, 0.5, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the overlay.", 100, 0, 2500, 1, ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the overlay.", 200, 0, 2500, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the text.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the session info text.", Color.WHITE);

	private long startMillis;
	private int joins;
	private int kills;
	private int deaths;

	public SessionInfoHack()
	{
		super("SessionInfo");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(background);
		addSetting(bgColor);
		addSetting(textColor);
		bgColor.visibleWhen(background::isChecked);
	}

	@Override
	protected void onEnable()
	{
		startMillis = System.currentTimeMillis();
		joins = MC.level != null ? 1 : 0;
		kills = 0;
		deaths = 0;

		EVENTS.add(GUIRenderListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		EVENTS.add(DeathListener.class, this);
		EVENTS.add(ChatInputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		EVENTS.remove(DeathListener.class, this);
		EVENTS.remove(ChatInputListener.class, this);
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		if(world != null)
			joins++;
	}

	@Override
	public void onDeath()
	{
		deaths++;
	}

	@Override
	public void onReceivedMessage(ChatInputEvent event)
	{
		if(MC.player == null)
			return;

		String message = event.getComponent().getString();

		// Ignore normal player chat, which always contains a colon.
		if(message.indexOf(':') >= 0)
			return;

		String playerName = MC.player.getName().getString();
		if(playerName.isEmpty())
			return;

		String normalized = message.replace(playerName, "*");
		for(String trigger : KILL_TRIGGERS)
			if(normalized.contains(trigger))
			{
				kills++;
				return;
			}
	}

	@Override
	public void onRenderGUI(GuiGraphics context, float partialTicks)
	{
		if(MC.player == null)
			return;

		String[] lines = {"Session Info", "Time: " + getPlaytime(),
			"Server: " + getServerName(), "Joins: " + joins,
			"Kills: " + kills, "Deaths: " + deaths};

		int width = 0;
		for(String line : lines)
			width = Math.max(width, MC.font.width(line));

		int lineHeight = MC.font.lineHeight + LINE_GAP;
		int height = lines.length * lineHeight;

		var pose = context.pose();
		pose.pushPose();
		pose.translate((float)posX.getValue(), (float)posY.getValue(), 0);
		pose.scale(scale.getValueF(), scale.getValueF(), 1);

		if(background.isChecked())
			context.fill(-PADDING, -PADDING, width + PADDING,
				height + PADDING, bgColor.getColor().getRGB());

		int color = textColor.getColorI();
		for(int i = 0; i < lines.length; i++)
			context.drawString(MC.font, lines[i], 0, i * lineHeight, color, true);

		pose.popPose();
	}

	private String getPlaytime()
	{
		long duration = System.currentTimeMillis() - startMillis;
		long seconds = duration / 1000 % 60;
		long minutes = duration / (1000 * 60) % 60;
		long hours = duration / (1000 * 60 * 60) % 24;
		return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes,
			seconds);
	}

	private String getServerName()
	{
		if(MC.isSingleplayer())
			return "Singleplayer";

		ServerData server = MC.getCurrentServer();
		if(server == null)
			return "Unknown";

		return server.ip != null && !server.ip.isEmpty() ? server.ip
			: server.name;
	}
}
