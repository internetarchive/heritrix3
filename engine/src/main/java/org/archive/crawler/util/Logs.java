/*
 *  This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual 
 *  contributors. 
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
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