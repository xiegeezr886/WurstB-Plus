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
		Consumer<Object> caller;
		try
		{
			caller = createFastCaller(target, method, eventClass);
		}catch(Throwable t)
		{
			// Private or package-private handlers cannot always be bound with
			// a lambda metafactory. Reflection still reaches them, so the
			// annotation keeps working instead of failing registration.
			caller = createReflectiveCaller(target, method);
		}

		subscriberCaller = caller;
		this.eventClass = eventClass;
		this.target = target;
		this.method = method;
		targetClass = target.getClass();
		signature = method.getDeclaringClass().getName() + "."
			+ method.getName() + "(" + eventClass.getName() + ")";
	}

	@SuppressWarnings("unchecked")
	private static Consumer<Object> createFastCaller(Object target,
		Method method, Class<?> eventClass) throws Throwable
	{
		MethodHandles.Lookup lookup;
		try
		{
			lookup = MethodHandles.privateLookupIn(method.getDeclaringClass(),
				MethodHandles.lookup());
		}catch(IllegalAccessException e)
		{
			lookup = MethodHandles.lookup();
		}

		CallSite callsite = LambdaMetafactory.metafactory(lookup, "accept",
			MethodType.methodType(Consumer.class, target.getClass()),
			MethodType.methodType(void.class, Object.class),
			lookup.unreflect(method),
			MethodType.methodType(void.class, eventClass));

		return (Consumer<Object>)callsite.getTarget().invokeWithArguments(target);
	}

	private static Consumer<Object> createReflectiveCaller(Object target,
		Method method)
	{
		try
		{
			method.setAccessible(true);
		}catch(RuntimeException ignored)
		{
			// Let invoke() surface the real access problem.
		}

		return event -> {
			try
			{
				method.invoke(target, event);
			}catch(ReflectiveOperationException e)
			{
				throw new RuntimeException(e);
			}
		};
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
