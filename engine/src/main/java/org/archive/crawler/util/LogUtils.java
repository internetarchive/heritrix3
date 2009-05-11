/* LogUtils
 * 
 * $Id$
 *
 * Created on Jun 8, 2005
 * 
 * Copyright (C) 2003 Internet Archive.
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
 */
package org.archive.crawler.util;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Logger;

import org.archive.util.PropertyUtils;

/**
 * Logging utils.
 * @author stack
 */
public class LogUtils {
    /**
     * Creates a file logger that use heritrix.properties file logger
     * configuration.
     * Change the java.util.logging.FileHandler.* properties in
     * heritrix.properties to change file handler properties.
     * Use this method if you want a class to log to its own file
     * rather than use default (console) logger.
     * @param logsDir Directory in which to write logs.
     * @param baseName Base name to use for log file (Will have
     * java.util.logging.FileHandler.pattern or '.log' for suffix).
     * @param logger Logger whose handler we'll replace with the
     * file handler created herein.
     */
    public static FileHandler createFileLogger(File logsDir, String baseName,
            Logger logger) {
        int limit =
            PropertyUtils.getIntProperty("java.util.logging.FileHandler.limit",
            1024 * 1024 * 1024 * 1024);
        int count =
            PropertyUtils.getIntProperty("java.util.logging.FileHandler.count", 1);
        try {
            String tmp =
                System.getProperty("java.util.logging.FileHandler.pattern");
                File logFile = new File(logsDir, baseName +
                    ((tmp != null && tmp.length() > 0)? tmp: ".log"));
            FileHandler fh = new FileHandler(logFile.getAbsolutePath(), limit,
                count, true);
            // Manage the formatter to use.
            tmp = System.getProperty("java.util.logging.FileHandler.formatter");
            if (tmp != null && tmp.length() > 0) {
                Constructor<?> co = Class.forName(tmp).
                    getConstructor(new Class[] {});
                Formatter f = (Formatter) co.newInstance(new Object[] {});
                fh.setFormatter(f);
            }
            logger.addHandler(fh);
            logger.setUseParentHandlers(false);
            return fh; 
        } catch (Exception e) {
            logger.severe("Failed customization of logger: " + e.getMessage());
            return null; 
        }
    }
}
