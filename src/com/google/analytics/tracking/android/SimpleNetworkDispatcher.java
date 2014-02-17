package com.google.analytics.tracking.android;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Locale;

import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.text.TextUtils;

class SimpleNetworkDispatcher implements Dispatcher {
	private static final String USER_AGENT_TEMPLATE = "%s/%s (Linux; U; Android %s; %s; %s Build/%s)";
	private final String userAgent;
	private final HttpClientFactory httpClientFactory;
	private final Context ctx;

	SimpleNetworkDispatcher(AnalyticsStore store,
			HttpClientFactory httpClientFactory, Context ctx) {
		this(httpClientFactory, ctx);
	}

	SimpleNetworkDispatcher(HttpClientFactory httpClientFactory, Context ctx) {
		this.ctx = ctx.getApplicationContext();
		this.userAgent = createUserAgentString(AnalyticsConstants.PRODUCT, AnalyticsConstants.VERSION,
				Build.VERSION.RELEASE, Utils.getLanguage(Locale.getDefault()),
				Build.MODEL, Build.ID);

		this.httpClientFactory = httpClientFactory;
	}

	public boolean okToDispatch() {
		ConnectivityManager cm = (ConnectivityManager) this.ctx.getSystemService(Context.CONNECTIVITY_SERVICE);

		NetworkInfo network = cm.getActiveNetworkInfo();

		if (network == null || !network.isConnected()) {
			Log.vDebug("...no network connectivity");
			return false;
		}
		return true;
	}

	public int dispatchHits(List<Hit> hits) {
		int hitsDispatched = 0;

		int maxHits = Math.min(hits.size(), AnalyticsConstants.MAX_REQUESTS_PER_DISPATCH);
		for (int i = 0; i < maxHits; i++) {
			HttpClient client = this.httpClientFactory.newInstance();
			Hit hit = (Hit) hits.get(i);
			URL url = getUrl(hit);

			if (url == null) {
				if (Log.isDebugEnabled())
					Log.w("No destination: discarding hit: " + hit.getHitParams());
				else {
					Log.w("No destination: discarding hit.");
				}
				hitsDispatched++;
			} else {
				HttpHost targetHost = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());

				String path = url.getPath();

				String params = TextUtils.isEmpty(hit.getHitParams()) ? "" : HitBuilder.postProcessHit(hit, System.currentTimeMillis());

				HttpEntityEnclosingRequest request = buildRequest(params, path);
				if (request == null) {
					hitsDispatched++;
				} else {
					request.addHeader("Host", targetHost.toHostString());
					logDebugInformation(Log.isDebugEnabled(), request);
					if (params.length() > AnalyticsConstants.MAX_POST_LENGTH) {						
						Log.w("Hit too long (> 8192 bytes)--not sent");
					} else {
						try {
							HttpResponse response = client.execute(targetHost, request);
							if (response.getStatusLine().getStatusCode() != 200) {
								Log.w("Bad response: " + response.getStatusLine().getStatusCode());
								return hitsDispatched;
							}
						} catch (ClientProtocolException e) {
							Log.w("ClientProtocolException sending hit; discarding hit...");
						} catch (IOException e) {
							Log.w("Exception sending hit: " + e.getClass().getSimpleName());
							Log.w(e.getMessage());
							return hitsDispatched;
						}
					}
					hitsDispatched++;
				}
			}
		}
		return hitsDispatched;
	}

	private HttpEntityEnclosingRequest buildRequest(String params, String path) {
		if (TextUtils.isEmpty(params)) {
			Log.w("Empty hit, discarding.");
			return null;
		}
		String full = path + "?" + params;
		HttpEntityEnclosingRequest request;
		if (full.length() < AnalyticsConstants.MAX_GET_LENGTH) {
			request = new BasicHttpEntityEnclosingRequest("GET", full);
		} else {
			request = new BasicHttpEntityEnclosingRequest("POST", path);
			try {
				request.setEntity(new StringEntity(params));
			} catch (UnsupportedEncodingException e) {
				Log.w("Encoding error, discarding hit");
				return null;
			}
		}
		request.addHeader("User-Agent", this.userAgent);
		return request;
	}

	private void logDebugInformation(boolean debug, HttpEntityEnclosingRequest request) {
		if (debug) {
			StringBuffer httpHeaders = new StringBuffer();
			for (Header header : request.getAllHeaders()) {
				httpHeaders.append(header.toString()).append("\n");
			}
			httpHeaders.append(request.getRequestLine().toString()).append("\n");
			if (request.getEntity() != null) {
				try {
					InputStream is = request.getEntity().getContent();
					if (is != null) {
						int avail = is.available();
						if (avail > 0) {
							byte[] b = new byte[avail];
							is.read(b);
							httpHeaders.append("POST:\n");
							httpHeaders.append(new String(b)).append("\n");
						}
					}
				} catch (IOException e) {
					Log.w("Error Writing hit to log...");
				}
			}
			Log.i(httpHeaders.toString());
		}
	}

	String createUserAgentString(String product, String version,
			String release, String language, String model, String id) {
		return String.format(USER_AGENT_TEMPLATE, product, version, release, language, model, id);
	}

	private URL getUrl(Hit hit) {
		if (TextUtils.isEmpty(hit.getHitUrl())) {			
			return null;
		}
		try {
			return new URL(hit.getHitUrl());
		} catch (MalformedURLException e) {
			try {
				return new URL(AnalyticsConstants.ANALYTICS_PATH_INSECURE);
			} catch (MalformedURLException e1) {
			}
		}
		return null;
	}
}