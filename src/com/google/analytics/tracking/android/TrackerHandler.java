package com.google.analytics.tracking.android;

import java.util.Map;

abstract interface TrackerHandler {
	public abstract void closeTracker(Tracker tracker);

	public abstract void sendHit(Map<String, String> hits);
}