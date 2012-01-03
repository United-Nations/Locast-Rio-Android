package edu.mit.mobile.android.locast.ver2.itineraries;
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

import android.database.Cursor;
import android.graphics.drawable.Drawable;

import com.google.android.maps.OverlayItem;

public class BasicLocatableOverlay extends LocatableItemOverlay {

	public BasicLocatableOverlay(Drawable marker) {
		super(marker);
	}

	public BasicLocatableOverlay(Drawable marker, Cursor c) {
		super(marker, c);
	}

	@Override
	protected OverlayItem createItem(int i) {
		this.mLocatableItems.moveToPosition(i);
		return new OverlayItem(getItemLocation(mLocatableItems), "", "");
	}
}
