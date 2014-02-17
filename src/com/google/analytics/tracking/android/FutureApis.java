package com.google.analytics.tracking.android;

import java.io.File;

import android.os.Build;

class FutureApis {
	public static int version() {
		int version;
		try {
			version = Integer.parseInt(Build.VERSION.SDK);
		} catch (NumberFormatException ignored) {
			Log.e("Invalid version number: " + Build.VERSION.SDK);
			version = 0;
		}
		return version;
	}

	static boolean setOwnerOnlyReadWrite(String path) {
		int minVersionForSetReadableWritable = 9;
		if (version() < minVersionForSetReadableWritable) {
			return false;
		}
		boolean ownerOnly = true;
		File file = new File(path);

		file.setReadable(false, false);
		file.setWritable(false, false);

		file.setReadable(true, true);
		file.setWritable(true, true);
		return true;
	}
}