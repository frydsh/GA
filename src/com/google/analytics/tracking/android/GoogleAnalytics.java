package com.google.analytics.tracking.android;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import com.google.android.gms.common.util.VisibleForTesting;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class GoogleAnalytics implements TrackerHandler {
	private boolean mDebug;
	private AnalyticsThread mThread;
	private Context mContext;
	private Tracker mDefaultTracker;
	private AdHitIdGenerator mAdHitIdGenerator;
	private volatile String mClientId;
	private volatile Boolean mAppOptOut;
	private final Map<String, Tracker> mTrackers = new HashMap<String, Tracker>();
	private String mLastTrackingId;
	private static GoogleAnalytics sInstance;

	@VisibleForTesting
	GoogleAnalytics() {
	}

	private GoogleAnalytics(Context context) {
		this(context, GAThread.getInstance(context));
	}

	private GoogleAnalytics(Context context, AnalyticsThread thread) {
		if (context == null) {
			throw new IllegalArgumentException("context cannot be null");
		}
		
		mContext = context.getApplicationContext();
		mThread = thread;
		mAdHitIdGenerator = new AdHitIdGenerator();
		
		mThread.requestAppOptOut(new AppOptOutCallback() {
			
			public void reportAppOptOut(boolean optOut) {
				mAppOptOut = optOut;
			}
		});
		
		mThread.requestClientId(new AnalyticsThread.ClientIdCallback() {
			
			public void reportClientId(String clientId) {
				mClientId = clientId;
			}
		});
	}

	public static GoogleAnalytics getInstance(Context context) {
		synchronized (GoogleAnalytics.class) {
			if (sInstance == null) {
				sInstance = new GoogleAnalytics(context);
			}
			return sInstance;
		}
	}

	static GoogleAnalytics getInstance() {
		synchronized (GoogleAnalytics.class) {
			return sInstance;
		}
	}

	@VisibleForTesting
	static GoogleAnalytics getNewInstance(Context context, AnalyticsThread thread) {
		synchronized (GoogleAnalytics.class) {
			if (sInstance != null) {
				sInstance.close();
			}
			sInstance = new GoogleAnalytics(context, thread);
			return sInstance;
		}
	}

	@VisibleForTesting
	static void clearInstance() {
		synchronized (GoogleAnalytics.class) {
			sInstance = null;
		}
	}

	public void setDebug(boolean debug) {
		GAUsage.getInstance().setUsage(GAUsage.Field.SET_DEBUG);
		mDebug = debug;
		Log.setDebug(debug);
	}

	public boolean isDebugEnabled() {
		GAUsage.getInstance().setUsage(GAUsage.Field.GET_DEBUG);
		return mDebug;
	}

	public Tracker getTracker(String trackingId) {
		synchronized (this) {
			if (trackingId == null) {
				throw new IllegalArgumentException("trackingId cannot be null");
			}
			Tracker tracker = mTrackers.get(trackingId);

			if (tracker == null) {
				tracker = new Tracker(trackingId, this);
				mTrackers.put(trackingId, tracker);
				if (mDefaultTracker == null) {
					mDefaultTracker = tracker;
				}
			}
			GAUsage.getInstance().setUsage(GAUsage.Field.GET_TRACKER);
			return tracker;
		}
	}

	public Tracker getDefaultTracker() {
		synchronized (this) {
			GAUsage.getInstance().setUsage(GAUsage.Field.GET_DEFAULT_TRACKER);
			return mDefaultTracker;
		}
	}

	public void setDefaultTracker(Tracker tracker) {
		synchronized (this) {
			GAUsage.getInstance().setUsage(GAUsage.Field.SET_DEFAULT_TRACKER);
			mDefaultTracker = tracker;
		}
	}

	public void closeTracker(Tracker tracker) {
		synchronized (this) {
			mTrackers.values().remove(tracker);
			if (tracker == mDefaultTracker) {				
				mDefaultTracker = null;
			}
		}
	}

	public void sendHit(Map<String, String> hit) {
		synchronized (this) {
			if (hit == null) {
				throw new IllegalArgumentException("hit cannot be null");
			}
			hit.put("language", Utils.getLanguage(Locale.getDefault()));
			hit.put("adSenseAdMobHitId", Integer.toString(mAdHitIdGenerator.getAdHitId()));
			hit.put("screenResolution",
					mContext.getResources().getDisplayMetrics().widthPixels
					+ "x"
					+ mContext.getResources().getDisplayMetrics().heightPixels);

			hit.put("usage", GAUsage.getInstance().getAndClearSequence());

			GAUsage.getInstance().getAndClearUsage();

			mThread.sendHit(hit);

			mLastTrackingId = hit.get("trackingId");
		}
	}

	@VisibleForTesting
	void close() {
	}

	String getTrackingIdForAds() {
		return mLastTrackingId;
	}

	String getClientIdForAds() {
		if (mClientId == null) {
			return null;
		}
		return mClientId.toString();
	}

	public void setAppOptOut(boolean optOut) {
		GAUsage.getInstance().setUsage(GAUsage.Field.SET_APP_OPT_OUT);
		mAppOptOut = Boolean.valueOf(optOut);
		mThread.setAppOptOut(optOut);
	}

	@VisibleForTesting
	Boolean getAppOptOut() {
		return mAppOptOut;
	}

	public void requestAppOptOut(AppOptOutCallback callback) {
		GAUsage.getInstance().setUsage(GAUsage.Field.REQUEST_APP_OPT_OUT);
		if (mAppOptOut != null) {			
			callback.reportAppOptOut(mAppOptOut.booleanValue());
		} else {
			mThread.requestAppOptOut(callback);
		}
	}

	public static abstract interface AppOptOutCallback {
		public abstract void reportAppOptOut(boolean paramBoolean);
	}
}