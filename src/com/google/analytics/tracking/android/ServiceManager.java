package com.google.analytics.tracking.android;

public abstract interface ServiceManager {
	public abstract void dispatch();

	public abstract void setDispatchPeriod(int dispatchPeriodInSeconds);

	public abstract void updateConnectivityStatus(boolean connected);
}