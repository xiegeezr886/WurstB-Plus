/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface WurstSubscribe
{
	/**
	 * 调用顺序：数值**越大越先**被调用（与
	 * {@link EventManager#add(Class, Listener, int)} 的约定一致）。
	 * 同优先级的订阅者按注册顺序调用（稳定排序）。
	 */
	int priority() default 0;
}
