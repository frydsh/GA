package com.google.analytics.tracking.android;

import com.google.android.gms.analytics.internal.Command;
import java.util.List;
import java.util.Map;

abstract interface AnalyticsClient {
	public abstract void sendHit(Map<String, String> wireParams, long hitTimeInMilliseconds, String path, List<Command> commands);

	public abstract void clearHits();

	public abstract void connect();

	public abstract void disconnect();
}