/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"auto steal", "ChestStealer", "chest stealer",
	"steal store buttons", "Steal/Store buttons"})
public final class AutoStealHack extends Hack
{
	private final SliderSetting delay = new SliderSetting("Delay",
		"Delay between moving stacks of items.\n"
			+ "Should be at least 70ms for NoCheat+ servers.",
		100, 0, 500, 10, ValueDisplay.INTEGER.withSuffix("ms"));
	
	private final CheckboxSetting buttons =
		new CheckboxSetting("Steal/Store buttons", true);
	
	private final CheckboxSetting reverseSteal =
		new CheckboxSetting("Reverse steal order", false);
	
	private Thread thread;
	
	/**
	 * 用于「客户端线程点完一格 -> 工作线程继续下一格」的再同步。工作线程拿到锁后
	 * 用 {@link Condition#await()} 等待，所以它不会忙等；锁只在两个线程间传一次
	 * 信号，不会阻塞客户端线程。
	 */
	private final ReentrantLock lock = new ReentrantLock();
	private final Condition clickDone = lock.newCondition();
	private boolean clicked;
	
	/**
	 * 工作线程只读该标记；判断界面是否还在的 {@code MC.screen} 只能在客户端线程
	 * 上读，读到的结果由 {@link #clickSlot} 写回这里。
	 */
	private volatile boolean screenOpen = true;
	
	public AutoStealHack()
	{
		super("AutoSteal");
		setCategory(Category.ITEMS);
		addSetting(buttons);
		addSetting(delay);
		addSetting(reverseSteal);
	}
	
	public void steal(AbstractContainerScreen<?> screen, int rows)
	{
		startClickingSlots(screen, 0, rows * 9, true);
	}
	
	public void store(AbstractContainerScreen<?> screen, int rows)
	{
		startClickingSlots(screen, rows * 9, rows * 9 + 36, false);
	}
	
	private void startClickingSlots(AbstractContainerScreen<?> screen, int from, int to,
		boolean steal)
	{
		if(thread != null && thread.isAlive())
			thread.interrupt();
		
		// 新一次操作要从「界面在」重新开始：上一轮可能因为界面关掉而把标记置成了 false
		screenOpen = true;
		
		thread = new Thread(() -> shiftClickSlots(screen, from, to, steal),
			"AutoSteal");
		thread.setUncaughtExceptionHandler((t, e) -> e.printStackTrace());
		thread.setDaemon(true);
		thread.start();
	}
	
	private void shiftClickSlots(AbstractContainerScreen<?> screen, int from, int to,
		boolean steal)
	{
		// 建表仍在工作线程上做（这是唯一可能偏重的一步），但下面每一次
		// 点击都必须交回客户端线程：slotClicked() 会改菜单并发包。
		List<Slot> slots = IntStream.range(from, to)
			.mapToObj(i -> screen.getMenu().slots.get(i)).toList();
		
		if(reverseSteal.isChecked() && steal)
			Collections.reverse(slots);
		
		for(Slot slot : slots)
		{
			if(!screenOpen || slot.getItem().isEmpty())
				continue;
			
			try
			{
				// 原来的 Thread.sleep(delay) 挪到这里：等待期间不占用客户端线程，
				// 只让工作线程挂起，所以节奏（两次点击之间至少 delay 毫秒）不变。
				Thread.sleep(delay.getValueI());
				
				if(!screenOpen)
					break;
				
				MC.execute(() -> clickSlot(screen, slot));
				awaitClick();
				
			}catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
				break;
			}
		}
	}
	
	/**
	 * 真正的点击。整段都在客户端线程上跑，因为 {@code slotClicked()}
	 * 是原版的界面回调：它会改菜单内容并向服务器发包。
	 */
	private void clickSlot(AbstractContainerScreen<?> screen, Slot slot)
	{
		try
		{
			// 界面已经换掉/关掉时不能再点，否则会点到别的菜单上
			if(MC.screen != screen)
			{
				screenOpen = false;
				return;
			}
			
			screen.slotClicked(slot, slot.index, 0, ClickType.QUICK_MOVE);
			
		}finally
		{
			// 即使 slotClicked() 抛异常也要放行工作线程，否则它会一直停在 awaitClick()
			lock.lock();
			try
			{
				clicked = true;
				clickDone.signal();
			}finally
			{
				lock.unlock();
			}
		}
	}
	
	/**
	 * 等客户端线程点完一格再继续。等待的是工作线程，不是客户端线程；界面已经
	 * 关闭、或线程被下一个 steal()/store() 打断时不再等，直接结束。
	 */
	private void awaitClick() throws InterruptedException
	{
		lock.lock();
		try
		{
			while(!clicked && screenOpen)
				clickDone.await();
			clicked = false;
			
		}finally
		{
			lock.unlock();
		}
	}
	
	public boolean areButtonsVisible()
	{
		return buttons.isChecked();
	}
	
	// See GenericContainerScreenMixin and ShulkerBoxScreenMixin
}
