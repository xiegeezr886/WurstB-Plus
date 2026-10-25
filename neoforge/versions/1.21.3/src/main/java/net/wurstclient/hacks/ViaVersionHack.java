/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.client.multiplayer.ServerData;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Displays the connected server's advertised version and protocol.
 *
 * <p>Soar bundled ViaVersion and translated packets. That backend is not
 * available in this multi-loader build, so this port deliberately exposes the
 * server information instead of pretending to translate protocols.
 */
@SearchTags({"viaversion", "protocol", "server version", "version info"})
public final class ViaVersionHack extends Hack implements GUIRenderListener
{
	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the server info panel.", 10, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the server info panel.", 10, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting showProtocol = new CheckboxSetting(
		"Show protocol", "Shows the server protocol number.", true);

	private final CheckboxSetting showVersion = new CheckboxSetting(
		"Show version", "Shows the server version string.", true);

	public ViaVersionHack()
	{
		super("ViaVersion");
		setCategory(Category.OTHER);
		addSetting(posX);
		addSetting(posY);
		addSetting(showProtocol);
		addSetting(showVersion);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(GUIRenderListener.class, this);
		ChatUtils.warning(
			"ViaVersion: protocol translation is not bundled; showing server"
				+ " information only.");
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
	}

	@Override
	public void onRenderGUI(GuiGraphics context, float partialTicks)
	{
		if(uiHidden())
			return;

		ServerData server = MC.getCurrentServer();
		String name = server == null ? "Singleplayer" : server.name;
		String address = server == null ? "-" : server.ip;
		String version = server == null || server.version == null ? "-"
			: server.version.getString();
		int protocol = server == null ? -1 : server.protocol;

		int left = (int)posX.getValue();
		int top = (int)posY.getValue();
		context.fill(left, top, left + 190, top + 56, 0x90000000);
		text(context, "Server: " + name, left + 5, top + 5, 0xFFFFFFFF, true);
		text(context, "Address: " + address, left + 5, top + 16, 0xFFB0B0B0,
			true);
		if(showVersion.isChecked())
			text(context, "Version: " + version, left + 5, top + 27,
				0xFFB0B0B0, true);
		if(showProtocol.isChecked())
			text(context, "Protocol: " + protocol, left + 5, top + 38,
				0xFFB0B0B0, true);
	}

	private int argb(java.awt.Color color, int alpha)
	{
		int a = Math.max(0, Math.min(255, alpha));
		return a << 24 | color.getRGB() & 0xFFFFFF;
	}

	private boolean uiHidden()
	{
		try
		{
			Object gui = MC.getClass().getField("gui").get(MC);
			if(gui.getClass().getMethod("screen").invoke(gui) != null)
				return true;
		}catch(Exception ignored)
		{}

		try
		{
			return Boolean.TRUE.equals(MC.options.getClass()
				.getField("hideGui").get(MC.options));
		}catch(Exception ignored)
		{
			return false;
		}
	}

	private void text(GuiGraphics context, String text, int x, int y,
		int color, boolean shadow)
	{
		context.drawString(MC.font, text, x, y, color, shadow);
	}

	private void centeredText(GuiGraphics context, String text, int x,
		int y, int color)
	{
		context.drawCenteredString(MC.font, text, x, y, color);
	}

}
