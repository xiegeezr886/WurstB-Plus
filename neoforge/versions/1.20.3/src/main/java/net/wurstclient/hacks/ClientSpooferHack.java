/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.TextFieldSetting;

/**
 * Spoofs the client brand sent to servers. Soar offered a Vanilla/Forge combo;
 * this port takes an arbitrary brand string and also resends it after joining
 * a server, since the vanilla brand is normally sent during configuration
 * before the hack can intercept it.
 */
@SearchTags({"client spoofer", "brand spoof", "brand", "vanilla", "forge",
	"client brand"})
public final class ClientSpooferHack extends Hack
	implements PacketOutputListener, UpdateListener
{
	private final TextFieldSetting brandName = new TextFieldSetting("Brand",
		"Client brand reported to the server.", "vanilla",
		s -> !s.isBlank());

	private ClientPacketListener lastConnection;
	private int resendDelay;

	public ClientSpooferHack()
	{
		super("ClientSpoofer");
		setCategory(Category.OTHER);
		addSetting(brandName);
	}

	@Override
	protected void onEnable()
	{
		lastConnection = null;
		resendDelay = 0;

		EVENTS.add(PacketOutputListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketOutputListener.class, this);
		EVENTS.remove(UpdateListener.class, this);

		lastConnection = null;
		resendDelay = 0;
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(!(event.getPacket() instanceof ServerboundCustomPayloadPacket packet))
			return;

		if(!(packet.payload() instanceof BrandPayload))
			return;

		String brand = getBrand();
		if(!brand.isEmpty())
			event.setPacket(new ServerboundCustomPayloadPacket(
				new BrandPayload(brand)));
	}

	@Override
	public void onUpdate()
	{
		ClientPacketListener connection = MC.getConnection();
		if(connection == null || MC.player == null)
		{
			lastConnection = null;
			return;
		}

		if(connection != lastConnection)
		{
			lastConnection = connection;
			resendDelay = 2;
		}

		if(resendDelay <= 0)
			return;

		resendDelay--;
		if(resendDelay == 0)
			sendBrand(connection);
	}

	private void sendBrand(ClientPacketListener connection)
	{
		String brand = getBrand();
		if(brand.isEmpty())
			return;

		connection.send(new ServerboundCustomPayloadPacket(
			new BrandPayload(brand)));
	}

	private String getBrand()
	{
		return brandName.getValue().trim();
	}
}
