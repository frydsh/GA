package com.google.analytics.tracking.android;

public abstract interface ExceptionParser {
	public abstract String getDescription(String threadName, Throwable t);
}