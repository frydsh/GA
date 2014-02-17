package com.google.analytics.tracking.android;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.text.TextUtils;
import com.google.android.gms.analytics.internal.Command;
import com.google.android.gms.common.util.VisibleForTesting;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

class GAThread extends Thread implements AnalyticsThread {
	private static final String CLIENT_VERSION = "ma1b5";
	private static final int MAX_SAMPLE_RATE = 100;
	private static final int SAMPLE_RATE_MULTIPLIER = 100;
	private static final int SAMPLE_RATE_MODULO = 10000;
	static final String API_VERSION = "1";
	private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();

	private volatile boolean mDisabled = false;
	private volatile boolean mClosed = false;
	private volatile boolean mAppOptOut;
	private volatile List<Command> mCommands;
	private volatile MetaModel mMetaModel;
	private volatile String mInstallCampaign;
	private volatile String mClientId;
	private static GAThread sInstance;
	private volatile ServiceProxy mServiceProxy;
	private final Context mContext;

	static GAThread getInstance(Context ctx) {
		if (sInstance == null) {
			sInstance = new GAThread(ctx);
		}
		return sInstance;
	}

	private GAThread(Context ctx) {
		super("GAThread");
		if (ctx != null) {			
			this.mContext = ctx.getApplicationContext();
		}
		else {
			this.mContext = ctx;
		}
		start();
	}

	@VisibleForTesting
	GAThread(Context ctx, ServiceProxy proxy) {
		super("GAThread");
		if (ctx != null) {			
			this.mContext = ctx.getApplicationContext();
		}
		else {
			this.mContext = ctx;
		}
		this.mServiceProxy = proxy;
		start();
	}

	private void init() {
		this.mServiceProxy.createService();
		this.mCommands = new ArrayList<Command>();
		this.mCommands.add(new Command("appendVersion", "_v", CLIENT_VERSION));
		this.mCommands.add(new Command("appendQueueTime", "qt", null));
		this.mCommands.add(new Command("appendCacheBuster", "z", null));
		this.mMetaModel = new MetaModel();
		MetaModelInitializer.set(this.mMetaModel);
	}

	public void sendHit(Map<String, String> hit) {
		final Map<String, String> hitCopy = new HashMap<String, String>(hit);
		final long hitTime = System.currentTimeMillis();
		hitCopy.put("hitTime", Long.toString(hitTime));
		queueToThread(new Runnable() {
			public void run() {
				hitCopy.put("clientId", mClientId);

				if (mAppOptOut || isSampledOut(hitCopy)) {
					return;
				}
				if (!TextUtils.isEmpty(mInstallCampaign)) {
					hitCopy.put("campaign", mInstallCampaign);
					mInstallCampaign = null;
				}
				fillAppParameters(hitCopy);
				fillCampaignParameters(hitCopy);
				fillExceptionParameters(hitCopy);
				Map<String, String> wireFormatParams = HitBuilder.generateHitParams(mMetaModel, hitCopy);
				mServiceProxy.putHit(wireFormatParams, hitTime, getHostUrl(hitCopy), mCommands);
			}
		});
	}

	private String getHostUrl(Map<String, String> hit) {
		String hitUrl = (String) hit.get("internalHitUrl");
		if (hitUrl == null) {
			if (hit.containsKey("useSecure")) {
				hitUrl = Utils.safeParseBoolean(hit.get("useSecure")) ?
						AnalyticsConstants.ANALYTICS_PATH_SECURE
						: AnalyticsConstants.ANALYTICS_PATH_INSECURE;
			} else {
				hitUrl = AnalyticsConstants.ANALYTICS_PATH_SECURE;
			}
		}
		return hitUrl;
	}

	private void fillExceptionParameters(Map<String, String> hit) {
		String rawExceptionString = hit.get("rawException");
		if (rawExceptionString == null) {
			return;
		}
		hit.remove("rawException");
		byte[] rawExceptionStringBytes = Utils.hexDecode(rawExceptionString);
		ByteArrayInputStream byteStream = new ByteArrayInputStream(rawExceptionStringBytes);
		Throwable exception;
		try {
			ObjectInputStream objectInputStream = new ObjectInputStream(byteStream);
			Object readObject = objectInputStream.readObject();
			objectInputStream.close();
			if (readObject instanceof Throwable)
				exception = (Throwable) readObject;
			else
				return;
		} catch (IOException e) {
			Log.w("IOException reading exception");
			return;
		} catch (ClassNotFoundException e) {
			Log.w("ClassNotFoundException reading exception");
			return;
		}

		ArrayList<String> additionalPackages = new ArrayList<String>();
		ExceptionParser exceptionParser = new StandardExceptionParser(mContext, additionalPackages);
		hit.put("exDescription", exceptionParser.getDescription(hit.get("exceptionThreadName"), exception));
	}

