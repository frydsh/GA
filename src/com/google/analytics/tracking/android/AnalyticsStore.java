package com.google.analytics.tracking.android;

import com.google.android.gms.analytics.internal.Command;
import java.util.Collection;
import java.util.Map;

abstract interface AnalyticsStore {
	public abstract void setDispatch(boolean dispatch);

	public abstract void putHit(
			Map<String, String> wireFormatParams,
			long hitTimeInMilliseconds, String path,
			Collection<Command> commands);

	public abstract void clearHits(long appId);

	public abstract void dispatch();

	public abstract void close();
}