/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */
package com.microsoft.azure.servicebus.amqp;

import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.qpid.proton.engine.BaseHandler;
import org.apache.qpid.proton.engine.Event;
import org.apache.qpid.proton.reactor.Reactor;

import com.microsoft.azure.servicebus.ClientConstants;

public class ReactorHandler extends BaseHandler
{
	private static final Logger TRACE_LOGGER = Logger.getLogger(ClientConstants.SERVICEBUS_CLIENT_TRACE);

	private final ConcurrentSkipListSet<Runnable> sendWorkers = new ConcurrentSkipListSet<Runnable>();
	
	@Override
	public void onReactorInit(Event e)
	{ 
		if(TRACE_LOGGER.isLoggable(Level.FINE))
		{
			TRACE_LOGGER.log(Level.FINE, "reactor.onReactorInit");
		}

		final Reactor reactor = e.getReactor();
		reactor.setTimeout(ClientConstants.REACTOR_IO_POLL_TIMEOUT);
	}

	@Override
	public void onReactorFinal(Event e)
	{
		if(TRACE_LOGGER.isLoggable(Level.FINE))
		{
			TRACE_LOGGER.log(Level.FINE, "reactor.onReactorFinal");
		}
	}
	
	@Override
	public void onReactorQuiesced(Event e)
	{
		// System.out.println(this.sendWorkers.size());
		
		for(Runnable sendWorker : sendWorkers)
		{
			sendWorker.run();
		}
	}
	
	public void registerSendWorker(final Runnable sendWorker)
	{
		this.sendWorkers.add(sendWorker);
	}
	
	public void unregisterSendWorker(final Runnable sendWorker)
	{
		this.sendWorkers.remove(sendWorker);
	}
}
