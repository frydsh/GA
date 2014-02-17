package com.google.analytics.tracking.android;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import com.google.android.gms.common.util.VisibleForTesting;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class EasyTracker {
	private static EasyTracker sInstance;
	static final int NUM_MILLISECONDS_TO_WAIT_FOR_OPEN_ACTIVITY = 1000;
	private boolean mIsEnabled = false;
	private String mTrackingId;
	private String mAppName;
	private String mAppVersion;
	private int mDispatchPeriod = 1800;
	private boolean mDebug;
	private Double mSampleRate;
	private boolean mIsAnonymizeIpEnabled;
	private boolean mIsReportUncaughtExceptionsEnabled;
	private Thread.UncaughtExceptionHandler mExceptionHandler;
	private boolean mIsAutoActivityTracking = false;

	private int mActivitiesActive = 0;
	private long mSessionTimeout;
	private long mLastOnStopTime;
	private Context mContext;
	private final Map<String, String> mActivityNameMap = new HashMap<String, String>();

	private Tracker mTracker = null;
	private ParameterLoader mParameterFetcher;
	private GoogleAnalytics mAnalyticsInstance;
	private ServiceManager mServiceManager;
	private Clock mClock;
	private Timer mTimer;
	private TimerTask mTimerTask;
	private boolean mIsInForeground = false;

	private EasyTracker() {
		mClock = new Clock() {
			public long currentTimeMillis() {
				return System.currentTimeMillis();
			}
		};
	}

	public static EasyTracker getInstance() {
		if (sInstance == null) {
			sInstance = new EasyTracker();
		}
		return sInstance;
	}

	public static Tracker getTracker() {
		if (getInstance().mContext == null) {
			throw new IllegalStateException("You must call EasyTracker.getInstance().setContext(context) or startActivity(activity) before calling getTracker()");
		}

		return getInstance().mTracker;
	}

	boolean checkForNewSession() {
		return mSessionTimeout == 0L || (mSessionTimeout > 0L && mClock.currentTimeMillis() > mLastOnStopTime + mSessionTimeout);
	}

	private void loadParameters() {
		mTrackingId = mParameterFetcher.getString("ga_trackingId");
		if (TextUtils.isEmpty(mTrackingId)) {
			mTrackingId = mParameterFetcher.getString("ga_api_key");
			if (TextUtils.isEmpty(mTrackingId)) {
				Log.e("EasyTracker requested, but missing required ga_trackingId");
				mTracker = new NoopTracker();
				return;
			}
		}
		mIsEnabled = true;
		mAppName = mParameterFetcher.getString("ga_appName");
		mAppVersion = mParameterFetcher.getString("ga_appVersion");
		mDebug = mParameterFetcher.getBoolean("ga_debug");

		mSampleRate = mParameterFetcher.getDoubleFromString("ga_sampleFrequency");
		if (mSampleRate == null) {
			mSampleRate = new Double(mParameterFetcher.getInt("ga_sampleRate", 100));
		}
		mDispatchPeriod = mParameterFetcher.getInt("ga_dispatchPeriod", 1800);
		mSessionTimeout = mParameterFetcher.getInt("ga_sessionTimeout", 30) * 1000;
		mIsAutoActivityTracking = mParameterFetcher.getBoolean("ga_autoActivityTracking") || mParameterFetcher.getBoolean("ga_auto_activity_tracking");

		mIsAnonymizeIpEnabled = mParameterFetcher.getBoolean("ga_anonymizeIp");
		mIsReportUncaughtExceptionsEnabled = mParameterFetcher.getBoolean("ga_reportUncaughtExceptions");

		mTracker = mAnalyticsInstance.getTracker(mTrackingId);
		if (!TextUtils.isEmpty(mAppName)) {
			Log.i("setting appName to " + mAppName);
			mTracker.setAppName(mAppName);
		}

		if (mAppVersion != null) {
			mTracker.setAppVersion(mAppVersion);
		}
		mTracker.setAnonymizeIp(mIsAnonymizeIpEnabled);
		mTracker.setSampleRate(mSampleRate.doubleValue());
		mAnalyticsInstance.setDebug(mDebug);
		mServiceManager.setDispatchPeriod(mDispatchPeriod);

		if (mIsReportUncaughtExceptionsEnabled) {
			Thread.UncaughtExceptionHandler newHandler = mExceptionHandler;
			if (newHandler == null) {
				ExceptionReporter reporter = new ExceptionReporter(
						mTracker, mServiceManager,
						Thread.getDefaultUncaughtExceptionHandler(),
						mContext);

				newHandler = reporter;
			}
			Thread.setDefaultUncaughtExceptionHandler(newHandler);
		}
	}

	@VisibleForTesting
	void setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler handler) {
		mExceptionHandler = handler;
	}

	public void setContext(Context ctx) {
		if (ctx == null) {
			Log.e("Context cannot be null");
		} else {
			ServiceManager sm = GAServiceManager.getInstance();
			setContext(ctx,
					new ParameterLoaderImpl(ctx.getApplicationContext()),
					GoogleAnalytics.getInstance(ctx.getApplicationContext()),
					sm);
		}
	}

	@VisibleForTesting
	void setContext(Context ctx, ParameterLoader parameterLoader,
			GoogleAnalytics ga, ServiceManager serviceManager) {
		if (ctx == null) {
			Log.e("Context cannot be null");
		}
		if (mContext == null) {
			mContext = ctx.getApplicationContext();
			mAnalyticsInstance = ga;
			mServiceManager = serviceManager;
			mParameterFetcher = parameterLoader;
			loadParameters();
		}
	}

	public void activityStart(Activity activity) {
		setContext(activity);
		if (!mIsEnabled) {
			return;
		}

		clearExistingTimer();

		if (!mIsInForeground && mActivitiesActive == 0 && checkForNewSession()) {
			mTracker.setStartSession(true);
		}

		mIsInForeground = true;
		mActivitiesActive += 1;
		if (mIsAutoActivityTracking) {			
			mTracker.sendView(getActivityName(activity));
		}
	}

	public void activityStop(Activity activity) {
		setContext(activity);
		if (!mIsEnabled) {
			return;
		}
		mActivitiesActive -= 1;

		mActivitiesActive = Math.max(0, mActivitiesActive);

		mLastOnStopTime = mClock.currentTimeMillis();

		if (mActivitiesActive == 0) {
			clearExistingTimer();

			mTimerTask = new NotInForegroundTimerTask();
			mTimer = new Timer("waitForActivityStart");
			mTimer.schedule(mTimerTask, NUM_MILLISECONDS_TO_WAIT_FOR_OPEN_ACTIVITY);
		}
	}

	public void dispatch() {
		if (mIsEnabled) {			
			mServiceManager.dispatch();
		}
	}

	private synchronized void clearExistingTimer() {
		if (mTimer != null) {
			mTimer.cancel();
			mTimer = null;
		}
	}

	private String getActivityName(Activity activity) {
		String canonicalName = activity.getClass().getCanonicalName();
		if (mActivityNameMap.containsKey(canonicalName)) {
			return mActivityNameMap.get(canonicalName);
		}
		String name = mParameterFetcher.getString(canonicalName);
		if (name == null) {
			name = canonicalName;
		}
		mActivityNameMap.put(canonicalName, name);
		return name;
	}

	@VisibleForTesting
	static void clearTracker() {
		sInstance = null;
	}

	@VisibleForTesting
	void setClock(Clock clock) {
		mClock = clock;
	}

	@VisibleForTesting
	int getActivitiesActive() {
		return mActivitiesActive;
	}

	private class NotInForegroundTimerTask extends TimerTask {
		private NotInForegroundTimerTask() {
		}

		public void run() {
			EasyTracker.this.mIsInForeground = false;
		}
	}

	class NoopTracker extends Tracker {
		private String mAppId;
		private String mAppInstallerId;
		private double mSampleRate = 100.0D;
		private boolean mIsAnonymizeIp;
		private boolean mIsUseSecure;
		private ExceptionParser mExceptionParser;

		NoopTracker() {
		}

		public void setStartSession(boolean startSession) {
		}

		public void setAppName(String appName) {
		}

		public void setAppVersion(String appVersion) {
		}

		public void setAppScreen(String appScreen) {
		}

		public void sendView() {
		}

		public void sendView(String appScreen) {
		}

		public void sendEvent(String category, String action, String label, Long value) {
		}

		public void sendTransaction(Transaction transaction) {
		}

		public void sendException(String description, boolean fatal) {
		}

		public void sendException(String threadName, Throwable exception, boolean fatal) {
		}

		public void sendTiming(String category, long intervalInMilliseconds, String name, String label) {
		}

		public void sendSocial(String network, String action, String target) {
		}

		public void close() {
		}

		public void send(String hitType, Map<String, String> params) {
		}

		public String get(String key) {
			return "";
		}

		public void set(String key, String value) {
		}

		public String getTrackingId() {
			return "";
		}

		public void setAnonymizeIp(boolean anonymizeIp) {
			mIsAnonymizeIp = anonymizeIp;
		}

		public boolean isAnonymizeIpEnabled() {
			return mIsAnonymizeIp;
		}

		public void setSampleRate(double sampleRate) {
			mSampleRate = sampleRate;
		}

		public double getSampleRate() {
			return mSampleRate;
		}

		public void setUseSecure(boolean useSecure) {
			mIsUseSecure = useSecure;
		}

		public boolean isUseSecure() {
			return mIsUseSecure;
		}

		public void setReferrer(String referrer) {
		}

		public void setCampaign(String campaign) {
		}

		public void setAppId(String appId) {
			mAppId = appId;
		}

		public String getAppId() {
			return mAppId;
		}

		public void setAppInstallerId(String appInstallerId) {
			mAppInstallerId = appInstallerId;
		}

		public String getAppInstallerId() {
			return mAppInstallerId;
		}

		public void setExceptionParser(ExceptionParser exceptionParser) {
			mExceptionParser = exceptionParser;
		}

		public ExceptionParser getExceptionParser() {
			return mExceptionParser;
		}

		public Map<String, String> constructEvent(String category, String action, String label, Long value) {
			return new HashMap<String, String>();
		}

		public Map<String, String> constructTransaction(Transaction trans) {
			return new HashMap<String, String>();
		}

		public Map<String, String> constructException(String exceptionDescription, boolean fatal) {
			return new HashMap<String, String>();
		}

		public Map<String, String> constructRawException(String threadName, Throwable exception, boolean fatal) {
			return new HashMap<String, String>();
		}

		public Map<String, String> constructTiming(String category, long intervalInMilliseconds, String name, String label) {
			return new HashMap<String, String>();
		}

		public Map<String, String> constructSocial(String network, String action, String target) {
			return new HashMap<String, String>();
		}

		public void setCustomDimension(int slot, String value) {
		}

		public void setCustomMetric(int slot, Long value) {
		}

		public void setCustomDimensionsAndMetrics(Map<Integer, String> dimensions, Map<Integer, Long> metrics) {
		}
	}
}