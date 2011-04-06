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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.IOUtils;
import org.archive.io.GzipHeader;
import org.archive.io.NoGzipMagicException;


/**
 * Miscellaneous useful methods.
 *
 * @author gojomo & others
 */
public class ArchiveUtils {
    private static final Logger LOGGER = Logger.getLogger(ArchiveUtils.class.getName());

    
    final public static String VERSION = loadVersion();
    
    /**
     * Arc-style date stamp in the format yyyyMMddHHmm and UTC time zone.
     */
    private static final ThreadLocal<SimpleDateFormat> 
        TIMESTAMP12 = threadLocalDateFormat("yyyyMMddHHmm");;
    
    /**
     * Arc-style date stamp in the format yyyyMMddHHmmss and UTC time zone.
     */
    private static final ThreadLocal<SimpleDateFormat> 
       TIMESTAMP14 = threadLocalDateFormat("yyyyMMddHHmmss");
    /**
     * Arc-style date stamp in the format yyyyMMddHHmmssSSS and UTC time zone.
     */
    private static final ThreadLocal<SimpleDateFormat> 
        TIMESTAMP17 = threadLocalDateFormat("yyyyMMddHHmmssSSS");

    /**
     * Log-style date stamp in the format yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
     * UTC time zone is assumed.
     */
    private static final ThreadLocal<SimpleDateFormat> 
        TIMESTAMP17ISO8601Z = threadLocalDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    
    /**
     * Log-style date stamp in the format yyyy-MM-dd'T'HH:mm:ss'Z'
     * UTC time zone is assumed.
     */
    private static final ThreadLocal<SimpleDateFormat>
        TIMESTAMP14ISO8601Z = threadLocalDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    
    /**
     * Default character to use padding strings.
     */
    private static final char DEFAULT_PAD_CHAR = ' ';

    /** milliseconds in an hour */ 
    private static final int HOUR_IN_MS = 60 * 60 * 1000;
    /** milliseconds in a day */
    private static final int DAY_IN_MS = 24 * HOUR_IN_MS;
    
    private static ThreadLocal<SimpleDateFormat> threadLocalDateFormat(final String pattern) {
        ThreadLocal<SimpleDateFormat> tl = new ThreadLocal<SimpleDateFormat>() {
            protected SimpleDateFormat initialValue() {
                SimpleDateFormat df = new SimpleDateFormat(pattern);
                df.setTimeZone(TimeZone.getTimeZone("GMT"));
                return df;
            }
        };
        return tl;
    }
    
    public static int MAX_INT_CHAR_WIDTH =
        Integer.toString(Integer.MAX_VALUE).length();
    
    /**
     * Utility function for creating arc-style date stamps
     * in the format yyyMMddHHmmssSSS.
     * Date stamps are in the UTC time zone
     * @return the date stamp
     */
    public static String get17DigitDate(){
        return TIMESTAMP17.get().format(new Date());
    }
    
    static long LAST_UNIQUE_NOW17 = 0;
    static String LAST_TIMESTAMP17 = ""; 
    /**
     * Utility function for creating UNIQUE-from-this-class 
     * arc-style date stamps in the format yyyMMddHHmmssSSS.
     * Rather than giving a duplicate datestamp on a 
     * subsequent call, will increment the milliseconds until a 
     * unique value is returned. 
     * 
     * Date stamps are in the UTC time zone
     * @return the date stamp
     */
    public synchronized static String getUnique17DigitDate(){
        long effectiveNow = System.currentTimeMillis(); 
        effectiveNow = Math.max(effectiveNow, LAST_UNIQUE_NOW17+1);
        String candidate = get17DigitDate(effectiveNow);
        while(candidate.equals(LAST_TIMESTAMP17)) {
            effectiveNow++;
            candidate = get17DigitDate(effectiveNow);
        }
        LAST_UNIQUE_NOW17 = effectiveNow;
        LAST_TIMESTAMP17 = candidate; 
        return candidate;
    }

    /**
     * Utility function for creating arc-style date stamps
     * in the format yyyyMMddHHmmss.
     * Date stamps are in the UTC time zone
     * @return the date stamp
     */
    public static String get14DigitDate(){
        return TIMESTAMP14.get().format(new Date());
    }
    
