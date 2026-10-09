/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.discord;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.client.Minecraft;
import net.wurstclient.WurstClient;
import net.wurstclient.events.UpdateListener;

public final class DiscordRpcManager implements UpdateListener
{
	private static final String CLIENT_ID = "1305324961551155534";

	private final DiscordRpc rpc = new DiscordRpc(CLIENT_ID);
	private volatile String lastState;
	private volatile String lastDetails;
	private int tickCounter;
	private boolean registered;
	private final AtomicBoolean operationPending = new AtomicBoolean();
	private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "WurstB-DiscordRPC");
		thread.setDaemon(true);
		return thread;
	});

	public void start()
	{
		submitConnect();
		register();
	}

	private void register()
	{
		if(registered)
			return;
		WurstClient.INSTANCE.getEventManager()
			.add(UpdateListener.class, this);
		registered = true;
	}

	@Override
	public void onUpdate()
	{
		if(Minecraft.getInstance().isPaused())
			return;

		tickCounter++;
		if(tickCounter % 40 != 0)
			return;

		if(!rpc.isConnected())
		{
			if(tickCounter % 200 == 0)
				submitConnect();
			return;
		}

		queuePresenceUpdate();
	}

	private void queuePresenceUpdate()
	{
		Minecraft mc = WurstClient.MC;
		String details;
		String state;

		if(mc.getCurrentServer() != null)
		{
			details = "Multiplayer";
			state = "WurstB+ Plus " + WurstClient.VERSION;
		}else if(mc.hasSingleplayerServer())
		{
			details = "Singleplayer";
			state = "WurstB+ Plus " + WurstClient.VERSION;
		}else
		{
			details = "Main Menu";
			state = "WurstB+ Plus " + WurstClient.VERSION;
		}

		int activeHacks = 0;
		for(net.wurstclient.hack.Hack hack : WurstClient.INSTANCE.getHax().getAllHax())
			if(hack.isEnabled())
				activeHacks++;
		if(activeHacks > 0)
			state += " | " + activeHacks + " hacks";

		if(details.equals(lastDetails) && state.equals(lastState))
			return;

		if(!operationPending.compareAndSet(false, true))
			return;

		String nextState = state;
		String nextDetails = details;
		worker.execute(() -> {
			try
			{
				if(rpc.setActivity(nextState, nextDetails, "wurst_logo",
					"WurstB+ Plus " + WurstClient.VERSION))
				{
					lastDetails = nextDetails;
					lastState = nextState;
				}
			}finally
			{
				operationPending.set(false);
			}
		});
	}

	private void submitConnect()
	{
		if(!operationPending.compareAndSet(false, true))
			return;
		worker.execute(() -> {
			try
			{
				if(rpc.connect())
					System.out.println("[DiscordRPC] Connected");
			}finally
			{
				operationPending.set(false);
			}
		});
	}

	public void shutdown()
	{
		if(registered)
		{
			WurstClient.INSTANCE.getEventManager()
				.remove(UpdateListener.class, this);
			registered = false;
		}
		rpc.close();
		worker.shutdownNow();
	}
}
