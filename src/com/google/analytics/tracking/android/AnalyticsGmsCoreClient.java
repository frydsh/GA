package com.google.analytics.tracking.android;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import com.google.android.gms.analytics.internal.Command;
import com.google.android.gms.analytics.internal.IAnalyticsService;
import com.google.android.gms.analytics.internal.IAnalyticsService.Stub;
import java.util.List;
import java.util.Map;

class AnalyticsGmsCoreClient implements AnalyticsClient {
	public static final int BIND_FAILED = 1;
	public static final int REMOTE_EXECUTION_FAILED = 2;
	private static final String SERVICE_DESCRIPTOR = "com.google.android.gms.analytics.internal.IAnalyticsService";
	static final String SERVICE_ACTION = "com.google.android.gms.analytics.service.START";
	private static final int BIND_ADJUST_WITH_ACTIVITY = 128;
	public static final String KEY_APP_PACKAGE_NAME = "app_package_name";
	private ServiceConnection mConnection;
	private OnConnectedListener mOnConnectedListener;
	private OnConnectionFailedListener mOnConnectionFailedListener;
	private Context mContext;
	private IAnalyticsService mService;

	public AnalyticsGmsCoreClient(Context context,
			OnConnectedListener onConnectedListener,
			OnConnectionFailedListener onConnectionFailedListener) {
		mContext = context;
		if (onConnectedListener == null) {
			throw new IllegalArgumentException("onConnectedListener cannot be null");
		}
		mOnConnectedListener = onConnectedListener;
		if (onConnectionFailedListener == null) {
			throw new IllegalArgumentException("onConnectionFailedListener cannot be null");
		}
		mOnConnectionFailedListener = onConnectionFailedListener;
	}

	public void connect() {
		Intent intent = new Intent(SERVICE_ACTION);
		intent.putExtra(KEY_APP_PACKAGE_NAME, mContext.getPackageName());
		if (mConnection != null) {
			Log.e("Calling connect() while still connected, missing disconnect().");
			return;
		}
		mConnection = new AnalyticsServiceConnection();
		boolean result = mContext.bindService(intent, mConnection, BIND_ADJUST_WITH_ACTIVITY | Context.BIND_AUTO_CREATE);

		Log.iDebug("connect: bindService returned " + result + " for " + intent);
		if (!result) {
			mConnection = null;
			mOnConnectionFailedListener.onConnectionFailed(BIND_FAILED, null);
		}
	}

	public void disconnect() {
		mService = null;
		if (mConnection != null) {
			try {
				mContext.unbindService(mConnection);
			} catch (IllegalStateException e) {
			} catch (IllegalArgumentException e) {
			}

			mConnection = null;
			mOnConnectedListener.onDisconnected();
		}
	}

	public void sendHit(Map<String, String> wireParams,
			long hitTimeInMilliseconds, String path, List<Command> commands) {
		try {
			getService().sendHit(wireParams, hitTimeInMilliseconds, path, commands);
		} catch (RemoteException e) {
			Log.e("sendHit failed: " + e);
		}
	}

	public void clearHits() {
		try {
			getService().clearHits();
		} catch (RemoteException e) {
			Log.e("clear hits failed: " + e);
		}
	}

	private IAnalyticsService getService() {
		checkConnected();
		return mService;
	}

	protected void checkConnected() {
		if (!isConnected()) {
			throw new IllegalStateException("Not connected. Call connect() and wait for onConnected() to be called.");
		}
	}

	public boolean isConnected() {
		return mService != null;
	}

	private void onServiceBound() {
		onConnectionSuccess();
	}

	private void onConnectionSuccess() {
		mOnConnectedListener.onConnected();
	}

	public static abstract interface OnConnectionFailedListener {
		public abstract void onConnectionFailed(int errorCode, Intent resolution);
	}

	public static abstract interface OnConnectedListener {
		public abstract void onConnected();

		public abstract void onDisconnected();
	}

	final class AnalyticsServiceConnection implements ServiceConnection {
		AnalyticsServiceConnection() {
		}

		public void onServiceConnected(ComponentName component, IBinder binder) {
			Log.dDebug("service connected, binder: " + binder);

			try {
				String descriptor = binder.getInterfaceDescriptor();
				if (SERVICE_DESCRIPTOR.equals(descriptor)) {
					Log.dDebug("bound to service");
					AnalyticsGmsCoreClient.this.mService = Stub.asInterface(binder);
					AnalyticsGmsCoreClient.this.onServiceBound();
					return;
				}
			} catch (RemoteException e) {
			}

			AnalyticsGmsCoreClient.this.mContext.unbindService(this);
			AnalyticsGmsCoreClient.this.mConnection = null;
			AnalyticsGmsCoreClient.this.mOnConnectionFailedListener.onConnectionFailed(REMOTE_EXECUTION_FAILED, null);
		}

		public void onServiceDisconnected(ComponentName component) {
			Log.dDebug("service disconnected: " + component);
			AnalyticsGmsCoreClient.this.mConnection = null;
			AnalyticsGmsCoreClient.this.mOnConnectedListener.onDisconnected();
		}
	}
}