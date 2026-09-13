/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

/**
 * The inventory arithmetic that decides how much a mining batch may excavate
 * and when the products have to be unloaded.
 */
public final class PerimeterInventoryPolicy
{
	public static final int INVENTORY_SLOTS = 36;
	public static final int OFFHAND_SLOT = 40;
	
	private PerimeterInventoryPolicy()
	{}
	
	/**
	 * Ported from the reference: one batch is limited to
	 * {@code (empty slots - reserved slots) * blocks per empty slot} blocks,
	 * but never less than a single block so that a full inventory still makes
	 * progress towards the next unloading trip.
	 */
	public static long batchLimit(int emptySlots, int reservedSlots,
		int blocksPerEmptySlot)
	{
		int usable = Math.max(0, emptySlots - reservedSlots);
		return Math.max(1L, (long)usable * Math.max(0, blocksPerEmptySlot));
	}
	
	/**
	 * @return whether the products should be unloaded before mining further.
	 */
	public static boolean needsUnloading(int emptySlots, int reservedSlots)
	{
		return emptySlots <= reservedSlots;
	}
	
	/**
	 * @return the slot whose contents are moved on, or -1 when everything is
	 *         worth keeping.
	 */
	public static int firstDisposableSlot(java.util.List<ItemStackLike> slots)
	{
		int limit = Math.min(INVENTORY_SLOTS, slots.size());
		
		for(int slot = 0; slot < limit; slot++)
		{
			ItemStackLike stack = slots.get(slot);
			
			if(stack != null && !stack.isEmpty() && !stack.keepWhenUnloading())
				return slot;
		}
		
		return -1;
	}
	
	/**
	 * The parts of an item stack the unloading rules depend on.
	 */
	public interface ItemStackLike
	{
		boolean isEmpty();
		
		/**
		 * Whether the stack is damageable, a tool, wearable, an elytra, a
		 * firework, food, or explicitly whitelisted.
		 */
		boolean keepWhenUnloading();
	}
}
