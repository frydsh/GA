package com.google.analytics.tracking.android;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;

class ParameterLoaderImpl implements ParameterLoader {
	
	private final Context mContext;

	public ParameterLoaderImpl(Context context) {
		if (context == null) {
			throw new NullPointerException("Context cannot be null");
		}
		mContext = context.getApplicationContext();
	}

	private int getResourceIdForType(String key, String type) {
		if (mContext == null) {
			return 0;
		}
		return mContext.getResources().getIdentifier(key, type, mContext.getPackageName());
	}

	public String getString(String key) {
		int id = getResourceIdForType(key, "string");
		if (id == 0) {
			return null;
		}
		return mContext.getString(id);
	}

	public boolean getBoolean(String key) {
		int id = getResourceIdForType(key, "bool");
		if (id == 0) {
			return false;
		}
		return "true".equalsIgnoreCase(mContext.getString(id));
	}

	public int getInt(String key, int defaultValue) {
		int id = getResourceIdForType(key, "integer");
		if (id == 0) {			
			return defaultValue;
		}
		try {
			return Integer.parseInt(mContext.getString(id));
		} catch (NumberFormatException e) {
			Log.w("NumberFormatException parsing " + mContext.getString(id));
		}
		return defaultValue;
	}

	public boolean isBooleanKeyPresent(String key) {
		return getResourceIdForType(key, "bool") != 0;
	}

	public Double getDoubleFromString(String key) {
		String value = getString(key);
		if (TextUtils.isEmpty(value))
			return null;
		try {
			return Double.valueOf(Double.parseDouble(value));
		} catch (NumberFormatException e) {
			Log.w("NumberFormatException parsing " + value);
		}
		return null;
	}
}