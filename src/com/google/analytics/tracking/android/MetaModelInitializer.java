package com.google.analytics.tracking.android;

import java.text.DecimalFormat;

class MetaModelInitializer {
	
	private static final MetaModel.Formatter BOOLEAN_FORMATTER = new MetaModel.Formatter() {
		
		public String format(String rawValue) {
			return Utils.safeParseBoolean(rawValue) ? "1" : "0";
		}
	};

	private static final MetaModel.Formatter UP_TO_TWO_DIGIT_FLOAT_FORMATTER = new MetaModel.Formatter() {
		
		private final DecimalFormat mFloatFormat = new DecimalFormat("0.##");

		public String format(String rawValue) {
			return mFloatFormat.format(Utils.safeParseDouble(rawValue));
		}
	};

	public static void set(MetaModel m) {
		m.addField("apiVersion", "v", null, null);
		m.addField("libraryVersion", "_v", null, null);
		m.addField("anonymizeIp", "aip", "0", BOOLEAN_FORMATTER);
		m.addField("trackingId", "tid", null, null);
		m.addField("hitType", "t", null, null);
		m.addField("sessionControl", "sc", null, null);
		m.addField("adSenseAdMobHitId", "a", null, null);
		m.addField("usage", "_u", null, null);

		m.addField("title", "dt", null, null);
		m.addField("referrer", "dr", null, null);
		m.addField("language", "ul", null, null);
		m.addField("encoding", "de", null, null);
		m.addField("page", "dp", null, null);

		m.addField("screenColors", "sd", null, null);
		m.addField("screenResolution", "sr", null, null);
		m.addField("viewportSize", "vp", null, null);
		m.addField("javaEnabled", "je", "1", BOOLEAN_FORMATTER);
		m.addField("flashVersion", "fl", null, null);

		m.addField("clientId", "cid", null, null);

		m.addField("campaignName", "cn", null, null);
		m.addField("campaignSource", "cs", null, null);
		m.addField("campaignMedium", "cm", null, null);
		m.addField("campaignKeyword", "ck", null, null);
		m.addField("campaignContent", "cc", null, null);
		m.addField("campaignId", "ci", null, null);
		m.addField("gclid", "gclid", null, null);
		m.addField("dclid", "dclid", null, null);
		m.addField("gmob_t", "gmob_t", null, null);

		m.addField("eventCategory", "ec", null, null);
		m.addField("eventAction", "ea", null, null);
		m.addField("eventLabel", "el", null, null);
		m.addField("eventValue", "ev", null, null);
		m.addField("nonInteraction", "ni", "0", BOOLEAN_FORMATTER);

		m.addField("socialNetwork", "sn", null, null);
		m.addField("socialAction", "sa", null, null);
		m.addField("socialTarget", "st", null, null);

		m.addField("appName", "an", null, null);
		m.addField("appVersion", "av", null, null);

		m.addField("description", "cd", null, null);

		m.addField("appId", "aid", null, null);
		m.addField("appInstallerId", "aiid", null, null);

		m.addField("transactionId", "ti", null, null);
		m.addField("transactionAffiliation", "ta", null, null);
		m.addField("transactionShipping", "ts", null, null);
		m.addField("transactionTotal", "tr", null, null);
		m.addField("transactionTax", "tt", null, null);
		m.addField("currencyCode", "cu", null, null);

		m.addField("itemPrice", "ip", null, null);
		m.addField("itemCode", "ic", null, null);
		m.addField("itemName", "in", null, null);
		m.addField("itemCategory", "iv", null, null);
		m.addField("itemQuantity", "iq", null, null);

		m.addField("exDescription", "exd", null, null);
		m.addField("exFatal", "exf", "1", BOOLEAN_FORMATTER);

		m.addField("timingVar", "utv", null, null);
		m.addField("timingValue", "utt", null, null);
		m.addField("timingCategory", "utc", null, null);
		m.addField("timingLabel", "utl", null, null);

		m.addField("sampleRate", "sf", "100", UP_TO_TWO_DIGIT_FLOAT_FORMATTER);
		m.addField("hitTime", "ht", null, null);

		m.addField("customDimension", "cd", null, null);
		m.addField("customMetric", "cm", null, null);
		m.addField("contentGrouping", "cg", null, null);
	}
}