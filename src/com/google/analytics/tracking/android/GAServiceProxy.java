package com.google.analytics.tracking.android;

import android.content.Context;
import android.content.Intent;
import com.google.android.gms.analytics.internal.Command;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

class GAServiceProxy implements ServiceProxy,
		AnalyticsGmsCoreClient.OnConnectedListener,
		AnalyticsGmsCoreClient.OnConnectionFailedListener {
	private static final int MAX_TRIES = 2;
	private static final long SERVICE_CONNECTION_TIMEOUT = 300000L;
	private static final long RECONNECT_WAIT_TIME = 5000L;
	private static final long FAILED_CONNECT_WAIT_TIME = 3000L;
	private volatile long lastRequestTime;
	private volatile ConnectState state;
	private volatile AnalyticsClient client;
	private AnalyticsStore store;
	private AnalyticsStore testStore;
	private final AnalyticsThread thread;
	private final Context ctx;
	private final Queue<HitParams> queue = new ConcurrentLinkedQueue<HitParams>();
	private volatile int connectTries;
	private volatile Timer reConnectTimer;
	private volatile Timer failedConnectTimer;
	private volatile Timer disconnectCheckTimer;
	private boolean pendingDispatch;
	private boolean pendingClearHits;
	private Clock clock;
	private long idleTimeout = SERVICE_CONNECTION_TIMEOUT;

	GAServiceProxy(Context ctx, AnalyticsThread thread, AnalyticsStore store) {
		this.testStore = store;
		this.ctx = ctx;
		this.thread = thread;
		this.clock = new Clock() {
			public long currentTimeMillis() {
				return System.currentTimeMillis();
			}
		};
		this.connectTries = 0;
		this.state = ConnectState.DISCONNECTED;
	}

	GAServiceProxy(Context ctx, AnalyticsThread thread) {
		this(ctx, thread, null);
	}

	void setClock(Clock clock) {
		this.clock = clock;
	}

	public void putHit(Map<String, String> wireFormatParams,
			long hitTimeInMilliseconds, String path, List<Command> commands) {
		Log.iDebug("putHit called");

		this.queue.add(new HitParams(wireFormatParams, hitTimeInMilliseconds,
				path, commands));
		sendQueue();
	}

	public void dispatch() {
		switch (state) {
		case CONNECTED_LOCAL:
			dispatchToStore();
			break;
		case CONNECTED_SERVICE:
			break;
		default:
			this.pendingDispatch = true;
		}
	}

	public void clearHits() {
		Log.iDebug("clearHits called");
		this.queue.clear();
		switch (state) {
		case CONNECTED_LOCAL:
			this.store.clearHits(0);
			this.pendingClearHits = false;
			break;
		case CONNECTED_SERVICE:
			this.client.clearHits();
			this.pendingClearHits = false;
			break;
		default:
			this.pendingClearHits = true;
		}
	}

	private Timer cancelTimer(Timer timer) {
		if (timer != null) {
			timer.cancel();
		}
		return null;
	}

	private void clearAllTimers() {
		this.reConnectTimer = cancelTimer(this.reConnectTimer);
		this.failedConnectTimer = cancelTimer(this.failedConnectTimer);
		this.disconnectCheckTimer = cancelTimer(this.disconnectCheckTimer);
	}

	public void createService() {
		if (this.client != null) {
			return;
		}
		this.client = new AnalyticsGmsCoreClient(this.ctx, this, this);
		connectToService();
	}

	void createService(AnalyticsClient client) {
		if (this.client != null) {
			return;
		}
		this.client = client;
		connectToService();
	}

	public void setIdleTimeout(long idleTimeout) {
		this.idleTimeout = idleTimeout;
	}

	private synchronized void sendQueue() {
		if (!Thread.currentThread().equals(this.thread.getThread())) {
			this.thread.getQueue().add(new Runnable() {
				public void run() {
					sendQueue();
				}
			});
			return;
		}
		if (this.pendingClearHits) {
			clearHits();
		}
		switch (this.state) {
		case CONNECTED_LOCAL:
			while (!this.queue.isEmpty()) {
				HitParams hitParams = this.queue.poll();
				Log.iDebug("Sending hit to store");
				this.store.putHit(hitParams.getWireFormatParams(),
						hitParams.getHitTimeInMilliseconds(),
						hitParams.getPath(), hitParams.getCommands());
			}

			if (this.pendingDispatch) {
				dispatchToStore();
			}
			break;
		case CONNECTED_SERVICE:
			while (!this.queue.isEmpty()) {
				HitParams hitParams = this.queue.peek();
				Log.iDebug("Sending hit to service");
				this.client.sendHit(hitParams.getWireFormatParams(),
						hitParams.getHitTimeInMilliseconds(),
						hitParams.getPath(), hitParams.getCommands());

				this.queue.poll();
			}
			this.lastRequestTime = this.clock.currentTimeMillis();
			break;
		case DISCONNECTED:
			Log.iDebug("Need to reconnect");
			if (!this.queue.isEmpty()) {				
				connectToService();
			}
			break;
		default:
			break;
		}
	}

	private void dispatchToStore() {
		this.store.dispatch();
		this.pendingDispatch = false;
	}

	private synchronized void useStore() {
		if (this.state == ConnectState.CONNECTED_LOCAL) {
			return;
		}

		clearAllTimers();
		Log.iDebug("falling back to local store");
		if (this.testStore != null) {
			this.store = this.testStore;
		} else {
			GAServiceManager instance = GAServiceManager.getInstance();
			instance.initialize(this.ctx, this.thread);
			this.store = instance.getStore();
		}
		this.state = ConnectState.CONNECTED_LOCAL;
		sendQueue();
	}

	private synchronized void connectToService() {
		if (this.client != null && this.state != ConnectState.CONNECTED_LOCAL) {
			try {
				this.connectTries += 1;
				cancelTimer(this.failedConnectTimer);
				this.state = ConnectState.CONNECTING;
				this.failedConnectTimer = new Timer("Failed Connect");
				this.failedConnectTimer.schedule(new FailedConnectTask(), FAILED_CONNECT_WAIT_TIME);
				Log.iDebug("connecting to Analytics service");
				this.client.connect();
			} catch (SecurityException e) {
				Log.w("security exception on connectToService");
				useStore();
			}
		} else {
			Log.w("client not initialized.");
			useStore();
		}
	}

	private synchronized void disconnectFromService() {
		if (this.client != null && this.state == ConnectState.CONNECTED_SERVICE) {
			this.state = ConnectState.PENDING_DISCONNECT;
			this.client.disconnect();
		}
	}

	public synchronized void onConnected() {
		this.failedConnectTimer = cancelTimer(this.failedConnectTimer);
		this.connectTries = 0;
		Log.iDebug("Connected to service");
		this.state = ConnectState.CONNECTED_SERVICE;
		sendQueue();
		this.disconnectCheckTimer = cancelTimer(this.disconnectCheckTimer);
		this.disconnectCheckTimer = new Timer("disconnect check");
		this.disconnectCheckTimer.schedule(new DisconnectCheckTask(), this.idleTimeout);
	}

	public synchronized void onDisconnected() {
		if (this.state == ConnectState.PENDING_DISCONNECT) {
			Log.iDebug("Disconnected from service");
			clearAllTimers();
			this.state = ConnectState.DISCONNECTED;
		} else {
			Log.iDebug("Unexpected disconnect.");
			this.state = ConnectState.PENDING_CONNECTION;
			if (this.connectTries < MAX_TRIES) {				
				fireReconnectAttempt();
			} else {				
				useStore();
			}
		}
	}

	public synchronized void onConnectionFailed(int errorCode, Intent resolution) {
		this.state = ConnectState.PENDING_CONNECTION;
		if (this.connectTries < MAX_TRIES) {
			Log.w("Service unavailable (code=" + errorCode + "), will retry.");
			fireReconnectAttempt();
		} else {
			Log.w("Service unavailable (code=" + errorCode + "), using local store.");
			useStore();
		}
	}

	private void fireReconnectAttempt() {
		this.reConnectTimer = cancelTimer(this.reConnectTimer);
		this.reConnectTimer = new Timer("Service Reconnect");
		this.reConnectTimer.schedule(new ReconnectTask(), RECONNECT_WAIT_TIME);
	}

	private static class HitParams {
		private final Map<String, String> wireFormatParams;
		private final long hitTimeInMilliseconds;
		private final String path;
		private final List<Command> commands;

		public HitParams(Map<String, String> wireFormatParams,
				long hitTimeInMilliseconds, String path, List<Command> commands) {
			this.wireFormatParams = wireFormatParams;
			this.hitTimeInMilliseconds = hitTimeInMilliseconds;
			this.path = path;
			this.commands = commands;
		}

		public Map<String, String> getWireFormatParams() {
			return this.wireFormatParams;
		}

		public long getHitTimeInMilliseconds() {
			return this.hitTimeInMilliseconds;
		}

		public String getPath() {
			return this.path;
		}

		public List<Command> getCommands() {
			return this.commands;
		}
	}

	private class DisconnectCheckTask extends TimerTask {
		private DisconnectCheckTask() {
		}

		public void run() {
			if (state == ConnectState.CONNECTED_SERVICE && queue.isEmpty()
					&& lastRequestTime + idleTimeout < clock.currentTimeMillis()) {
				Log.iDebug("Disconnecting due to inactivity");
				disconnectFromService();
			} else {
				disconnectCheckTimer.schedule(new DisconnectCheckTask(), idleTimeout);
			}
		}
	}

	private class ReconnectTask extends TimerTask {
		private ReconnectTask() {
		}

		public void run() {
			connectToService();
		}
	}

	private class FailedConnectTask extends TimerTask {
		private FailedConnectTask() {
		}

		public void run() {
			if (state == ConnectState.CONNECTING) {				
				useStore();
			}
		}
	}

	private static enum ConnectState {
		CONNECTING,
		CONNECTED_SERVICE,
		CONNECTED_LOCAL,
		BLOCKED,
		PENDING_CONNECTION,
		PENDING_DISCONNECT,
		DISCONNECTED;
	}
}