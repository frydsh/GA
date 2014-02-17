package com.google.analytics.tracking.android;

import com.google.android.gms.common.util.VisibleForTesting;
import java.util.HashMap;
import java.util.Map;

class MetaModel {
	
	private Map<String, MetaInfo> mMetaInfos;

	MetaModel() {
		mMetaInfos = new HashMap<String, MetaInfo>();
	}

	MetaInfo getMetaInfo(String key) {
		if (key.startsWith("&")) {
			return new MetaInfo(key.substring(1), null, null);
		}

		String tmpKey = key;
		if (key.contains("*")) {
			tmpKey = key.substring(0, key.indexOf("*"));
		}
		return mMetaInfos.get(tmpKey);
	}

	public void addField(String key, String urlParam,
			String defaultValue, Formatter formatter)
	{
		mMetaInfos.put(key, new MetaInfo(urlParam, defaultValue, formatter));
	}

	public static class MetaInfo {
		
		private final String mUrlParam;
		private final String mDefaultValue;
		private final MetaModel.Formatter mFormatter;

		public MetaInfo(
				String urlParam,
				String defaultValue,
				MetaModel.Formatter formatter)
		{
			mUrlParam = urlParam;
			mDefaultValue = defaultValue;
			mFormatter = formatter;
		}

		public String getUrlParam(String actualKey) {
			if (actualKey.contains("*")) {
				String param = mUrlParam;
				int slot = 0;
				String[] splits = actualKey.split("\\*");
				if (splits.length > 1) {
					try {
						slot = Integer.parseInt(splits[1]);
					} catch (NumberFormatException e) {
						Log.w("Unable to parse slot for url parameter " + param);
						return null;
					}
					return param + slot;
				}
				return null;
			}
			return mUrlParam;
		}

		public String getDefaultValue() {
			return mDefaultValue;
		}

		public MetaModel.Formatter getFormatter() {
			return mFormatter;
		}

		@VisibleForTesting
		String getUrlParam() {
			return mUrlParam;
		}
	}

	public static abstract interface Formatter {
		public abstract String format(String paramString);
	}
}