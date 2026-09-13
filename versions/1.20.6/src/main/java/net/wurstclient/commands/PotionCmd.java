/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.commands;

import java.util.ArrayList;
import net.minecraft.ResourceLocationException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.wurstclient.command.CmdError;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.MathUtils;

public final class PotionCmd extends Command
{
	public PotionCmd()
	{
		super("potion", "Changes the effects of the held potion.",
			".potion add (<effect> <amplifier> <duration>)...",
			".potion set (<effect> <amplifier> <duration>)...",
			".potion remove <effect>");
	}
	
	@Override
	public void call(String[] args) throws CmdException
	{
		if(args.length == 0)
			throw new CmdSyntaxError();
		
		if(!MC.player.getAbilities().instabuild)
			throw new CmdError("仅限创造模式。");
		
		ItemStack stack = MC.player.getInventory().getSelected();
		if(!(stack.getItem() instanceof PotionItem))
			throw new CmdError("You must hold a potion in your main hand.");
		
		// remove
		if(args[0].equalsIgnoreCase("remove"))
		{
			remove(stack, args);
			return;
		}
		
		if((args.length - 1) % 3 != 0)
			throw new CmdSyntaxError();
		
		// get effects to start with
		ArrayList<MobEffectInstance> effects;
		Potion potion;
		switch(args[0].toLowerCase())
		{
			case "add":
			effects = new ArrayList<>(PotionUtils.getCustomEffects(stack));
			potion = PotionUtils.getPotion(stack);
			break;
			
			case "set":
			effects = new ArrayList<>();
			potion = Potions.EMPTY;
			break;
			
			default:
			throw new CmdSyntaxError();
		}
		
		// add new effects
		for(int i = 0; i < (args.length - 1) / 3; i++)
		{
			MobEffect effect = parseEffect(args[1 + i * 3]);
			int amplifier = parseInt(args[2 + i * 3]) - 1;
			int duration = parseInt(args[3 + i * 3]) * 20;
			
			effects.add(new MobEffectInstance(effect, duration, amplifier));
		}
		
		PotionUtils.setPotion(stack, potion);
		setCustomPotionEffects(stack, effects);
		ChatUtils.message("药水已修改。");
	}
	
	private void remove(ItemStack stack, String[] args) throws CmdSyntaxError
	{
		if(args.length != 2)
			throw new CmdSyntaxError();
		
		MobEffect targetEffect = parseEffect(args[1]);
		
		Potion oldPotion = PotionUtils.getPotion(stack);
		boolean mainPotionContainsTargetEffect = oldPotion.getEffects().stream()
			.anyMatch(effect -> effect.getEffect() == targetEffect);
		
		ArrayList<MobEffectInstance> newEffects = new ArrayList<>();
		if(mainPotionContainsTargetEffect)
			PotionUtils.getMobEffects(stack).forEach(newEffects::add);
		else
			PotionUtils.getCustomEffects(stack).forEach(newEffects::add);
		newEffects.removeIf(effect -> effect.getEffect() == targetEffect);
		
		Potion newPotion =
			mainPotionContainsTargetEffect ? Potions.EMPTY : oldPotion;
		
		PotionUtils.setPotion(stack, newPotion);
		setCustomPotionEffects(stack, newEffects);
		ChatUtils.message("效果已移除。");
	}
	
	private MobEffect parseEffect(String input) throws CmdSyntaxError
	{
		MobEffect effect;
		
		if(MathUtils.isInteger(input))
			effect = BuiltInRegistries.MOB_EFFECT.byId(Integer.parseInt(input));
		else
			try
			{
				ResourceLocation identifier = new ResourceLocation(input);
				effect = BuiltInRegistries.MOB_EFFECT.get(identifier);
				
			}catch(ResourceLocationException e)
			{
				throw new CmdSyntaxError("Invalid effect: " + input);
			}
		
		if(effect == null)
			throw new CmdSyntaxError("Invalid effect: " + input);
		
		return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect).value();
	}
	
	private void setCustomPotionEffects(ItemStack stack,
		ArrayList<MobEffectInstance> effects)
	{
		// PotionUtil doesn't remove effects when passing an empty list to it
		if(effects.isEmpty())
			stack.removeTagKey("CustomPotionEffects");
		else
			PotionUtils.setCustomEffects(stack, effects);
	}
	
	private int parseInt(String s) throws CmdSyntaxError
	{
		try
		{
			return Integer.parseInt(s);
			
		}catch(NumberFormatException e)
		{
			throw new CmdSyntaxError("Not a number: " + s);
		}
	}
}
