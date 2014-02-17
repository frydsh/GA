package com.google.analytics.tracking.android;

abstract interface AnalyticsStoreStateListener {
	public abstract void reportStoreIsEmpty(boolean isEmpty);
}