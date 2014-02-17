package com.google.analytics.tracking.android;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWindow;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.text.TextUtils;

import com.google.android.gms.analytics.internal.Command;
import com.google.android.gms.common.util.VisibleForTesting;

class PersistentAnalyticsStore implements AnalyticsStore {

	@VisibleForTesting
	static final String BACKEND_LIBRARY_VERSION = "";

	@VisibleForTesting
	static final String HIT_ID = "hit_id";

	@VisibleForTesting
	static final String HIT_STRING = "hit_string";

	@VisibleForTesting
	static final String HIT_TIME = "hit_time";

	@VisibleForTesting
	static final String HIT_URL = "hit_url";

	@VisibleForTesting
	static final String HIT_APP_ID = "hit_app_id";

	@VisibleForTesting
	static final String HITS_TABLE = "hits2";
	
	private static final String CREATE_HITS_TABLE = String.format(
			"CREATE TABLE IF NOT EXISTS %s ( '%s' INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
			"'%s' INTEGER NOT NULL, '%s' TEXT NOT NULL, '%s' TEXT NOT NULL, '%s' INTEGER);",
			"hits2", "hit_id", "hit_time", "hit_url", "hit_string", "hit_app_id");
	
	private static final String DATABASE_FILENAME = "google_analytics_v2.db";
	private final AnalyticsDatabaseHelper mDbHelper;
	private volatile Dispatcher mDispatcher;
	private final AnalyticsStoreStateListener mListener;
	private final Context mContext;
	private final String mDatabaseName;
	private long mLastDeleteStaleHitsTime;
	private Clock mClock;

	PersistentAnalyticsStore(AnalyticsStoreStateListener listener, Context ctx) {
		this(listener, ctx, DATABASE_FILENAME);
	}

	@VisibleForTesting
	PersistentAnalyticsStore(AnalyticsStoreStateListener listener,
			Context ctx, String databaseName)
	{
		mContext = ctx.getApplicationContext();
		mDatabaseName = databaseName;
		mListener = listener;
		mClock = new Clock() {
			
			public long currentTimeMillis() {
				return System.currentTimeMillis();
			}
		};
		mDbHelper = new AnalyticsDatabaseHelper(mContext, mDatabaseName);
		mDispatcher = new SimpleNetworkDispatcher(
				this, createDefaultHttpClientFactory(), mContext);

		mLastDeleteStaleHitsTime = 0;
	}

	@VisibleForTesting
	public void setClock(Clock clock) {
		mClock = clock;
	}

	@VisibleForTesting
	public AnalyticsDatabaseHelper getDbHelper() {
		return mDbHelper;
	}

	private HttpClientFactory createDefaultHttpClientFactory() {
		return new HttpClientFactory() {
			
			public HttpClient newInstance() {
				return new DefaultHttpClient();
			}
		};
	}

	public void setDispatch(boolean dispatch) {
		mDispatcher = (dispatch ?
				new SimpleNetworkDispatcher(this,
						createDefaultHttpClientFactory(),
						this.mContext)
		        : new NoopDispatcher());
	}

	@VisibleForTesting
	void setDispatcher(Dispatcher dispatcher) {
		mDispatcher = dispatcher;
	}

	public void clearHits(long appId) {
		SQLiteDatabase db = getWritableDatabase("Error opening database for clearHits");
		if (db != null) {
			if (appId == 0) {
				db.delete(HITS_TABLE, null, null);
			} else {
				String[] params = new String[1];
				params[0] = Long.valueOf(appId).toString();
				db.delete(HITS_TABLE, HIT_APP_ID + " = ?", params);
			}
			mListener.reportStoreIsEmpty(getNumStoredHits() == 0);
		}
	}

	public void putHit(Map<String, String> wireFormatParams,
			long hitTimeInMilliseconds, String path,
			Collection<Command> commands)
	{
		deleteStaleHits();
		fillVersionParametersIfNecessary(wireFormatParams, commands);

		removeOldHitIfFull();
		writeHitToDatabase(wireFormatParams, hitTimeInMilliseconds, path);
	}