    static long LAST_UNIQUE_NOW14 = 0;
    static String LAST_TIMESTAMP14 = ""; 
    /**
     * Utility function for creating UNIQUE-from-this-class 
     * arc-style date stamps in the format yyyMMddHHmmss.
     * Rather than giving a duplicate datestamp on a 
     * subsequent call, will increment the seconds until a 
     * unique value is returned. 
     * 
     * Date stamps are in the UTC time zone
     * @return the date stamp
     */
    public synchronized static String getUnique14DigitDate(){
        long effectiveNow = System.currentTimeMillis(); 
        effectiveNow = Math.max(effectiveNow, LAST_UNIQUE_NOW14+1);
        String candidate = get14DigitDate(effectiveNow);
        while(candidate.equals(LAST_TIMESTAMP14)) {
            effectiveNow += 1000;
            candidate = get14DigitDate(effectiveNow);
        }
        LAST_UNIQUE_NOW14 = effectiveNow;
        LAST_TIMESTAMP14 = candidate; 
        return candidate;
    }

    /**
     * Utility function for creating arc-style date stamps
     * in the format yyyyMMddHHmm.
     * Date stamps are in the UTC time zone
     * @return the date stamp
     */
    public static String get12DigitDate(){
        return TIMESTAMP12.get().format(new Date());
    }

    /**
     * Utility function for creating log timestamps, in
     * W3C/ISO8601 format, assuming UTC. Use current time. 
     * 
     * Format is yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
     * 
     * @return the date stamp
     */
    public static String getLog17Date(){
        return TIMESTAMP17ISO8601Z.get().format(new Date());
    }
    
    /**
     * Utility function for creating log timestamps, in
     * W3C/ISO8601 format, assuming UTC. 
     * 
     * Format is yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
     * @param date Date to format.
     * 
     * @return the date stamp
     */
    public static String getLog17Date(long date){
        return TIMESTAMP17ISO8601Z.get().format(new Date(date));
    }
    
    /**
     * Utility function for creating log timestamps, in
     * W3C/ISO8601 format, assuming UTC. Use current time. 
     * 
     * Format is yyyy-MM-dd'T'HH:mm:ss'Z'
     * 
     * @return the date stamp
     */
    public static String getLog14Date(){
        return TIMESTAMP14ISO8601Z.get().format(new Date());
    }
    
    /**
     * Utility function for creating log timestamps, in
     * W3C/ISO8601 format, assuming UTC. 
     * 
     * Format is yyyy-MM-dd'T'HH:mm:ss'Z'
     * @param date long timestamp to format.
     * 
     * @return the date stamp
     */
    public static String getLog14Date(long date){
        return TIMESTAMP14ISO8601Z.get().format(new Date(date));
    }
    
    /**
     * Utility function for creating log timestamps, in
     * W3C/ISO8601 format, assuming UTC. 
     * 
     * Format is yyyy-MM-dd'T'HH:mm:ss'Z'
     * @param date Date to format.
     * 
     * @return the date stamp
     */
    public static String getLog14Date(Date date){
        return TIMESTAMP14ISO8601Z.get().format(date);
    }
    
    /**
     * Utility function for creating arc-style date stamps
     * in the format yyyyMMddHHmmssSSS.
     * Date stamps are in the UTC time zone
     *
     * @param date milliseconds since epoc
     * @return the date stamp
     */
    public static String get17DigitDate(long date){
        return TIMESTAMP17.get().format(new Date(date));
    }
    
    public static String get17DigitDate(Date date){
        return TIMESTAMP17.get().format(date);
    }

    /**
     * Utility function for creating arc-style date stamps
     * in the format yyyyMMddHHmmss.
     * Date stamps are in the UTC time zone
     *
     * @param date milliseconds since epoc
     * @return the date stamp
     */
    public static String get14DigitDate(long date){
        return TIMESTAMP14.get().format(new Date(date));
    }

    public static String get14DigitDate(Date d) {
        return TIMESTAMP14.get().format(d);
    }

    /**
     * Utility function for creating arc-style date stamps
     * in the format yyyyMMddHHmm.
     * Date stamps are in the UTC time zone
     *
     * @param date milliseconds since epoc
     * @return the date stamp
     */
    public static String get12DigitDate(long date){
        return TIMESTAMP12.get().format(new Date(date));
    }
    
