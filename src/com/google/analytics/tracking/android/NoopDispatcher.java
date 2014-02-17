package com.google.analytics.tracking.android;

import android.text.TextUtils;
import java.util.List;

class NoopDispatcher implements Dispatcher {
	
	public boolean okToDispatch() {
		return true;
	}

	public int dispatchHits(List<Hit> hits) {
		if (hits == null) {
			return 0;
		}
		Log.iDebug("Hits not actually being sent as dispatch is false...");
		int maxHits = Math.min(hits.size(), 40);
		for (int i = 0; i < maxHits; i++) {
			if (Log.isDebugEnabled()) {
				String logMessage = null;
				String hitString = (hits.get(i)).getHitParams();
				String modifiedHit = TextUtils.isEmpty(hitString) ? ""
						: HitBuilder.postProcessHit(hits.get(i),
								System.currentTimeMillis());

				if (TextUtils.isEmpty(modifiedHit))
					logMessage = "Hit couldn't be read, wouldn't be sent:";
				else if (modifiedHit.length() <= AnalyticsConstants.MAX_GET_LENGTH)
					logMessage = "GET would be sent:";
				else if (modifiedHit.length() > AnalyticsConstants.MAX_POST_LENGTH)
					logMessage = "Would be too big:";
				else {
					logMessage = "POST would be sent:";
				}
				Log.iDebug(logMessage + modifiedHit);
			}
		}
		return maxHits;
	}
}