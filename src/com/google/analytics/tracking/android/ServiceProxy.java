package com.google.analytics.tracking.android;

import com.google.android.gms.analytics.internal.Command;
import java.util.List;
import java.util.Map;

abstract interface ServiceProxy {
	public abstract void putHit(Map<String, String> wireFormatParams,
			long hitTimeInMilliseconds, String path, List<Command> commands);

	public abstract void clearHits();

	public abstract void dispatch();

	public abstract void createService();
}