    public static String get12DigitDate(Date d) {
        return TIMESTAMP12.get().format(d);
    }
    
    /**
     * Parses an ARC-style date.  If passed String is < 12 characters in length,
     * we pad.  At a minimum, String should contain a year (>=4 characters).
     * Parse will also fail if day or month are incompletely specified.  Depends
     * on the above getXXDigitDate methods.
     * @param A 4-17 digit date in ARC style (<code>yyyy</code> to
     * <code>yyyyMMddHHmmssSSS</code>) formatting.  
     * @return A Date object representing the passed String. 
     * @throws ParseException
     */
    public static Date getDate(String d) throws ParseException {
        Date date = null;
        if (d == null) {
            throw new IllegalArgumentException("Passed date is null");
        }
        switch (d.length()) {
        case 14:
            date = ArchiveUtils.parse14DigitDate(d);
            break;

        case 17:
            date = ArchiveUtils.parse17DigitDate(d);
            break;

        case 12:
            date = ArchiveUtils.parse12DigitDate(d);
            break;
           
        case 0:
        case 1:
        case 2:
        case 3:
            throw new ParseException("Date string must at least contain a" +
                "year: " + d, d.length());
            
        default:
            if (!(d.startsWith("19") || d.startsWith("20"))) {
                throw new ParseException("Unrecognized century: " + d, 0);
            }
            if (d.length() < 8 && (d.length() % 2) != 0) {
                throw new ParseException("Incomplete month/date: " + d,
                    d.length());
            }
            StringBuilder sb = new StringBuilder(d);
            if (sb.length() < 8) {
                for (int i = sb.length(); sb.length() < 8; i += 2) {
                    sb.append("01");
                }
            }
            if (sb.length() < 12) {
                for (int i = sb.length(); sb.length() < 12; i++) {
                    sb.append("0");
                }
            }
            date = ArchiveUtils.parse12DigitDate(sb.toString());
        }

        return date;
    }

    /**
     * Utility function for parsing arc-style date stamps
     * in the format yyyMMddHHmmssSSS.
     * Date stamps are in the UTC time zone.  The whole string will not be
     * parsed, only the first 17 digits.
     *
     * @param date an arc-style formatted date stamp
     * @return the Date corresponding to the date stamp string
     * @throws ParseException if the inputstring was malformed
     */
    public static Date parse17DigitDate(String date) throws ParseException {
        return TIMESTAMP17.get().parse(date);
    }

    /**
     * Utility function for parsing arc-style date stamps
     * in the format yyyMMddHHmmss.
     * Date stamps are in the UTC time zone.  The whole string will not be
     * parsed, only the first 14 digits.
     *
     * @param date an arc-style formatted date stamp
     * @return the Date corresponding to the date stamp string
     * @throws ParseException if the inputstring was malformed
     */
    public static Date parse14DigitDate(String date) throws ParseException{
        return TIMESTAMP14.get().parse(date);
    }

    /**
     * Utility function for parsing arc-style date stamps
     * in the format yyyMMddHHmm.
     * Date stamps are in the UTC time zone.  The whole string will not be
     * parsed, only the first 12 digits.
     *
     * @param date an arc-style formatted date stamp
     * @return the Date corresponding to the date stamp string
     * @throws ParseException if the inputstring was malformed
     */
    public static Date parse12DigitDate(String date) throws ParseException{
        return TIMESTAMP12.get().parse(date);
    }
    
    /**
     * @param timestamp A 14-digit timestamp or the suffix for a 14-digit
     * timestamp: E.g. '20010909014640' or '20010101' or '1970'.
     * @return Seconds since the epoch as a string zero-pre-padded so always
     * Integer.MAX_VALUE wide (Makes it so sorting of resultant string works
     * properly).
     * @throws ParseException 
     */
    public static String secondsSinceEpoch(String timestamp)
    throws ParseException {
        return zeroPadInteger((int)
            (getSecondsSinceEpoch(timestamp).getTime()/1000));
    }
    
