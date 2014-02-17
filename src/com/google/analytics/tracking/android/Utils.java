package com.google.analytics.tracking.android;

import android.text.TextUtils;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

class Utils {
	
	private static final char[] HEXBYTES = {
		'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
		'A', 'B', 'C', 'D', 'E', 'F'
	};

	public static Map<String, String> parseURLParameters(String urlParameters) {
		Map<String, String> parameters = new HashMap<String, String>();
		String[] params = urlParameters.split("&");
		for (String s : params) {
			String[] ss = s.split("=");
			if (ss.length > 1) {				
				parameters.put(ss[0], ss[1]);
			} else if (ss.length == 1 && ss[0].length() != 0) {
				parameters.put(ss[0], null);
			}
		}
		return parameters;
	}

	public static double safeParseDouble(String s) {
		if (s == null) {			
			return 0;
		}
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
		}
		return 0.;
	}

	public static long safeParseLong(String s) {
		if (s == null) {			
			return 0;
		}
		try {
			return Long.parseLong(s);
		} catch (NumberFormatException e) {
		}
		return 0;
	}

	public static boolean safeParseBoolean(String s) {
		return Boolean.parseBoolean(s);
	}

	public static String filterCampaign(String campaign) {
		if (TextUtils.isEmpty(campaign)) {
			return null;
		}

		String urlParameters = campaign;
		if (campaign.contains("?")) {
			urlParameters = campaign.split("[\\?]")[1];
		}

		if (urlParameters.contains("%3D")) {
			try {
				urlParameters = URLDecoder.decode(urlParameters, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				return null;
			}
		} else if (!urlParameters.contains("=")) {
			return null;
		}

		Map<String, String> paramsMap = parseURLParameters(urlParameters);

		String[] validParameters = { 
			"dclid", "utm_source", "gclid",
			"utm_campaign", "utm_medium", "utm_term",
			"utm_content", "utm_id", "gmob_t"
		};
		
		StringBuilder params = new StringBuilder();
		for (int i = 0; i < validParameters.length; i++) {
			String value = paramsMap.get(validParameters[i]);
			if (!TextUtils.isEmpty(value)) {
				if (params.length() > 0) {
					params.append("&");
				}
				params.append(validParameters[i]).append("=").append(value);
			}
		}
		return params.toString();
	}

	static String getLanguage(Locale locale) {
		if (locale == null) {
			return null;
		}
		if (TextUtils.isEmpty(locale.getLanguage())) {
			return null;
		}
		StringBuilder lang = new StringBuilder();
		lang.append(locale.getLanguage().toLowerCase());
		if (!TextUtils.isEmpty(locale.getCountry())) {
			lang.append("-").append(locale.getCountry().toLowerCase());
		}
		return lang.toString();
	}

	static String hexEncode(byte[] bytes) {
		char[] out = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++) {
			int b = bytes[i] & 0xff;
			out[2 * i] = HEXBYTES[b >> 4];
			out[2 * i + 1] = HEXBYTES[b & 0xf];
		}
		return new String(out);
	}

	static int fromHexDigit(char hexDigit) {
		int value = hexDigit - '0';

		if (value > 9) {
			value -= 7; // in ascii table, between 'A' and '9', there is 7 char   
		}
		return value;
	}

	static byte[] hexDecode(String s) {
		byte[] bytes = new byte[s.length() / 2];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte) (fromHexDigit(s.charAt(2 * i)) << 4 |
					fromHexDigit(s.charAt(2 * i + 1)));
		}

		return bytes;
	}

	static String getSlottedModelField(String field, int slot) {
		return new StringBuilder().append(field).append("*").append(slot).toString();
	}
}