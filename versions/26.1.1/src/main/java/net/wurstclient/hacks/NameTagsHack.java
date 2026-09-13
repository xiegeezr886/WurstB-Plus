/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.util.NameTagRenderState;

@SearchTags({"name tags"})
public final class NameTagsHack extends Hack
	implements UpdateListener, WorldChangeListener
{
	private final SliderSetting scale =
		new SliderSetting("Scale", "How large the nametags should be.", 1, 0.05,
			5, 0.05, SliderSetting.ValueDisplay.PERCENTAGE);
	
	private final CheckboxSetting unlimitedRange =
		new CheckboxSetting("Unlimited range",
			"Removes the 64 block distance limit for nametags.", true);
	
	private final CheckboxSetting seeThrough = new CheckboxSetting(
		"See-through mode",
		"Renders nametags on the see-through text layer. This makes them"
			+ " easier to read behind walls, but causes some graphical glitches"
			+ " with water and other transparent things.",
		false);
	
	private final CheckboxSetting forceMobNametags = new CheckboxSetting(
		"Always show named mobs", "Displays the nametags of named mobs even"
			+ " when you are not looking directly at them.",
		true);
	
	private final CheckboxSetting forcePlayerNametags =
		new CheckboxSetting("Always show player names",
			"Displays your own nametag as well as any player names that would"
				+ " normally be disabled by scoreboard team settings.",
			false);

	private final CheckboxSetting showHealth = new CheckboxSetting(
		"Show health", "Adds snapshotted health to player nametags.", true);

	private final CheckboxSetting showPing = new CheckboxSetting("Show ping",
		"Adds snapshotted network latency to player nametags.", true);

	private final CheckboxSetting showEntityId = new CheckboxSetting(
		"Show entity ID", "Adds the numeric entity ID to player nametags.",
		false);

	private final CheckboxSetting showEquipment = new CheckboxSetting(
		"Show equipment", "Renders snapshotted player equipment above names.",
		true);

	private final CheckboxSetting showDurability = new CheckboxSetting(
		"Show durability", "Shows equipment durability percentages.", true)
			.visibleWhen(showEquipment::isChecked);

	private Map<Integer, NameTagRenderState> renderStates = Map.of();
	
	public NameTagsHack()
	{
		super("NameTags");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(unlimitedRange);
		addSetting(seeThrough);
		addSetting(forceMobNametags);
		addSetting(forcePlayerNametags);
		addSetting(showHealth);
		addSetting(showPing);
		addSetting(showEntityId);
		addSetting(showEquipment);
		addSetting(showDurability);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		onUpdate();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		renderStates = Map.of();
	}

	@Override
	public void onUpdate()
	{
		if(MC.level == null)
		{
			renderStates = Map.of();
			return;
		}

		HashMap<Integer, NameTagRenderState> next = new HashMap<>();
		for(AbstractClientPlayer player : WURST.getEntitySnapshotManager()
			.getCurrent().players())
			next.put(player.getId(), createRenderState(player));
		renderStates = Map.copyOf(next);
	}

	private NameTagRenderState createRenderState(AbstractClientPlayer player)
	{
		MutableComponent label = Component.empty().append(player.getDisplayName());
		if(showHealth.isChecked())
		{
			float health = player.getHealth() + player.getAbsorptionAmount();
			ChatFormatting color = health <= 5 ? ChatFormatting.DARK_RED
				: health <= 10 ? ChatFormatting.GOLD
					: health <= 15 ? ChatFormatting.YELLOW
						: ChatFormatting.GREEN;
			label.append(Component.literal(" " + Math.round(health))
				.withStyle(color));
		}
		if(showPing.isChecked())
		{
			PlayerInfo info = MC.getConnection() == null ? null
				: MC.getConnection().getPlayerInfo(player.getUUID());
			int latency = info == null ? -1 : info.getLatency();
			label.append(Component.literal(" [" + latency + "ms]")
				.withStyle(latency < 0 ? ChatFormatting.GRAY
					: latency < 100 ? ChatFormatting.GREEN
						: latency < 200 ? ChatFormatting.YELLOW
							: ChatFormatting.RED));
		}
		if(showEntityId.isChecked())
			label.append(Component.literal(" #" + player.getId())
				.withStyle(ChatFormatting.GRAY));

		ArrayList<ItemStack> equipment = new ArrayList<>();
		if(!player.getMainHandItem().isEmpty())
			equipment.add(player.getMainHandItem());
		for(var slot : new net.minecraft.world.entity.EquipmentSlot[]{
			net.minecraft.world.entity.EquipmentSlot.FEET,
			net.minecraft.world.entity.EquipmentSlot.LEGS,
			net.minecraft.world.entity.EquipmentSlot.CHEST,
			net.minecraft.world.entity.EquipmentSlot.HEAD})
		{
			ItemStack stack = player.getItemBySlot(slot);
			if(!stack.isEmpty())
				equipment.add(stack);
		}
		if(!player.getOffhandItem().isEmpty())
			equipment.add(player.getOffhandItem());

		List<Integer> durability = equipment.stream().map(stack ->
			stack.isDamageableItem()
				? Math.max(0, (stack.getMaxDamage() - stack.getDamageValue())
					* 100 / Math.max(1, stack.getMaxDamage()))
				: -1).toList();
		return new NameTagRenderState(label, equipment, durability);
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		renderStates = Map.of();
	}

	public NameTagRenderState getRenderState(Entity entity)
	{
		return isEnabled() ? renderStates.get(entity.getId()) : null;
	}

	public boolean isHealthShown()
	{
		return isEnabled() && showHealth.isChecked();
	}

	public boolean shouldShowEquipment()
	{
		return isEnabled() && showEquipment.isChecked();
	}

	public boolean shouldShowDurability()
	{
		return shouldShowEquipment() && showDurability.isChecked();
	}
	
	public float getScale()
	{
		return isEnabled() ? scale.getValueF() : 1;
	}
	
	public boolean isUnlimitedRange()
	{
		return isEnabled() && unlimitedRange.isChecked();
	}
	
	public boolean isSeeThrough()
	{
		return isEnabled() && seeThrough.isChecked();
	}
	
	public boolean shouldForceMobNametags()
	{
		return isEnabled() && forceMobNametags.isChecked();
	}
	
	public boolean shouldForcePlayerNametags()
	{
		return isEnabled() && forcePlayerNametags.isChecked();
	}
	
	// See EntityRendererMixin.wurstRenderLabelIfPresent(),
	// LivingEntityRendererMixin, MobEntityRendererMixin
}