    /**
     * @param timestamp A 14-digit timestamp or the suffix for a 14-digit
     * timestamp: E.g. '20010909014640' or '20010101' or '1970'.
     * @return A date.
     * @see #secondsSinceEpoch(String)
     * @throws ParseException 
     */
    public static Date getSecondsSinceEpoch(String timestamp)
    throws ParseException {
        if (timestamp.length() < 14) {
            if (timestamp.length() < 10 && (timestamp.length() % 2) == 1) {
                throw new IllegalArgumentException("Must have year, " +
                    "month, date, hour or second granularity: " + timestamp);
            }
            if (timestamp.length() == 4) {
                // Add first month and first date.
                timestamp = timestamp + "01010000";
            }
            if (timestamp.length() == 6) {
                // Add a date of the first.
                timestamp = timestamp + "010000";
            }
            if (timestamp.length() < 14) {
                timestamp = timestamp +
                    ArchiveUtils.padTo("", 14 - timestamp.length(), '0');
            }
        }
        return ArchiveUtils.parse14DigitDate(timestamp);
    }
    
    /**
     * @param i Integer to add prefix of zeros too.  If passed
     * 2005, will return the String <code>0000002005</code>. String
     * width is the width of Integer.MAX_VALUE as a string (10
     * digits).
     * @return Padded String version of <code>i</code>.
     */
    public static String zeroPadInteger(int i) {
        return ArchiveUtils.padTo(Integer.toString(i),
                MAX_INT_CHAR_WIDTH, '0');
    }

    /** 
     * Convert an <code>int</code> to a <code>String</code>, and pad it to
     * <code>pad</code> spaces.
     * @param i the int
     * @param pad the width to pad to.
     * @return String w/ padding.
     */
    public static String padTo(final int i, final int pad) {
        String n = Integer.toString(i);
        return padTo(n, pad);
    }
    
    /** 
     * Pad the given <code>String</code> to <code>pad</code> characters wide
     * by pre-pending spaces.  <code>s</code> should not be <code>null</code>.
     * If <code>s</code> is already wider than <code>pad</code> no change is
     * done.
     *
     * @param s the String to pad
     * @param pad the width to pad to.
     * @return String w/ padding.
     */
    public static String padTo(final String s, final int pad) {
        return padTo(s, pad, DEFAULT_PAD_CHAR);
    }

    /** 
     * Pad the given <code>String</code> to <code>pad</code> characters wide
     * by pre-pending <code>padChar</code>.
     * 
     * <code>s</code> should not be <code>null</code>. If <code>s</code> is
     * already wider than <code>pad</code> no change is done.
     *
     * @param s the String to pad
     * @param pad the width to pad to.
     * @param padChar The pad character to use.
     * @return String w/ padding.
     */
    public static String padTo(final String s, final int pad,
            final char padChar) {
        String result = s;
        int l = s.length();
        if (l < pad) {
            StringBuffer sb = new StringBuffer(pad);
            while(l < pad) {
                sb.append(padChar);
                l++;
            }
            sb.append(s);
            result = sb.toString();
        }
        return result;
    }