	private void fillVersionParametersIfNecessary(
			Map<String, String> wireFormatParams,
			Collection<Command> commands)
	{
		for (Command command : commands) {
			if (command.getId().equals(Command.APPEND_VERSION)) {
				String clientVersion = command.getValue();
				storeVersion(wireFormatParams, command.getUrlParam(), clientVersion);
				break;
			}
		}
	}

	private void storeVersion(Map<String, String> wireFormatParams,
			String versionUrlParam, String clientVersion)
	{
		String version = clientVersion;
		if (clientVersion == null)
			version = "";
		else {
			version = clientVersion + "";
		}
		if (versionUrlParam != null) {			
			wireFormatParams.put(versionUrlParam, version);
		}
	}

	private void removeOldHitIfFull() {
		int hitsOverLimit = getNumStoredHits() - AnalyticsConstants.MAX_NUM_STORED_HITS + 1;
		if (hitsOverLimit > 0) {
			List<Hit> hitsToDelete = peekHits(hitsOverLimit);
			Log.wDebug("Store full, deleting " + hitsToDelete.size() + " hits to make room");
			deleteHits(hitsToDelete);
		}
	}

	private void writeHitToDatabase(Map<String, String> hit,
			long hitTimeInMilliseconds, String path)
	{
		SQLiteDatabase db = getWritableDatabase("Error opening database for putHit");
		if (db == null) {
			return;
		}

		ContentValues content = new ContentValues();

		content.put(HIT_STRING, generateHitString(hit));
		content.put(HIT_TIME, hitTimeInMilliseconds);
		long appSystemId = 0;
		if (hit.containsKey(ModelFields.ANDROID_APP_UID)) {
			try {
				appSystemId = Long.parseLong(hit.get(ModelFields.ANDROID_APP_UID));
			} catch (NumberFormatException e) {}
		}
		content.put(HIT_APP_ID, appSystemId);
		if (path == null) {
			path = AnalyticsConstants.ANALYTICS_PATH_INSECURE;
		}

		if (path.length() == 0) {
			Log.w("empty path: not sending hit");
			return;
		}
		content.put(HIT_URL, path);
		try {
			db.insert(HITS_TABLE, null, content);
			mListener.reportStoreIsEmpty(false);
		} catch (SQLiteException e) {
			Log.w("Error storing hit");
		}
	}

	public static String generateHitString(Map<String, String> urlParams) {
		List<String> keyAndValues = new ArrayList<String>(urlParams.size());
		for (Map.Entry<String, String> entry : urlParams.entrySet()) {
			keyAndValues.add(entry.getKey() + "=" + HitBuilder.encode(entry.getValue()));
		}
		return TextUtils.join("&", keyAndValues);
	}

	public List<Hit> peekHits(int maxHits) {
		SQLiteDatabase db = getWritableDatabase(
				"Error opening database for peekHits");
		if (db == null) {
			return new ArrayList<Hit>();
		}

		Cursor cursor = null;
		List<Hit> hits = new ArrayList<Hit>();
		try {
			cursor = db.query(HITS_TABLE,
					new String[] { HIT_ID, HIT_TIME, HIT_URL },
					null, null, null, null,
					String.format("%s ASC, %s ASC", HIT_URL, HIT_ID),
					Integer.toString(maxHits));
			
			if (cursor.moveToFirst()) {
				do {
					Hit hit = new Hit(null, cursor.getLong(0), cursor.getLong(1));
					hit.setHitUrl(cursor.getString(2));
					hits.add(hit);
				} while (cursor.moveToNext());
			}
		} catch (SQLiteException e) {
			Log.w("error in peekHits fetching hitIds: " + e.getMessage());
			return new ArrayList<Hit>();
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}

		int count = 0;
		try {
			cursor = db.query(HITS_TABLE,
					new String[] { HIT_ID, HIT_STRING },
					null, null, null, null,
					String.format("%s ASC", new Object[] { HIT_ID }),
					Integer.toString(maxHits));
			
			if (cursor.moveToFirst()) {
				do {
					if (cursor instanceof SQLiteCursor) {
						CursorWindow cw = ((SQLiteCursor) cursor).getWindow();
						if (cw.getNumRows() > 0) {							
							hits.get(count).setHitString(cursor.getString(1));
						} else {
							Log.w("hitString for hitId "+ hits.get(count).getHitId() +
									" too large.  Hit will be deleted.");
						}
					} else {
						hits.get(count).setHitString(cursor.getString(1));
					}
					count++;
				} while (cursor.moveToNext());
			}
			return hits;
		} catch (SQLiteException e) {
			Log.w("error in peekHits fetching hitString: " + e.getMessage());

			ArrayList<Hit> partialHits = new ArrayList<Hit>();
			boolean foundOneBadHit = false;
			for (Hit hit : hits) {
				if (TextUtils.isEmpty(hit.getHitParams())) {
					if (foundOneBadHit) {
						break;
					}
					foundOneBadHit = true;
				}
				partialHits.add(hit);
			}
			return partialHits;
		} finally {
			if (cursor != null) {				
				cursor.close();
			}
		}
	}

