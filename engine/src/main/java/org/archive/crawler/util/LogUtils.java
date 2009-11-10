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
