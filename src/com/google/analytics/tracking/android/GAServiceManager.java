package com.google.analytics.tracking.android;

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Message;
import com.google.android.gms.common.util.VisibleForTesting;

public class GAServiceManager implements ServiceManager {
	private static final int MSG_KEY = 1;
	private static final Object MSG_OBJECT = new Object();
	private Context ctx;
	private AnalyticsStore store;
	private volatile AnalyticsThread thread;
	private int dispatchPeriodInSeconds = 1800;
	private boolean pendingDispatch = true;

	private boolean connected = true;

	private boolean listenForNetwork = true;

	private AnalyticsStoreStateListener listener = new AnalyticsStoreStateListener() {
		public void reportStoreIsEmpty(boolean isEmpty) {
			updatePowerSaveMode(isEmpty, connected);
		}
	};
	
	private Handler handler;
	private GANetworkReceiver networkReceiver;
	private boolean storeIsEmpty = false;
	private static GAServiceManager instance;

	public static GAServiceManager getInstance() {
		if (instance == null) {
			instance = new GAServiceManager();
		}
		return instance;
	}

	private GAServiceManager() {
	}

	@VisibleForTesting
	GAServiceManager(Context ctx, AnalyticsThread thread, AnalyticsStore store,
			boolean listenForNetwork) {
		this.store = store;
		this.thread = thread;
		this.listenForNetwork = listenForNetwork;
		initialize(ctx, thread);
	}

	private void initializeNetworkReceiver() {
		networkReceiver = new GANetworkReceiver(this);
		IntentFilter filter = new IntentFilter();
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		ctx.registerReceiver(networkReceiver, filter);
	}

	private void initializeHandler() {
		handler = new Handler(ctx.getMainLooper(), new Handler.Callback() {
			public boolean handleMessage(Message msg) {
				if (MSG_KEY == msg.what && MSG_OBJECT.equals(msg.obj)) {
					GAUsage.getInstance().setDisableUsage(true);
					dispatch();
					GAUsage.getInstance().setDisableUsage(false);
					if (dispatchPeriodInSeconds > 0 && !storeIsEmpty) {
						handler.sendMessageDelayed(handler.obtainMessage(MSG_KEY, MSG_OBJECT), dispatchPeriodInSeconds * 1000);
					}
				}
				return true;
			}
		});
		if (dispatchPeriodInSeconds > 0) {
			handler.sendMessageDelayed(handler.obtainMessage(MSG_KEY, MSG_OBJECT), dispatchPeriodInSeconds * 1000);
		}
	}

	synchronized void initialize(Context ctx, AnalyticsThread thread) {
		if (this.ctx != null) {
			return;
		}
		this.ctx = ctx.getApplicationContext();

		if (this.thread == null) {
			this.thread = thread;
			if (this.pendingDispatch) {				
				thread.dispatch();
			}
		}
	}

	@VisibleForTesting
	AnalyticsStoreStateListener getListener() {
		return this.listener;
	}

	synchronized AnalyticsStore getStore() {
		if (this.store == null) {
			if (this.ctx == null) {
				throw new IllegalStateException("Cant get a store unless we have a context");
			}
			this.store = new PersistentAnalyticsStore(this.listener, this.ctx);
		}
		if (this.handler == null) {
			initializeHandler();
		}
		if (this.networkReceiver == null && this.listenForNetwork) {
			initializeNetworkReceiver();
		}
		return this.store;
	}

	public synchronized void dispatch() {
		if (this.thread == null) {
			Log.w("dispatch call queued.  Need to call GAServiceManager.getInstance().initialize().");
			this.pendingDispatch = true;
			return;
		}

		GAUsage.getInstance().setUsage(GAUsage.Field.DISPATCH);
		this.thread.dispatch();
	}

	public synchronized void setDispatchPeriod(int dispatchPeriodInSeconds) {
		if (this.handler == null) {
			Log.w("Need to call initialize() and be in fallback mode to start dispatch.");
			this.dispatchPeriodInSeconds = dispatchPeriodInSeconds;
			return;
		}

		GAUsage.getInstance().setUsage(GAUsage.Field.SET_DISPATCH_PERIOD);

		if (!this.storeIsEmpty && this.connected && this.dispatchPeriodInSeconds > 0) {
			this.handler.removeMessages(MSG_KEY, MSG_OBJECT);
		}
		this.dispatchPeriodInSeconds = dispatchPeriodInSeconds;
		if (dispatchPeriodInSeconds > 0 && !this.storeIsEmpty && this.connected)
			this.handler.sendMessageDelayed(
					this.handler.obtainMessage(MSG_KEY, MSG_OBJECT),
					dispatchPeriodInSeconds * 1000);
	}

	@VisibleForTesting
	synchronized void updatePowerSaveMode(boolean storeIsEmpty, boolean connected) {
		if (this.storeIsEmpty == storeIsEmpty && this.connected == connected) {
			return;
		}
		if ((storeIsEmpty || !connected) && this.dispatchPeriodInSeconds > 0) {
			this.handler.removeMessages(MSG_KEY, MSG_OBJECT);
		}
		if (!storeIsEmpty && connected && this.dispatchPeriodInSeconds > 0) {
			this.handler.sendMessageDelayed(
					this.handler.obtainMessage(MSG_KEY, MSG_OBJECT),
					this.dispatchPeriodInSeconds * 1000);
		}

		Log.iDebug(new StringBuilder().append("PowerSaveMode ").append((storeIsEmpty) || (!connected) ? "initiated." : "terminated.").toString());

		this.storeIsEmpty = storeIsEmpty;
		this.connected = connected;
	}

	public synchronized void updateConnectivityStatus(boolean connected) {
		updatePowerSaveMode(this.storeIsEmpty, connected);
	}
}