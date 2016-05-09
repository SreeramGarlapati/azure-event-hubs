package com.microsoft.azure.eventhubs.extensions.batchSend;

import java.util.concurrent.CompletableFuture;

import com.microsoft.azure.eventhubs.EventData;

public interface ISender {

	CompletableFuture<Void> send(final Iterable<EventData> edatas);
	
	CompletableFuture<Void> send(final Iterable<EventData> edatas,final String partitionKey);
	
}
