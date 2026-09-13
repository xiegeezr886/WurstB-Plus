package net.wurstclient.util.inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.wurstclient.WurstClient;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;

public final class InventoryActionQueue
	implements UpdateListener, WorldChangeListener
{
	private final ArrayList<ActionChain> pending = new ArrayList<>();
	private long sequence;
	private boolean started;

	public void start()
	{
		if(started)
			return;

		WurstClient.INSTANCE.getEventManager().add(UpdateListener.class, this);
		WurstClient.INSTANCE.getEventManager().add(WorldChangeListener.class,
			this);
		started = true;
	}

	public void stop()
	{
		if(!started)
			return;

		WurstClient.INSTANCE.getEventManager().remove(UpdateListener.class, this);
		WurstClient.INSTANCE.getEventManager().remove(WorldChangeListener.class,
			this);
		clear();
		started = false;
	}

	public synchronized boolean submit(Object owner, int priority,
		BooleanSupplier validator, Runnable... actions)
	{
		if(owner == null || actions.length == 0 || hasPending(owner)
			|| WurstClient.MC.player == null)
			return false;

		int menuId = WurstClient.MC.player.containerMenu.containerId;
		pending.add(new ActionChain(owner, priority, sequence++, menuId,
			System.currentTimeMillis(), validator,
			List.copyOf(Arrays.asList(actions))));
		return true;
	}

	public synchronized boolean hasPending(Object owner)
	{
		return pending.stream().anyMatch(chain -> chain.owner == owner);
	}

	public synchronized void cancel(Object owner)
	{
		pending.removeIf(chain -> chain.owner == owner);
	}

	public synchronized void clear()
	{
		pending.clear();
	}

	@Override
	public void onUpdate()
	{
		ActionChain chain;
		synchronized(this)
		{
			chain = selectNext(System.currentTimeMillis());
			
			if(chain != null)
				pending.remove(chain);
		}
		
		if(chain == null)
			return;
		
		for(Runnable action : chain.actions)
			action.run();
	}
	
	/**
	 * 选出这一 tick 要跑的链，并顺手丢掉没救的。
	 *
	 * <p>
	 * 与原来不同：条件没通过的链**留在队列里**等下一 tick（受
	 * {@link ActionRetryPolicy#DEFAULT_RETRY_WINDOW_MS} 限制），而不是"先移除再
	 * 校验"导致它被静默丢弃、owner 永远等不到。为了不让一条在等的链挡住后面的
	 * 链，RETRY 只是跳过、不参与本次选择。
	 */
	private ActionChain selectNext(long nowMs)
	{
		ActionChain best = null;
		Iterator<ActionChain> iterator = pending.iterator();
		
		while(iterator.hasNext())
		{
			ActionChain chain = iterator.next();
			ActionRetryPolicy.Decision decision = decide(chain, nowMs);
			
			if(decision == ActionRetryPolicy.Decision.ABORT)
			{
				iterator.remove();
				continue;
			}
			
			if(decision == ActionRetryPolicy.Decision.RETRY)
				continue;
			
			if(best == null || chain.priority > best.priority
				|| chain.priority == best.priority
					&& chain.sequence < best.sequence)
				best = chain;
		}
		
		return best;
	}
	
	private ActionRetryPolicy.Decision decide(ActionChain chain, long nowMs)
	{
		boolean menuMatches = WurstClient.MC.player != null
			&& WurstClient.MC.player.containerMenu.containerId == chain.menuId;
		boolean validatorPassed =
			menuMatches && chain.validator.getAsBoolean();
		
		return ActionRetryPolicy.decide(menuMatches, validatorPassed,
			nowMs - chain.submittedAtMs,
			ActionRetryPolicy.DEFAULT_RETRY_WINDOW_MS);
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		clear();
	}

	private record ActionChain(Object owner, int priority, long sequence,
		int menuId, long submittedAtMs, BooleanSupplier validator,
		List<Runnable> actions)
	{
	}
}
