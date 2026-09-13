package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.network.chat.Component;

@Mixin(KeyBindsList.KeyEntry.class)
public interface KeyEntryAccessor
{
	@Accessor("name")
	Component getName();
}