	@VisibleForTesting
	void setLastDeleteStaleHitsTime(long timeInMilliseconds) {
		mLastDeleteStaleHitsTime = timeInMilliseconds;
	}

	int deleteStaleHits() {
		long now = mClock.currentTimeMillis();

		if (now <= mLastDeleteStaleHitsTime + AnalyticsConstants.MILLISECONDS_PER_DAY) {
			return 0;
		}
		mLastDeleteStaleHitsTime = now;
		SQLiteDatabase db = getWritableDatabase("Error opening database for deleteStaleHits");
		if (db == null) {
			return 0;
		}
		long lastGoodTime = mClock.currentTimeMillis() - AnalyticsConstants.MILLISECONDS_PER_MONTH;
		int rslt = db.delete(HITS_TABLE, "HIT_TIME < ?", new String[] { Long.toString(lastGoodTime) });
		mListener.reportStoreIsEmpty(getNumStoredHits() == 0);
		return rslt;
	}

	public void deleteHits(Collection<Hit> hits) {
		if (hits == null) {
			throw new NullPointerException("hits cannot be null");
		}
		if (hits.isEmpty()) {
			return;
		}
		SQLiteDatabase db = getWritableDatabase("Error opening database for deleteHit");
		if (db == null) {
			return;
		}
		String[] ids = new String[hits.size()];
		String whereClause = String.format("HIT_ID in (%s)",
				TextUtils.join(",", Collections.nCopies(ids.length, "?")));

		int i = 0;
		for (Hit hit : hits) {			
			ids[i++] = Long.toString(hit.getHitId());
		}
		try {
			db.delete(HITS_TABLE, whereClause, ids);
			mListener.reportStoreIsEmpty(getNumStoredHits() == 0);
		} catch (SQLiteException e) {
			Log.w("Error deleting hit " + hits);
		}
	}