    /** check that two byte arrays are equal.  They may be <code>null</code>.
     *
     * @param lhs a byte array
     * @param rhs another byte array.
     * @return <code>true</code> if they are both equal (or both
     * <code>null</code>)
     */
    public static boolean byteArrayEquals(final byte[] lhs, final byte[] rhs) {
        if (lhs == null && rhs != null || lhs != null && rhs == null) {
            return false;
        }
        if (lhs==rhs) {
            return true;
        }
        if (lhs.length != rhs.length) {
            return false;
        }
        for(int i = 0; i<lhs.length; i++) {
            if (lhs[i]!=rhs[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Converts a double to a string.
     * @param val The double to convert
     * @param precision How many characters to include after '.'
     * @return the double as a string.
     */
    public static String doubleToString(double val, int maxFractionDigits){
        return doubleToString(val, maxFractionDigits, 0);
    }

    private static String doubleToString(double val, int maxFractionDigits, int minFractionDigits) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.US); 
        f.setMaximumFractionDigits(maxFractionDigits);
        f.setMinimumFractionDigits(minFractionDigits);
        return f.format(val); 
    }

    /**
     * Takes a byte size and formats it for display with 'friendly' units. 
     * <p>
     * This involves converting it to the largest unit 
     * (of B, KiB, MiB, GiB, TiB) for which the amount will be > 1.
     * <p>
     * Additionally, at least 2 significant digits are always displayed. 
     * <p>
     * Negative numbers will be returned as '0 B'.
     *
     * @param amount the amount of bytes
     * @return A string containing the amount, properly formated.
     */
    public static String formatBytesForDisplay(long amount) {
        double displayAmount = (double) amount;
        int unitPowerOf1024 = 0; 

        if(amount <= 0){
            return "0 B";
        }
        
        while(displayAmount>=1024 && unitPowerOf1024 < 4) {
            displayAmount = displayAmount / 1024;
            unitPowerOf1024++;
        }
        
        final String[] units = { " B", " KiB", " MiB", " GiB", " TiB" };
        
        // ensure at least 2 significant digits (#.#) for small displayValues
        int fractionDigits = (displayAmount < 10) ? 1 : 0; 
        return doubleToString(displayAmount, fractionDigits, fractionDigits) 
                   + units[unitPowerOf1024];
    }

    /**
     * Convert milliseconds value to a human-readable duration
     * @param time
     * @return Human readable string version of passed <code>time</code>
     */
    public static String formatMillisecondsToConventional(long time) {
        return formatMillisecondsToConventional(time,5);
    }
        
    /**
     * Convert milliseconds value to a human-readable duration of 
     * mixed units, using units no larger than days. For example,
     * "5d12h13m12s113ms" or "19h51m". 
     * 
     * @param duration
     * @param unitCount how many significant units to show, at most
     *  for example, a value of 2 would show days+hours or hours+seconds 
     *  but not hours+second+milliseconds
     * @return Human readable string version of passed <code>time</code>
     */
    public static String formatMillisecondsToConventional(long duration, int unitCount) {
        if(unitCount <=0) {
            unitCount = 5;
        }
        if(duration==0) {
            return "0ms";
        }
        StringBuffer sb = new StringBuffer();
        if(duration<0) {
            sb.append("-");
        }
        long absTime = Math.abs(duration);
        long[] thresholds = {DAY_IN_MS, HOUR_IN_MS, 60000, 1000, 1};
        String[] units = {"d","h","m","s","ms"};
        
        for(int i = 0; i < thresholds.length; i++) {
            if(absTime >= thresholds[i]) {
                sb.append(absTime / thresholds[i] + units[i]);
                absTime = absTime % thresholds[i];
                unitCount--;
            }
            if(unitCount==0) {
                break;
            }
        }
        return sb.toString();
    }
    
    /**
     * Copy the raw bytes of a long into a byte array, starting at
     * the specified offset.
     * 
     * @param l
     * @param array
     * @param offset
     */
    public static void longIntoByteArray(long l, byte[] array, int offset) {
        int i, shift;
                  
        for(i = 0, shift = 56; i < 8; i++, shift -= 8)
        array[offset+i] = (byte)(0xFF & (l >> shift));
    }
    
    public static long byteArrayIntoLong(byte [] bytearray) {
        return byteArrayIntoLong(bytearray, 0);
    }
    
    /**
     * Byte array into long.
     * @param bytearray Array to convert to a long.
     * @param offset Offset into array at which we start decoding the long.
     * @return Long made of the bytes of <code>array</code> beginning at
     * offset <code>offset</code>.
     * @see #longIntoByteArray(long, byte[], int)
     */
    public static long byteArrayIntoLong(byte [] bytearray,
            int offset) {
        long result = 0;
        for (int i = offset; i < 8 /*Bytes in long*/; i++) {
            result = (result << 8 /*Bits in byte*/) |
                (0xff & (byte)(bytearray[i] & 0xff));
        }
        return result;
    }

    /**
     * Given a string that may be a plain host or host/path (without
     * URI scheme), add an implied http:// if necessary. 
     * 
     * @param u string to evaluate
     * @return string with http:// added if no scheme already present
     */
    public static String addImpliedHttpIfNecessary(String u) {
        int colon = u.indexOf(':');
        int period = u.indexOf('.');
        if (colon == -1 || (period >= 0) && (period < colon)) {
            // No scheme present; prepend "http://"
            u = "http://" + u;
        }
        return u;
    }

    /**
     * Verify that the array begins with the prefix. 
     * 
     * @param array
     * @param prefix
     * @return true if array is identical to prefix for the first prefix.length
     * positions 
     */
    public static boolean startsWith(byte[] array, byte[] prefix) {
        if(prefix.length>array.length) {
            return false;
        }
        for(int i = 0; i < prefix.length; i++) {
            if(array[i]!=prefix[i]) {
                return false; 
            }
        }
        return true; 
    }

    /**
     * Utility method to get a String shortReportLine from Reporter
     * @param rep  Reporter to get shortReportLine from
     * @return String of report
     */
    public static String shortReportLine(Reporter rep) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try {
            rep.shortReportLineTo(pw);
        } catch (IOException e) {
            // not really possible
            e.printStackTrace();
        }
        pw.flush();
        return sw.toString();
    }

    /**
     * Compose the requested report into a String. DANGEROUS IF REPORT
     * CAN BE LARGE.
     * 
     * @param rep Reported
     * @param name String name of report to compose
     * @return String of report
     */
    public static String writeReportToString(MultiReporter rep, String name) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        rep.reportTo(name,pw);
        pw.flush();
        return sw.toString();
    }
    
