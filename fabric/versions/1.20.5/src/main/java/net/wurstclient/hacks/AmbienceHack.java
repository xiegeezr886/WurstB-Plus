/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import java.awt.Color;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.minecraft.client.gui.GuiGraphics;

@SearchTags({"ambience", "world color", "custom time", "fullbright",
	"full bright"})
public final class AmbienceHack extends Hack
	implements GUIRenderListener, UpdateListener, PacketInputListener
{
	private final CheckboxSetting filterEnabled = new CheckboxSetting(
		"Filter", "Overlays a translucent color on the screen.", false);

	private final ColorSetting filterColor =
		new ColorSetting("Filter color", "The color of the screen overlay.",
			new Color(255, 255, 255, 20)).visibleWhen(filterEnabled::isChecked);

	private final CheckboxSetting customTime = new CheckboxSetting("Custom time",
		"Overrides the client-side time of day.", false);

	private final SliderSetting time = new SliderSetting("Time",
		"The time of day to display on the client.", 0, 0, 24000, 100,
		ValueDisplay.INTEGER).visibleWhen(customTime::isChecked);

	private final CheckboxSetting fullBright = new CheckboxSetting("Full bright",
		"Raises the client-side gamma to make the world fully lit.", false);

	private Double oldGamma;
	private long oldGameTime;
	private long oldDayTime;
	private boolean hasOldTime;

	public AmbienceHack()
	{
		super("Ambience");
		setCategory(Category.RENDER);
		addSetting(filterEnabled);
		addSetting(filterColor);
		addSetting(customTime);
		addSetting(time);
		addSetting(fullBright);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(GUIRenderListener.class, this);
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		oldGamma = MC.options.gamma().get();
		rememberTime(MC.level);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);

		if(oldGamma != null)
			MC.options.gamma().set(oldGamma);
		oldGamma = null;

		ClientLevel level = MC.level;
		if(level != null && hasOldTime)
		{
			level.getLevelData().setGameTime(oldGameTime);
			level.getLevelData().setDayTime(oldDayTime);
		}
		hasOldTime = false;
	}

	@Override
	public void onUpdate()
	{
		ClientLevel level = MC.level;
		if(level == null)
			return;

		if(customTime.isChecked())
		{
			long value = time.getValueI();
			level.getLevelData().setGameTime(value);
			level.getLevelData().setDayTime(value);
		}

		if(fullBright.isChecked())
			MC.options.gamma().set(1.0);
		else if(oldGamma != null)
			MC.options.gamma().set(oldGamma);
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(!customTime.isChecked()
			|| !(event.getPacket() instanceof ClientboundSetTimePacket))
			return;

		event.cancel();
	}

	@Override
	public void onRenderGUI(GuiGraphics context, float partialTicks)
	{
		if(!filterEnabled.isChecked())
			return;

		context.fill(0, 0, MC.getWindow().getGuiScaledWidth(),
			MC.getWindow().getGuiScaledHeight(),
			filterColor.getColor().getRGB());
	}

	private void rememberTime(ClientLevel level)
	{
		if(level == null)
		{
			hasOldTime = false;
			return;
		}

		oldGameTime = level.getLevelData().getGameTime();
		oldDayTime = level.getLevelData().getDayTime();
		hasOldTime = true;
	}
}
