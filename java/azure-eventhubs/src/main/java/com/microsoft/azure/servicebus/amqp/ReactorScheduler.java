/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */
package com.microsoft.azure.servicebus.amqp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.HashSet;

import org.apache.qpid.proton.engine.BaseHandler;
import org.apache.qpid.proton.engine.Event;
import org.apache.qpid.proton.reactor.Reactor;
import org.apache.qpid.proton.reactor.Selectable;
import org.apache.qpid.proton.reactor.Selectable.Callback;

public final class ReactorScheduler
{
	private final Reactor reactor;
	private final Pipe ioSignal;
	private final ConcurrentLinkedQueue<BaseHandler> workQueue;

	public ReactorScheduler(final Reactor reactor) throws IOException
	{
		this.reactor = reactor;
		this.ioSignal = Pipe.open(); // TODO: we need a hook for Reactor.IO.pipe()
		this.workQueue = new ConcurrentLinkedQueue<BaseHandler>();
		
		initializeSelectable();
	}
	
	private void initializeSelectable()
	{
		Selectable schedulerSelectable = this.reactor.selectable();
		
		schedulerSelectable.setChannel(this.ioSignal.source());
		schedulerSelectable.onReadable(new ScheduleHandler(this.ioSignal, this.workQueue));
		
		schedulerSelectable.onFree(new Callback()
		{
			@Override public void run(Selectable selectable)
			{
				try
				{
					selectable.getChannel().close();
				}
				catch (IOException ignore)
				{
				}
			} 
		});
		
		schedulerSelectable.setReading(true);
		this.reactor.update(schedulerSelectable);
	}

	public void schedule(final BaseHandler timerCallback) throws IOException
	{
		this.workQueue.offer(timerCallback);
		this.signalWorkQueue();
	}
	
	public void schedule(final int delay, final BaseHandler timerCallback) throws IOException
	{
		this.workQueue.offer(new DelayHandler(this.reactor, delay, timerCallback));
		this.signalWorkQueue();
	}
	
	private void signalWorkQueue() throws IOException
	{
		this.ioSignal.sink().write(ByteBuffer.allocate(1));
	}
	
	private final class DelayHandler extends BaseHandler
	{
		final int delay;
		final BaseHandler timerCallback;
		final Reactor reactor;
		
		public DelayHandler(final Reactor reactor, final int delay, final BaseHandler timerCallback)
		{
			this.delay = delay;
			this.timerCallback = timerCallback;
			this.reactor = reactor;
		}
		
		@Override
		public void onTimerTask(Event e) 
		{
			this.reactor.schedule(this.delay, this.timerCallback);
		}
	}
	
	private final class ScheduleHandler implements Callback
	{
		final ConcurrentLinkedQueue<BaseHandler> workQueue;
		final Pipe ioSignal;
		
		public ScheduleHandler(final Pipe ioSignal, final ConcurrentLinkedQueue<BaseHandler> workQueue)
		{
			this.workQueue = workQueue;
			this.ioSignal = ioSignal;
		}

		@Override
		public void run(Selectable selectable)
		{
			try
			{
				this.ioSignal.source().read(ByteBuffer.allocate(1024));
			}
			catch(IOException ioException)
			{
				throw new RuntimeException(ioException);
			}
			
			final HashSet<BaseHandler> completedWork = new HashSet<BaseHandler>();
			
			BaseHandler topWork = this.workQueue.poll(); 
			while (topWork != null)
			{
				if (!completedWork.contains(topWork))
				{
					topWork.onTimerTask(null);
					completedWork.add(topWork);
				}
				
				topWork = this.workQueue.poll();
			}
		}	
	}
}