    /**
     * Enhance given object's default String display for appearing
     * nested in a pretty Map String.
     * 
     * @param obj Object to prettify
     * @return prettified String
     */
    @SuppressWarnings("unchecked")
    public static String prettyString(Object obj) {
        // these things have to checked and casted unfortunately
        if (obj instanceof Object[]) {
            return prettyString((Object[]) obj);
        } else if (obj instanceof Map) {
            return prettyString((Map) obj);
        } else {
        return "<"+obj+">"; 
    }
    }
    
    /**
     * Provide a improved String of a Map's entries
     * 
     * @param Map
     * @return prettified (in curly brackets) string of Map contents
     */
    @SuppressWarnings("unchecked")
    public static String prettyString(Map map) {
        StringBuilder builder = new StringBuilder();
        builder.append("{ ");
        boolean needsComma = false; 
        for( Object key : map.keySet()) {
            if(needsComma) {
                builder.append(", ");
            }
            builder.append(key);
            builder.append(": ");
            builder.append(prettyString(map.get(key)));
            needsComma = true; 
        }
        builder.append(" }");
        return builder.toString();
    }
    
    /**
     * Provide a slightly-improved String of Object[]
     * 
     * @param Object[]
     * @return prettified (in square brackets) of Object[]
     */
    public static String prettyString(Object[] array) {
        StringBuilder builder = new StringBuilder();
        builder.append("[ ");
        boolean needsComma = false; 
        for (Object o: array) {
            if(o==null) continue;
            if(needsComma) {
                builder.append(", ");
            }
            builder.append(prettyString(o));
            needsComma = true; 
        }
        builder.append(" ]");
        return builder.toString();
    }
    
    
    private static String loadVersion() {
        InputStream input = ArchiveUtils.class.getResourceAsStream(
                "/org/archive/util/version.txt");
        if (input == null) {
            return "UNKNOWN";
        }
        BufferedReader br = null;
        String version;
        try {
            br = new BufferedReader(new InputStreamReader(input));
            version = br.readLine();
            br.readLine();
        } catch (IOException e) {
            return e.getMessage();
        } finally {
            closeQuietly(br);
        }
        
        version = version.trim();
        if (!version.endsWith("SNAPSHOT")) {
            return version;
        }
        
        input = ArchiveUtils.class.getResourceAsStream("/org/archive/util/timestamp.txt");
        if (input == null) {
            return version;
        }
        
        br = null;
        String timestamp;
        try {
            br = new BufferedReader(new InputStreamReader(input));
            timestamp = br.readLine();
        } catch (IOException e) {
            return version;
        } finally {
            closeQuietly(br);
        }
        
        if (timestamp.startsWith("timestamp=")) {
            timestamp = timestamp.substring(10);
        }
        
        return version.trim() + "-" + timestamp.trim();
    }

    public static Set<String> TLDS;
    
