/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.ArrayList;
import net.minecraft.world.entity.item.ItemEntity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EspBoxSizeSetting;
import net.wurstclient.settings.EspStyleSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EntityEspRenderer;

@SearchTags({"item esp", "ItemTracers", "item tracers"})
public final class ItemEspHack extends Hack implements UpdateListener,
	CameraTransformViewBobbingListener, RenderListener
{
	private final EspStyleSetting style = new EspStyleSetting();
	
	private final EspBoxSizeSetting boxSize = new EspBoxSizeSetting(
		"\u00a7lAccurate\u00a7r mode shows the exact hitbox of each item.\n"
			+ "\u00a7lFancy\u00a7r mode shows larger boxes that look better.");
	
	private final ColorSetting color = new ColorSetting("Color",
		"Items will be highlighted in this color.", Color.YELLOW);

	private final SliderSetting maxDistance = new SliderSetting("Max distance",
		"Items farther away than this are not rendered.", 256, 16, 512, 8,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting fillOpacity = new SliderSetting("Fill opacity",
		"Opacity of the filled area inside item boxes.", 0.18, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE).visibleWhen(style::hasBoxes);

	private final SliderSetting lineOpacity = new SliderSetting("Line opacity",
		"Opacity of box outlines and tracer lines.", 0.75, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final SliderSetting nearFadeDistance = new SliderSetting(
		"Near fade distance",
		"Smoothly fades ESP when an item is close to the camera.", 3, 0, 12,
		0.5, ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders items even when blocks are in the way.", true);
	
	private final ArrayList<ItemEntity> items = new ArrayList<>();
	
	public ItemEspHack()
	{
		super("ItemESP");
		setCategory(Category.RENDER);
		addSetting(style);
		addSetting(boxSize);
		addSetting(color);
		addSetting(maxDistance);
		addSetting(fillOpacity);
		addSetting(lineOpacity);
		addSetting(nearFadeDistance);
		addSetting(throughWalls);
		boxSize.visibleWhen(style::hasBoxes);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		items.clear();
	}
	
	@Override
	public void onUpdate()
	{
		items.clear();
		double maxDistanceSq = maxDistance.getValueSq();
		for(ItemEntity item : WURST.getEntitySnapshotManager().getCurrent()
			.items())
			if(!item.isRemoved()
				&& MC.player.distanceToSqr(item) <= maxDistanceSq)
				items.add(item);
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		if(style.hasLines())
			event.cancel();
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		EntityEspRenderer.render(matrixStack, partialTicks, items, style,
			boxSize.getExtraSize(), fillOpacity.getValue(),
			lineOpacity.getValue(), nearFadeDistance.getValue(),
			!throughWalls.isChecked(),
			entity -> color.getColorI());
	}
}
