/* StatisticsReporter.java
 *
 * $Id$
 *
 * Created Jul 18, 2005
 *
 * Copyright (C) 2005 Internet Archive.
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
package org.archive.util;

import java.io.IOException;
import java.io.PrintWriter;

public interface ProgressStatisticsReporter {
    /**
     * @param writer Where to write statistics.
     * @throws IOException 
     */
    public void progressStatisticsLine(PrintWriter writer) throws IOException;
    
    /**
     * @param writer Where to write statistics legend.
     * @throws IOException 
     */
    public void progressStatisticsLegend(PrintWriter writer) throws IOException;
}