    static {
        TLDS = new HashSet<String>();
        InputStream is = ArchiveUtils.class.getResourceAsStream("tlds-alpha-by-domain.txt");
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line; 
            while((line = reader.readLine())!=null) {
                if (line.startsWith("#")) {
                    continue;
                }
                TLDS.add(line.trim().toLowerCase()); 
            }
        } catch (Exception e) { 
            LOGGER.log(Level.SEVERE,"TLD list unavailable",e);
        } finally {
            IOUtils.closeQuietly(is); 
        }
    }
    /**
     * Return whether the given string represents a known 
     * top-level-domain (like "com", "org", etc.) per IANA
     * as of 20100419
     * 
     * @param dom candidate string
     * @return boolean true if recognized as TLD
     */
    public static boolean isTld(String dom) {
        return TLDS.contains(dom.toLowerCase());
    }
    
    public static void closeQuietly(Object input) {
        if(input == null || ! (input instanceof Closeable)) {
            return;
        }
        try {
            ((Closeable)input).close();
        } catch (IOException ioe) {
            // ignore
        }
    }
    
    /**
     * Perform checks as to whether normal execution should proceed.
     * 
     * If an external interrupt is detected, throw an interrupted exception.
     * Used before anything that should not be attempted by a 'zombie' thread
     * that the Frontier/Crawl has given up on.
     * 
     * @throws InterruptedException
     */
    public static void continueCheck() throws InterruptedException {
        if(Thread.interrupted()) {
            throw new InterruptedException("interrupt detected");
        }
    }

    /**
     * Read stream into buf until EOF or buf full.
     * 
     * @param input
     * @param buf
     * @throws IOException
     */
    public static int readFully(InputStream input, byte[] buf) 
    throws IOException {
        int max = buf.length;
        int ofs = 0;
        while (ofs < max) {
            int l = input.read(buf, ofs, max - ofs);
            if (l == 0) {
                throw new EOFException();
            }
            ofs += l;
        }
        return ofs; 
    }

    /** suffix to recognize gzipped files */
    public static final String GZIP_SUFFIX = ".gz";

    /**
     * Get a BufferedReader on the crawler journal given
     * 
     * TODO: move to a general utils class 
     * 
     * @param source File journal
     * @return journal buffered reader.
     * @throws IOException
     */
    public static BufferedReader getBufferedReader(File source) throws IOException {
        InputStream is = new BufferedInputStream(new FileInputStream(source));
        boolean isGzipped = source.getName().toLowerCase().
            endsWith(GZIP_SUFFIX);
        if(isGzipped) {
            is = new GZIPInputStream(is);
        }
        return new BufferedReader(new InputStreamReader(is));
    }

    /**
     * Get a BufferedReader on the crawler journal given.
     * 
     * @param source URL journal
     * @return journal buffered reader.
     * @throws IOException
     */
    public static BufferedReader getBufferedReader(URL source) throws IOException {
        URLConnection conn = source.openConnection();
        boolean isGzipped = conn.getContentType() != null && conn.getContentType().equalsIgnoreCase("application/x-gzip")
                || conn.getContentEncoding() != null && conn.getContentEncoding().equalsIgnoreCase("gzip");
        InputStream uis = conn.getInputStream();
        return new BufferedReader(isGzipped?
            new InputStreamReader(new GZIPInputStream(uis)):
            new InputStreamReader(uis));    
    }
    
    /**
     * Gzip passed bytes.
     * Use only when bytes is small.
     * @param bytes What to gzip.
     * @return A gzip member of bytes.
     * @throws IOException
     */
    public static byte [] gzip(byte [] bytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzipOS = new GZIPOutputStream(baos);
        gzipOS.write(bytes, 0, bytes.length);
        gzipOS.close();
        return baos.toByteArray();
    }
    
    /**
     * Tests passed stream is gzip stream by reading in the HEAD.
     * Does not mark/reset stream -- so this test actually makes
     * stream unopenable within GZIP streams, unless reset. 
     * @param is An InputStream.
     * @return True if compressed stream.
     * @throws IOException
     */
    public static boolean isGzipped(final InputStream is)
    throws IOException {
        try {
            new GzipHeader(is);
        } catch (NoGzipMagicException e) {
            return false;
        }
        return true;
    }
    
//    public static long doubleMurmur(byte[] data) {
//        int first = MurmurHash.hash(data, 7);
//        int second = MurmurHash.hash(data, 13);
//        return (((long)first)<<32) | ((long)second & 0x00000000ffffffffl);
//    }
}

