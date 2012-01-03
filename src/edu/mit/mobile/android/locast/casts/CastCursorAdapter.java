package edu.mit.mobile.android.locast.casts;
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
import android.content.Context;
import android.database.Cursor;
import android.view.View;
import edu.mit.mobile.android.imagecache.SimpleThumbnailCursorAdapter;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.ver2.R;

public class CastCursorAdapter extends SimpleThumbnailCursorAdapter {
	private final static String[] DEFAULT_FROM = new String[] {	Cast._THUMBNAIL_URI, 	Cast._AUTHOR, 	Cast._TITLE, Cast._DRAFT};
	private final static int[] DEFAULT_TO      = new int[] {	R.id.media_thumbnail, 	R.id.author, 	android.R.id.text1, R.id.draft};
	private final static int[] IMAGE_IDS = new int[] {R.id.media_thumbnail };

	public final static String[] DEFAULT_PROJECTION = {
			Cast._ID,
			Cast._AUTHOR,
			Cast._TITLE,
			Cast._DESCRIPTION,
			Cast._THUMBNAIL_URI
		};

	/**
	 * To add a thumbnail, make sure to include an ImageView  with an ID of R.id.media_thumbnail
	 *
	 * @param context
	 * @param c
	 * @param layout Layout to load individual casts into.
	 * @param from table column names to map data from
	 * @param to resource IDs to map data to
	 */
	public CastCursorAdapter(Context context, Cursor c, int layout, String[]from, int[] to) {
		super(context, layout, c, from, to, IMAGE_IDS, 0);
	}

	/**
	 * A CastCursorAdapter which uses the default cast layout.
	 *
	 * @param context
	 * @param c
	 */
	public CastCursorAdapter(Context context, Cursor c) {
		this(context, c, R.layout.browse_content_item, DEFAULT_FROM, DEFAULT_TO);

	}

	@Override
	public void setViewText(android.widget.TextView v, String text) {

		switch (v.getId()){
		case android.R.id.text2:
			if (text == null || text.length() == 0){
				v.setVisibility(View.INVISIBLE);
			}else{
				v.setVisibility(View.VISIBLE);
			}
			super.setViewText(v, text);
			break;

		case R.id.draft:
			if (text != null && text.equals("1")){
				v.setVisibility(View.VISIBLE);
			}else{
				v.setVisibility(View.INVISIBLE);
			}
			break;

			default:
				super.setViewText(v, text);
		}

	}
}
