package com.google.analytics.tracking.android;

import java.util.List;

abstract interface Dispatcher {
	public abstract int dispatchHits(List<Hit> hits);
	public abstract boolean okToDispatch();
}