	int getNumStoredHits() {
		int numStoredHits = 0;
		SQLiteDatabase db = getWritableDatabase("Error opening database for requestNumHitsPending");
		if (db == null) {
			return numStoredHits;
		}
		Cursor cursor = null;
		try {
			cursor = db.rawQuery("SELECT COUNT(*) from hits2", null);
			if (cursor.moveToFirst()) {				
				numStoredHits = (int) cursor.getLong(0);
			}
		} catch (SQLiteException e) {
			Log.w("Error getting numStoredHits");
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return numStoredHits;
	}

	public void dispatch() {
		Log.vDebug("dispatch running...");

		if (!mDispatcher.okToDispatch()) {
			return;
		}

		List<Hit> hits = peekHits(AnalyticsConstants.MAX_REQUESTS_PER_DISPATCH);
		if (hits.isEmpty()) {
			Log.vDebug("...nothing to dispatch");
			mListener.reportStoreIsEmpty(true);
			return;
		}
		int hitsDispatched = mDispatcher.dispatchHits(hits);
		Log.vDebug("sent " + hitsDispatched + " of " + hits.size() + " hits");

		deleteHits(hits.subList(0, Math.min(hitsDispatched, hits.size())));

		if (hitsDispatched == hits.size() && getNumStoredHits() > 0) {			
			GAServiceManager.getInstance().dispatch();
		}
	}

	public void close() {
		try {
			mDbHelper.getWritableDatabase().close();
		} catch (SQLiteException e) {
			Log.w("Error opening database for close");
			return;
		}
	}

	@VisibleForTesting
	AnalyticsDatabaseHelper getHelper() {
		return mDbHelper;
	}

	private SQLiteDatabase getWritableDatabase(String errorMessage) {
		SQLiteDatabase db = null;
		try {
			db = mDbHelper.getWritableDatabase();
		} catch (SQLiteException e) {
			Log.w(errorMessage);
			return null;
		}
		return db;
	}

	@VisibleForTesting
	class AnalyticsDatabaseHelper extends SQLiteOpenHelper {
		private boolean mBadDatabase;
		private long mLastDatabaseCheckTime = 0;

		boolean isBadDatabase() {
			return mBadDatabase;
		}

		void setBadDatabase(boolean badDatabase) {
			mBadDatabase = badDatabase;
		}

		AnalyticsDatabaseHelper(Context context, String databaseName) {
			super(context, databaseName, null, 1);
		}

		private boolean tablePresent(String table, SQLiteDatabase db) {
			Cursor cursor = null;
			try {
				cursor = db.query("SQLITE_MASTER", new String[] { "name" },
						"name=?", new String[] { table }, null, null, null);
				return cursor.moveToFirst();
			} catch (SQLiteException e) {
				Log.w("error querying for table " + table);
				return false;
			} finally {
				if (cursor != null) {
					cursor.close();
				}
			}
		}

		public SQLiteDatabase getWritableDatabase() {
			if (mBadDatabase) {
				if (mLastDatabaseCheckTime +
					AnalyticsConstants.DATABASE_RECOVERY_TIMEOUT_MS >
				    mClock.currentTimeMillis())
				{
					throw new SQLiteException("Database creation failed");
				}
			}
			SQLiteDatabase db = null;
			mBadDatabase = true;
			mLastDatabaseCheckTime = mClock.currentTimeMillis();
			try {
				db = super.getWritableDatabase();
			} catch (SQLiteException e) {
				mContext.getDatabasePath(mDatabaseName).delete();
			}
			if (db == null) {
				db = super.getWritableDatabase();
			}
			mBadDatabase = false;
			return db;
		}

		public void onOpen(SQLiteDatabase db) {
			if (Build.VERSION.SDK_INT < 15) {
				Cursor cursor = db.rawQuery("PRAGMA journal_mode=memory", null);
				try {
					cursor.moveToFirst();
				} finally {
					cursor.close();
				}
			}
			if (!tablePresent(HITS_TABLE, db)) {				
				db.execSQL(CREATE_HITS_TABLE);
			} else {				
				validateColumnsPresent(db);
			}
		}

		private void validateColumnsPresent(SQLiteDatabase db) {
			Cursor c = db.rawQuery("SELECT * FROM hits2 WHERE 0", null);

			Set<String> columns = new HashSet<String>();
			try {
				String[] columnNames = c.getColumnNames();
				for (int i = 0; i < columnNames.length; i++) {					
					columns.add(columnNames[i]);
				}
			} finally {
				c.close();
			}

			if (!columns.remove(HIT_ID) ||
				!columns.remove(HIT_URL) ||
				!columns.remove(HIT_STRING) ||
				!columns.remove(HIT_TIME))
			{
				throw new SQLiteException("Database column missing");
			}

			boolean needsAppId = !columns.remove(HIT_APP_ID);

			if (!columns.isEmpty()) {
				throw new SQLiteException("Database has extra columns");
			}
			if (needsAppId) {				
				db.execSQL("ALTER TABLE hits2 ADD COLUMN hit_app_id");
			}
		}

		public void onCreate(SQLiteDatabase db) {
			FutureApis.setOwnerOnlyReadWrite(db.getPath());
		}

		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
	}
}