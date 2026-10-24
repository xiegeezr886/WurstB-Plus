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

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.ArrayList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

/**
 * Simplified port of Meteor's Marker module.
 *
 * <p>
 * Markers are stored in memory only and are cleared when the world changes.
 * Right-clicking places a marker at the player's current position; the
 * original module's shape editor and disk persistence are not ported.
 */
@SearchTags({"marker", "waypoint", "beam", "mark position"})
public final class MarkerHack extends Hack
	implements UpdateListener, RenderListener, WorldChangeListener
{
	private final ColorSetting color = new ColorSetting("Color",
		"The color of the marker beams.", new Color(255, 64, 64));

	private final SliderSetting beamHeight = new SliderSetting("Beam height",
		"How tall the marker beams are.", 64, 1, 256, 1,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting maxMarkers = new SliderSetting("Max markers",
		"The maximum number of markers to keep. Older markers are removed"
			+ " first.",
		64, 1, 512, 1, ValueDisplay.INTEGER);

	private final SliderSetting fillOpacity = new SliderSetting("Fill opacity",
		"Opacity of the filled area inside the beams.", 0.25, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final SliderSetting lineOpacity = new SliderSetting("Line opacity",
		"Opacity of the beam outlines.", 0.75, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders the markers through solid blocks.", true);

	private final CheckboxSetting markOnUse = new CheckboxSetting(
		"Mark on right-click",
		"Places a marker at your position whenever you right-click.", true);

	private final ArrayList<Vec3> markers = new ArrayList<>();
	private boolean wasUseDown;

	public MarkerHack()
	{
		super("Marker");
		setCategory(Category.RENDER);
		addSetting(color);
		addSetting(beamHeight);
		addSetting(maxMarkers);
		addSetting(fillOpacity);
		addSetting(lineOpacity);
		addSetting(throughWalls);
		addSetting(markOnUse);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		wasUseDown = false;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		wasUseDown = false;
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		markers.clear();
		wasUseDown = false;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		boolean useDown = markOnUse.isChecked() && MC.gui.screen() == null
			&& MC.options.keyUse.isDown();

		if(useDown && !wasUseDown)
		{
			markers.add(MC.player.position());
			while(markers.size() > maxMarkers.getValueI())
				markers.remove(0);
		}

		wasUseDown = useDown;
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(markers.isEmpty())
			return;

		boolean depthTest = !throughWalls.isChecked();
		int fill = color.getColorI(fillOpacity.getValueF());
		int line = color.getColorI(lineOpacity.getValueF());
		double height = beamHeight.getValue();

		for(Vec3 marker : markers)
		{
			AABB beam = new AABB(marker.x - 0.25, marker.y, marker.z - 0.25,
				marker.x + 0.25, marker.y + height, marker.z + 0.25);

			if((fill >>> 24) != 0)
				RenderUtils.drawSolidBox(matrixStack, beam, fill, depthTest);
			if((line >>> 24) != 0)
				RenderUtils.drawOutlinedBox(matrixStack, beam, line,
					depthTest);
		}
	}
}
