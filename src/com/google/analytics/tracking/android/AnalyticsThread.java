package com.google.analytics.tracking.android;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.analytics.tracking.android.GoogleAnalytics.AppOptOutCallback;

abstract interface AnalyticsThread {
	public abstract void sendHit(Map<String, String> hit);

	public abstract void dispatch();

	public abstract void setAppOptOut(boolean appOptOut);

	public abstract void requestAppOptOut(AppOptOutCallback callback);

	public abstract void requestClientId(ClientIdCallback callback);

	public abstract LinkedBlockingQueue<Runnable> getQueue();

	public abstract Thread getThread();

	public static abstract interface ClientIdCallback {
		public abstract void reportClientId(String clientId);
	}
}