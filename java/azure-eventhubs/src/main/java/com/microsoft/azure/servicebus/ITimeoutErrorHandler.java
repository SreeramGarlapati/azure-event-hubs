/*
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */
package com.microsoft.azure.servicebus;

// this helps recover client from - the underlying TransportStackStuck situation
public interface ITimeoutErrorHandler
{
	public void reportTimeoutError();
	
	public void resetTimeoutErrorCount();
}
