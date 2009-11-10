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
