package com.google.analytics.tracking.android;

abstract interface ParameterLoader {
	public abstract String getString(String paramString);

	public abstract Double getDoubleFromString(String key);

	public abstract boolean getBoolean(String key);

	public abstract boolean isBooleanKeyPresent(String key);

	public abstract int getInt(String key, int defaultValue);
}