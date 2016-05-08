/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */
package com.microsoft.azure.eventhubs.extensions;

import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.microsoft.azure.eventhubs.EventData;
import com.microsoft.azure.eventhubs.EventHubClient;

public class HighThruputSender {

	public final static int BATCH_FLUSH_INTERVAL_MS = 20;
	public final static int MAX_BATCH_SIZE = 5100;
	public final static int MAX_MSG_SIZE = 210000;
	
	private final static Logger logger = Logger.getLogger(HighThruputSender.class.getName());
	
	private final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
	
	private final EventHubClient sender;
	private final ConcurrentLinkedQueue<SendWork> pendingSends;
	
	private HighThruputSender(final EventHubClient eventHubClient) {
		this.sender = eventHubClient;
		this.pendingSends = new ConcurrentLinkedQueue<SendWork>();
		scheduler.scheduleWithFixedDelay(new Sender(), 0, BATCH_FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
	}
	
	public static HighThruputSender create(final EventHubClient eventHubClient) {
		return new HighThruputSender(eventHubClient);
	}
	
	public CompletableFuture<Void> send(final EventData edata) {
		CompletableFuture<Void> future = new CompletableFuture<Void>();
		this.pendingSends.add(new SendWork(edata, future));
		return future;
	}
	
	private class Sender implements Runnable {

		@Override
		public void run() {
			final LinkedList<EventData> events = new LinkedList<EventData>();
			final LinkedList<CompletableFuture<Void>> futures = new LinkedList<CompletableFuture<Void>>();

			int batchSize = 0;
			int aggregatedSize = 0;
			while (pendingSends.peek() != null
					&& batchSize <= MAX_BATCH_SIZE 
					&& (aggregatedSize + pendingSends.peek().getEventData().getBody().length) < MAX_MSG_SIZE)
			{
				SendWork work = pendingSends.poll();
				events.add(work.getEventData());
				futures.add(work.getSendFuture());
				batchSize++;
				aggregatedSize += work.getEventData().getBody().length;
				Map<String, String> properties = work.getEventData().getProperties();
				if (properties != null)
				{
					for (Map.Entry<String, String> property : properties.entrySet())
					{
						aggregatedSize += ((property.getKey().length() + property.getValue().length()) * 2); 
					}
				}
			}
			
			if(!events.isEmpty())
			{
				CompletableFuture<Void> realSend = sender.send(events);
				if (logger.isLoggable(Level.FINE))
					logger.log(Level.FINE, String.format(Locale.US, "Sending batchSize: %s, total messages Size: %s", batchSize, aggregatedSize));
				
				realSend.thenApplyAsync(new Function<Void, Void>() {
					@Override
					public Void apply(Void t) {
						for (CompletableFuture<Void> work: futures)
							work.complete(t);
						return null;
					}})
				.exceptionally(new Function<Throwable, Void>() {
					@Override
					public Void apply(Throwable t) {
						for (CompletableFuture<Void> work: futures)
							work.completeExceptionally(t);
						return null;
					}});
			}
		}
		
	}
	
	private static class SendWork {
		
		private final EventData eventData;
		private final CompletableFuture<Void> pendingSend;
		
		private SendWork(final EventData edata, final CompletableFuture<Void> pendingSend) {
			this.eventData = edata;
			this.pendingSend = pendingSend;
		}
		
		public EventData getEventData() {
			return this.eventData;
		}
		
		public CompletableFuture<Void> getSendFuture() {
			return this.pendingSend;
		}
		
	}
	
}
