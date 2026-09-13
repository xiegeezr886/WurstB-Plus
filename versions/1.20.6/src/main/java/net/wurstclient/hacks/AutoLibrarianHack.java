/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.autolibrarian.BookOffer;
import net.wurstclient.hacks.autolibrarian.UpdateBooksSetting;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.BookOffersSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.FacingSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.*;
import net.wurstclient.util.BlockBreaker.BlockBreakingParams;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;

@SearchTags({"auto librarian", "AutoVillager", "auto villager",
	"VillagerTrainer", "villager trainer", "LibrarianTrainer",
	"librarian trainer", "AutoHmmm", "auto hmmm"})
public final class AutoLibrarianHack extends Hack
	implements UpdateListener, RenderListener
{
	private final BookOffersSetting wantedBooks = new BookOffersSetting(
		"Wanted books",
		"A list of enchanted books that you want your villagers to sell.\n\n"
			+ "AutoLibrarian will stop training the current villager"
			+ " once it has learned to sell one of these books.\n\n"
			+ "You can also set a maximum price for each book, in case you"
			+ " already have a villager selling it but you want it for a"
			+ " cheaper price.",
		"minecraft:depth_strider", "minecraft:efficiency",
		"minecraft:feather_falling", "minecraft:fortune", "minecraft:looting",
		"minecraft:mending", "minecraft:protection", "minecraft:respiration",
		"minecraft:sharpness", "minecraft:silk_touch", "minecraft:unbreaking");
	
	private final CheckboxSetting lockInTrade = new CheckboxSetting(
		"Lock in trade",
		"Automatically buys something from the villager once it has learned to"
			+ " sell the book you want. This prevents the villager from"
			+ " changing its trade offers later.\n\n"
			+ "Make sure you have at least 24 paper and 9 emeralds in your"
			+ " inventory when using this feature. Alternatively, 1 book and"
			+ " 64 emeralds will also work.",
		false);
	
	private final UpdateBooksSetting updateBooks = new UpdateBooksSetting();
	
	private final SliderSetting range =
		new SliderSetting("Range", 5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final FacingSetting facing = FacingSetting.withoutPacketSpam(
		"How AutoLibrarian should face the villager and job site.\n\n"
			+ "\u00a7lOff\u00a7r - Don't face the villager at all. Will be"
			+ " detected by anti-cheat plugins.\n\n"
			+ "\u00a7lServer-side\u00a7r - Face the villager on the"
			+ " server-side, while still letting you move the camera freely on"
			+ " the client-side.\n\n"
			+ "\u00a7lClient-side\u00a7r - Face the villager by moving your"
			+ " camera on the client-side. This is the most legit option, but"
			+ " can be disorienting to look at.");
	
	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.SERVER);
	
	private final SliderSetting repairMode = new SliderSetting("Repair mode",
		"Prevents AutoLibrarian from using your axe when its durability reaches"
			+ " the given threshold, so you can repair it before it breaks.\n"
			+ "Can be adjusted from 0 (off) to 100 remaining uses.",
		1, 0, 100, 1, ValueDisplay.INTEGER.withLabel(0, "off"));
	
	private final OverlayRenderer overlay = new OverlayRenderer();
	private final HashSet<Villager> experiencedVillagers =
		new HashSet<>();
	
	private Villager villager;
	private BlockPos jobSite;
	
	private boolean placingJobSite;
	private boolean breakingJobSite;
	
	public AutoLibrarianHack()
	{
		super("AutoLibrarian");
		setCategory(Category.OTHER);
		addSetting(wantedBooks);
		addSetting(lockInTrade);
		addSetting(updateBooks);
		addSetting(range);
		addSetting(facing);
		addSetting(swingHand);
		addSetting(repairMode);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		if(breakingJobSite)
		{
			((net.wurstclient.mixin.MultiPlayerGameModeAccessor)(Object)MC.gameMode).setIsDestroying(true);
			MC.gameMode.stopDestroyBlock();
			breakingJobSite = false;
		}
		
		overlay.resetProgress();
		villager = null;
		jobSite = null;
		placingJobSite = false;
		breakingJobSite = false;
		experiencedVillagers.clear();
	}
	
	@Override
	public void onUpdate()
	{
		if(villager == null)
		{
			setTargetVillager();
			return;
		}
		
		if(jobSite == null)
		{
			setTargetJobSite();
			return;
		}
		
		if(placingJobSite && breakingJobSite)
			throw new IllegalStateException(
				"Trying to place and break job site at the same time. Something is wrong.");
		
		if(placingJobSite)
		{
			placeJobSite();
			return;
		}
		
		if(breakingJobSite)
		{
			breakJobSite();
			return;
		}
		
		if(!(MC.screen instanceof MerchantScreen tradeScreen))
		{
			openTradeScreen();
			return;
		}
		
		// Can't see experience until the trade screen is open, so we have to
		// check it here and start over if the villager is already experienced.
		int experience = tradeScreen.getMenu().getTraderXp();
		if(experience > 0)
		{
			ChatUtils.warning("Villager at "
				+ villager.blockPosition().toShortString()
				+ " is already experienced, meaning it can't be trained anymore.");
			ChatUtils.message("正在寻找另一个村民...");
			experiencedVillagers.add(villager);
			villager = null;
			jobSite = null;
			closeTradeScreen();
			return;
		}
		
		// check which book the villager is selling
		BookOffer bookOffer =
			findEnchantedBookOffer(tradeScreen.getMenu().getOffers());
		
		if(bookOffer == null)
		{
			ChatUtils.message("村民未出售附魔书。");
			closeTradeScreen();
			breakingJobSite = true;
			System.out.println("Breaking job site...");
			return;
		}
		
		ChatUtils.message(
			"Villager is selling " + bookOffer.getEnchantmentNameWithLevel()
				+ " for " + bookOffer.getFormattedPrice() + ".");
		
		// if wrong enchantment, break job site and start over
		if(!wantedBooks.isWanted(bookOffer))
		{
			breakingJobSite = true;
			System.out.println("Breaking job site...");
			closeTradeScreen();
			return;
		}
		
		// lock in the trade, if enabled
		if(lockInTrade.isChecked())
		{
			// select the first valid trade
			tradeScreen.getMenu().setSelectionHint(0);
			tradeScreen.getMenu().tryMoveItems(0);
			MC.getConnection()
				.send(new ServerboundSelectTradePacket(0));
			
			// buy whatever the villager is selling
			MC.gameMode.handleInventoryMouseClick(
				tradeScreen.getMenu().containerId, 2, 0,
				ClickType.PICKUP, MC.player);
			
			// close the trade screen
			closeTradeScreen();
		}
		
		// update wanted books based on the user's settings
		updateBooks.getSelected().update(wantedBooks, bookOffer);
		
		ChatUtils.message("完成！");
		setEnabled(false);
	}
	
	private void breakJobSite()
	{
		if(jobSite == null)
			throw new IllegalStateException("Job site is null.");
		
		BlockBreakingParams params =
			BlockBreaker.getBlockBreakingParams(jobSite);
		
		if(params == null || BlockUtils.getState(jobSite).canBeReplaced())
		{
			System.out.println("Job site has been broken. Replacing...");
			breakingJobSite = false;
			placingJobSite = true;
			return;
		}
		
		// equip tool
		WURST.getHax().autoToolHack.equipBestTool(jobSite, false, true,
			repairMode.getValueI());
		
		// face block
		facing.getSelected().face(params.hitVec());
		
		// damage block and swing hand
		if(MC.gameMode.continueDestroyBlock(jobSite,
			params.side()))
			swingHand.swing(InteractionHand.MAIN_HAND);
		
		// update progress
		overlay.updateProgress();
	}
	
	private void placeJobSite()
	{
		if(jobSite == null)
			throw new IllegalStateException("Job site is null.");
		
		if(!BlockUtils.getState(jobSite).canBeReplaced())
		{
			if(BlockUtils.getBlock(jobSite) == Blocks.LECTERN)
			{
				System.out.println("Job site has been placed.");
				placingJobSite = false;
				
			}else
			{
				System.out
					.println("Found wrong block at job site. Breaking...");
				breakingJobSite = true;
				placingJobSite = false;
			}
			
			return;
		}
		
		// check if holding a lectern
		if(!MC.player.isHolding(Items.LECTERN))
		{
			InventoryUtils.selectItem(Items.LECTERN, 36);
			return;
		}
		
		// get the hand that is holding the lectern
		InteractionHand hand = MC.player.getMainHandItem().is(Items.LECTERN)
			? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		
		// sneak-place to avoid activating trapdoors/chests/etc.
		IKeyBinding sneakKey = IKeyBinding.get(MC.options.keyShift);
		sneakKey.setPressed(true);
		if(!MC.player.isShiftKeyDown())
			return;
		
		// get block placing params
		BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(jobSite);
		if(params == null)
		{
			sneakKey.resetPressedState();
			return;
		}
		
		// face block
		facing.getSelected().face(params.hitVec());
		
		// place block
		InteractionResult result = MC.gameMode.useItemOn(MC.player,
			hand, params.toHitResult());
		
		// swing hand
		if(result.consumesAction() && result.shouldSwing())
			swingHand.swing(hand);
		
		// reset sneak
		sneakKey.resetPressedState();
	}
	
	private void openTradeScreen()
	{
		if(((net.wurstclient.mixin.MinecraftAccessor)(Object)MC).getRightClickDelay() > 0)
			return;
		
		MultiPlayerGameMode im = MC.gameMode;
		LocalPlayer player = MC.player;
		
		if(player.distanceToSqr(villager) > range.getValueSq())
		{
			ChatUtils.error("村民超出范围。请考虑困住"
				+ " the villager so it doesn't wander away.");
			setEnabled(false);
			return;
		}
		
		// create realistic hit result
		AABB box = villager.getBoundingBox();
		Vec3 start = RotationUtils.getEyesPos();
		Vec3 end = box.getCenter();
		Vec3 hitVec = box.clip(start, end).orElse(start);
		EntityHitResult hitResult = new EntityHitResult(villager, hitVec);
		
		// face end vector
		facing.getSelected().face(end);
		
		// click on villager
		InteractionHand hand = InteractionHand.MAIN_HAND;
		InteractionResult actionResult =
			im.interactAt(player, villager, hitResult, hand);
		
		if(!actionResult.consumesAction())
			im.interact(player, villager, hand);
		
		// swing hand
		if(actionResult.consumesAction() && actionResult.shouldSwing())
			swingHand.swing(hand);
		
		// set cooldown
		((net.wurstclient.mixin.MinecraftAccessor)(Object)MC).setRightClickDelay(4);
	}
	
	private void closeTradeScreen()
	{
		MC.player.closeContainer();
		((net.wurstclient.mixin.MinecraftAccessor)(Object)MC).setRightClickDelay(4);
	}
	
	private BookOffer findEnchantedBookOffer(MerchantOffers tradeOffers)
	{
		for(MerchantOffer tradeOffer : tradeOffers)
		{
			ItemStack stack = tradeOffer.getResult();
			if(!(stack.getItem() instanceof EnchantedBookItem))
				continue;
			
			ItemEnchantments enchantments = stack.getOrDefault(
				DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
			if(enchantments.isEmpty())
				continue;
			
			var enchantmentEntry = enchantments.entrySet().iterator().next();
			String enchantment = enchantmentEntry.getKey().unwrapKey()
				.orElseThrow().location().toString();
			int level = enchantmentEntry.getIntValue();
			int price = tradeOffer.getCostA().getCount();
			BookOffer bookOffer = new BookOffer(enchantment, level, price);
			
			if(!bookOffer.isValid())
			{
				System.out.println("Found invalid enchanted book offer.\n"
					+ "Item components: " + stack.getComponentsPatch());
				continue;
			}
			
			return bookOffer;
		}
		
		return null;
	}
	
	private void setTargetVillager()
	{
		LocalPlayer player = MC.player;
		double rangeSq = range.getValueSq();
		
		Stream<Villager> stream =
			StreamSupport.stream(MC.level.entitiesForRendering().spliterator(), true)
				.filter(e -> !e.isRemoved())
				.filter(Villager.class::isInstance)
				.map(e -> (Villager)e).filter(e -> e.getHealth() > 0)
				.filter(e -> player.distanceToSqr(e) <= rangeSq)
				.filter(e -> e.getVillagerData()
					.getProfession() == VillagerProfession.LIBRARIAN)
				.filter(e -> e.getVillagerData().getLevel() == 1)
				.filter(e -> !experiencedVillagers.contains(e));
		
		villager = stream
			.min(Comparator.comparingDouble(e -> player.distanceToSqr(e)))
			.orElse(null);
		
		if(villager == null)
		{
			String errorMsg = "Couldn't find a nearby librarian.";
			int numExperienced = experiencedVillagers.size();
			if(numExperienced > 0)
				errorMsg += " (Except for " + numExperienced + " that "
					+ (numExperienced == 1 ? "is" : "are")
					+ " already experienced.)";
			
			ChatUtils.error(errorMsg);
			ChatUtils.message("请确保图书管理员和讲台"
				+ " are reachable from where you are standing.");
			setEnabled(false);
			return;
		}
		
		System.out.println("Found villager at " + villager.blockPosition());
	}
	
	private void setTargetJobSite()
	{
		Vec3 eyesVec = RotationUtils.getEyesPos();
		double rangeSq = range.getValueSq();
		
		Stream<BlockPos> stream = BlockUtils
			.getAllInBoxStream(BlockPos.containing(eyesVec),
				range.getValueCeil())
			.filter(pos -> eyesVec
				.distanceToSqr(Vec3.atCenterOf(pos)) <= rangeSq)
			.filter(pos -> BlockUtils.getBlock(pos) == Blocks.LECTERN);
		
		jobSite = stream
			.min(Comparator.comparingDouble(
				pos -> villager.distanceToSqr(Vec3.atCenterOf(pos))))
			.orElse(null);
		
		if(jobSite == null)
		{
			ChatUtils.error("找不到图书管理员的讲台。");
			ChatUtils.message("请确保图书管理员和讲台"
				+ " are reachable from where you are standing.");
			setEnabled(false);
			return;
		}
		
		System.out.println("Found lectern at " + jobSite);
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		int green = 0xC000FF00;
		int red = 0xC0FF0000;
		
		if(villager != null)
			RenderUtils.drawOutlinedBox(matrixStack, villager.getBoundingBox(),
				green, false);
		
		if(jobSite != null)
			RenderUtils.drawOutlinedBox(matrixStack, new AABB(jobSite), green,
				false);
		
		List<AABB> expVilBoxes = experiencedVillagers.stream()
			.map(Villager::getBoundingBox).toList();
		RenderUtils.drawOutlinedBoxes(matrixStack, expVilBoxes, red, false);
		RenderUtils.drawCrossBoxes(matrixStack, expVilBoxes, red, false);
		
		if(breakingJobSite)
			overlay.render(matrixStack, partialTicks, jobSite);
	}
}
