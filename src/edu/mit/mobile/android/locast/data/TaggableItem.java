package edu.mit.mobile.android.locast.data;
/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.stackoverflow.CollectionUtils;
import com.stackoverflow.Predicate;

import edu.mit.mobile.android.locast.accounts.Authenticator;

/**
 * DB entry for an item that can be tagged.
 *
 * @author stevep
 *
 */
public abstract class TaggableItem extends JsonSyncableItem {
	@SuppressWarnings("unused")
	private static final String TAG = TaggableItem.class.getSimpleName();

	public static final String _PRIVACY = "privacy",
								_AUTHOR = "author",
								_AUTHOR_URI = "author_uri",
								_DRAFT  = "draft";

	public static final String  PRIVACY_PUBLIC    = "public",
								PRIVACY_PROTECTED = "protected",
								PRIVACY_PRIVATE   = "private";

	// the ordering of this must match the arrays.xml
	public static final String[] PRIVACY_LIST = {PRIVACY_PUBLIC, PRIVACY_PRIVATE};

	public static final TaggableItemSyncMap SYNC_MAP = new TaggableItemSyncMap();

	/**
	 * The name of the server query parameter to filter using tags.
	 */
	public static final String SERVER_QUERY_PARAMETER = "tags";

	/**
	 * An item that will sync "tags" and "system_tags" fields.
	 * @author steve
	 *
	 */
	public static class TaggableItemSyncMap extends JsonSyncableItem.ItemSyncMap {
		public TaggableItemSyncMap() {
			super();
			put(Tag.PATH, new SyncMapJoiner(
					new TagSyncField("tags", SyncItem.SYNC_TO),
					new TagSyncField("system_tags", SYSTEM_PREFIX, SyncItem.SYNC_TO)) {

				@Override
				public ContentValues joinContentValues(ContentValues[] cv) {
					return null;
				}
			});

			final SyncMap authorSync = new SyncMap();
			authorSync.put(_AUTHOR, new SyncFieldMap("display_name", SyncFieldMap.STRING, SyncItem.FLAG_OPTIONAL));
			authorSync.put(_AUTHOR_URI,		new SyncFieldMap("uri", SyncFieldMap.STRING));
			put("_author", 			new SyncMapChain("author", authorSync, SyncItem.SYNC_FROM));


			put(_PRIVACY,          	new SyncFieldMap("privacy", SyncFieldMap.STRING));
		}
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;

	};

	@Override
	public SyncMap getSyncMap() {
		return SYNC_MAP;
	}

	public static class TagSyncField extends SyncCustom {
		final private String prefix;

		public TagSyncField(String remoteKey, int flags) {
			super(remoteKey, flags);
			prefix = null;
		}

		public TagSyncField(String remoteKey, String prefix, int flags) {
			super(remoteKey, flags);
			this.prefix = prefix;
		}


		@Override
		public JSONArray toJSON(Context context, Uri localItem, Cursor c, String lProp) throws JSONException {
			if (localItem == null || context.getContentResolver().getType(localItem).startsWith("vnd.android.cursor.dir")){
				return null;
			}
			JSONArray jo = null;
			if (localItem != null){
				jo = new JSONArray(getTags(context.getContentResolver(), localItem, prefix));
			}
			return jo;
		}

		@Override
		public ContentValues fromJSON(Context context, Uri localItem, JSONObject item, String lProp)
				throws JSONException {
			return null; // this shouldn't be called.
		}

		@Override
		public void onPostSyncItem(Context context, Uri uri,
				JSONObject item, boolean updated) throws SyncException,
				IOException {
			super.onPostSyncItem(context, uri, item, updated);
			if (updated){
				// tags need to be loaded here, as they need a valid localUri in order to save.
				final JSONArray ja = item.optJSONArray(remoteKey);
				final List<String> tags = new ArrayList<String>(ja.length());
				for (int i = 0; i < ja.length(); i++){
					tags.add(ja.optString(i));
				}
				//Log.d(TAG, uri + " has the following "+remoteKey +": "+ tags);
				TaggableItem.putTags(context.getContentResolver(), uri, tags, prefix);
			}
		}
	}