	private boolean isSampledOut(Map<String, String> hit) {
		if (hit.get("sampleRate") != null) {
			double sampleRate = Utils.safeParseDouble((String) hit
					.get("sampleRate"));
			if (sampleRate <= 0.0) {
				return true;
			}
			if (sampleRate < MAX_SAMPLE_RATE) {
				String clientId = (String) hit.get("clientId");
				if ((clientId != null)
						&& (Math.abs(clientId.hashCode()) % SAMPLE_RATE_MODULO >= sampleRate * SAMPLE_RATE_MULTIPLIER)) {
					return true;
				}
			}
		}
		return false;
	}

	private void fillAppParameters(Map<String, String> hit) {
		PackageManager pm = this.mContext.getPackageManager();
		String appId = this.mContext.getPackageName();
		String appInstallerId = pm.getInstallerPackageName(appId);

		String appName = appId;
		String appVersion = null;
		try {
			PackageInfo packageInfo = pm.getPackageInfo(
					this.mContext.getPackageName(), 0);
			if (packageInfo != null) {
				appName = pm.getApplicationLabel(packageInfo.applicationInfo)
						.toString();
				appVersion = packageInfo.versionName;
			}
		} catch (PackageManager.NameNotFoundException exception) {
			Log.e("Error retrieving package info: appName set to " + appName);
		}
		putIfAbsent(hit, "appName", appName);
		putIfAbsent(hit, "appVersion", appVersion);
		putIfAbsent(hit, "appId", appId);
		putIfAbsent(hit, "appInstallerId", appInstallerId);
		hit.put("apiVersion", API_VERSION);
	}

	private void putIfAbsent(Map<String, String> hit, String key, String value) {
		if (!hit.containsKey(key))
			hit.put(key, value);
	}

	private void fillCampaignParameters(Map<String, String> hit) {
		String campaign = Utils.filterCampaign((String) hit.get("campaign"));
		if (TextUtils.isEmpty(campaign)) {
			return;
		}

		Map<String, String> paramsMap = Utils.parseURLParameters(campaign);

		hit.put("campaignContent", paramsMap.get("utm_content"));
		hit.put("campaignMedium", paramsMap.get("utm_medium"));
		hit.put("campaignName", paramsMap.get("utm_campaign"));
		hit.put("campaignSource", paramsMap.get("utm_source"));
		hit.put("campaignKeyword", paramsMap.get("utm_term"));
		hit.put("campaignId", paramsMap.get("utm_id"));
		hit.put("gclid", paramsMap.get("gclid"));
		hit.put("dclid", paramsMap.get("dclid"));
		hit.put("gmob_t", paramsMap.get("gmob_t"));
	}

	public void dispatch() {
		queueToThread(new Runnable() {
			public void run() {
				GAThread.this.mServiceProxy.dispatch();
			}
		});
	}

	public void setAppOptOut(final boolean appOptOut) {
		queueToThread(new Runnable() {
			public void run() {
				if (GAThread.this.mAppOptOut == appOptOut) {
					return;
				}
				if (appOptOut) {
					File f = GAThread.this.mContext
							.getFileStreamPath("gaOptOut");
					try {
						f.createNewFile();
					} catch (IOException e) {
						Log.w("Error creating optOut file.");
					}

					GAThread.this.mServiceProxy.clearHits();
				} else {
					GAThread.this.mContext.deleteFile("gaOptOut");
				}
				GAThread.this.mAppOptOut = appOptOut;
			}
		});
	}

	public void requestAppOptOut(
			final GoogleAnalytics.AppOptOutCallback callback) {
		queueToThread(new Runnable() {
			public void run() {
				callback.reportAppOptOut(GAThread.this.mAppOptOut);
			}
		});
	}

	public void requestClientId(final AnalyticsThread.ClientIdCallback callback) {
		queueToThread(new Runnable() {
			public void run() {
				callback.reportClientId(GAThread.this.mClientId);
			}
		});
	}

