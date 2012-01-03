package edu.mit.mobile.android.content;
/*
 * Copyright (C) 2011  MIT Mobile Experience Lab
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
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Database helper to make it easier to create many-to-many relationships
 * between two arbitrary tables.
 *
 * <pre>
 *     relation
 *        ↓
 * [from] → [to]
 *        → [to 2]
 * </pre>
 *
 * For example, you could have an Itinerary that has a relation to multiple
 * Casts.
 *
 * To use, first {@link ManyToMany#createJoinTable(SQLiteDatabase)}. Then you
 * can create relations between between tables by
 * {@link #addRelation(SQLiteDatabase, long, long)}.
 *
 * @author steve
 *
 */
public class ManyToMany {
	public static class ManyToManyColumns implements BaseColumns {

		public static final String
		TO_ID = "to_id",
		FROM_ID = "from_id";
	}

	public static class M2MDBHelper implements DBHelper {
		private final String mFromTable, mToTable, mJoinTable;
		private final Uri mToContentUri;

		private final IdenticalChildFinder mIdenticalChildFinder;

		/**
		 * @param fromTable
		 * @param toTable
		 * @param toContentUri
		 *            will make calls to the content provider to do updates
		 *            using this URI
		 */
		public M2MDBHelper(String fromTable, String toTable,
				IdenticalChildFinder identicalChildFinder, Uri toContentUri) {
			mFromTable = fromTable;
			mToTable = toTable;
			mJoinTable = mFromTable + "_" + mToTable;

			mToContentUri = toContentUri;

			mIdenticalChildFinder = identicalChildFinder;
		}

		/**
		 * Provides a bunch of CRUD routines for manipulating items and their relationship to one another.
		 *
		 * @param fromTable
		 * @param toTable
		 */
		public M2MDBHelper(String fromTable, String toTable, IdenticalChildFinder identicalChildFinder) {
			mFromTable = fromTable;
			mToTable = toTable;
			mJoinTable = mFromTable + "_" + mToTable;

			mToContentUri = null;

			mIdenticalChildFinder = identicalChildFinder;
		}

		public String getJoinTableName(){
			return mJoinTable;
		}

		/**
		 * @return the name of the from table
		 */
		public String getFromTable(){
			return mFromTable;
		}

		/**
		 * @return the name of the to table
		 */
		public String getToTable() {
			return mToTable;
		}

		/**
		 * Generates a join table.
		 */
		public void createJoinTable(SQLiteDatabase db){
			db.execSQL("CREATE TABLE "+mJoinTable + " ("
					+ ManyToManyColumns._ID 			+ " INTEGER PRIMARY KEY,"
					+ ManyToManyColumns.TO_ID   		+ " INTEGER"
					// TODO foreign keys are not supported in 2.1 or below. Dynamically enable this feature.
					// + " REFERENCES "+ mToTable + "("+BaseColumns._ID+")"
					+ ","
					+ ManyToManyColumns.FROM_ID 		+ " INTEGER"
					+ ");");
		}

		/**
		 * Deletes the join table.
		 *
		 */
		public void deleteJoinTable(SQLiteDatabase db){
			db.execSQL("DROP TABLE IF EXISTS "+mJoinTable);
		}

		/**
		 * Creates a link from `from' to `to'.
		 *
		 * @param db database that has the many-to-many table
		 * @param from ID of the item in the FROM table
		 * @param to ID of the item in the TO table
		 * @return ID of the newly created relation
		 */
		public long addRelation(SQLiteDatabase db, long from, long to){
			final ContentValues relation = new ContentValues();
			// make a many-to-many relation
			relation.put(ManyToManyColumns.FROM_ID, from);
			relation.put(ManyToManyColumns.TO_ID, to);
			return db.insert(mJoinTable, null, relation);
		}

		/**
		 * Removes all relations from a given item.
		 *
		 * @param db
		 * @param from
		 * @return the count of deleted relations
		 */
		public int removeRelation(SQLiteDatabase db, long from){
			return db.delete(mJoinTable,
					ManyToManyColumns.FROM_ID + "=?",
					new String[]{Long.toString(from)});
		}

