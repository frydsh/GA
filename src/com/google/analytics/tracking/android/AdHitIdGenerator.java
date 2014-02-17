package com.google.analytics.tracking.android;

import com.google.android.gms.common.util.VisibleForTesting;

class AdHitIdGenerator {
	private boolean mAdMobSdkInstalled;

	AdHitIdGenerator() {
		try {
			mAdMobSdkInstalled = Class.forName("com.google.ads.AdRequest") != null;
		} catch (ClassNotFoundException e) {
			mAdMobSdkInstalled = false;
		}
	}

	@VisibleForTesting
	AdHitIdGenerator(boolean adMobSdkInstalled) {
		mAdMobSdkInstalled = adMobSdkInstalled;
	}

	int getAdHitId() {
		if (!mAdMobSdkInstalled) {
			return 0;
		}
		return AdMobInfo.getInstance().generateAdHitId();
	}
}