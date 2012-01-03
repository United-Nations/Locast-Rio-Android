package edu.mit.mobile.android.appupdater;
/*
 * Copyright (C) 2010-2011  MIT Mobile Experience Lab
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import edu.mit.mobile.android.utils.StreamUtils;

/**
 * A fairly simple non-Market app update checker. Give it a URL pointing to a JSON file
 * and it will compare its version (from the manifest file) to the versions listed in the JSON.
 * If there are newer version(s), it will provide the changelog between the installed version
 * and the latest version. The updater checks against the versionCode, but displays the versionName.
 *
 * While you can create your own OnAppUpdateListener to listen for new updates, OnUpdateDialog is
 * a handy implementation that displays a Dialog with a bulleted list and a button to do the upgrade.
 *
 * The JSON format looks like this:
 * <pre>
  {
    "package": {
        "downloadUrl": "http://locast.mit.edu/connects/lcc.apk"
    },

    "1.4.3": {
    "versionCode": 6,
    "changelog": ["New automatic update checker", "Improved template interactions"]
    },
    "1.4.2": {
    "versionCode": 5,
    "changelog": ["fixed crash when saving cast"]
    }
}
 * </pre>
 *
 * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
 *
 */
public class AppUpdateChecker {
	private final static String TAG = AppUpdateChecker.class.getSimpleName();

	public static final String SHARED_PREFERENCES_NAME = "edu.mit.mobile.android.appupdater.preferences";
	public static final String
		PREF_ENABLED = "enabled",
		PREF_MIN_INTERVAL = "min_interval",
		PREF_LAST_UPDATED = "last_checked";

	private final String mVersionListUrl;
	private int currentAppVersion;

	private JSONObject pkgInfo;
	private final Context mContext;

	private final OnAppUpdateListener mUpdateListener;
	private SharedPreferences mPrefs;

	private static final int MILLISECONDS_IN_MINUTE = 60000;

	/**
	 * @param context
	 * @param versionListUrl URL pointing to a JSON file with the update list.
	 * @param updateListener
	 */
	public AppUpdateChecker(Context context, String versionListUrl, OnAppUpdateListener updateListener) {
		mContext = context;
		mVersionListUrl = versionListUrl;
		mUpdateListener = updateListener;

    	try {
			currentAppVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
		} catch (final NameNotFoundException e) {
			Log.e(TAG, "Cannot get version for self! Who am I?! What's going on!? I'm so confused :-(");
			return;
		}

		mPrefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		// defaults are kept in the preference file for ease of tweaking
		// TODO put this on a thread somehow
//		PreferenceManager.setDefaultValues(mContext, SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE, R.xml.preferences, false);
    }

	public static interface OnAppUpdateListener {
		public void appUpdateStatus(boolean isLatestVersion, String latestVersionName, List<String> changelog, Uri downloadUrl);
	}

	// min interval is stored as a string so a preference editor could potentially edit it using a text edit widget

	public int getMinInterval(){
		return Integer.valueOf(mPrefs.getString(PREF_MIN_INTERVAL, "60"));
	}

	public void setMinInterval(int minutes){
		mPrefs.edit().putString(PREF_MIN_INTERVAL, String.valueOf(minutes)).commit();
	}

	public boolean getEnabled(){
		return mPrefs.getBoolean(PREF_ENABLED, true);
	}

	public void setEnabled(boolean enabled){
		mPrefs.edit().putBoolean(PREF_ENABLED, enabled).commit();
	}

	/**
	 * You normally shouldn't need to call this, as {@link #checkForUpdates()} checks it before doing any updates.
	 *
	 * @return true if the updater should check for updates
	 */
	public boolean isStale(){
		return System.currentTimeMillis() - mPrefs.getLong(PREF_LAST_UPDATED, 0) > getMinInterval() * MILLISECONDS_IN_MINUTE;
	}

	/**
	 * Checks for updates if updates haven't been checked for recently and if checking is enabled.
	 */
	public void checkForUpdates(){
    	if (mPrefs.getBoolean(PREF_ENABLED, true) && isStale()){
    		forceCheckForUpdates();
    	}
	}

