package edu.mit.mobile.android.locast.ver2.casts;
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

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;

import com.stackoverflow.ArrayUtils;

import edu.mit.mobile.android.imagecache.SimpleThumbnailCursorAdapter;
import edu.mit.mobile.android.locast.data.CastMedia;
import edu.mit.mobile.android.locast.ver2.R;

public class CastMediaAdapter extends SimpleThumbnailCursorAdapter {

	public static final String[] CAST_MEDIA_DISPLAY = new String[]{CastMedia._TITLE, CastMedia._THUMBNAIL, CastMedia._THUMB_LOCAL, CastMedia._MEDIA_URL, CastMedia._LOCAL_URI, CastMedia._MIME_TYPE};
	public static final String[] CAST_MEDIA_PROJECTION = ArrayUtils.concat(new String[]{CastMedia._ID}, CAST_MEDIA_DISPLAY);

	private int mimeTypeCol;

	public CastMediaAdapter(Context context, int layout, Cursor c,
			String[] from, int[] to, int[] imageIDs, int flags) {
		super(context, layout, c, from, to, imageIDs, flags);

		cacheColumns(mCursor);
	}

	private void cacheColumns(Cursor c){
		if (c != null){
			mimeTypeCol = c.getColumnIndex(CastMedia._MIME_TYPE);
		}
	}

	@Override
	public void changeCursor(Cursor cursor) {
		cacheColumns(cursor);
		super.changeCursor(cursor);
	}

	@Override
	public Cursor swapCursor(Cursor c) {
		cacheColumns(c);
		return super.swapCursor(c);
	}

	@Override
	public void changeCursorAndColumns(Cursor c, String[] from, int[] to) {
		cacheColumns(c);
		super.changeCursorAndColumns(c, from, to);
	}

	public CastMediaAdapter(Context context) {
		super(context,
				R.layout.cast_media_item,
				null,
				CAST_MEDIA_DISPLAY,
				new int[]{R.id.title, R.id.media_thumbnail, R.id.media_thumbnail},
				new int[]{R.id.media_thumbnail},
				0);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		final View v = super.getView(position, convertView, parent);

		final View overlay = v.findViewById(R.id.thumbnail_overlay);
		if (overlay != null){
			final String mimeType = getCursor().getString(mimeTypeCol);
			final boolean visible = mimeType != null && (mimeType.startsWith("video/") || "text/html".equals(mimeType));

			overlay.setVisibility(visible ? View.VISIBLE : View.GONE);
		}
		return v;
	}
}