		public int removeRelation(SQLiteDatabase db, long from, long to){
			return db.delete(mJoinTable,
					ManyToManyColumns.TO_ID + "=? AND " + ManyToManyColumns.FROM_ID + "=?",
					new String[]{Long.toString(to),					Long.toString(from)});
		}

		@Override
		public Uri insertDir(SQLiteDatabase db, ContentProvider provider,
				Uri uri, ContentValues values) {
			return insertItemWithRelation(db, provider, uri, values, mIdenticalChildFinder);
		}

		/**
		 * Inserts a child into the database and adds a relation to its parent. If the item described by values is already present, only adds the relation.
		 *
		 * @param db
		 * @param uri URI to insert into. This must be a be a hierarchical URI that points to the directory of the desired parent's children. Eg. "/itinerary/1/casts/"
		 * @param values values for the child
		 * @param childFinder a finder that will look for
		 * @return the URI of the child that was either related or inserted.
		 */
		public Uri insertItemWithRelation(SQLiteDatabase db, ContentProvider provider, Uri parentChildDir, ContentValues values, IdenticalChildFinder childFinder) {
			final Uri parent = ProviderUtils.removeLastPathSegment(parentChildDir);

			final long parentId = ContentUris.parseId(parent);
			Uri newItem;

			db.beginTransaction();
			try {

				if (childFinder != null){
					newItem = childFinder.getIdenticalChild(this, parentChildDir, db, mToTable, values);
				}else{
					newItem = null;
				}

				long childId = -1;

				// existing item found, but no relation has been established yet.
				if (newItem != null){
					childId = ContentUris.parseId(newItem);

				// no existing child or relation
				}else{
					if (mToContentUri != null){
						newItem = provider.insert(mToContentUri, values);
						childId = ContentUris.parseId(newItem);
					}else{
						childId = db.insert(mToTable, null, values);
						if (childId != -1){
							newItem = ContentUris.withAppendedId(parentChildDir, childId);
						}
					}
				}

				if (newItem != null && childId != -1){
					addRelation(db, parentId, childId);
				}

				db.setTransactionSuccessful();
			}finally{
				db.endTransaction();
			}
			return newItem;
		}

		/**
		 * Updates the item in the "to" table whose URI is specified.
		 *
		 * XXX Does not verify that there's actually a relationship between from and to.
		 *
		 * @param db
		 * @param provider
		 * @param uri the URI of the child. Child uri must end in its ID
		 * @param values
		 * @param where
		 * @param whereArgs
		 * @return the number of items that have been updated
		 */
		@Override
		public int updateItem(SQLiteDatabase db, ContentProvider provider, Uri uri, ContentValues values, String where, String[] whereArgs){
			int count;
			if (mToContentUri != null){
				count = provider.update(ContentUris.withAppendedId(mToContentUri, ContentUris.parseId(uri)), values, where, whereArgs);
			}else{
				count = db.update(mToTable, values, ProviderUtils.addExtraWhere(where, BaseColumns._ID+"=?"), ProviderUtils.addExtraWhereArgs(whereArgs, uri.getLastPathSegment()));
			}

			return count;
		}

		// TODO does not yet verify a relationship.
		@Override
		public int updateDir(SQLiteDatabase db, ContentProvider provider, Uri uri, ContentValues values, String where, String[] whereArgs){
			int count;
			if (mToContentUri != null){
				count = provider.update(mToContentUri, values, where, whereArgs);
			}else{
				count = db.update(mToTable, values, where, whereArgs);
			}
			return count;
		}

		/* (non-Javadoc)
		 * @see edu.mit.mobile.android.content.DBHelper#deleteItem(android.database.sqlite.SQLiteDatabase, android.content.ContentProvider, android.net.Uri, java.lang.String, java.lang.String[])
		 */
		@Override
		public int deleteItem(SQLiteDatabase db, ContentProvider provider, Uri uri,String where, String[] whereArgs){
			int count;
			try {
				db.beginTransaction();
				final long childId = ContentUris.parseId(uri);
				final Uri parent = ProviderUtils.removeLastPathSegments(uri, 2);

				if (mToContentUri != null){
					count = provider.delete(ContentUris.withAppendedId(mToContentUri, childId), where, whereArgs);
				}else{
					count = db.delete(mToTable, ProviderUtils.addExtraWhere(where, BaseColumns._ID+"=?"), ProviderUtils.addExtraWhereArgs(whereArgs, String.valueOf(childId)));
				}

				final int rows = removeRelation(db, ContentUris.parseId(parent), childId);

				if (rows == 0){
					throw new IllegalArgumentException("There is no relation between "+ parent + " and " + mToTable + ": ID "+ childId);
				}

				db.setTransactionSuccessful();

			}finally{
				db.endTransaction();
			}
			return count;
		}

