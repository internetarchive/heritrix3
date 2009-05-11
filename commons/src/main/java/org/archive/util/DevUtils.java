/* DevUtils
 *
 * Created on Oct 29, 2003
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
 */
package org.archive.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Logger;


/**
 * Write a message and stack trace to the 'org.archive.util.DevUtils' logger.
 *
 * @author gojomo
 * @version $Revision$ $Date$
 */
public class DevUtils {
    public static Logger logger =
        Logger.getLogger(DevUtils.class.getName());

    /**
     * Log a warning message to the logger 'org.archive.util.DevUtils' made of
     * the passed 'note' and a stack trace based off passed exception.
     *
     * @param ex Exception we print a stacktrace on.
     * @param note Message to print ahead of the stacktrace.
     */
    public static void warnHandle(Throwable ex, String note) {
        logger.warning(TextUtils.exceptionToString(note, ex));
    }

    /**
     * @return Extra information gotten from current Thread.  May not
     * always be available in which case we return empty string.
     */
    public static String extraInfo() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw); 
        final Thread current = Thread.currentThread();
        if (current instanceof MultiReporter) {
            MultiReporter tt = (MultiReporter)current;
            try {
                tt.reportTo(pw);
            } catch (IOException e) {
                // Not really possible w/ a StringWriter
                e.printStackTrace();
            } 
        }
        if (current instanceof ProgressStatisticsReporter) {
            ProgressStatisticsReporter tt = (ProgressStatisticsReporter)current;
            try {
                tt.progressStatisticsLegend(pw);
                tt.progressStatisticsLine(pw);
            } catch (IOException e) {
                // Not really possible w/ a StringWriter
                e.printStackTrace();
            }
        }
        pw.flush();
        return sw.toString();
    }

    /**
     * Nothing to see here, move along.
     * @deprecated  This method was never used.
     */
    @Deprecated
    public static void betterPrintStack(RuntimeException re) {
        re.printStackTrace(System.err);
    }
    
    /**
     * Send this JVM process a SIGQUIT; giving a thread dump and possibly
     * a heap histogram (if using -XX:+PrintClassHistogram).
     * 
     * Used to automatically dump info, for example when a serious error
     * is encountered. Would use 'jmap'/'jstack', but have seen JVM
     * lockups -- perhaps due to lost thread wake signals -- when using
     * those against Sun 1.5.0+03 64bit JVM. 
     */
    public static void sigquitSelf() {
        try {
            Process p = Runtime.getRuntime().exec(
                    new String[] {"perl", "-e", "print getppid(). \"\n\";"});
            BufferedReader br =
                new BufferedReader(new InputStreamReader(p.getInputStream()));
            String ppid = br.readLine();
            Runtime.getRuntime().exec(
                    new String[] {"sh", "-c", "kill -3 "+ppid}).waitFor();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
