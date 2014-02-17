package com.google.analytics.tracking.android;

abstract interface Clock {
	public abstract long currentTimeMillis();
}