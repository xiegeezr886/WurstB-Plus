/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.proxy;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.netty.channel.ChannelHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import java.net.InetSocketAddress;

import net.wurstclient.proxy.ProxyConfig.ProxyType;
import net.wurstclient.util.json.JsonException;
import net.wurstclient.util.json.JsonUtils;
import net.wurstclient.util.json.WsonArray;
import net.wurstclient.util.json.WsonObject;

public final class ProxyManager
{
	private final List<ProxyConfig> proxies = new ArrayList<>();
	private ProxyConfig activeProxy;
	private final Path file;

	public ProxyManager(Path wurstFolder)
	{
		file = wurstFolder.resolve("proxies.json");
		load();
	}

	private void load()
	{
		try
		{
			WsonArray wson = JsonUtils.parseFileToArray(file);
			for(int i = 0; i < wson.size(); i++)
			{
				WsonObject obj = wson.getObject(i);
				String name = obj.getString("name");
				ProxyType type = ProxyType.valueOf(
					obj.getString("type", "SOCKS5"));
				String host = obj.getString("host");
				int port = obj.getInt("port");
				ProxyConfig pc = new ProxyConfig(name, type, host, port);
				if(obj.has("username"))
					pc.setUsername(obj.getString("username"));
				if(obj.has("password"))
					pc.setPassword(obj.getString("password"));
				proxies.add(pc);
			}
		}catch(NoSuchFileException e)
		{}catch(IOException | JsonException | IllegalArgumentException e)
		{
			System.err.println("Couldn't load proxies.json");
			e.printStackTrace();
		}
	}

	public void save()
	{
		JsonArray array = new JsonArray();
		for(ProxyConfig pc : proxies)
		{
			JsonObject obj = new JsonObject();
			obj.addProperty("name", pc.getName());
			obj.addProperty("type", pc.getType().name());
			obj.addProperty("host", pc.getHost());
			obj.addProperty("port", pc.getPort());
			if(pc.hasAuth())
			{
				obj.addProperty("username", pc.getUsername());
				obj.addProperty("password",
					pc.getPassword() != null ? pc.getPassword() : "");
			}
			array.add(obj);
		}
		try
		{
			JsonUtils.toJson(array, file);
		}catch(IOException | JsonException e)
		{
			System.err.println("Couldn't save proxies.json");
			e.printStackTrace();
		}
	}

	public List<ProxyConfig> getAllProxies()
	{
		return Collections.unmodifiableList(proxies);
	}

	public ProxyConfig getActiveProxy()
	{
		return activeProxy;
	}

	public boolean setActiveProxy(String name)
	{
		ProxyConfig selected = proxies.stream()
			.filter(p -> p.getName().equals(name)).findFirst().orElse(null);
		if(selected == null)
			return false;
		activeProxy = selected;
		return true;
	}

	public void clearActiveProxy()
	{
		activeProxy = null;
	}

	public void add(ProxyConfig proxy)
	{
		boolean replaceActive = activeProxy != null
			&& activeProxy.getName().equals(proxy.getName());
		proxies.removeIf(p -> p.getName().equals(proxy.getName()));
		proxies.add(proxy);
		if(replaceActive)
			activeProxy = proxy;
		save();
	}

	public boolean remove(String name)
	{
		boolean removed = proxies.removeIf(p -> p.getName().equals(name));
		if(!removed)
			return false;
		if(activeProxy != null && activeProxy.getName().equals(name))
			activeProxy = null;
		save();
		return true;
	}

	public ChannelHandler createProxyHandler()
	{
		ProxyConfig proxy = activeProxy;
		if(proxy == null)
			return null;

		InetSocketAddress address = InetSocketAddress.createUnresolved(
			proxy.getHost(), proxy.getPort());
		String username = proxy.hasAuth() ? proxy.getUsername() : null;
		if(proxy.getType() == ProxyType.SOCKS4)
			return new Socks4ProxyHandler(address, username);
		return new Socks5ProxyHandler(address, username,
			proxy.hasAuth() ? proxy.getPassword() : null);
	}
}
