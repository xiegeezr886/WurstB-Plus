/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.MouseScrollListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"zoom", "fov", "spyglass", "optifine zoom"})
public final class ZoomHack extends Hack
	implements UpdateListener, MouseScrollListener
{
	private final SliderSetting zoom = new SliderSetting("Zoom",
		"How far to zoom in.", 6, 1, 20, 0.5, ValueDisplay.DECIMAL);

	private final SliderSetting scrollSensitivity = new SliderSetting(
		"Scroll sensitivity",
		"How much the scroll wheel changes the zoom level. 0 disables it.", 0.5,
		0, 2, 0.05, ValueDisplay.DECIMAL);

	private final CheckboxSetting smooth = new CheckboxSetting("Smooth",
		"Smoothly transition between zoom levels.", true);

	private double currentZoom = 1;
	private boolean neoForgeRegistered;

	public ZoomHack()
	{
		super("Zoom");
		setCategory(Category.RENDER);
		addSetting(zoom);
		addSetting(scrollSensitivity);
		addSetting(smooth);
	}

	@Override
	protected void onEnable()
	{
		currentZoom = smooth.isChecked() ? 1 : zoom.getValue();

		if(!neoForgeRegistered)
		{
			NeoForge.EVENT_BUS.register(this);
			neoForgeRegistered = true;
		}

		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(MouseScrollListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(MouseScrollListener.class, this);

		NeoForge.EVENT_BUS.unregister(this);
		neoForgeRegistered = false;
		currentZoom = 1;
	}

	@Override
	public void onUpdate()
	{
		double target = zoom.getValue();

		if(smooth.isChecked())
			currentZoom += (target - currentZoom) * 0.35;
		else
			currentZoom = target;
	}

	@Override
	public void onMouseScroll(double amount)
	{
		if(MC.screen != null || scrollSensitivity.getValue() <= 0)
			return;

		double newZoom = zoom.getValue()
			+ amount * scrollSensitivity.getValue() * zoom.getValue() * 0.25;
		zoom.setValue(Math.max(1, Math.min(20, newZoom)));
	}

	/**
	 * NeoForge fires this for both the world and the hand projection, so
	 * dividing the FOV here zooms both without touching the FOV option. The
	 * option itself only accepts 30-110, which would cap the zoom.
	 */
	@SubscribeEvent
	public void onComputeFov(ViewportEvent.ComputeFov event)
	{
		if(!isEnabled() || currentZoom <= 1.0001)
			return;

		event.setFOV((float)(event.getFOV() / currentZoom));
	}
}
