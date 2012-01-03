package edu.mit.mobile.android.locast.data;
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

import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncFieldMap;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncItem;

public class Commentable {
	public interface Columns {
		public static final String
			_COMMENT_DIR_URI = "comment_dir_uri";
	}

	public static final SyncMap SYNC_MAP = new SyncMap();

	static {
		SYNC_MAP.put(Columns._COMMENT_DIR_URI, new JsonSyncableItem.SyncFieldMap("comments_uri", SyncFieldMap.STRING, SyncItem.FLAG_OPTIONAL | SyncFieldMap.SYNC_FROM));
	}
}
