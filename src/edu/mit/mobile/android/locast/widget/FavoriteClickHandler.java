package edu.mit.mobile.android.locast.widget;
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

import java.io.IOException;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import edu.mit.mobile.android.locast.data.Favoritable;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.widget.ValidatingCheckBox;
import edu.mit.mobile.android.widget.ValidatingCheckBox.ValidatedClickHandler;

public class FavoriteClickHandler implements ValidatedClickHandler {
	private final Uri data;
	private final Context context;

	public FavoriteClickHandler(Context context, Uri favoritableItem) {
		this.data = favoritableItem;
		this.context = context;
	}

	public Boolean performClick(ValidatingCheckBox checkBox) {
		final boolean currentState = checkBox.isChecked();

		try {
			final boolean newState = NetworkClient.getInstance(this.context).setFavorite(data, !currentState);
			final ContentResolver cr = context.getContentResolver();

			final ContentValues cv = new ContentValues();
			cv.put(Favoritable.Columns._FAVORITED, newState);
			cv.put(MediaProvider.CV_FLAG_DO_NOT_MARK_DIRTY, true);
			cr.update(data, cv, null, null);

		} catch (final NetworkProtocolException e) {
			e.printStackTrace();

		} catch (final IOException e) {
			e.printStackTrace();
		}

		// instead of returning the new value, use DB listeners to update this. The DB will always contain
		// the latest state if the network succeeded.
		return null;

	}
	/**
	 * Sets the star to the current state from the _FAVORITED column in the DB
	 * and sets up a FavoriteClickHandler() on it.
	 *
	 * @param activity An activity that contains a ValidatingCheckBox with an id of R.id.star
	 * @param c cursor pointing to an active row that has a Favoritable.Columns._FAVORITED
	 * @param item uri of the item that the cursor is pointing to.
	 */
	public static void setStarred(Activity activity, Cursor c, Uri item){
		final int favIdx = c.getColumnIndex(Favoritable.Columns._FAVORITED);
		final boolean favorited = !c.isNull(favIdx) && c.getInt(favIdx) >  0;

		final ValidatingCheckBox cb = (ValidatingCheckBox)activity.findViewById(R.id.favorite);
		cb.setValidatedClickHandler(new FavoriteClickHandler(activity, item));

		((ValidatingCheckBox)activity.findViewById(R.id.favorite)).setChecked(favorited);
	}

	@Override
	public void prePerformClick(ValidatingCheckBox checkBox) {}
}