	/**
	 * @param c a cursor pointing at an item's row
	 * @return true if the item is editable by the logged-in user.
	 */
	public static boolean canEdit(Context context, Cursor c){
		final String privacy = c.getString(c.getColumnIndex(_PRIVACY));
		final String useruri = Authenticator.getUserUri(context);
		return privacy == null || useruri == null || useruri.length() == 0 ||
			useruri.equals(c.getString(c.getColumnIndex(_AUTHOR_URI)));
	}

	/**
	 * @param c
	 * @return true if the authenticated user can change the item's privacy level.
	 */
	public static boolean canChangePrivacyLevel(Context context, Cursor c){
		final String useruri = Authenticator.getUserUri(context);
		return useruri == null || useruri.equals(c.getString(c.getColumnIndex(_AUTHOR_URI)));
	}

	/**
	 * @param cr
	 * @return a list of all the tags attached to a given item
	 */
	public static Set<String> getTags(ContentResolver cr, Uri item) {
		return getTags(cr, item, null);
	}

	/**
	 * @param cr
	 * @param item
	 * @param prefix
	 * @return a list of all the tags attached to a given item
	 */
	public static Set<String> getTags(ContentResolver cr, Uri item, String prefix) {
		final Cursor tags = cr.query(Uri.withAppendedPath(item, Tag.PATH), Tag.DEFAULT_PROJECTION, null, null, null);
		final Set<String> tagSet = new HashSet<String>(tags.getCount());
		final int tagColumn = tags.getColumnIndex(Tag._NAME);
		final Predicate<String> predicate = getPrefixPredicate(prefix);
		for (tags.moveToFirst(); !tags.isAfterLast(); tags.moveToNext()){
			final String tag = tags.getString(tagColumn);
			if (predicate.apply(tag)){
				final int separatorIndex = tag.indexOf(PREFIX_SEPARATOR);
				if (separatorIndex == -1){
					tagSet.add(tag);
				}else{
					tagSet.add(tag.substring(separatorIndex + 1));
				}
			}
		}
		tags.close();
		return tagSet;
	}

	/**
	 * Sets the tags of the given item. Any existing tags will be deleted.
	 * @param cr
	 * @param item
	 * @param tags
	 */
	public static void putTags(ContentResolver cr, Uri item, Collection<String> tags) {
		putTags(cr, item, tags, null);
	}

	public static String CV_TAG_PREFIX = "prefix";

	/**
	 * Sets the tags of a given prefix for the given item. Any existing tags using the given prefix will be deleted.
	 * @param cr
	 * @param item
	 * @param tags
	 * @param prefix
	 */
	public static void putTags(ContentResolver cr, Uri item, Collection<String> tags, String prefix) {
		final ContentValues cv = new ContentValues();
		cv.put(Tag.PATH, TaggableItem.toListString(addPrefixToTags(prefix, tags)));
		cv.put(CV_TAG_PREFIX, prefix);
		cr.update(Uri.withAppendedPath(item, Tag.PATH), cv, null, null);
	}

	public static int MAX_POPULAR_TAGS = 10;

	/**
	 * TODO make this pick the set of tags for a set of content.
	 *
	 * @param cr a content resolver
	 * @return the top MAX_POPULAR_TAGS most popular tags in the set, with the most popular first.
	 */
	public static List<String> getPopularTags(ContentResolver cr){

		final Map<String, Integer> tagPop = new HashMap<String, Integer>();
		final List<String> popTags;

		final Cursor c = cr.query(Tag.CONTENT_URI, Tag.DEFAULT_PROJECTION, null, null, null);
		final int tagColumn = c.getColumnIndex(Tag._NAME);

		for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()){
			final String tag = c.getString(tagColumn);

			final Integer count = tagPop.get(tag);
			if (count == null){
				tagPop.put(tag, 1);
			}else{
				tagPop.put(tag, count + 1);
			}
		}
		c.close();

		popTags = new ArrayList<String>(tagPop.keySet());

