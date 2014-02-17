package com.google.analytics.tracking.android;

import android.content.Context;
import java.util.ArrayList;

public class ExceptionReporter implements Thread.UncaughtExceptionHandler {
	private final Thread.UncaughtExceptionHandler mOriginalHandler;
	private final Tracker mTracker;
	private final ServiceManager mServiceManager;
	private ExceptionParser mExceptionParser;
	static final String DEFAULT_DESCRIPTION = "UncaughtException";

	public ExceptionReporter(Tracker tracker, ServiceManager serviceManager,
			Thread.UncaughtExceptionHandler originalHandler, Context context) {
		if (tracker == null) {
			throw new NullPointerException("tracker cannot be null");
		}
		if (serviceManager == null) {
			throw new NullPointerException("serviceManager cannot be null");
		}
		mOriginalHandler = originalHandler;
		mTracker = tracker;
		mServiceManager = serviceManager;
		mExceptionParser = new StandardExceptionParser(context, new ArrayList<String>());
		Log.iDebug(new StringBuilder()
				.append("ExceptionReporter created, original handler is ")
				.append(originalHandler == null ? "null" : originalHandler
						.getClass().getName()).toString());
	}

	public ExceptionParser getExceptionParser() {
		return mExceptionParser;
	}

	public void setExceptionParser(ExceptionParser exceptionParser) {
		mExceptionParser = exceptionParser;
	}

	public void uncaughtException(Thread t, Throwable e) {
		String description = DEFAULT_DESCRIPTION;
		if (mExceptionParser != null) {
			String threadName = t != null ? t.getName() : null;
			description = mExceptionParser.getDescription(threadName, e);
		}
		Log.iDebug(new StringBuilder().append("Tracking Exception: ")
				.append(description).toString());
		mTracker.sendException(description, true);

		mServiceManager.dispatch();
		if (mOriginalHandler != null) {
			Log.iDebug("Passing exception to original handler.");
			mOriginalHandler.uncaughtException(t, e);
		}
	}
}