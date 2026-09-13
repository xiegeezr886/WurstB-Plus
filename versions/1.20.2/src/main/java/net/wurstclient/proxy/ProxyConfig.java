/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.proxy;

import java.util.Objects;

public final class ProxyConfig
{
	private final String name;
	private ProxyType type;
	private String host;
	private int port;
	private String username;
	private String password;

	public ProxyConfig(String name, ProxyType type, String host, int port)
	{
		this.name = requireText(name, "name");
		this.type = Objects.requireNonNull(type);
		this.host = requireText(host, "host");
		setPort(port);
	}

	public String getName()
	{
		return name;
	}

	public ProxyType getType()
	{
		return type;
	}

	public void setType(ProxyType type)
	{
		this.type = Objects.requireNonNull(type);
	}

	public String getHost()
	{
		return host;
	}

	public void setHost(String host)
	{
		this.host = requireText(host, "host");
	}

	public int getPort()
	{
		return port;
	}

	public void setPort(int port)
	{
		if(port < 1 || port > 65535)
			throw new IllegalArgumentException("Proxy port must be between 1 and 65535");
		this.port = port;
	}

	public String getUsername()
	{
		return username;
	}

	public void setUsername(String username)
	{
		this.username = username;
	}

	public String getPassword()
	{
		return password;
	}

	public void setPassword(String password)
	{
		this.password = password;
	}

	public boolean hasAuth()
	{
		return username != null && !username.isEmpty();
	}

	private static String requireText(String value, String field)
	{
		Objects.requireNonNull(value, field);
		if(value.isBlank())
			throw new IllegalArgumentException("Proxy " + field + " cannot be blank");
		return value;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(obj instanceof ProxyConfig other)
			return name.equals(other.name);
		return false;
	}

	@Override
	public int hashCode()
	{
		return name.hashCode();
	}

	public enum ProxyType
	{
		SOCKS4("SOCKS4"),
		SOCKS5("SOCKS5");

		private final String displayName;

		ProxyType(String displayName)
		{
			this.displayName = displayName;
		}

		public String getDisplayName()
		{
			return displayName;
		}
	}
}
