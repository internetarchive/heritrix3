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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.archive.io.CompositeFileReader;
import org.archive.util.ArchiveUtils;

/**
 * This class contains a variety of methods for reading log files (or other text 
 * files containing repeated lines with similar information).
 * <p>
 * All methods are static.
 *
 * @author Kristinn Sigurdsson
 */

public class LogReader
{
    /**
     * Returns the entire file. Useful for smaller files.
     *
     * @param aFileName a file name
     * @return The String representation of the entire file.
     *         Null is returned if errors occur (file not found or io exception)
     */
    public static String get(String aFileName){
        try {
            return get(new FileReader(aFileName));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Reads entire contents of reader, returns as string.
     *
     * @param reader
     * @return String of entire contents; null for any error.
     */
    public static String get(InputStreamReader reader){
        StringBuffer ret = new StringBuffer();
        try{
            BufferedReader bf = new BufferedReader(reader, 8192);

            String line = null;
            while ((line = bf.readLine()) != null) {
                ret.append(line);
                ret.append("\n");
            }
        } catch(IOException e){
            e.printStackTrace();
            return null;
        }
        return ret.toString();
    }

    /**
     * Gets a portion of a log file. Starting at a given line number and the n-1
     * lines following that one or until the end of the log if that is reached
     * first.
     *
     * @param aFileName The filename of the log/file
     * @param lineNumber The number of the first line to get (if larger then the 
     *                   file an empty string will be returned)
     * @param n How many lines to return (total, including the one indicated by 
     *                   lineNumber). If smaller then 1 then an empty string 
     *                   will be returned.
     *
     * @return An array of two strings is returned. At index 0 a portion of the
     *         file starting at lineNumber and reaching lineNumber+n is located.
     *         At index 1 there is an informational string about how large a
     *         segment of the file is being returned.
     *         Null is returned if errors occur (file not found or io exception)
     */
    public static String[] get(String aFileName, int lineNumber, int n)
    {
        File f = new File(aFileName);
        long logsize = f.length();
        try {
            return get(new FileReader(aFileName),lineNumber,n,logsize);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Gets a portion of a log spread across a numbered series of files.
     *
     * Starting at a given line number and the n-1 lines following that
     * one or until the end of the log if that is reached
     * first.
     *
     * @param aFileName The filename of the log/file
     * @param lineNumber The number of the first line to get (if larger then the
     *                   file an empty string will be returned)
     * @param n How many lines to return (total, including the one indicated by 
     *                   lineNumber). If smaller then 1 then an empty string 
     *                   will be returned.
     *
     * @return An array of two strings is returned. At index 0 a portion of the
     *         file starting at lineNumber and reaching lineNumber+n is located.
     *         At index 1 there is an informational string about how large a
     *         segment of the file is being returned.
     *         Null is returned if errors occur (file not found or io exception)
     */
    public static String[] getFromSeries(String aFileName, int lineNumber, int n)
    {
        File f = new File(aFileName);
        long logsize = f.length();
        try {
            return get(seriesReader(aFileName),lineNumber,n,logsize);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String buildDisplayingHeader(int len, long logsize)
    {
        double percent = 0.0;
        if (logsize != 0) {
            percent = ((double) len/logsize) * 100;
        }
        return "Displaying: " + ArchiveUtils.doubleToString(percent,1) +
            "% of " + ArchiveUtils.formatBytesForDisplay(logsize);
    }

    /**
     * Gets a portion of a log file. Starting at a given line number and the n-1
     * lines following that one or until the end of the log if that is reached
     * first.
     *
     * @param reader source to scan for lines
     * @param lineNumber The number of the first line to get (if larger then the
     *                   file an empty string will be returned)
     * @param n How many lines to return (total, including the one indicated by
     *                   lineNumber). If smaller then 1 then an empty string
     *                   will be returned.
     *
     * @param logsize total size of source
     * @return An array of two strings is returned. At index 0 a portion of the
     *         file starting at lineNumber and reaching lineNumber+n is located.
     *         At index 1 there is an informational string about how large a
     *         segment of the file is being returned.
     *         Null is returned if errors occur (file not found or io exception)
     */
    public static String[] get(InputStreamReader reader, 
                               int lineNumber, 
                               int n, 
                               long logsize)
    {
        StringBuffer ret = new StringBuffer();
        String info = null;
        try{
            BufferedReader bf = new BufferedReader(reader, 8192);

            String line = null;
            int i=1;
            while ((line = bf.readLine()) != null) {
                if(i >= lineNumber && i < (lineNumber+n))
                {
                    ret.append(line);
                    ret.append('\n');
                } else if( i >= (lineNumber+n)){
                    break;
                }
                i++;
            }
            info = buildDisplayingHeader(ret.length(), logsize);
        }catch(IOException e){
            e.printStackTrace();
            return null;
        }
        String[] tmp = {ret.toString(),info};
        return tmp;
    }

    /**
     * Return the line number of the first line in the
     * log/file that matches a given regular expression.
     *
     * @param aFileName The filename of the log/file
     * @param regex The regular expression that is to be used
     * @return The line number (counting from 1, not zero) of the first line
     *         that matches the given regular expression. -1 is returned if no
     *         line matches the regular expression. -1 also is returned if 
     *         errors occur (file not found, io exception etc.)
     */
    public static int findFirstLineContaining(String aFileName, String regex)
    {
        try {
            return findFirstLineContaining(new FileReader(aFileName), regex);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Return the line number of the first line in the
     * log/file that begins with the given string.
     *
     * @param aFileName The filename of the log/file
     * @param prefix The prefix string to match
     * @return The line number (counting from 1, not zero) of the first line
     *         that matches the given regular expression. -1 is returned if no
     *         line matches the regular expression. -1 also is returned if 
     *         errors occur (file not found, io exception etc.)
     */
    public static int findFirstLineBeginningFromSeries(String aFileName, 
                                                        String prefix)
    {
        try {
            return findFirstLineBeginning(seriesReader(aFileName), prefix);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }
    
    /**
     * Return the line number of the first line in the
     * log/file that that begins with the given string.
     *
     * @param reader The reader of the log/file
     * @param prefix The prefix string to match
     * @return The line number (counting from 1, not zero) of the first line
     *         that matches the given regular expression. -1 is returned if no
     *         line matches the regular expression. -1 also is returned if 
     *         errors occur (file not found, io exception etc.)
     */
    public static int findFirstLineBeginning(InputStreamReader reader, 
                                              String prefix)
    {

        try{
            BufferedReader bf = new BufferedReader(reader, 8192);

            String line = null;
            int i = 1;
            while ((line = bf.readLine()) != null) {
                if(line.startsWith(prefix)){
                    // Found a match
                    return i;
                }
                i++;
            }
        } catch(IOException e){
            e.printStackTrace();
        }
        return -1;
    }
    
    /**
     * Return the line number of the first line in the
     * log/file that matches a given regular expression.
     *
     * @param aFileName The filename of the log/file
     * @param regex The regular expression that is to be used
     * @return The line number (counting from 1, not zero) of the first line
     *         that matches the given regular expression. -1 is returned if no
     *         line matches the regular expression. -1 also is returned if 
     *         errors occur (file not found, io exception etc.)
     */
    public static int findFirstLineContainingFromSeries(String aFileName, 
                                                        String regex)
    {
        try {
            return findFirstLineContaining(seriesReader(aFileName), regex);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Return the line number of the first line in the
     * log/file that matches a given regular expression.
     *
     * @param reader The reader of the log/file
     * @param regex The regular expression that is to be used
     * @return The line number (counting from 1, not zero) of the first line
     *         that matches the given regular expression. -1 is returned if no
     *         line matches the regular expression. -1 also is returned if 
     *         errors occur (file not found, io exception etc.)
     */
    public static int findFirstLineContaining(InputStreamReader reader, 
                                              String regex)
    {
        Pattern p = Pattern.compile(regex);

        try{
            BufferedReader bf = new BufferedReader(reader, 8192);

            String line = null;
            int i = 1;
            while ((line = bf.readLine()) != null) {
                if(p.matcher(line).matches()){
                    // Found a match
                    return i;
                }
                i++;
            }
        } catch(IOException e){
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Returns all lines in a log/file matching a given regular expression.  
     * Possible to get lines immediately following the matched line.  Also 
     * possible to have each line prepended by it's line number.
     *
     * @param aFileName The filename of the log/file
     * @param regex The regular expression that is to be used
     * @param addLines How many lines (in addition to the matched line) to add. 
     *                 A value less then 1 will mean that only the matched line 
     *                 will be included. If another matched line is hit before 
     *                 we reach this limit it will be included and this counter
     *                 effectively reset for it.
     * @param prependLineNumbers If true, then each line will be prepended by 
     *                           it's line number in the file.
     * @param skipFirstMatches The first number of matches up to this value will
     *                         be skipped over.
     * @param numberOfMatches Once past matches that are to be skipped this many
     *                        matches will be added to the return value. A
     *                        value of 0 will cause all matching lines to be
     *                        included.
     * @return An array of two strings is returned. At index 0 tall lines in a
     *         log/file matching a given regular expression is located.
     *         At index 1 there is an informational string about how large a
     *         segment of the file is being returned.
     *         Null is returned if errors occur (file not found or io exception)
     *         If a PatternSyntaxException occurs, it's error message will be
     *         returned and the informational string will be empty (not null).
     */
    public static String[] getByRegex(String aFileName,
                                        String regex,
                                        int addLines,
                                        boolean prependLineNumbers,
                                        int skipFirstMatches,
                                        int numberOfMatches) {
        try {
            File f = new File(aFileName);
            return getByRegex(
                    new FileReader(f), 
                    regex, 
                    addLines, 
                    prependLineNumbers,
                    skipFirstMatches,
                    numberOfMatches,
                    f.length());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Returns all lines in a log/file matching a given regular expression.  
     * Possible to get lines immediately following the matched line.  Also 
     * possible to have each line prepended by it's line number.
     *
     * @param aFileName The filename of the log/file
     * @param regex The regular expression that is to be used
     * @param addLines How many lines (in addition to the matched line) to add. 
     *                 A value less then 1 will mean that only the matched line 
     *                 will be included. If another matched line is hit before 
     *                 we reach this limit it will be included and this counter
     *                 effectively reset for it.
     * @param prependLineNumbers If true, then each line will be prepended by 
     *                           it's line number in the file.
     * @param skipFirstMatches The first number of matches up to this value will
     *                         be skipped over.
     * @param numberOfMatches Once past matches that are to be skipped this many
     *                        matches will be added to the return value. A
     *                        value of 0 will cause all matching lines to be
     *                        included.
     * @return An array of two strings is returned. At index 0 tall lines in a
     *         log/file matching a given regular expression is located.
     *         At index 1 there is an informational string about how large a
     *         segment of the file is being returned.
     *         Null is returned if errors occur (file not found or io exception)
     *         If a PatternSyntaxException occurs, it's error message will be
     *         returned and the informational string will be empty (not null).
     */
    public static String[] getByRegexFromSeries(String aFileName,
                                      String regex,
                                      int addLines,
                                      boolean prependLineNumbers,
                                      int skipFirstMatches,
                                      int numberOfMatches) {
        try {
            File f = new File(aFileName);
            return getByRegex(
                    seriesReader(aFileName), 
                    regex, 
                    addLines, 
                    prependLineNumbers,
                    skipFirstMatches,
                    numberOfMatches,
                    f.length());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Returns all lines in a log/file matching a given regular expression.  
     * Possible to get lines immediately following the matched line.  Also 
     * possible to have each line prepended by it's line number.
     *
     * @param reader The reader of the log/file
     * @param regex The regular expression that is to be used
     * @param addLines How many lines (in addition to the matched line) to add. 
     *                 A value less then 1 will mean that only the matched line 
     *                 will be included. If another matched line is hit before 
     *                 we reach this limit it will be included and this counter
     *                 effectively reset for it.
     * @param prependLineNumbers If true, then each line will be prepended by 
     *                           it's line number in the file.
     * @param skipFirstMatches The first number of matches up to this value will
     *                         be skipped over.
     * @param numberOfMatches Once past matches that are to be skipped this many
     *                        matches will be added to the return value. A
     *                        value of 0 will cause all matching lines to be
     *                        included.
     * @param logsize Size of the log in bytes
     * @return An array of two strings is returned. At index 0 all lines in a
     *         log/file matching a given regular expression is located.
     *         At index 1 there is an informational string about how large a
     *         segment of the file is being returned.
     *         Null is returned if errors occur (file not found or io exception)
     *         If a PatternSyntaxException occurs, it's error message will be
     *         returned and the informational string will be empty (not null).
     */
    public static String[] getByRegex(InputStreamReader reader,
                                      String regex,
                                      int addLines,
                                      boolean prependLineNumbers,
                                      int skipFirstMatches,
                                      int numberOfMatches,
                                      long logsize) {
        StringBuffer ret = new StringBuffer();
        String info = "";

        try{
            Pattern p = Pattern.compile(regex);
            BufferedReader bf = new BufferedReader(reader, 8192);

            String line = null;
            int i = 1;
            boolean doAdd = false;
            int addCount = 0;
            long linesMatched = 0;
            while ((line = bf.readLine()) != null) {
                if(p.matcher(line).matches()){
                    // Found a match
                    if(numberOfMatches > 0 &&
                            linesMatched >= skipFirstMatches + numberOfMatches){
                        // Ok, we are done.
                        break;
                    }
                    linesMatched++;
                    if(linesMatched > skipFirstMatches){
                        if(prependLineNumbers){
                            ret.append(i);
                            ret.append(". ");
                        }
                        ret.append(line);
                        ret.append("\n");
                        doAdd = true;
                        addCount = 0;
                    }
                } else if(doAdd) {
                    if(addCount < addLines){
                        //Ok, still within addLines
                        linesMatched++;
                        if(prependLineNumbers){
                            ret.append(i);
                            ret.append(". ");
                        }
                        ret.append(line);
                        ret.append("\n");
                    }else{
                        doAdd = false;
                        addCount = 0;
                    }
                }
                i++;
            }
            info = buildDisplayingHeader(ret.length(), logsize);
        }catch(FileNotFoundException e){
            return null;
        }catch(IOException e){
            e.printStackTrace();
            return null;
        }catch(PatternSyntaxException e){
            ret = new StringBuffer(e.getMessage());
        }
        String[] tmp = {ret.toString(),info};
        return tmp;
    }

    /**
     * Returns all lines in a log/file matching a given regular expression.  
     * Possible to get lines immediately following the matched line.  Also 
     * possible to have each line prepended by it's line number.
     *
     * @param aFileName The filename of the log/file
     * @param regex The regular expression that is to be used
     * @param addLines Any lines following a match that <b>begin</b> with this 
     *                 string will also be included. We will stop including new 
     *                 lines once we hit the first that does not match.
     * @param prependLineNumbers If true, then each line will be prepended by 
     *                           it's line number in the file.
     * @param skipFirstMatches The first number of matches up to this value will
     *                         be skipped over.
     * @param numberOfMatches Once past matches that are to be skipped this many
     *                        matches will be added to the return value. A
     *                        value of 0 will cause all matching lines to be
     *                        included.
     * @return An array of two strings is returned. At index 0 tall lines in a
     *         log/file matching a given regular expression is located.
     *         At index 1 there is an informational string about how large a
     *         segment of the file is being returned.
     *         Null is returned if errors occur (file not found or io exception)
     *         If a PatternSyntaxException occurs, it's error message will be
     *         returned and the informational string will be empty (not null).
     */
    public static String[] getByRegex(String aFileName, 
                                        String regex, 
                                        String addLines, 
                                        boolean prependLineNumbers,
                                        int skipFirstMatches,
                                        int numberOfMatches){
        try {
            File f = new File(aFileName);
            return getByRegex(
                    new FileReader(f),
                    regex,
                    addLines,
                    prependLineNumbers,
                    skipFirstMatches,
                    numberOfMatches,
                    f.length());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Returns all lines in a log/file matching a given regular expression.  
     * Possible to get lines immediately following the matched line.  Also 
     * possible to have each line prepended by it's  line number.
     *
     * @param aFileName The filename of the log/file
     * @param regex The regular expression that is to be used
     * @param addLines Any lines following a match that <b>begin</b> with this 
     *                 string will also be included. We will stop including new 
     *                 lines once we hit the first that does not match.
     * @param prependLineNumbers If true, then each line will be prepended by 
     *                           it's line number in the file.
     * @param skipFirstMatches The first number of matches up to this value will
     *                         be skipped over.
     * @param numberOfMatches Once past matches that are to be skipped this many
     *                        matches will be added to the return value. A
     *                        value of 0 will cause all matching lines to be
     *                        included.
     * @return An array of two strings is returned. At index 0 tall lines in a
     *         log/file matching a given regular expression is located.
     *         At index 1 there is an informational string about how large a
     *         segment of the file is being returned.
     *         Null is returned if errors occur (file not found or io exception)
     *         If a PatternSyntaxException occurs, it's error message will be
     *         returned and the informational string will be empty (not null).
     */
    public static String[] getByRegexFromSeries(String aFileName, 
                                                  String regex, 
                                                  String addLines, 
                                                  boolean prependLineNumbers,
                                                  int skipFirstMatches,
                                                  int numberOfMatches){
        try {
            File f = new File(aFileName);
            return getByRegex(
                    seriesReader(aFileName),
                    regex,
                    addLines,
                    prependLineNumbers,
                    skipFirstMatches,
                    numberOfMatches,
                    f.length());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Returns all lines in a log/file matching a given regular expression.  
     * Possible to get lines immediately following the matched line.  Also 
     * possible to have each line prepended by it's line number.
     *
     * @param reader The reader of the log/file
     * @param regex The regular expression that is to be used
     * @param addLines Any lines following a match that <b>begin</b> with this 
     *                 string will also be included. We will stop including new 
     *                 lines once we hit the first that does not match.
     * @param prependLineNumbers If true, then each line will be prepended by 
     *                           it's line number in the file.
     * @param skipFirstMatches The first number of matches up to this value will
     *                         be skipped over.
     * @param numberOfMatches Once past matches that are to be skipped this many
     *                        matches will be added to the return value. A
     *                        value of 0 will cause all matching lines to be
     *                        included.
     * @param logsize Size of the log in bytes
     * @return An array of two strings is returned. At index 0 tall lines in a
     *         log/file matching a given regular expression is located.
     *         At index 1 there is an informational string about how large a
     *         segment of the file is being returned.
     *         Null is returned if errors occur (file not found or io exception)
     *         If a PatternSyntaxException occurs, it's error message will be
     *         returned and the informational string will be empty (not null).
     */
    public static String[] getByRegex(InputStreamReader reader, 
                                        String regex, 
                                        String addLines, 
                                        boolean prependLineNumbers,
                                        int skipFirstMatches,
                                        int numberOfMatches,
                                        long logsize) {
        StringBuffer ret = new StringBuffer();
        String info = "";
        try{
            Matcher m = Pattern.compile(regex).matcher("");
            BufferedReader bf = new BufferedReader(reader, 8192);

            String line = null;
            int i = 1;
            boolean doAdd = false;
            long linesMatched = 0;
            while ((line = bf.readLine()) != null) {
                m.reset(line);
                if(m.matches()){
                    // Found a match
                    if(numberOfMatches > 0 && 
                            linesMatched >= skipFirstMatches + numberOfMatches){
                        // Ok, we are done.
                        break;
                    }
                    linesMatched++;
                    if(linesMatched > skipFirstMatches){
                        if(prependLineNumbers){
                            ret.append(i);
                            ret.append(". ");
                        }
                        ret.append(line);
                        ret.append("\n");
                        doAdd = true;
                    }
                } else if(doAdd) {
                    if(line.indexOf(addLines)==0){
                        linesMatched++;
                        //Ok, line begins with 'addLines'
                        if(prependLineNumbers){
                            ret.append(i);
                            ret.append(". ");
                        }
                        ret.append(line);
                        ret.append("\n");
                    }else{
                        doAdd = false;
                    }
                }
                i++;
            }
            info = buildDisplayingHeader(ret.length(), logsize);
        }catch(FileNotFoundException e){
            return null;
        }catch(IOException e){
            e.printStackTrace();
            return null;
        }catch(PatternSyntaxException e){
            ret = new StringBuffer(e.getMessage());
        }
        String[] tmp = {ret.toString(),info};
        return tmp;
    }

    /**
     * Implementation of a unix-like 'tail' command
     *
     * @param aFileName a file name String
     * @return An array of two strings is returned. At index 0 the String
     *         representation of at most 10 last lines is located.
     *         At index 1 there is an informational string about how large a
     *         segment of the file is being returned.
     *         Null is returned if errors occur (file not found or io exception)
     */
    public static String[] tail(String aFileName) {
        return tail(aFileName, 10);
    }

    /**
     * Implementation of a unix-like 'tail -n' command
     *
     * @param aFileName a file name String
     * @param n int number of lines to be returned
     * @return An array of two strings is returned. At index 0 the String
     *         representation of at most n last lines is located.
     *         At index 1 there is an informational string about how large a
     *         segment of the file is being returned.
     *         Null is returned if errors occur (file not found or io exception)
     */
    public static String[] tail(String aFileName, int n) {
        try {
            return tail(new RandomAccessFile(new File(aFileName),"r"),n);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Implementation of a unix-like 'tail -n' command
     *
     * @param raf a RandomAccessFile to tail
     * @param n int number of lines to be returned
     * @return An array of two strings is returned. At index 0 the String
     *         representation of at most n last lines is located.
     *         At index 1 there is an informational string about how large a
     *         segment of the file is being returned.
     *         Null is returned if errors occur (file not found or io exception)
     */
    public static String[] tail(RandomAccessFile raf, int n) {
        int BUFFERSIZE = 1024;
        long pos;
        long endPos;
        long lastPos;
        int numOfLines = 0;
        String info=null;
        byte[] buffer = new byte[BUFFERSIZE];
        StringBuffer sb = new StringBuffer();
        try {
            endPos = raf.length();
            lastPos = endPos;

            // Check for non-empty file
            // Check for newline at EOF
            if (endPos > 0) {
                byte[] oneByte = new byte[1];
                raf.seek(endPos - 1);
                raf.read(oneByte);
                if ((char) oneByte[0] != '\n') {
                    numOfLines++;
                }
            }

            do {
                // seek back BUFFERSIZE bytes
                // if length of the file if less then BUFFERSIZE start from BOF
                pos = 0;
                if ((lastPos - BUFFERSIZE) > 0) {
                    pos = lastPos - BUFFERSIZE;
                }
                raf.seek(pos);
                // If less then BUFFERSIZE avaliable read the remaining bytes
                if ((lastPos - pos) < BUFFERSIZE) {
                    int remainer = (int) (lastPos - pos);
                    buffer = new byte[remainer];
                }
                raf.readFully(buffer);
                // in the buffer seek back for newlines
                for (int i = buffer.length - 1; i >= 0; i--) {
                    if ((char) buffer[i] == '\n') {
                        numOfLines++;
                        // break if we have last n lines
                        if (numOfLines > n) {
                            pos += (i + 1);
                            break;
                        }
                    }
                }
                // reset last postion
                lastPos = pos;
            } while ((numOfLines <= n) && (pos != 0));

            // print last n line starting from last postion
            for (pos = lastPos; pos < endPos; pos += buffer.length) {
                raf.seek(pos);
                if ((endPos - pos) < BUFFERSIZE) {
                    int remainer = (int) (endPos - pos);
                    buffer = new byte[remainer];
                }
                raf.readFully(buffer);
                sb.append(new String(buffer));
            }

            info = buildDisplayingHeader(sb.length(), raf.length());
        } catch (FileNotFoundException e) {
            sb = null;
        } catch (IOException e) {
            e.printStackTrace();
            sb = null;
        } finally {
            try {
                if (raf != null) {
                    raf.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(sb==null){
            return null;
        }
        String[] tmp = {sb.toString(),info};
        return tmp;
    }

    /**
     * @param fileName
     * @return
     * @throws IOException
     */
    private static CompositeFileReader seriesReader(String fileName)
    throws IOException {
        LinkedList<File> filenames = new LinkedList<File>();
        int seriesNumber = 1;
        NumberFormat fmt = new DecimalFormat("00000");
        String predecessorFilename =
            fileName
            + fmt.format(seriesNumber);
        while((new File(predecessorFilename)).exists()) {
            filenames.add(new File(predecessorFilename));
            seriesNumber++;
            predecessorFilename =
                fileName
                + fmt.format(seriesNumber);
        }
        filenames.add(new File(fileName)); // add current file
        return new CompositeFileReader(filenames);
    }
}
