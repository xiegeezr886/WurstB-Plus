/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.event;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.function.Consumer;

public final class WurstSubscriber
{
	private final Consumer<Object> subscriberCaller;
	private final Class<?> eventClass;
	private final Class<?> targetClass;
	private final Object target;
	private final Method method;
	private final String signature;

	public WurstSubscriber(Object target, Method method)
	{
		this(target, method, getEvent(method));
	}

	@SuppressWarnings("unchecked")
	public WurstSubscriber(Object target, Method method, Class<?> eventClass)
	{
		try
		{
			MethodHandles.Lookup lookup = MethodHandles.lookup();
			CallSite callsite = LambdaMetafactory.metafactory(lookup,
				"accept",
				MethodType.methodType(Consumer.class, target.getClass()),
				MethodType.methodType(void.class, Object.class),
				lookup.unreflect(method),
				MethodType.methodType(void.class, eventClass));

			subscriberCaller =
				(Consumer<Object>)callsite.getTarget().invokeWithArguments(target);

			this.eventClass = eventClass;
			this.target = target;
			this.method = method;
			targetClass = target.getClass();
			signature = method.getDeclaringClass().getName() + "."
				+ method.getName() + "("
				+ eventClass.getName() + ")";
		}catch(Throwable t)
		{
			throw new RuntimeException(t);
		}
	}

	private static Class<?> getEvent(Method method)
	{
		Parameter[] parameters = method.getParameters();
		if(parameters.length == 0)
			throw new RuntimeException(
				"Tried to create Subscriber with no parameters");

		Class<?> paramType = parameters[0].getType();
		if(!Event.class.isAssignableFrom(paramType))
			throw new RuntimeException(
				"Tried to create Subscriber with non-Event parameter: "
					+ paramType.getName());

		return paramType;
	}

	public void callSubscriber(Event event)
	{
		subscriberCaller.accept(event);
	}

	public Class<?> getEventClass()
	{
		return eventClass;
	}

	public Class<?> getTargetClass()
	{
		return targetClass;
	}

	public String getSignature()
	{
		return signature;
	}

	public boolean isTarget(Object candidate)
	{
		return target == candidate;
	}

	@Override
	public int hashCode()
	{
		return 31 * System.identityHashCode(target) + method.hashCode();
	}

	@Override
	public boolean equals(Object obj)
	{
		return obj instanceof WurstSubscriber other && target == other.target
			&& method.equals(other.method);
	}
}
