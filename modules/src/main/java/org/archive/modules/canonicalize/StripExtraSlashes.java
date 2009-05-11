/*RELICENSE-RESEARCH*/
/*
 * Created on 2006-aug-25
 *
 * Copyright (C) 2006 Royal Library of Sweden.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.archive.modules.canonicalize;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strip any extra slashes, '/', found in the path. 
 * Use this rule to equate 'http://www.archive.org//A//B/index.html' and 
 * 'http://www.archive.org/A/B/index.html'."
 */
public class StripExtraSlashes extends BaseRule {
    private static final long serialVersionUID = 1L;

    private static final Pattern REGEX = Pattern.compile("(^https?://.*?)//+(.*)");

    public StripExtraSlashes() {
        super();
    }

    public String canonicalize(String url) {
        Matcher matcher = REGEX.matcher(url);
        while (matcher.matches()) {
            url = matcher.group(1) + "/" + matcher.group(2);
            matcher = REGEX.matcher(url);
        }
        return url;
    }
}