	private void queueToThread(Runnable r) {
		this.queue.add(r);
	}

	private boolean loadAppOptOut() {
		return this.mContext.getFileStreamPath("gaOptOut").exists();
	}

	private boolean storeClientId(String clientId) {
		try {
			FileOutputStream fos = this.mContext
					.openFileOutput("gaClientId", 0);

			fos.write(clientId.getBytes());
			fos.close();
			return true;
		} catch (FileNotFoundException e) {
			Log.e("Error creating clientId file.");
			return false;
		} catch (IOException e) {
			Log.e("Error writing to clientId file.");
		}
		return false;
	}

	private String generateClientId() {
		String result = UUID.randomUUID().toString().toLowerCase();
		if (!storeClientId(result)) {
			result = "0";
		}
		return result;
	}

	@VisibleForTesting
	String initializeClientId() {
		String rslt = null;
		try {
			FileInputStream input = this.mContext.openFileInput("gaClientId");
			byte[] bytes = new byte[''];
			int readLen = input.read(bytes, 0, 128);
			if (input.available() > 0) {
				Log.e("clientId file seems corrupted, deleting it.");
				input.close();
				this.mContext.deleteFile("gaInstallData");
			}
			if (readLen <= 0) {
				Log.e("clientId file seems empty, deleting it.");
				input.close();
				this.mContext.deleteFile("gaInstallData");
			} else {
				rslt = new String(bytes, 0, readLen);
				input.close();
			}
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
			Log.e("Error reading clientId file, deleting it.");
			this.mContext.deleteFile("gaInstallData");
		} catch (NumberFormatException e) {
			Log.e("cliendId file doesn't have long value, deleting it.");
			this.mContext.deleteFile("gaInstallData");
		}

		if (rslt == null) {
			rslt = generateClientId();
		}
		return rslt;
	}

	@VisibleForTesting
	static String getAndClearCampaign(Context context) {
		try {
			FileInputStream input = context.openFileInput("gaInstallData");

			byte[] inputBytes = new byte[8192];
			int readLen = input.read(inputBytes, 0, 8192);
			if (input.available() > 0) {
				Log.e("Too much campaign data, ignoring it.");
				input.close();
				context.deleteFile("gaInstallData");
				return null;
			}
			input.close();
			context.deleteFile("gaInstallData");
			if (readLen <= 0) {
				Log.w("Campaign file is empty.");
				return null;
			}
			String campaignString = new String(inputBytes, 0, readLen);
			Log.i("Campaign found: " + campaignString);
			return campaignString;
		} catch (FileNotFoundException e) {
			Log.i("No campaign data found.");
			return null;
		} catch (IOException e) {
			Log.e("Error reading campaign data.");
			context.deleteFile("gaInstallData");
		}
		return null;
	}

	private String printStackTrace(Throwable t) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream stream = new PrintStream(baos);
		t.printStackTrace(stream);
		stream.flush();
		return new String(baos.toByteArray());
	}

	public void run() {
		try {
			Thread.sleep(5000L);
		} catch (InterruptedException e) {
			Log.w("sleep interrupted in GAThread initialize");
		}

		if (this.mServiceProxy == null) {
			this.mServiceProxy = new GAServiceProxy(this.mContext, this);
		}
		init();
		try {
			this.mAppOptOut = loadAppOptOut();
			this.mClientId = initializeClientId();
			this.mInstallCampaign = getAndClearCampaign(this.mContext);
		} catch (Throwable t) {
			Log.e("Error initializing the GAThread: " + printStackTrace(t));

			Log.e("Google Analytics will not start up.");
			this.mDisabled = true;
		}
		while (!this.mClosed) {
			try {
				try {
					Runnable r = (Runnable) this.queue.take();
					if (!this.mDisabled)
						r.run();
				} catch (InterruptedException e) {
					Log.i(e.toString());
				}
			} catch (Throwable t) {
				Log.e("Error on GAThread: " + printStackTrace(t));

				Log.e("Google Analytics is shutting down.");
				this.mDisabled = true;
			}
		}
	}

	public LinkedBlockingQueue<Runnable> getQueue() {
		return this.queue;
	}

	public Thread getThread() {
		return this;
	}

	@VisibleForTesting
	void close() {
		this.mClosed = true;
		interrupt();
	}

	@VisibleForTesting
	boolean isDisabled() {
		return this.mDisabled;
	}
}