/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.RegistryIdFilter;

@SearchTags({"potion saver"})
public final class PotionSaverHack extends Hack implements PacketOutputListener
{
	private final CheckboxSetting allEffects = new CheckboxSetting("All effects",
		"Preserve all local effects instead of the selected list.", true);
	private final TextFieldSetting effects = new TextFieldSetting("Effects",
		"Effect registry IDs to preserve when All effects is disabled.",
		"strength,absorption,resistance,fire_resistance,speed,haste,regeneration,"
			+ "water_breathing,saturation,luck,slow_falling,dolphins_grace,"
			+ "conduit_power,hero_of_the_village");
	private final CheckboxSetting onlyWhenStationary = new CheckboxSetting(
		"Only when stationary", "Only freeze local effect timers while stationary.", true);
	private final CheckboxSetting suppressMovement = new CheckboxSetting(
		"Suppress movement packets", "Also suppress idle movement packets while grounded.", true);
	private final RegistryIdFilter effectFilter = new RegistryIdFilter();

	public PotionSaverHack()
	{
		super("PotionSaver");
		setCategory(Category.OTHER);
		addSetting(allEffects);
		addSetting(effects);
		addSetting(onlyWhenStationary);
		addSetting(suppressMovement);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(PacketOutputListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketOutputListener.class, this);
	}
	
	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(!suppressMovement.isChecked() || !isFrozen())
			return;
		
		if(event.getPacket() instanceof ServerboundMovePlayerPacket)
			event.cancel();
	}
	
	public boolean isFrozen()
	{
		return isEnabled() && MC.player != null
			&& MC.player.onGround() && stationary()
			&& MC.player.getActiveEffectsMap().values().stream().anyMatch(this::shouldFreeze);
	}

	public boolean shouldFreeze(MobEffectInstance instance)
	{
		if(!isEnabled() || MC.player == null || instance == null
			|| onlyWhenStationary.isChecked() && !stationary())
			return false;
		// MobEffectInstance.equals compares effect data, so use identity here.
		if(MC.player.getActiveEffectsMap().values().stream().noneMatch(active -> active == instance))
			return false;
		if(allEffects.isChecked())
			return true;
		var id = BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect());
		return id != null && effectFilter.contains(effects.getValue(), id.toString());
	}

	private boolean stationary()
	{
		return MC.player.zza == 0 && MC.player.xxa == 0
			&& MC.player.getDeltaMovement().x == 0 && MC.player.getDeltaMovement().z == 0;
	}
}
