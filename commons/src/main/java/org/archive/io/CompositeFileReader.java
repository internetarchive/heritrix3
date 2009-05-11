/* CompositeFileReader
*
* $Id$
*
* Created on May 18, 2004
*
* Copyright (C) 2004 Internet Archive.
*
* This file is part of the Heritrix web crawler (crawler.archive.org).
*
* Heritrix is free software; you can redistribute it and/or modify
* it under the terms of the GNU Lesser Public License as published by
* the Free Software Foundation; either version 2.1 of the License, or
* any later version.
*
* Heritrix is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Lesser Public License for more details.
*
* You should have received a copy of the GNU Lesser Public License
* along with Heritrix; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package org.archive.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;


/**
 * @author gojomo
 */
public class CompositeFileReader extends InputStreamReader {

    /**
     * @param filenames
     * @throws IOException
     */
    public CompositeFileReader(List<File> filenames) throws IOException {
        super(new CompositeFileInputStream(filenames));
    }

}
