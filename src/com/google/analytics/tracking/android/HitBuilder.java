package com.google.analytics.tracking.android;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.google.analytics.tracking.android.MetaModel.MetaInfo;

class HitBuilder {
	
	static Map<String, String> generateHitParams(
			MetaModel metaModel, Map<String, String> hit)
	{
		Map<String, String> params = new HashMap<String, String>();
		for (Entry<String, String> entry : hit.entrySet()) {
			MetaInfo metaInfo = metaModel.getMetaInfo(entry.getKey());
			if (metaInfo != null) {
				String urlParam = metaInfo.getUrlParam(entry.getKey());
				if (urlParam != null) {
					String value = entry.getValue();
					if (metaInfo.getFormatter() != null) {
						value = metaInfo.getFormatter().format(value);
					}
					if (value != null && !value.equals(metaInfo.getDefaultValue())) {
						params.put(urlParam, value);
					}
				}
			}
		}
		return params;
	}

	static String postProcessHit(Hit hit, long currentTimeMillis) {
		StringBuilder builder = new StringBuilder();
		builder.append(hit.getHitParams());

		if (hit.getHitTime() > 0) {
			long queueTime = currentTimeMillis - hit.getHitTime();
			if (queueTime >= 0) {
				builder.append("&").append("qt").append("=").append(queueTime);
			}
		}

		builder.append("&").append("z").append("=").append(hit.getHitId());

		return builder.toString();
	}

	static String encode(String input) {
		try {
			return URLEncoder.encode(input, "UTF-8");
		} catch (UnsupportedEncodingException e) {
		}
		throw new AssertionError(
				new StringBuilder().
				append("URL encoding failed for: ").
				append(input).toString());
	}
}