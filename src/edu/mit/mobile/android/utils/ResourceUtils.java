package edu.mit.mobile.android.utils;
/*
 * Copyright (C) 2011 MIT Mobile Experience Lab
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

import android.content.Context;
import android.text.Html;
import android.text.Spanned;

public class ResourceUtils {

	/**
	 * Like {@link Context#getString(int, Object...)}, but supports styled text.
	 *
	 * Note: this routine converts from html back to html a few times, so it's
	 * not the most efficient way to format text. Only use it if you need styled
	 * text.
	 *
	 * @param context
	 * @param resID
	 *            the string text resource
	 * @param formatArgs
	 * @return formatted, potentially styled text
	 */
	public static CharSequence getText(Context context, int resID,
			Object... formatArgs) {
		final CharSequence text = context.getText(resID);
		if (text instanceof Spanned) {
			final String htmlFormatString = Html.toHtml((Spanned) text);
			return Html.fromHtml(String.format(htmlFormatString, formatArgs));
		} else {
			return context.getString(resID, formatArgs);
		}
	}
}
