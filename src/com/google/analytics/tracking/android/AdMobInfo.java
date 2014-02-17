package com.google.analytics.tracking.android;

import java.util.Map;
import java.util.Random;

class AdMobInfo {
	private static final AdMobInfo INSTANCE = new AdMobInfo();
	private int mAdHitId;
	private Random mRandom = new Random();

	static AdMobInfo getInstance() {
		return INSTANCE;
	}

	Map<String, String> getJoinIds() {
		return null;
	}

	int generateAdHitId() {
		mAdHitId = mRandom.nextInt(Integer.MAX_VALUE - 1) + 1;
		return mAdHitId;
	}

	void setAdHitId(int adHitId) {
		mAdHitId = adHitId;
	}

	int getAdHitId() {
		return mAdHitId;
	}

	static enum AdMobKey {
		CLIENT_ID_KEY("ga_cid"),
		HIT_ID_KEY("ga_hid"),
		PROPERTY_ID_KEY("ga_wpids"),
		VISITOR_ID_KEY("ga_uid");

		private String mBowParameter;

		private AdMobKey(String bowParameter) {
			mBowParameter = bowParameter;
		}

		String getBowParameter() {
			return mBowParameter;
		}
	}
}