/* 
 * Copyright (C) 2007 Internet Archive.
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
 *
 * Logs.java
 *
 * Created on June 14, 2007
 *
 * $Id:$
 */
package org.archive.crawler.util;


/**
 * Enumerates existing Heritrix logs
 * 
 * @author Kristinn Sigurdsson
 */
public enum Logs{
	// TODO: This enum belongs in the heritrix sub project
	CRAWL ("crawl.log"),
    ALERTS ("alerts.log"),
	PROGRESS_STATISTICS ("progress-statistics.log"),
	RUNTIME_ERRORS ("runtime-errors.log"),
    NONFATAL_ERRORS ("nonfatal-errors.log"),
	URI_ERRORS ("uri-errors.log");
	
	String filename;
	
	Logs(String filename){
		this.filename = filename;
	}
	
	public String getFilename(){
		return filename;
	}
}