    /**
     * Checks for updates regardless of when the last check happened or if checking for updates is enabled.
     */
    public void forceCheckForUpdates(){
    	Log.d(TAG, "checking for updates...");
    	if (versionTask == null){
    		versionTask = new GetVersionJsonTask();
    		versionTask.execute(mVersionListUrl);
    	}else{
    		Log.w(TAG, "checkForUpdates() called while already checking for updates. Ignoring...");
    	}
    }

    /**
     * A handy pop-up dialog box which lists the changelog and asks if you want to update.
     *
     * @author steve
     *
     */
    public static class OnUpdateDialog implements OnAppUpdateListener {
    	private final Context mContext;
    	private final CharSequence mAppName;
    	private Uri downloadUri;
    	private final Handler mHandler;
    	private static final int MSG_SHOW_DIALOG = 1;
    	private Dialog mDialog;

    	public OnUpdateDialog(Context context, CharSequence appName) {
    		mContext = context;
    		mAppName = appName;
    		mHandler = new Handler(){
    			@Override
    			public void handleMessage(Message msg) {
    				switch (msg.what){
    				case MSG_SHOW_DIALOG:
    					try{
    						// TODO fix this so it'll pop up appropriately
    						mDialog.show();
    					}catch (final Exception e){
    						// XXX ignore for the moment
    					}

    					break;
    				}
    			}
    		};
    	}

    	public void appUpdateStatus(boolean isLatestVersion,
    			String latestVersionName, List<String> changelog, Uri downloadUri) {
    		this.downloadUri = downloadUri;

    		if (!isLatestVersion){
    			final Builder db = new Builder(mContext);
    			db.setTitle(mAppName);

    			final StringBuilder sb = new StringBuilder();
//    			sb.append(mContext.getString(R.string.app_update_new_version, latestVersionName, mAppName));
    			sb.append("\n\n");
    			for (final String item: changelog){
    				sb.append(" • ").append(item).append("\n");
    			}

    			db.setMessage(sb);

//    			db.setPositiveButton(R.string.upgrade, dialogOnClickListener);
    			db.setNegativeButton(android.R.string.cancel, dialogOnClickListener);
    			mDialog = db.create();
    			mHandler.sendEmptyMessage(MSG_SHOW_DIALOG);

    		}
    	}

    	private final DialogInterface.OnClickListener dialogOnClickListener = new DialogInterface.OnClickListener() {

    		public void onClick(DialogInterface dialog, int which) {
    			switch(which){
    			case Dialog.BUTTON_POSITIVE:
    				mContext.startActivity(new Intent(Intent.ACTION_VIEW, downloadUri));
    			}

    		}
    	};
    }

    // why oh why is the JSON API so poorly integrated into java?
    @SuppressWarnings("unchecked")
	private void triggerFromJson(JSONObject jo) throws JSONException {

    	final ArrayList<String> changelog = new ArrayList<String>();

    	// keep a sorted map of versionCode to the version information objects.
    	// Most recent is at the top.
    	final TreeMap<Integer, JSONObject> versionMap =
    		new TreeMap<Integer, JSONObject>(new Comparator<Integer>() {
    		public int compare(Integer object1, Integer object2) {
    			return object2.compareTo(object1);
    		};
		});

    	for (final Iterator<String> i = jo.keys(); i.hasNext(); ){
    		final String versionName = i.next();
    		if (versionName.equals("package")){
    			pkgInfo = jo.getJSONObject(versionName);
    			continue;
    		}
    		final JSONObject versionInfo = jo.getJSONObject(versionName);
    		versionInfo.put("versionName", versionName);

    		final int versionCode = versionInfo.getInt("versionCode");
    		versionMap.put(versionCode, versionInfo);
    	}
    	final int latestVersionNumber = versionMap.firstKey();
    	final String latestVersionName = versionMap.get(latestVersionNumber).getString("versionName");
    	final Uri downloadUri = Uri.parse(pkgInfo.getString("downloadUrl"));

    	if (currentAppVersion > latestVersionNumber){
    		Log.d(TAG, "We're newer than the latest published version ("+latestVersionName+"). Living in the future...");
    		mUpdateListener.appUpdateStatus(true, latestVersionName, null, downloadUri);
    		return;
    	}

    	if (currentAppVersion == latestVersionNumber){
    		Log.d(TAG, "We're at the latest version ("+currentAppVersion+")");
    		mUpdateListener.appUpdateStatus(true, latestVersionName, null, downloadUri);
    		return;
    	}

    	// construct the changelog. Newest entries are at the top.
    	for (final Entry<Integer, JSONObject> version: versionMap.headMap(currentAppVersion).entrySet()){
    		final JSONObject versionInfo = version.getValue();
    		final JSONArray versionChangelog = versionInfo.optJSONArray("changelog");
    		if (versionChangelog != null){
    			final int len = versionChangelog.length();
    			for (int i = 0; i < len; i++){
    				changelog.add(versionChangelog.getString(i));
    			}
    		}
    	}

    	mUpdateListener.appUpdateStatus(false, latestVersionName, changelog, downloadUri);
    }

