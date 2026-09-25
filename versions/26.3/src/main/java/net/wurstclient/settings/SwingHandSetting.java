/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings;

import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.Hack;
import net.wurstclient.util.text.WText;

public final class SwingHandSetting
	extends EnumSetting<SwingHandSetting.SwingHand>
{
	private static final Minecraft MC = WurstClient.MC;
	private static final WText FULL_DESCRIPTION_SUFFIX =
		buildDescriptionSuffix(true);
	private static final WText REDUCED_DESCRIPTION_SUFFIX =
		buildDescriptionSuffix(false);
	
	private SwingHandSetting(String name, WText description, SwingHand[] values,
		SwingHand selected)
	{
		super(name, description, values, selected);
	}
	
	public SwingHandSetting(WText description, SwingHand selected)
	{
		this("Swing hand", description.append(FULL_DESCRIPTION_SUFFIX),
			SwingHand.values(), selected);
	}
	
	public SwingHandSetting(Hack hack, SwingHand selected)
	{
		this(hackDescription(hack), selected);
	}
	
	public static SwingHandSetting withoutOffOption(WText description,
		SwingHand selected)
	{
		SwingHand[] values = {SwingHand.SERVER, SwingHand.CLIENT};
		return new SwingHandSetting("Swing hand",
			description.append(REDUCED_DESCRIPTION_SUFFIX), values, selected);
	}
	
	public static SwingHandSetting withoutOffOption(Hack hack,
		SwingHand selected)
	{
		return withoutOffOption(hackDescription(hack), selected);
	}
	
	public static WText genericMiningDescription(Hack hack)
	{
		return WText.translated(
			"description.wurst.setting.generic.swing_hand_mining",
			hack.getName());
	}
	
	public static WText genericCombatDescription(Hack hack)
	{
		return WText.translated(
			"description.wurst.setting.generic.swing_hand_combat",
			hack.getName());
	}
	
	private static WText hackDescription(Hack hack)
	{
		return WText.translated("description.wurst.setting."
			+ hack.getName().toLowerCase() + ".swing_hand");
	}
	
	public void swing(InteractionHand hand)
	{
		getSelected().swing(hand);
	}
	
	public void swing(InteractionHand hand, SwingAnimation animation)
	{
		getSelected().swing(hand, animation);
	}
	
	private static WText buildDescriptionSuffix(boolean includeOff)
	{
		WText text = WText.literal("\n\n");
		SwingHand[] values = includeOff ? SwingHand.values()
			: new SwingHand[]{SwingHand.SERVER, SwingHand.CLIENT};
		
		for(SwingHand value : values)
			text.append("\u00a7l" + value.name + "\u00a7r - ")
				.append(value.description).append("\n\n");
		
		return text;
	}
	
	/**
	 * How to swing the hand when interacting.
	 *
	 * <p>26.3 removed {@code ServerboundSwingPacket}, so a client can no longer
	 * send a swing to the server on its own, and {@code Player.swing(hand)} is
	 * gone too. The only swing left is
	 * {@code LivingEntity.swing(hand, animation, sendToSwingingEntity)}, whose
	 * {@code ServerLevel} branch is what broadcasts
	 * {@code ClientboundSwingAnimationPacket} - on a client it starts the local
	 * animation and nothing else.
	 *
	 * <p>That flips the meaning of this setting: "Server-side" used to mean "notify
	 * the server instead of animating locally", but notifying is no longer possible,
	 * so the only thing left for it to mean is "don't animate locally". It is
	 * therefore a no-op, and so is {@link #OFF} - the two are equivalent now.
	 * Upstream Wurst resolved this by deleting its {@code OFF} value; this fork
	 * keeps both so existing configs keep loading, and the translation says so.
	 */
	public enum SwingHand
	{
		OFF("Off", (hand, animation) -> {}),
		
		SERVER("Server-side", (hand, animation) -> {}),
		
		CLIENT("Client-side",
			(hand, animation) -> MC.player.swing(hand, animation, false));
		
		private static final String TRANSLATION_KEY_PREFIX =
			"description.wurst.setting.generic.swing_hand.";
		
		private final String name;
		private final WText description;
		private final BiConsumer<InteractionHand, SwingAnimation> swing;
		
		private SwingHand(String name,
			BiConsumer<InteractionHand, SwingAnimation> swing)
		{
			this.name = name;
			description =
				WText.translated(TRANSLATION_KEY_PREFIX + name().toLowerCase());
			this.swing = swing;
		}
		
		public void swing(InteractionHand hand)
		{
			if(this != CLIENT)
				return;
			
			SwingAnimation animation =
				MC.player.getItemInHand(hand).getInteractAnimation();
			swing.accept(hand, animation);
		}
		
		public void swing(InteractionHand hand, SwingAnimation animation)
		{
			swing.accept(hand, animation);
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
