/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun v1.9.0 (GPL-3.0).
package net.wurstclient.hacks;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"tp diagnostic", "tp diag", "gcore diagnostic", "tp诊断"})
public final class GcoreTpDiagnosticHack extends Hack
	implements UpdateListener, PacketInputListener, PacketOutputListener
{
	private final CheckboxSetting logMovePackets = new CheckboxSetting(
		"Log move packets", "Log every outgoing position packet.", true);

	private final CheckboxSetting logS08 = new CheckboxSetting(
		"Log pullbacks", "Log every incoming server position packet.", true);

	private final CheckboxSetting logTickState = new CheckboxSetting(
		"Log tick state",
		"Log the client position once per tick to see whether a pullback was"
			+ " applied.",
		true);

	private final CheckboxSetting logActionPackets = new CheckboxSetting(
		"Log action packets",
		"Log attacks, digs, item switches and placements.", true);

	private int tickCounter;
	private int movePacketsThisTick;
	private int totalMovePackets;
	private int totalS08;
	private BufferedWriter writer;

	public GcoreTpDiagnosticHack()
	{
		super("GcoreTpDiagnostic");
		setCategory(Category.OTHER);
		addSetting(logMovePackets);
		addSetting(logS08);
		addSetting(logTickState);
		addSetting(logActionPackets);
	}

	@Override
	protected void onEnable()
	{
		tickCounter = 0;
		movePacketsThisTick = 0;
		totalMovePackets = 0;
		totalS08 = 0;
		openLog();
		line("=== GcoreTpDiagnostic start ===");
		line("time(ms)=" + System.currentTimeMillis());

		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);

		line("=== stop: movePackets=" + totalMovePackets + " s08=" + totalS08
			+ " ===");
		closeLog();
	}

	@Override
	public void onUpdate()
	{
		tickCounter++;
		if(movePacketsThisTick > 5)
			line("!! TICK " + tickCounter + " sent " + movePacketsThisTick
				+ " move packets (>5, server punishment zone)");
		movePacketsThisTick = 0;

		if(logTickState.isChecked() && MC.player != null)
		{
			Vec3 p = MC.player.position();
			line(String.format(Locale.ROOT,
				"TICKSTATE tick=%d pos=[%.4f,%.4f,%.4f] onGround=%b",
				tickCounter, p.x, p.y, p.z, MC.player.onGround()));
		}
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(logActionPackets.isChecked() && isActionPacket(event.getPacket()))
			line(String.format(Locale.ROOT,
				"ACTION tick=%d t=%.3f thread=%s cls=%s (moves this tick=%d)",
				tickCounter, System.nanoTime() / 1_000_000.0,
				Thread.currentThread().getName(),
				event.getPacket().getClass().getSimpleName(),
				movePacketsThisTick));

		if(!logMovePackets.isChecked()
			|| !(event.getPacket() instanceof ServerboundMovePlayerPacket p))
			return;

		movePacketsThisTick++;
		totalMovePackets++;

		boolean hasPos = p.hasPosition();
		line(String.format(Locale.ROOT,
			"SEND tick=%d inTick=%d t=%.3f thread=%s cls=%s pos=%s"
				+ " xyz=[%.4f,%.4f,%.4f] onGround=%b hasLook=%b",
			tickCounter, movePacketsThisTick,
			System.nanoTime() / 1_000_000.0, Thread.currentThread().getName(),
			p.getClass().getSimpleName(), hasPos ? "yes" : "no", p.getX(0),
			p.getY(0), p.getZ(0), p.isOnGround(), p.hasRotation()));
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(!logS08.isChecked()
			|| !(event.getPacket() instanceof ClientboundPlayerPositionPacket p))
			return;

		totalS08++;
		line(String.format(Locale.ROOT,
			"RECV-S08 tick=%d t=%.3f thread=%s id=%d"
				+ " serverPos=[%.4f,%.4f,%.4f] clientPos=%s",
			tickCounter, System.nanoTime() / 1_000_000.0,
			Thread.currentThread().getName(), p.getId(), p.getX(), p.getY(),
			p.getZ(), MC.player == null ? "null" : MC.player.position()));
	}

	private boolean isActionPacket(Object packet)
	{
		return packet instanceof ServerboundInteractPacket
			|| packet instanceof ServerboundPlayerActionPacket
			|| packet instanceof ServerboundSetCarriedItemPacket
			|| packet instanceof ServerboundSwingPacket
			|| packet instanceof ServerboundUseItemOnPacket
			|| packet instanceof ServerboundUseItemPacket;
	}

	private void openLog()
	{
		try
		{
			Path path =
				MC.gameDirectory.toPath().resolve("gcore-tp-diag.log");
			writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}catch(IOException | RuntimeException e)
		{
			writer = null;
		}
	}

	private void line(String s)
	{
		if(writer == null)
			return;
		try
		{
			writer.write(s);
			writer.newLine();
			writer.flush();
		}catch(IOException ignored)
		{
			// Diagnostics must never break the game.
		}
	}

	private void closeLog()
	{
		if(writer == null)
			return;
		try
		{
			writer.flush();
			writer.close();
		}catch(IOException ignored)
		{
			// Ignore.
		}
		writer = null;
	}
}