		Collections.sort(popTags, new Comparator<String>() {
			public int compare(String object1, String object2) {
				return tagPop.get(object2).compareTo(tagPop.get(object1));
			}

		});
		int limit;
		if (popTags.size() < MAX_POPULAR_TAGS){
			limit = popTags.size();
		}else{
			limit = MAX_POPULAR_TAGS;
		}
		return popTags.subList(0, limit);
	}

	/**
	 * Given a base content URI of a taggable item and a list of tags, constructs a URI
	 * representing all the items of the baseUri that match all the listed tags.
	 *
	 * @param baseUri a content URI of a TaggableItem
	 * @param tags a collection of tags
	 * @return a URI representing all the items that match all the given tags
	 * @see #getTagUri(Uri, Collection)
	 */
	public static Uri getTagUri(Uri baseUri, String ... tags){
		return getTagUri(baseUri, Arrays.asList(tags));
	}

	/**
	 * Given a base content URI of a taggable item and a list of tags, constructs a URI
	 * representing all the items of the baseUri that match all the listed tags.
	 *
	 * @param baseUri a content URI of a TaggableItem
	 * @param tags a collection of tags
	 * @return a URI representing all the items that match all the given tags
	 */
	public static Uri getTagUri(Uri baseUri, Collection<String> tags){
		if (tags.isEmpty()){
			return baseUri;
		}

		return baseUri.buildUpon().appendQueryParameter(SERVER_QUERY_PARAMETER, Tag.toTagQuery(tags))  .build();
	}

	private final static char PREFIX_SEPARATOR = ':';
	public final static String SYSTEM_PREFIX = "system";

	// cache predicates so objects don't get created each time a query is made.
	private static HashMap<String,HasPrefixPredicate> predicates = new HashMap<String, HasPrefixPredicate>();

	private static HasPrefixPredicate getPrefixPredicate(String prefix){
		if (predicates.containsKey(prefix)){
			return predicates.get(prefix);
		}else{
			final HasPrefixPredicate predicate = new HasPrefixPredicate(prefix);
			predicates.put(prefix, predicate);
			return predicate;
		}
	}

	public static Collection<String> filterTags(String prefix, Collection<String> tags){
		final Predicate<String> predicate = getPrefixPredicate(prefix);
		return CollectionUtils.filter(tags, predicate);
	}

	public static void filterTagsInPlace(String prefix, Collection<String> tags){
		final Predicate<String> predicate = getPrefixPredicate(prefix);
		CollectionUtils.filterInPlace(tags, predicate);
	}

	private static class HasPrefixPredicate implements Predicate<String> {
		private final String mPrefix;

		/**
		 * @param prefix prefix string or null for un-prefixed tags.
		 */
		public HasPrefixPredicate(String prefix) {
			mPrefix = prefix;
		}
		public boolean apply(String in) {
			final int separatorIndex = in.indexOf(PREFIX_SEPARATOR);
			if (separatorIndex == -1){
				// we asked for a prefix, but this contains none.
				if (mPrefix != null){
					return false;
				// a null prefix was requested, so it's good that we have no separator.
				}else{
					return true;
				}
			}
			return in.substring(0, separatorIndex).equals(mPrefix);
		}
	}


	public static String addPrefixToTag(String prefix, String tag){
		return prefix + PREFIX_SEPARATOR + tag;
	}

	private static Collection<String> addPrefixToTags(String prefix, Collection<String> tags){
		if (prefix == null){
			return tags;
		}
		final ArrayList<String> prefixedTags = new ArrayList<String>(tags.size());
		for (final String tag: tags){
			prefixedTags.add(addPrefixToTag(prefix, tag));
		}
		return prefixedTags;
	}

	/**
	 * Strips prefixes from tags.
	 *
	 * @param tags
	 * @return a list of the tags with any prefix removed.
	 */
	public static Set<String> removePrefixesFromTags(Collection<String> tags){
		if (tags == null){
			return null;
		}

		final Set<String> nonPrefixedTags = new HashSet<String>(tags.size());
		for (final String tag: tags){
			final int sepIndex = tag.indexOf(PREFIX_SEPARATOR);
			if (sepIndex != -1){
				nonPrefixedTags.add(tag.substring(sepIndex+1));
			}else{
				nonPrefixedTags.add(tag);
			}
		}
		return nonPrefixedTags;
	}
}
