package com.google.analytics.tracking.android;

import android.text.TextUtils;
import com.google.android.gms.common.util.VisibleForTesting;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Tracker {
	private static final DecimalFormat DF = new DecimalFormat("0.######", new DecimalFormatSymbols(Locale.US));
	private final TrackerHandler mHandler;
	private final SimpleModel mModel;
	private volatile ExceptionParser mExceptionParser;
	private volatile boolean mIsTrackerClosed = false;
	private volatile boolean mIsTrackingStarted = false;
	static final long TIME_PER_TOKEN_MILLIS = 2000;
	static final long MAX_TOKENS = 120000;
	private long mTokens = 120000;
	private long mLastTrackTime;
	private boolean mIsThrottlingEnabled = true;

	Tracker() {
		this.mHandler = null;
		this.mModel = null;
	}

	Tracker(String trackingId, TrackerHandler handler) {
		if (trackingId == null) {
			throw new IllegalArgumentException("trackingId cannot be null");
		}
		this.mHandler = handler;
		this.mModel = new SimpleModel();

		this.mModel.set("trackingId", trackingId);

		this.mModel.set("sampleRate", "100");

		this.mModel.setForNextHit("sessionControl", "start");

		this.mModel.set("useSecure", Boolean.toString(true));
	}

	private void assertTrackerOpen() {
		if (this.mIsTrackerClosed)
			throw new IllegalStateException("Tracker closed");
	}

	public void setStartSession(boolean startSession) {
		assertTrackerOpen();
		GAUsage.getInstance().setUsage(GAUsage.Field.SET_START_SESSION);
		this.mModel.setForNextHit("sessionControl", startSession ? "start" : null);
	}

	public void setAppName(String appName) {
		if (this.mIsTrackingStarted) {
			Log.wDebug("Tracking already started, setAppName call ignored");
			return;
		}
		if (TextUtils.isEmpty(appName)) {
			Log.wDebug("setting appName to empty value not allowed, call ignored");
			return;
		}
		GAUsage.getInstance().setUsage(GAUsage.Field.SET_APP_NAME);
		this.mModel.set("appName", appName);
	}

	public void setAppVersion(String appVersion) {
		if (this.mIsTrackingStarted) {
			Log.wDebug("Tracking already started, setAppVersion call ignored");
			return;
		}
		GAUsage.getInstance().setUsage(GAUsage.Field.SET_APP_VERSION);
		this.mModel.set("appVersion", appVersion);
	}

	public void setAppScreen(String appScreen) {
		assertTrackerOpen();
		GAUsage.getInstance().setUsage(GAUsage.Field.SET_APP_SCREEN);
		this.mModel.set("description", appScreen);
	}

	@Deprecated
	public void trackView() {
		sendView();
	}

	public void sendView() {
		assertTrackerOpen();
		if (TextUtils.isEmpty(this.mModel.get("description"))) {
			throw new IllegalStateException("trackView requires a appScreen to be set");
		}
		GAUsage.getInstance().setUsage(GAUsage.Field.TRACK_VIEW);
		internalSend("appview", null);
	}

	@Deprecated
	public void trackView(String appScreen) {
		sendView(appScreen);
	}

	public void sendView(String appScreen) {
		assertTrackerOpen();
		if (TextUtils.isEmpty(appScreen)) {
			throw new IllegalStateException("trackView requires a appScreen to be set");
		}
		GAUsage.getInstance().setUsage(GAUsage.Field.TRACK_VIEW_WITH_APPSCREEN);
		this.mModel.set("description", appScreen);
		internalSend("appview", null);
	}

	@Deprecated
	public void trackEvent(String category, String action, String label, Long value) {
		sendEvent(category, action, label, value);
	}

	public void sendEvent(String category, String action, String label, Long value) {
		assertTrackerOpen();
		GAUsage.getInstance().setUsage(GAUsage.Field.TRACK_EVENT);
		GAUsage.getInstance().setDisableUsage(true);
		internalSend("event", constructEvent(category, action, label, value));
		GAUsage.getInstance().setDisableUsage(false);
	}

	@Deprecated
	public void trackTransaction(Transaction transaction) {
		sendTransaction(transaction);
	}

	public void sendTransaction(Transaction transaction) {
		assertTrackerOpen();
		GAUsage.getInstance().setUsage(GAUsage.Field.TRACK_TRANSACTION);
		GAUsage.getInstance().setDisableUsage(true);
		internalSend("tran", constructTransaction(transaction));

		for (Transaction.Item item : transaction.getItems()) {
			internalSend("item", constructItem(item, transaction));
		}
		GAUsage.getInstance().setDisableUsage(false);
	}

	@Deprecated
	public void trackException(String description, boolean fatal) {
		sendException(description, fatal);
	}

	public void sendException(String description, boolean fatal) {
		assertTrackerOpen();
		GAUsage.getInstance().setUsage(GAUsage.Field.TRACK_EXCEPTION_WITH_DESCRIPTION);
		GAUsage.getInstance().setDisableUsage(true);
		internalSend("exception", constructException(description, fatal));
		GAUsage.getInstance().setDisableUsage(false);
	}

	@Deprecated
	public void trackException(String threadName, Throwable exception, boolean fatal) {
		sendException(threadName, exception, fatal);
	}

	public void sendException(String threadName, Throwable exception, boolean fatal) {
		assertTrackerOpen();

		GAUsage.getInstance().setUsage(GAUsage.Field.TRACK_EXCEPTION_WITH_THROWABLE);
		String description;
		if (this.mExceptionParser != null)
			description = this.mExceptionParser.getDescription(threadName, exception);
		else {
			try {
				GAUsage.getInstance().setDisableUsage(true);
				internalSend("exception", constructRawException(threadName, exception, fatal));
				GAUsage.getInstance().setDisableUsage(false);
				return;
			} catch (IOException e) {
				Log.w("trackException: couldn't serialize, sending \"Unknown Exception\"");
				description = "Unknown Exception";
			}
		}
		GAUsage.getInstance().setDisableUsage(true);
		sendException(description, fatal);
		GAUsage.getInstance().setDisableUsage(false);
	}

	@Deprecated
	public void trackTiming(String category, long intervalInMilliseconds, String name, String label) {
		sendTiming(category, intervalInMilliseconds, name, label);
	}

	public void sendTiming(String category, long intervalInMilliseconds, String name, String label) {
		assertTrackerOpen();
		GAUsage.getInstance().setUsage(GAUsage.Field.TRACK_TIMING);
		GAUsage.getInstance().setDisableUsage(true);
		internalSend("timing", constructTiming(category, intervalInMilliseconds, name, label));

		GAUsage.getInstance().setDisableUsage(false);
	}

	@Deprecated
	public void trackSocial(String network, String action, String target) {
		sendSocial(network, action, target);
	}

	public void sendSocial(String network, String action, String target) {
		assertTrackerOpen();
		GAUsage.getInstance().setUsage(GAUsage.Field.TRACK_SOCIAL);
		GAUsage.getInstance().setDisableUsage(true);
		internalSend("social", constructSocial(network, action, target));
		GAUsage.getInstance().setDisableUsage(false);
	}

	public void close() {
		this.mIsTrackerClosed = true;
		this.mHandler.closeTracker(this);
	}

	public void send(String hitType, Map<String, String> params) {
		assertTrackerOpen();
		GAUsage.getInstance().setUsage(GAUsage.Field.SEND);
		internalSend(hitType, params);
	}

	private void internalSend(String hitType, Map<String, String> params) {
		this.mIsTrackingStarted = true;
		if (params == null) {
			params = new HashMap<String, String>();
		}
		params.put("hitType", hitType);
		this.mModel.setAll(params, Boolean.valueOf(true));
		if (!tokensAvailable())
			Log.wDebug("Too many hits sent too quickly, throttling invoked.");
		else {
			this.mHandler.sendHit(this.mModel.getKeysAndValues());
		}
		this.mModel.clearTemporaryValues();
	}

	public String get(String key) {
		GAUsage.getInstance().setUsage(GAUsage.Field.GET);
		return this.mModel.get(key);
	}

	public void set(String key, String value) {
		GAUsage.getInstance().setUsage(GAUsage.Field.SET);
		this.mModel.set(key, value);
	}

	public String getTrackingId() {
		GAUsage.getInstance().setUsage(GAUsage.Field.GET_TRACKING_ID);
		return this.mModel.get("trackingId");
	}

	public void setAnonymizeIp(boolean anonymizeIp) {
		GAUsage.getInstance().setUsage(GAUsage.Field.SET_ANONYMIZE_IP);
		this.mModel.set("anonymizeIp", Boolean.toString(anonymizeIp));
	}

	public boolean isAnonymizeIpEnabled() {
		GAUsage.getInstance().setUsage(GAUsage.Field.GET_ANONYMIZE_IP);
		return Utils.safeParseBoolean(this.mModel.get("anonymizeIp"));
	}

	public void setSampleRate(double sampleRate) {
		GAUsage.getInstance().setUsage(GAUsage.Field.SET_SAMPLE_RATE);
		this.mModel.set("sampleRate", Double.toString(sampleRate));
	}

	public double getSampleRate() {
		GAUsage.getInstance().setUsage(GAUsage.Field.GET_SAMPLE_RATE);
		return Utils.safeParseDouble(this.mModel.get("sampleRate"));
	}

	public void setUseSecure(boolean useSecure) {
		GAUsage.getInstance().setUsage(GAUsage.Field.SET_USE_SECURE);
		this.mModel.set("useSecure", Boolean.toString(useSecure));
	}

	public boolean isUseSecure() {
		GAUsage.getInstance().setUsage(GAUsage.Field.GET_USE_SECURE);
		return Boolean.parseBoolean(this.mModel.get("useSecure"));
	}

	public void setReferrer(String referrer) {
		GAUsage.getInstance().setUsage(GAUsage.Field.SET_REFERRER);
		this.mModel.setForNextHit("referrer", referrer);
	}

	public void setCampaign(String campaign) {
		GAUsage.getInstance().setUsage(GAUsage.Field.SET_CAMPAIGN);
		this.mModel.setForNextHit("campaign", campaign);
	}

	public void setAppId(String appId) {
		GAUsage.getInstance().setUsage(GAUsage.Field.SET_APP_ID);
		this.mModel.set("appId", appId);
	}

	public String getAppId() {
		GAUsage.getInstance().setUsage(GAUsage.Field.GET_APP_ID);
		return this.mModel.get("appId");
	}

	public void setAppInstallerId(String appInstallerId) {
		GAUsage.getInstance().setUsage(GAUsage.Field.SET_APP_INSTALLER_ID);
		this.mModel.set("appInstallerId", appInstallerId);
	}

	public String getAppInstallerId() {
		GAUsage.getInstance().setUsage(GAUsage.Field.GET_APP_INSTALLER_ID);
		return this.mModel.get("appInstallerId");
	}

	public void setExceptionParser(ExceptionParser exceptionParser) {
		GAUsage.getInstance().setUsage(GAUsage.Field.SET_EXCEPTION_PARSER);
		this.mExceptionParser = exceptionParser;
	}

	public ExceptionParser getExceptionParser() {
		GAUsage.getInstance().setUsage(GAUsage.Field.GET_EXCEPTION_PARSER);
		return this.mExceptionParser;
	}

	public void setCustomDimension(int index, String value) {
		if (index < 1) {
			Log.w("index must be > 0, ignoring setCustomDimension call for " + index + ", " + value);
			return;
		}
		this.mModel.setForNextHit(Utils.getSlottedModelField("customDimension", index), value);
	}

	public void setCustomMetric(int index, Long value) {
		if (index < 1) {
			Log.w("index must be > 0, ignoring setCustomMetric call for " + index + ", " + value);
			return;
		}
		String tmpValue = null;
		if (value != null) {
			tmpValue = Long.toString(value.longValue());
		}
		this.mModel.setForNextHit(Utils.getSlottedModelField("customMetric", index), tmpValue);
	}

	public void setCustomDimensionsAndMetrics(Map<Integer, String> dimensions, Map<Integer, Long> metrics) {
		if (dimensions != null) {
			for (Integer key : dimensions.keySet()) {
				setCustomDimension(key.intValue(), (String) dimensions.get(key));
			}
		}
		if (metrics != null) {
			for (Integer key : metrics.keySet()) {				
				setCustomMetric(key.intValue(), (Long) metrics.get(key));
			}
		}
	}

	public Map<String, String> constructEvent(String category, String action,
			String label, Long value) {
		Map<String, String> params = new HashMap<String, String>();

		params.put("eventCategory", category);
		params.put("eventAction", action);
		params.put("eventLabel", label);
		if (value != null) {
			params.put("eventValue", Long.toString(value.longValue()));
		}
		GAUsage.getInstance().setUsage(GAUsage.Field.CONSTRUCT_EVENT);
		return params;
	}

	private static String microsToCurrencyString(long currencyInMicros) {
		return DF.format(currencyInMicros / 1000000.0D);
	}

	public Map<String, String> constructTransaction(Transaction trans) {
		Map<String, String> params = new HashMap<String, String>();

		params.put("transactionId", trans.getTransactionId());
		params.put("transactionAffiliation", trans.getAffiliation());
		params.put("transactionShipping", microsToCurrencyString(trans.getShippingCostInMicros()));

		params.put("transactionTax", microsToCurrencyString(trans.getTotalTaxInMicros()));
		params.put("transactionTotal", microsToCurrencyString(trans.getTotalCostInMicros()));
		params.put("currencyCode", trans.getCurrencyCode());
		GAUsage.getInstance().setUsage(GAUsage.Field.CONSTRUCT_TRANSACTION);
		return params;
	}

	private Map<String, String> constructItem(Transaction.Item item, Transaction trans) {
		Map<String, String> params = new HashMap<String, String>();

		params.put("transactionId", trans.getTransactionId());
		params.put("currencyCode", trans.getCurrencyCode());
		params.put("itemCode", item.getSKU());
		params.put("itemName", item.getName());
		params.put("itemCategory", item.getCategory());
		params.put("itemPrice", microsToCurrencyString(item.getPriceInMicros()));
		params.put("itemQuantity", Long.toString(item.getQuantity()));
		GAUsage.getInstance().setUsage(GAUsage.Field.CONSTRUCT_ITEM);
		return params;
	}

	public Map<String, String> constructException(String exceptionDescription, boolean fatal) {
		Map<String, String> params = new HashMap<String, String>();

		params.put("exDescription", exceptionDescription);
		params.put("exFatal", Boolean.toString(fatal));
		GAUsage.getInstance().setUsage(GAUsage.Field.CONSTRUCT_EXCEPTION);
		return params;
	}

	public Map<String, String> constructRawException(String threadName,
			Throwable exception, boolean fatal) throws IOException {
		Map<String, String> params = new HashMap<String, String>();
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		ObjectOutputStream stream = new ObjectOutputStream(byteStream);
		stream.writeObject(exception);
		stream.close();
		params.put("rawException", Utils.hexEncode(byteStream.toByteArray()));

		if (threadName != null) {
			params.put("exceptionThreadName", threadName);
		}
		params.put("exFatal", Boolean.toString(fatal));
		GAUsage.getInstance().setUsage(GAUsage.Field.CONSTRUCT_RAW_EXCEPTION);
		return params;
	}

	public Map<String, String> constructTiming(String category,
			long intervalInMilliseconds, String name, String label) {
		Map<String, String> params = new HashMap<String, String>();

		params.put("timingCategory", category);
		params.put("timingValue", Long.toString(intervalInMilliseconds));
		params.put("timingVar", name);
		params.put("timingLabel", label);
		GAUsage.getInstance().setUsage(GAUsage.Field.CONSTRUCT_TIMING);
		return params;
	}

	public Map<String, String> constructSocial(String network, String action,
			String target) {
		Map<String, String> params = new HashMap<String, String>();

		params.put("socialNetwork", network);
		params.put("socialAction", action);
		params.put("socialTarget", target);
		GAUsage.getInstance().setUsage(GAUsage.Field.CONSTRUCT_SOCIAL);
		return params;
	}

	@VisibleForTesting
	void setLastTrackTime(long lastTrackTime) {
		this.mLastTrackTime = lastTrackTime;
	}

	@VisibleForTesting
	void setTokens(long tokens) {
		this.mTokens = tokens;
	}

	@VisibleForTesting
	synchronized boolean tokensAvailable() {
		if (!this.mIsThrottlingEnabled) {
			return true;
		}
		long timeNow = System.currentTimeMillis();
		if (this.mTokens < MAX_TOKENS) {
			long timeElapsed = timeNow - this.mLastTrackTime;
			if (timeElapsed > 0) {
				this.mTokens = Math.min(MAX_TOKENS, this.mTokens + timeElapsed);
			}
		}
		this.mLastTrackTime = timeNow;
		if (this.mTokens >= TIME_PER_TOKEN_MILLIS) {
			this.mTokens -= TIME_PER_TOKEN_MILLIS;
			return true;
		}
		Log.wDebug("Excessive tracking detected.  Tracking call ignored.");
		return false;
	}

	@VisibleForTesting
	public void setThrottlingEnabled(boolean throttlingEnabled) {
		this.mIsThrottlingEnabled = throttlingEnabled;
	}

	private static class SimpleModel {
		private Map<String, String> temporaryMap = new HashMap<String, String>();
		private Map<String, String> permanentMap = new HashMap<String, String>();

		public synchronized void setForNextHit(String key, String value) {
			this.temporaryMap.put(key, value);
		}

		public synchronized void set(String key, String value) {
			this.permanentMap.put(key, value);
		}

		public synchronized void clearTemporaryValues() {
			this.temporaryMap.clear();
		}

		public synchronized String get(String key) {
			String result = (String) this.temporaryMap.get(key);
			if (result != null) {
				return result;
			}
			return (String) this.permanentMap.get(key);
		}

		public synchronized void setAll(Map<String, String> keysAndValues, Boolean isForNextHit) {
			if (isForNextHit.booleanValue()) {				
				this.temporaryMap.putAll(keysAndValues);
			} else {				
				this.permanentMap.putAll(keysAndValues);
			}
		}

		public synchronized Map<String, String> getKeysAndValues() {
			Map<String, String> result = new HashMap<String, String>(this.permanentMap);
			result.putAll(this.temporaryMap);
			return result;
		}
	}
}