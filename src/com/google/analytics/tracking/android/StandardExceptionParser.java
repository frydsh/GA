package com.google.analytics.tracking.android;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class StandardExceptionParser implements ExceptionParser {
	private final TreeSet<String> includedPackages = new TreeSet<String>();

	public StandardExceptionParser(Context context,
			Collection<String> additionalPackages) {
		setIncludedPackages(context, additionalPackages);
	}

	public void setIncludedPackages(Context context,
			Collection<String> additionalPackages) {
		this.includedPackages.clear();
		Set<String> packages = new HashSet<String>();
		if (additionalPackages != null) {
			packages.addAll(additionalPackages);
		}
		if (context != null) {
			try {
				String appPackage = context.getApplicationContext().getPackageName();
				this.includedPackages.add(appPackage);
				PackageInfo pi = context.getApplicationContext().getPackageManager().getPackageInfo(appPackage, 15);

				ActivityInfo[] ai = pi.activities;
				if (ai != null) {
					for (ActivityInfo sx : ai) {						
						packages.add(sx.packageName);
					}
				}
			} catch (PackageManager.NameNotFoundException e) {
				Log.i("No package found");
			}
		}

		for (String packageName : packages) {
			boolean needToAdd = true;
			for (String oldName : this.includedPackages) {
				if (!packageName.startsWith(oldName)) {
					if (!oldName.startsWith(packageName)) break;
					this.includedPackages.remove(oldName);
					break;
				}

				needToAdd = false;
			}

			if (needToAdd) {				
				this.includedPackages.add(packageName);
			}
		}
	}

	protected Throwable getCause(Throwable t) {
		Throwable result = t;
		while (result.getCause() != null) {
			result = result.getCause();
		}
		return result;
	}

	protected StackTraceElement getBestStackTraceElement(Throwable t) {
		StackTraceElement[] elements = t.getStackTrace();
		if (elements == null || elements.length == 0) {
			return null;
		}
		for (StackTraceElement e : elements) {
			String className = e.getClassName();
			for (String packageName : this.includedPackages) {
				if (className.startsWith(packageName)) {
					return e;
				}
			}
		}
		return elements[0];
	}

	protected String getDescription(Throwable cause, StackTraceElement element, String threadName) {
		StringBuilder descriptionBuilder = new StringBuilder();
		descriptionBuilder.append(cause.getClass().getSimpleName());
		if (element != null) {
			String[] classNameParts = element.getClassName().split("\\.");
			String className = "unknown";
			if (classNameParts != null && classNameParts.length > 0) {
				className = classNameParts[classNameParts.length - 1];
			}
			descriptionBuilder.append(String.format(" (@%s:%s:%s)", className, element.getMethodName(), element.getLineNumber()));
		}

		if (threadName != null) {
			descriptionBuilder.append(String.format(" {%s}", threadName));
		}
		return descriptionBuilder.toString();
	}

	public String getDescription(String threadName, Throwable t) {
		return getDescription(getCause(t), getBestStackTraceElement(getCause(t)), threadName);
	}
}