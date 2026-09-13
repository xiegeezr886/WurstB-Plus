/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter.config;

import java.util.function.Function;

/**
 * One entry of the advanced configuration table: its key, display group, short
 * label, a getter and a validating setter.
 */
public final class PerimeterAdvancedOption
{
	private final String key;
	private final String group;
	private final String label;
	private final Function<PerimeterAdvancedConfig, Number> getter;
	private final Setter setter;
	
	PerimeterAdvancedOption(String key, String group, String label,
		Function<PerimeterAdvancedConfig, Number> getter, Setter setter)
	{
		this.key = key;
		this.group = group;
		this.label = label;
		this.getter = getter;
		this.setter = setter;
	}
	
	public String key()
	{
		return key;
	}
	
	public String group()
	{
		return group;
	}
	
	public String label()
	{
		return label;
	}
	
	public double get(PerimeterAdvancedConfig config)
	{
		return getter.apply(config).doubleValue();
	}
	
	/**
	 * Validates and stores a new value.
	 *
	 * @throws IllegalArgumentException
	 *             if the value is out of range or breaks a cross-field rule.
	 */
	public void set(PerimeterAdvancedConfig config, double value)
	{
		setter.set(config, value);
	}
	
	/**
	 * @return the current value formatted without a trailing {@code .0} for
	 *         whole numbers, so that round-tripping a value reproduces what the
	 *         user typed.
	 */
	public String format(PerimeterAdvancedConfig config)
	{
		double value = get(config);
		
		if(value == Math.rint(value) && !Double.isInfinite(value))
			return Long.toString((long)value);
		
		return Double.toString(value);
	}
	
	@FunctionalInterface
	interface Setter
	{
		void set(PerimeterAdvancedConfig config, double value);
	}
}
