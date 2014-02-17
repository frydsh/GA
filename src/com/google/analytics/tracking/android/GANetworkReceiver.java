package com.google.analytics.tracking.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Bundle;

class GANetworkReceiver extends BroadcastReceiver {
	private final ServiceManager mManager;

	GANetworkReceiver(ServiceManager manager) {
		mManager = manager;
	}

	public void onReceive(Context ctx, Intent intent) {
		String action = intent.getAction();
		if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
			Bundle b = intent.getExtras();
			boolean notConnected = false;
			if (b != null) {
				notConnected = b.getBoolean(ConnectivityManager.EXTRA_NO_CONNECTIVITY);
			}
			mManager.updateConnectivityStatus(!notConnected);
		}
	}
}