		/* (non-Javadoc)
		 * @see edu.mit.mobile.android.content.DBHelper##deleteDir(android.database.sqlite.SQLiteDatabase, android.content.ContentProvider, android.net.Uri, java.lang.String, java.lang.String[])
		 */
		@Override
		public int deleteDir(SQLiteDatabase db, ContentProvider provider, Uri uri,String where, String[] whereArgs){
			int count;
			try {
				db.beginTransaction();
				final Uri parent = ProviderUtils.removeLastPathSegment(uri);

				if (mToContentUri != null){
					count = provider.delete(mToContentUri, where, whereArgs);
				}else{
					count = db.delete(mToTable, where, whereArgs);
				}

				removeRelation(db, ContentUris.parseId(parent));

				db.setTransactionSuccessful();

			}finally{
				db.endTransaction();
			}
			return count;
		}


		public static interface IdenticalChildFinder {
			/**
			 * Search the database and see if there is a child that is identical (using whatever criteria you prefer) to the one described in values.
			 *
			 * @param m2m the DBHelper for the parent/child relationship
			 * @param parentChildDir the URI of the parent's children
			 * @param db the database to do lookups on
			 * @param childTable the child table to look into
			 * @param values the values that describe the child in question.
			 * @return if an identical child is found, returns its Uri. If none are found, returns null.
			 */
			public Uri getIdenticalChild(DBHelper m2m, Uri parentChildDir, SQLiteDatabase db, String childTable, ContentValues values);
		}

		/**
		 * Selects rows from the TO table that have a relation from the given item in the FROM table.
		 *
		 * @param fromId _ID of the item that's being
		 * @param db DB that contains all the tables
		 * @param toProjection projection for the TO table
		 * @param selection any extra selection query or null
		 * @param selectionArgs any extra selection arguments or null
		 * @param sortOrder the desired sort order or null
		 * @return a cursor whose content represents the to table
		 */
		public Cursor queryTo(long fromId, SQLiteDatabase db, String[] toProjection, String selection, String[] selectionArgs, String sortOrder){
			// XXX hack to get around ambiguous column names. Is there a better way to write this query?
			if (selection != null){
				selection = selection.replaceAll("(\\w+=\\?)", mToTable + ".$1");
			}

			return db.query(mToTable
					+ " INNER JOIN " + mJoinTable
					+ " ON " + mJoinTable+"."+ManyToManyColumns.TO_ID + "=" + mToTable + "." + BaseColumns._ID,
					ProviderUtils.addPrefixToProjection(mToTable, toProjection),
					ProviderUtils.addExtraWhere(selection, mJoinTable + "." + ManyToManyColumns.FROM_ID + "=?"),
					ProviderUtils.addExtraWhereArgs(selectionArgs, Long.toString(fromId)), null, null, sortOrder);
		}


		@Override
		public Cursor queryDir(SQLiteDatabase db, Uri uri, String[] projection,
				String selection, String[] selectionArgs, String sortOrder) {

			final Uri parent = ProviderUtils.removeLastPathSegment(uri);

			final long parentId = ContentUris.parseId(parent);
			return queryTo(parentId, db, projection, selection, selectionArgs, sortOrder);

		}

		@Override
		public Cursor queryItem(SQLiteDatabase db, Uri uri,
				String[] projection, String selection, String[] selectionArgs,
				String sortOrder) {
			final Uri parent = ProviderUtils.removeLastPathSegments(uri, 2);

			final long parentId = ContentUris.parseId(parent);

			final String childId = uri.getLastPathSegment();

			return queryTo(parentId,
					db,
					projection,
					ProviderUtils.addExtraWhere(selection, BaseColumns._ID+"=?"),
					ProviderUtils.addExtraWhereArgs(selectionArgs, childId),
					sortOrder);
		}
	}
}