    private class VersionCheckException extends Exception {
    	/**
		 *
		 */
		private static final long serialVersionUID = 397593559982487816L;

		public VersionCheckException(String msg) {
    		super(msg);
		}
    }

    /**
     * Send off an intent to start the download of the app.
     */
    public void startUpgrade(){
		try {
			final Uri downloadUri = Uri.parse(pkgInfo.getString("downloadUrl"));
			mContext.startActivity(new Intent(Intent.ACTION_VIEW, downloadUri));
		} catch (final JSONException e) {
			e.printStackTrace();
		}
    }

    private GetVersionJsonTask versionTask;
    private class GetVersionJsonTask extends AsyncTask<String, Integer, JSONObject>{
    	private String errorMsg = null;

    	@Override
    	protected void onProgressUpdate(Integer... values) {
    		Log.d(TAG, "update check progress: " + values[0]);
    		super.onProgressUpdate(values);
    	}

		@Override
		protected JSONObject doInBackground(String... params) {
			publishProgress(0);
			final DefaultHttpClient hc = new DefaultHttpClient();
			final String url = params[0];
			final HttpGet req = new HttpGet(url);
			JSONObject jo = null;
			try {
				publishProgress(50);
				final HttpResponse res = hc.execute(req);

				final StatusLine status = res.getStatusLine();
				final int statusCode = status.getStatusCode();
				if (statusCode == HttpStatus.SC_NOT_FOUND) {
					throw new VersionCheckException(url + " " + status.getReasonPhrase());
				}
				if (statusCode != HttpStatus.SC_OK){
					final HttpEntity e = res.getEntity();
					if (e.getContentType().getValue().equals("text/html") || e.getContentLength() > 40){
						// long response body. Serving HTML...
						throw new VersionCheckException("Got a HTML instead of expected JSON.");
					}
					throw new VersionCheckException("HTTP " + res.getStatusLine().getStatusCode() + " "+ res.getStatusLine().getReasonPhrase());
				}

				final HttpEntity ent = res.getEntity();

				jo = new JSONObject(StreamUtils.inputStreamToString(ent.getContent()));
				ent.consumeContent();
				mPrefs.edit().putLong(PREF_LAST_UPDATED, System.currentTimeMillis()).commit();

			} catch (final Exception e) {
				//e.printStackTrace();

				errorMsg = e.getClass().getSimpleName() + ": " + e.getLocalizedMessage();
			}finally {
				publishProgress(100);
			}
			return jo;
		}

		@Override
		protected void onPostExecute(JSONObject result) {
			if (result == null){
				Log.e(TAG, errorMsg);
			}else{
				try {
					triggerFromJson(result);

				} catch (final JSONException e) {
					Log.e(TAG, "Error in JSON version file.", e);
				}
			}
			versionTask = null; // forget about us, we're done.
		};
    };
}
