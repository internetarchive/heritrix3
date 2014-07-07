/*RELICENSE_RESEARCH*/
/* MirrorWriter
 *
 * $Id$
 *
 * Created on 2004 October 26
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
package org.archive.modules.writer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.management.AttributeNotFoundException;

import org.apache.commons.io.IOUtils;
import org.archive.io.RecordingInputStream;
import org.archive.io.ReplayInputStream;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.net.UURI;
import org.archive.spring.ConfigPath;
import org.archive.util.FileUtils;

/**
   Processor module that writes the results of successful fetches to
   files on disk.
   
   Writes contents of one URI to one file on disk.  The files are
   arranged in a directory hierarchy based on the URI paths.  In that sense
   they mirror the file hierarchy that might exist on the servers.
   <p>
   There are a number of issues involved:
   <ul>
   <li>
   URIs can have arbitrary length, but file systems have length constraints.
   </li>
   <li>
   URIs can contain characters that file systems prohibit.
   </li>
   <li>
   URI paths are case-sensitive, but some file systems are case-insensitive.
   </li>
   </ul>
   This class tries very hard to map each URI into a file system path that
   obeys all file system constraints and yet reasonably represents
   the original URI.
   <p>
   There would normally be a single instance of this class per Heritrix
   instance. This class is thread-safe; any number of threads can be in its
   innerProcess method at once. However, conflicts can still arise in the file
   system. For example, if several threads try to create the same directory at
   the same time, only one can win. Therefore, there should be at most one
   access to a server at a given time.
   
   @author Howard Lee Gayle
*/
public class MirrorWriterProcessor extends Processor {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 3L;
    private static final Logger logger =
        Logger.getLogger(MirrorWriterProcessor.class.getName());

    final public static String A_MIRROR_PATH = "mirror-path";

    
    /**
     * Regular expression matching a file system path segment. The intent is one
     * or more non-file-separator characters. The backslash is to quote
     * File.separator if it's also backslash.
     */
    private static final Pattern PATH_SEGMENT_RE = 
        Pattern.compile("[^\\" + File.separator + "]+");

    
    /**
     * Regular expression constraint on ATTR_DIRECTORY_FILE. The intent is one
     * non-file-separator character, followed by zero or more characters. The
     * backslash is to quote File.separator if it's also backslash.
     */
    private static final Pattern TOO_LONG_DIRECTORY_RE = 
        Pattern.compile("[^\\" + File.separator + "].*");


    /**
     * True if the file system is case-sensitive, like UNIX. False if the file
     * system is case-insensitive, like Macintosh HFS+ and Windows.
     */
    protected boolean caseSensitiveFilesystem = true; 
    public boolean getCaseSensitiveFilesystem() {
        return this.caseSensitiveFilesystem;
    }
    public void setCaseSensitiveFilesystem(boolean sensitive) {
        this.caseSensitiveFilesystem = sensitive;
    }

    /**
     * This list is grouped in pairs. The first string in each pair must have a
     * length of one. If it occurs in a URI path, it is replaced by the second
     * string in the pair. For UNIX, no character mapping is normally needed.
     * For Macintosh, the recommended value is [: %%3A]. For Windows, the
     * recommended value is [' ' %%20 &quot; %%22 * %%2A : %%3A < %%3C \\> %%3E ?
     * %%3F \\\\ %%5C ^ %%5E | %%7C].
     */
    protected List<String> characterMap = new ArrayList<String>(); 
    public List<String> getCharacterMap() {
        return this.characterMap;
    }
    public void setCharacterMap(List<String> list) {
        this.characterMap = list; 
    }
// FIXME: This probably wants to be a Map, and should have a sane default
    // based on auto-detected platform using system properties


    /**
     * This list is grouped in pairs. If the content type of a resource begins
     * (case-insensitive) with the first string in a pair, the suffix is set to
     * the second string in the pair, replacing any suffix that may have been in
     * the URI. For example, to force all HTML files to have the same suffix,
     * use [text/html html].
     */
    protected List<String> contentTypeMap = new ArrayList<String>(); 
    public List<String> getContentTypeMap() {
        return this.contentTypeMap;
    }
    public void setContentTypeMap(List<String> list) {
        this.contentTypeMap = list; 
    }

    /**
     * If a segment starts with '.', the '.' is replaced by this.
     */
    protected String dotBegin = "%2E";
    public String getDotBegin() {
        return this.dotBegin;
    }
    public void setDotBegin(String s) {
        validate(PATH_SEGMENT_RE,s);
        this.dotBegin = s; 
    }

    protected void validate(Pattern pat, String s) {
        if(!pat.matcher(s).matches()) {
            throw new IllegalArgumentException("invalid value: "+s+" does not match "+pat.pattern());
        }
    }

    /**
     * If a directory name ends with '.' it is replaced by this. For all file
     * systems except Windows, '.' is recommended. For Windows, %%2E is
     * recommended.
     */
    protected String dotEnd = ".";
    public String getDotEnd() {
        return this.dotEnd;
    }
    public void setDotEnd(String s) {
        validate(PATH_SEGMENT_RE,s);
        this.dotEnd = s; 
    }
    
    /**
     * Implicitly append this to a URI ending with '/'.
     */
    protected String directoryFile = "index.html";
    public String getDirectoryFile() {
        return this.directoryFile;
    }
    public void setDirectoryFile(String s) {
        validate(PATH_SEGMENT_RE,s);
        this.directoryFile = s; 
    }

    /**
     * Create a subdirectory named for the host in the URI.
     */
    protected boolean createHostDirectory = true; 
    public boolean getCreateHostDirectory() {
        return this.createHostDirectory;
    }
    public void setCreateHostDirectory(boolean hostDir) {
        this.createHostDirectory = hostDir; 
    }

    /**
     * This list is grouped in pairs. If a host name matches (case-insensitive)
     * the first string in a pair, it is replaced by the second string in the
     * pair. This can be used for consistency when several names are used for
     * one host, for example [12.34.56.78 www42.foo.com].
     */
    protected List<String> hostMap = new ArrayList<String>(); 
    public List<String> getHostMap() {
        return this.hostMap;
    }
    public void setHostMap(List<String> list) {
        this.hostMap = list; 
    }


    /**
     * Maximum file system path length.
     */
    protected int maxPathLength = 1023; 
    public int getMaxPathLength() {
        return maxPathLength;
    }
    public void setMaxPathLength(int max) {
        this.maxPathLength = max;
    }

    /**
     * Maximum file system path segment length.
     */
    protected int maxSegLength = 255; 
    public int getMaxSegLength() {
        return maxSegLength;
    }
    public void setMaxSegLength(int max) {
        this.maxSegLength = max;
    }
    
    // TODO: Add a new Constraint subclass so ATTR_MAX_PATH_LEN and
    // ATTR_MAX_SEG_LEN can be constained to reasonable values.


    /**
     * Top-level directory for mirror files.
     */
    protected ConfigPath path = new ConfigPath("mirror writer top level directory", "${launchId}/mirror");
    public ConfigPath getPath() {
        return this.path;
    }
    public void setPath(ConfigPath s) {
        this.path = s; 
    }
    
    /**
     * Create a subdirectory named for the port in the URI.
     */
    protected boolean createPortDirectory = false; 
    public boolean getCreatePortDirectory() {
        return this.createPortDirectory;
    }
    public void setCreatePortDirectory(boolean portDir) {
        this.createPortDirectory = portDir; 
    }

    /**
     * If true, the suffix is placed at the end of the path, after the query (if
     * any). If false, the suffix is placed before the query.
     */
    protected boolean suffixAtEnd = true; 
    public boolean getSuffixAtEnd() {
        return this.suffixAtEnd;
    }
    public void setSuffixAtEnd(boolean suffixAtEnd) {
        this.suffixAtEnd = suffixAtEnd; 
    }

    /**
     * If all the directories in the URI would exceed, or come close to
     * exceeding, the file system maximum path length, then they are all
     * replaced by this.
     */
    protected String tooLongDirectory = "LONG";
    public String getTooLongDirectory() {
        return this.tooLongDirectory;
    }
    public void setTooLongDirectory(String s) {
        validate(TOO_LONG_DIRECTORY_RE,s);
        this.tooLongDirectory = s; 
    }

    /**
     * If a directory name appears (case-insensitive) in this list then an
     * underscore is placed before it. For all file systems except Windows, this
     * is not needed. For Windows, the following is recommended: [com1 com2 com3
     * com4 com5 com6 com7 com8 com9 lpt1 lpt2 lpt3 lpt4 lpt5 lpt6 lpt7 lpt8
     * lpt9 con nul prn].
     */
    protected List<String> underscoreSet = new ArrayList<String>(); 
    public List<String> getUnderscoreSet() {
        return this.underscoreSet;
    }
    public void setUnderscoreSet(List<String> list) {
        this.underscoreSet = list; 
    }

    /** An empty Map.*/
    private static final Map<String,String> EMPTY_MAP
     = Collections.unmodifiableMap(new TreeMap<String,String>());





    /**
     * @param name Name of this processor.
     */
    public MirrorWriterProcessor() {
    }

    
    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        return isSuccess(curi);
    }
    
    
    @Override
    protected void innerProcess(CrawlURI curi) {
        UURI uuri = curi.getUURI(); // Current URI.

        // Only http and https schemes are supported.
        String scheme = uuri.getScheme();
        if (!"http".equalsIgnoreCase(scheme)
                && !"https".equalsIgnoreCase(scheme)) {
            return;
        }
        RecordingInputStream recis = curi.getRecorder().getRecordedInput();
        if (0L == recis.getResponseContentLength()) {
            return;
        }

        String baseDir = getPath().getFile().getAbsolutePath();

        // Already have a path for this URI.
        boolean reCrawl = curi.getData().containsKey(A_MIRROR_PATH);

        /*
          The file system path, relative to the value of ATTR_PATH, where
          this resource should be written.  The intent is to
          add later a persistent mapping from URI to path.
          This will allow a URI to be re-crawled and updated
          if it has changed.  If the resource has already been fetched
          and written to a file before, the path to that file
          has already been obtained from the persistent mapping
          and placed on the AList by some other module,
          such as the frontier.
        */
        String mps = null;
        File destFile = null; // Write resource contents to this file.
        try {
            if (reCrawl) {
                mps = (String)curi.getData().get(A_MIRROR_PATH);
                destFile = new File(baseDir + File.separator + mps);
                File parent = destFile.getParentFile();
                if (null != parent) {
                    FileUtils.ensureWriteableDirectory(parent);
                }
            } else {
                URIToFileReturn r = null; // Return from uriToFile().
                try {
                     r = uriToFile(baseDir, curi);
                } catch (AttributeNotFoundException e) {
                    logger.warning(e.getLocalizedMessage());
                    return;
                }
                destFile = r.getFile();
                mps = r.getRelativePath();
            }
            logger.info(uuri.toString() + " -> " + destFile.getPath());
            writeToPath(recis, destFile);
            if (!reCrawl) {
                curi.getData().put(A_MIRROR_PATH, mps);
            }
        } catch (IOException e) {
            curi.getNonFatalFailures().add(e);
        }
    }

    /**
       Gets the directory in which the file will reside.
       Any directories needed are created.
       @param baseDir the path to the starting directory
       @param host the host part of the URI, or null if the host name
       should not be part of the returned path
       @param port the port part of the URI, or -1 if the port
       should not be part of the returned path
       @param segs all the segments in the URI
       @param maxLen the maximum path length allowed to the directory;
       this must leave some room for the file itself
       @return the directory, or null if maxLen would be exceeded
       @throws IOException
       if a needed directory could not be created
       @throws IOException
       if a needed directory is not writeable
       @throws IOException
       if a non-directory file exists with the same path as a needed directory
    */
    private URIToFileReturn dirPath(String baseDir, String host, int port,
                                    PathSegment[] segs, int maxLen)
        throws IOException {

        // Return value.
        URIToFileReturn r = new URIToFileReturn(baseDir, host, port);
        r.mkdirs();
        for (int i = 0; (segs.length - 1) != i; ++i) {
            segs[i].addToPath(r);
            if (r.longerThan(maxLen)) {
                return null;
            }
        }
        return r;
    }

    /**
       Ensures that a list contains an even number of elements.
       If not, the last element is removed.
       @param list the list
    */
    private void ensurePairs(List<?> list) {
        if (1 == (list.size() % 2)) {
            list.remove(list.size() - 1);
        }
    }

    /**
       Makes a path in which a resource can be stored.
       @param baseDir the path to the starting directory
       @param curi the URI
       @return a path to the file in which to store the resource
       @throws AttributeNotFoundException
       if a needed setting is missing
       @throws IOException
       if a needed directory could not be created
       @throws IOException
       if a needed directory is not writeable
       @throws IOException
       if a non-directory file exists with the same path as a needed directory
    */
    private URIToFileReturn uriToFile(String baseDir, CrawlURI curi)
        throws AttributeNotFoundException, IOException {
        UURI uuri = curi.getUURI(); // Current URI.
        String host = null;
        boolean hd = getCreateHostDirectory();
        if (hd) {
            host = uuri.getHost();
            List<String> hostMap = getHostMap();
            if ((null != hostMap) && (hostMap.size() > 1)) {
                ensurePairs(hostMap);
                Iterator<String> i = hostMap.iterator();
                for (boolean more = true; more && i.hasNext();) {
                    String h1 = i.next();
                    String h2 = i.next();
                    if (host.equalsIgnoreCase(h1)) {
                        more = false;
                        if ((null != h2) && (0 != h2.length())) {
                            host = h2;
                        }
                    }
                }
            }
        }

        int port = getCreatePortDirectory() ? uuri.getPort() : -1;

        String suffix = null; // Replacement suffix.
        List<String> ctm = getContentTypeMap();
        if ((null != ctm) && (ctm.size() > 1)) {
            ensurePairs(ctm);
            String contentType = curi.getContentType().toLowerCase();
            Iterator<String> i = ctm.iterator();
            for (boolean more = true; more && i.hasNext();) {
                String ct = (String) i.next();
                String suf = (String) i.next();
                if ((null != ct) && contentType.startsWith(ct.toLowerCase())) {
                    more = false;
                    if ((null != suf) && (0 != suf.length())) {
                        suffix = suf;
                    }
                }
            }
        }

        int maxSegLen = getMaxSegLength();
        if (maxSegLen < 2) {
            maxSegLen = 2; // MAX_SEG_LEN.getDefaultValue();
        }

        int maxPathLen = getMaxPathLength();
        if (maxPathLen < 2) {
            maxPathLen = 2; // MAX_PATH_LENGTH.getDefaultValue();
        }

        Map<String,String> characterMap = Collections.emptyMap();
        List<String> cm = getCharacterMap();
        if ((null != cm) && (cm.size() > 1)) {
            ensurePairs(cm);
            characterMap = new HashMap<String,String>(cm.size()); 
            // Above will be half full.
            for (Iterator<String> i = cm.iterator(); i.hasNext();) {
                String s1 = (String) i.next();
                String s2 = (String) i.next();
                if ((null != s1) && (1 == s1.length()) && (null != s2)
                        && (0 != s2.length())) {
                    characterMap.put(s1, s2);
                }
            }
        }

        String dotBegin = getDotBegin();
        if (".".equals(dotBegin)) {
            dotBegin = null;
        }

        String dotEnd = getDotEnd();
        if (".".equals(dotEnd)) {
            dotEnd = null;
        }

        String tld = getTooLongDirectory();
        if ((null == tld) || (0 == tld.length())
                || (-1 != tld.indexOf(File.separatorChar))) {
            tld = "LONG"; // TOO_LONG_DIRECTORY.getDefaultValue();
        }

        Set<String> underscoreSet = null;
        List<String> us = getUnderscoreSet();
        if ((null != us) && (0 != us.size())) {
            underscoreSet = new HashSet<String>(us.size(), 0.5F);
            for (String s: us) {
                if ((null != s) && (0 != s.length())) {
                    underscoreSet.add(s.toLowerCase());
                }
            }
        }

        return uriToFile(curi, host, port, uuri.getPath(), uuri.getQuery(),
            suffix, baseDir, maxSegLen, maxPathLen,
            getCaseSensitiveFilesystem(),
            getDirectoryFile(),
            characterMap, dotBegin, dotEnd, tld,
            getSuffixAtEnd(),
            underscoreSet);
    }

    /**
       Makes a path in which a resource can be stored.
       @param curi the URI
       @param host the host part of the URI, or null if the host name
       should not be part of the returned path
       @param port the port part of the URI, or -1 if the port
       should not be part of the returned path
       @param uriPath the path part of the URI (must be absolute)
       @param query the query part of the URI, or null if none
       @param suffix if non-null, use this as the suffix in preference to
       any suffix that uriPath might have
       @param baseDir the path to the starting directory
       @param maxSegLen the maximum number of characters allowed in one
       file system path segment (component)
       @param maxPathLen the maximum number of characters allowed in a
       file system path
       @param caseSensitive if true, the file system is assumed to be
       case-sensitive; otherwise the file system is assumed to be
       case-insensitive but case-preserving
       @param dirFile the simple file name to append to a URI path
       ending in '/'
       @param characterMap a map from characters (as length-1 String values) in
       the URI path and query to replacement String values
       @param dotBegin if non-null, this replaces a '.' at
       the beginning of a segment
       @param dotEnd if non-null, this replaces a '.' that appears at the end
       of a directory name
       @param tooLongDir if the path length would exceed or be close to
       exceeding maxPathLen then this simple name is used as a directory
       under baseDir instead
       @param suffixAtEnd if true, the suffix is placed at the end of the
       path, after the query (if any); otherwise, the suffix is placed
       before the query
       @param underscoreSet if non-null and a segment, after conversion
       to lower case, is in this set, then prepend an underscore
       to the segment
       @return a path to the file in which to store the resource
       @throws IOException
       if a needed directory could not be created
       @throws IOException
       if a needed directory is not writable
       @throws IOException
       if a non-directory file exists with the same path as a needed directory
    */
    private URIToFileReturn uriToFile(CrawlURI curi, String host, int port,
            String uriPath, String query, String suffix, String baseDir,
            int maxSegLen, int maxPathLen, boolean caseSensitive,
            String dirFile, Map<String, String> characterMap, String dotBegin, String dotEnd,
            String tooLongDir, boolean suffixAtEnd, Set<String> underscoreSet)
            throws IOException {
        assert (null == host) || (0 != host.length());
        assert 0 != uriPath.length();
        assert '/' == uriPath.charAt(0) : "uriPath: " + uriPath;
        assert -1 == uriPath.indexOf("//") : "uriPath: " + uriPath;
        assert -1 == uriPath.indexOf("/./") : "uriPath: " + uriPath;
        assert !uriPath.endsWith("/.") : "uriPath: " + uriPath;
        assert (null == query) || (-1 == query.indexOf('/'))
            : "query: " + query;
        assert (null == suffix)
            || ((0 != suffix.length()) && (-1 == suffix.indexOf('/')))
            : "suffix: " + suffix;
        assert 0 != baseDir.length();
        assert maxSegLen > 2 : "maxSegLen: " + maxSegLen;
        assert maxPathLen > 1;
        assert maxPathLen >= maxSegLen
            : "maxSegLen: " + maxSegLen + " maxPathLen: " + maxPathLen;
        assert 0 != dirFile.length();
        assert -1 == dirFile.indexOf("/") : "dirFile: " + dirFile;
        assert null != characterMap;
        assert (null == dotBegin) || (0 != dotBegin.length());
        assert (null == dotEnd) || !dotEnd.endsWith(".") : "dotEnd: " + dotEnd;
        assert 0 != tooLongDir.length();
        assert '/' != tooLongDir.charAt(0) : "tooLongDir: " + tooLongDir;

        int nSegs = 0; // Number of segments in the URI path.
        for (int i = 0; uriPath.length() != i; ++i) {
            if ('/' == uriPath.charAt(i)) {
                ++nSegs; // Just count slashes.
            }
        }
        assert nSegs > 0 : "uriPath: " + uriPath;
        PathSegment[] segs = new PathSegment[nSegs]; // The segments.
        int slashIndex = 0; // Index in uriPath of current /.
        for (int i = 0; (segs.length - 1) != i; ++i) {
            int nsi = uriPath.indexOf('/', slashIndex + 1); // Next index.
            assert nsi > slashIndex : "uriPath: " + uriPath;
            segs[i] = new DirSegment(uriPath, slashIndex + 1, nsi,
                                     maxSegLen, caseSensitive, curi,
                                     characterMap, dotBegin, dotEnd,
                                     underscoreSet);
            slashIndex = nsi;
        }
        if (slashIndex < (uriPath.length() - 1)) {

            // There's something after the last /.
            segs[segs.length - 1] = new EndSegment(uriPath, slashIndex + 1,
                    uriPath.length(), maxSegLen, caseSensitive, curi,
                    characterMap, dotBegin, query, suffix, maxPathLen,
                    suffixAtEnd);
        } else {

            // The URI ends with a /.
            segs[segs.length - 1] = new EndSegment(dirFile, 0, dirFile.length(),
                    maxSegLen, caseSensitive, curi, characterMap, null,
                    query, suffix, maxPathLen, suffixAtEnd);
        }
        URIToFileReturn r = dirPath(baseDir, host, port, segs,
                                    maxPathLen - maxSegLen);
        if (null == r) {

            // The path is too long.
            // Replace all the segment directories by tooLongDir.
            PathSegment endSegment = segs[segs.length - 1];
            segs = new PathSegment[2];
            segs[0] = new DirSegment(tooLongDir, 0, tooLongDir.length(),
                                     maxSegLen, caseSensitive, curi, EMPTY_MAP,
                                     null, null, null);
            segs[1] = endSegment;
            r = dirPath(baseDir, host, port, segs, maxPathLen - maxSegLen);
        }
        segs[segs.length - 1].addToPath(r);
        return r;
    }

    /**
       Copies a resource into a file.
       A temporary file is created and then atomically renamed to
       the destination file.
       This prevents leaving a partial file in case of a crash.
       @param recis the RecordingInputStream that recorded the contents
       of the resource
       @param dest the destination file
       @throws IOException on I/O error
       @throws IOException if
       the file rename fails
    */
    private void writeToPath(RecordingInputStream recis, File dest)
        throws IOException {
        File tf = new File (dest.getPath() + "N");
        ReplayInputStream replayis = null;
        FileOutputStream fos = null;
        try {
            replayis = recis.getMessageBodyReplayInputStream();
            fos = new FileOutputStream(tf);

            replayis.readFullyTo(fos);
        } finally {
            IOUtils.closeQuietly(replayis);
            IOUtils.closeQuietly(fos);
        }
        if (!tf.renameTo(dest)) {
            throw new IOException("Can not rename " + tf.getAbsolutePath()
                                  + " to " + dest.getAbsolutePath());
        }

    }

    /**
       This class represents one segment (component) of a URI path.
       A segment between '/' characters is a directory segment.
       The segment after the last '/' is the end segment.
    */
    abstract class PathSegment {
        /**
           existsMaybeCaseSensitive return code
           for a file that does not exist.
        */
        protected static final int EXISTS_NOT = 1;

        /**
           existsMaybeCaseSensitive return code
           for a file that exists.
           Furthermore, the comparison is case-sensitive.
        */
        protected static final int EXISTS_EXACT_MATCH = 2;

        /**
           existsMaybeCaseSensitive return code
           for a file that exists, using a case-insensitive comparison.
           Furthermore, the file would not exist if the comparison
           were case-sensitive.
        */
        protected static final int EXISTS_CASE_INSENSITIVE_MATCH = 3;

        /** The URI, for logging and error reporting.*/
        protected CrawlURI curi;

        /**
           The main part of this segment.
           For a directory segment, that's all there is.
           For an end segment, it's the part of the URI after the last '/'
           up to but not including the '.' before the suffix (if any).
        */
        protected LumpyString mainPart = null;

        /**
           The maximum number of characters allowed
           in one file system path segment.
           A URI segment can potentially be much longer,
           but we'll trim it to this.
        */
        protected int maxSegLen;

        /** If true, the file system is assumed to be
            case-sensitive; otherwise the file system is assumed to be
            case-insensitive.
        */
        private boolean caseSensitive;

        /**
           Creates a new PathSegment.
           @param maxSegLen the maximum number of characters
           allowed in one path segment
           @param caseSensitive if true, the file system is assumed to be
           case-sensitive; otherwise the file system is assumed to be
           case-insensitive
           @param curi the URI
           @throws IllegalArgumentException if
           maxSegLen is too small
        */
        PathSegment(int maxSegLen, boolean caseSensitive, CrawlURI curi) {
            if (maxSegLen < 2) {
                throw new IllegalArgumentException("maxSegLen: " + maxSegLen);
            }
            this.maxSegLen = maxSegLen;
            this.caseSensitive = caseSensitive;
            this.curi = curi;
        }

        /**
           Adds this segment to a file path.
           This is the key method of this class.
           It extends the given path by one segment,
           named to obey all constraints.
           A new directory is created if necessary.
           @param currentPath the current path, to which this segment is added
           @throws IOException
           if a needed directory could not be created
           @throws IOException
           if a needed directory is not writeable
        */
        abstract void addToPath(URIToFileReturn currentPath) throws IOException;

        /**
           Checks if a file (including directories) exists.
           @param fsf the directory containing the file to be checked
           @param segStr the simple file or directory name
           @param check the file or directory for which to check
           @return EXISTS_NOT if check does not exist,
           EXISTS_EXACT_MATCH if check exists with a name that matches
           (case-sensitive) segStr, and
           EXISTS_CASE_INSENSITIVE_MATCH if check exists
           with a name that matches
           segStr using a case-insensitive match but not using a
           case-sensitive match
        */
        protected int existsMaybeCaseSensitive(File fsf, String segStr,
                                               File check) {
            if (caseSensitive) {
                return check.exists() ? EXISTS_EXACT_MATCH : EXISTS_NOT;
            }
            if (!check.exists()) {
                return EXISTS_NOT;
            }

            /*
              The JVM says the file exists, but the file system is assumed to be
              case-insensitive, so do we have an exact match or just a
              case-insensitive match?  We get an array of all the
              file names that match (case-insensitive) the one we're
              checking, then we can look for a case-sensitive match.
            */
            String[] fna = fsf.list(new CaseInsensitiveFilenameFilter(segStr));
            for (int i = 0; fna.length != i; ++i) {
                if (segStr.equals(fna[i])) {
                  return EXISTS_EXACT_MATCH;
                }
            }
            return EXISTS_CASE_INSENSITIVE_MATCH;
        }

        /**
           This class implements a FilenameFilter that matches
           by name, ignoring case.
        */
        class CaseInsensitiveFilenameFilter implements FilenameFilter {
            /** The file name we're looking for. */
            private String target;

            /**
               Creates a CaseInsensitiveFilenameFilter.
               @param target the target file name
               @throws IllegalArgumentException if
               target is null or empty.
            */
            CaseInsensitiveFilenameFilter(String target) {
                if (null == target) {
                    throw new IllegalArgumentException("target null");
                }
                if (0 == target.length()) {
                    throw new IllegalArgumentException("target empty");
                }
                this.target = target;
            }

            public boolean accept(File dir, String name) {
                return target.equalsIgnoreCase(name);
            }
        }
    }

    /**
       This class represents one directory segment (component) of a URI path.
    */
    class DirSegment extends PathSegment {
        /** If a segment name is in this set, prepend an underscore.*/
        private Set<String> underscoreSet;

        /**
           Creates a DirSegment.
           @param uriPath the path part of the URI
           @param beginIndex the beginning index, inclusive, of the substring
           of uriPath to be used
           @param endIndex the ending index, exclusive, of the substring
           of uriPath to be used
           @param maxSegLen the maximum number of characters allowed in one
           file system path segment (component)
           @param caseSensitive if true, the file system is assumed to be
           case-sensitive; otherwise the file system is assumed to be
           case-insensitive but case-preserving
           @param curi the URI
           @param characterMap a map from characters
           (as length-1 String values) in
           the URI path and query to replacement String values
           @param dotBegin if non-null, this replaces a '.' at
           the beginning of the directory name
           @param dotEnd if non-null, this replaces a '.'
           that appears at the end of a directory name
           @param underscoreSet if non-null and a segment, after conversion
           to lower case, is in this set, then prepend an underscore
           to the segment
           @throws IllegalArgumentException if
           beginIndex is negative.
           @throws IllegalArgumentException if
           endIndex is less than beginIndex.
           @throws IllegalArgumentException if
           maxSegLen is too small.
        */
        DirSegment(String uriPath, int beginIndex, int endIndex, int maxSegLen,
                   boolean caseSensitive, CrawlURI curi, Map<String, String> characterMap,
                   String dotBegin, String dotEnd, Set<String> underscoreSet) {
            super(maxSegLen, caseSensitive, curi);
            mainPart = new LumpyString(uriPath, beginIndex, endIndex,
                                       (null == dotEnd) ? 0 : dotEnd.length(),
                                       this.maxSegLen, characterMap, dotBegin);
            if (null != dotEnd) {

                // We might get a segment like /VeryLong............../
                // so we have to loop to guarantee the segment doesn't
                // end with a dot.
                int dl = dotEnd.length();
                while (mainPart.endsWith('.')) {

                    // Chop off the dot at the end.
                    mainPart.trimToMax(mainPart.length() - 1);
                    if ((mainPart.length() + dl) <= this.maxSegLen) {
                        mainPart.append(dotEnd);
                    }
                }
            }
            this.underscoreSet = underscoreSet;
        }

        void addToPath(URIToFileReturn currentPath) throws IOException {
            NumberFormat nf = null;
            int startLen = mainPart.length(); // Starting length.
            for (int i = 0; ; ++i) {
                if (0 != i) {

                    // Try to create a unique file name by appending a
                    // number.
                    if (null == nf) {
                        nf = NumberFormat.getIntegerInstance();
                    }
                    String ending = nf.format(i);
                    mainPart.trimToMax(Math.min(startLen,
                                                maxSegLen - ending.length()));
                    mainPart.append(ending);
                }
                String segStr = mainPart.toString();
                if ((null != underscoreSet)
                        && underscoreSet.contains(segStr.toLowerCase())) {
                    mainPart.prepend('_');
                    ++startLen;
                    mainPart.trimToMax(maxSegLen);
                    segStr = mainPart.toString();
                }
                File fsf = currentPath.getFile();
                File f = new File(fsf, segStr);
                int er = existsMaybeCaseSensitive(fsf, segStr, f);
                switch (er) {
                case EXISTS_NOT:
                    if (!f.mkdir()) {
                        throw new IOException("Can not mkdir "
                                              + f.getAbsolutePath());
                    }
                    currentPath.append(f, segStr);
                    return; // Created new directory.

                case EXISTS_EXACT_MATCH:
                    if (f.isDirectory()) {
                        if (!f.canWrite()) {
                            throw new IOException("Directory "
                                                  + f.getAbsolutePath()
                                                  + " not writeable.");
                        }

                        /*
                          A writeable directory already exists.
                          Assume it's the one we want.
                          This assumption fails for cases like
                          http://foo.com/a*256/b.html
                          followed by
                          http://foo.com/a*256z/b.html
                          where a*256 means a sequence of the maximum allowed
                          number of "a"s.
                        */
                        currentPath.append(f, segStr);
                        return;
                    }

                    /*
                      A segment already exists but isn't a directory.
                      This could arise from, for example,
                      http://foo.com/a*256
                      followed by
                      http://foo.com/a*256b/b.html
                      We need to find a directory we created before in this
                      situation, or make a new directory with a unique name.
                      Going around the loop should eventually do that.
                    */
                    break;

                case EXISTS_CASE_INSENSITIVE_MATCH:
                    /*
                      A segment already exists that's a case-insensitive match
                      but not an exact match.  It may or may not be a directory.
                      This could arise, on a case-insensitive, case-preserving
                      file system (such as Macintosh HFS+).  For example,
                      http://foo.com/bar/z.html
                      followed by
                      http://foo.com/BAR/z.html
                      would do it.  We want bar and BAR to turn into different
                      directories.
                      Going around the loop should eventually do that.
                    */
                    break;

                default:
                    throw new IllegalStateException("Code: " + er);
                }
            }
        }
    }

    /**
       This class represents the last segment (component) of a URI path.
    */
    class EndSegment extends PathSegment {
        /**
           The number of characters in the path up to this EndSegment,
           including the final File.separatorChar.
        */
        private int dirPathLen;

        /**
           The maximum number of characters allowed in a file path, minus 1.
           The extra 1 is reserved for temporarily appending
           a character so an existing file can be replaced atomically,
           for example, by writing
           <code>foo.htmlN</code>
           and then renaming it to
           <code>foo.html</code>.
        */
        private int maxPathLen;

        /** The query part of the URI, or null if none.*/
        private LumpyString query = null;

        /**
           The suffix, or null if none.
           This isn't a LumpyString because we'd only trim a suffix
           if space were very, very tight.
        */
        private String suffix = null;

        /**
           True if the suffix goes at the end, after the query.
           False if the suffix goes before the query.
        */
        private boolean suffixAtEnd;

        /** Appended to mainPart if necessary to create a unique file name.*/
        private String uniquePart = null;

        /**
           Creates an EndSegment.
           @param uriPath the path part of the URI
           @param beginIndex the beginning index, inclusive, of the substring
           of uriPath to be used
           @param endIndex the ending index, exclusive, of the substring
           of uriPath to be used
           @param maxSegLen the maximum number of characters allowed in one
           file system path segment (component)
           @param caseSensitive if true, the file system is assumed to be
           case-sensitive; otherwise the file system is assumed to be
           case-insensitive but case-preserving
           @param curi the URI
           @param characterMap maps characters (as length-1 String values) in
           the URI path and query to replacement String values
           @param dotBegin if non-null, this replaces a '.' at
           the beginning of the segment
           @param query the query part of the URI, or null if none
           @param suffix if non-null, use this as the suffix in preference to
           any suffix that uriPath might have
           @param maxPathLen the maximum number of characters allowed in a
           file system path
           @param suffixAtEnd if true, the suffix is placed at the end of the
           path, after the query (if any); otherwise, the suffix is placed
           before the query
           @throws IllegalArgumentException if
           beginIndex is negative.
           @throws IllegalArgumentException if
           endIndex is less than beginIndex.
           @throws IllegalArgumentException if
           maxSegLen is too small.
        */
        EndSegment(String uriPath, int beginIndex, int endIndex, int maxSegLen,
                   boolean caseSensitive, CrawlURI curi, Map<String, String> characterMap,
                   String dotBegin, String query, String suffix,
                   int maxPathLen, boolean suffixAtEnd) {
            super(maxSegLen - 1, caseSensitive, curi);
            int mpe = endIndex; // endIndex for the main part (no suffix).
            int ldi = uriPath.lastIndexOf('.'); // Index of last dot.
            if ((ldi > 0) && (ldi < (endIndex - 1)) && (ldi > beginIndex)) {
                mpe = ldi; // uriPath has a suffix.
            }
            this.suffix = suffix;
            if ((null == this.suffix) && (mpe < (endIndex - 1))) {

                // There's no replacement suffix and uriPath has a suffix.
                // Run it through a LumpyString to do the character mapping.
                LumpyString ls = new LumpyString(uriPath, mpe + 1, endIndex, 0,
                                                 this.maxSegLen, characterMap,
                                                 null);
                this.suffix = ls.toString();
            }
            int pad = ((null == this.suffix) ? 0 : (1 + this.suffix.length()))
                + ((null == query) ? 0 : query.length());
            mainPart = new LumpyString(uriPath, beginIndex, mpe, pad,
                                       this.maxSegLen, characterMap, dotBegin);
            this.maxPathLen = maxPathLen - 1;
            if (null != query) {
                this.query = new LumpyString(query, 0, query.length(), 0,
                                             this.maxSegLen, characterMap,
                                             null);
            }
            this.suffixAtEnd = suffixAtEnd;
        }

        void addToPath(URIToFileReturn currentPath) {
            File fsf = currentPath.getFile();
            NumberFormat nf = null;
            dirPathLen = 1 + fsf.getPath().length();
            for (int i = 0; ; ++i) {
                if (0 != i) {
                    if (null == nf) {
                        nf = NumberFormat.getIntegerInstance();
                    }
                    uniquePart = nf.format(i);
                }
                trimWithPadding((null == uniquePart) ? 0 : uniquePart.length());
                String segStr = joinParts(); // This EndSegment as a String.
                File f = new File(fsf, segStr);

                // Code for whether file exists.
                int er = existsMaybeCaseSensitive(fsf, segStr, f);
                switch (er) {
                case EXISTS_NOT:
                    currentPath.append(f, segStr);
                    return;

                case EXISTS_EXACT_MATCH:
                    if (f.isFile()) {
                        currentPath.append(f, segStr);
                        return;
                    }

                    /*
                      A file already exists but isn't an ordinary file.
                      It might be a directory, special file, named pipe,
                      whatever.
                      We need to find an unused file name,
                      or an ordinary file.
                      Going around the loop should eventually do that.
                    */
                    break;

                case EXISTS_CASE_INSENSITIVE_MATCH:
                    /*
                      A file already exists that's a case-insensitive match
                      but not an exact match.
                      This could arise, on a case-insensitive, case-preserving
                      file system (such as Macintosh HFS+).  For example,
                      http://foo.com/files.zip
                      followed by
                      http://foo.com/FILES.ZIP
                      would do it.  We want files.zip and FILES.ZIP to turn into
                      different files. Going around the loop should eventually
                      do that.
                    */
                    break;

                default:
                    throw new IllegalStateException("Code: " + er);
                }
            }
        }

        /**
           Creates a simple file name from the parts of this EndSegment.
           @return a simple file name constructed from the main part,
           unique part, query, and suffix
        */
        private String joinParts() {
            StringBuffer sb = new StringBuffer(length());
            sb.append(mainPart.asStringBuffer());
            if (null != uniquePart) {
                sb.append(uniquePart);
            }
            if (suffixAtEnd) {
                if (null != query) {
                    sb.append(query);
                }
                if (null != suffix) {
                    sb.append('.');
                    sb.append(suffix);
                }
            } else {
                if (null != suffix) {
                    sb.append('.');
                    sb.append(suffix);
                }
                if (null != query) {
                    sb.append(query);
                }
            }
            return sb.toString();
        }

        /**
           Gets the number of available character positions.
           If this EndSegment were converted to a path,
           it would have a path length and a segment length.
           There are two constraints: maxSegLen and maxPathLen.
           The number of character positions available before bumping
           into the lower constraint is computed.
           @return the number of available positions, which may be negative
        */
        private int lenAvail() {
            int len = length();
            return Math.min(maxSegLen - len, maxPathLen - dirPathLen - len);
        }

        /**
           Gets the length of the simple file name that would be
           created for this EndSegment.
           @return the length
        */
        private int length() {
            int r = mainPart.length(); // Return value.
            if (null != uniquePart) {
                r += uniquePart.length();
            }
            if (null != query) {
                r += query.length();
            }
            if (null != suffix) {
                r += 1 + suffix.length(); // 1 for the '.'
            }
            return r;
        }

        /**
           Trims this EndSegment so a given number of characters are available.
           After trimming, there will be room for at least
           padding more characters before one of the constraints is
           encountered.
           The choices for trimming, in priority order, are:
           <ol>
           <li>Shorten the query.</li>
           <li>Remove the query.</li>
           <li>Shorten the main part.</li>
           <li>Shorten the suffix.</li>
           </ol>
           @param padding the number of character positions that need to be
           available
           @throws IllegalStateException
           if it's impossible to trim enough
        */
        private void trimWithPadding(int padding) {
            assert padding >= 0 : "padding: " + padding;
            int la = lenAvail();
            if (la >= padding) {
                return;
            }

            // We need space for (padding - la) characters.
            // la might be negative.
            if (null != query) {
                query.trimToMax(Math.max(0, query.length() - (padding - la)));
                if (0 == query.length()) {
                    query = null;
                }
                la = lenAvail();
                if (la >= padding) {
                    return;
                }
            }
            mainPart.trimToMax(Math.max(1, mainPart.length() - (padding - la)));
            la = lenAvail();
            if (la >= padding) {
                return;
            }
            if (null != suffix) {
                suffix = suffix.substring(0, Math.max(1, suffix.length()
                                                      - (padding - la)));
                la = lenAvail();
                if (la >= padding) {
                    return;
                }
            }
            throw new IllegalStateException("Can not trim " + curi.toString());
        }
    }

    /**
       This class represents a dynamically growable string
       consisting of substrings ("lumps") that
       are treated atomically.  If the string is shortened, then an entire
       lump is removed.  The intent is to treat each %XX escape as a lump.
       This class also allows single characters in a source string to be
       re-mapped to a different string, possible containing more than
       one character.
       Each re-mapped character is also treated as a lump.
       <p>
       For example, suppose part of a URI, between two slashes, is
       <code>/VeryLongString...%3A/</code>.
       We want to create a corresponding file system directory, but the string
       is a little longer than the allowed maximum.
       It's better to trim the entire
       <code>%3A</code>
       off the end than part of it.
       This is especially true if, later, we need to append some digits
       to create a unique directory name.
       So we treat the entire
       <code>%3A</code>
       as one lump.
    */
    class LumpyString {
        /**
           Lumps are indicated by an auxiliary array aux[],
           indexed the same as the string.  The LUMP_BEGIN bit is set
           for a position in the string at which a lump begins.
        */
        private static final byte LUMP_BEGIN = 0x1;

        /** Bit set for the end of a lump. */
        private static final byte LUMP_END = 0x2;

        /**
           Bit set for all characters in a lump of length greater than 1,
           except the beginning and ending characters.
        */
        private static final byte LUMP_MID = 0x4;

        /** The auxiliary array. */
        private byte[] aux;

        /** Holds the string. */
        private StringBuffer string;

        /**
           Creates a LumpyString.
           @param str the source string
           @param beginIndex the beginning index, inclusive, of the substring
           of str to be used
           @param endIndex the ending index, exclusive, of the substring
           of str to be used
           @param padding reserve this many additional character positions
           before dynamic growth is needed
           @param maxLen the maximum string length, regardless of the
           values of beginIndex, endIndex, and padding
           @param characterMap maps from characters in the source string
           (represented as length-one String values) to replacement String
           values (length at least 1).
           Each replacement string is treated as one lump.
           This is intended to cope with characters that a file system
           does not allow.
           @param dotBegin if non-null, this replaces a '.' at
           <code>str[beginIndex]</code>
           @throws IllegalArgumentException if
           beginIndex is negative.
           @throws IllegalArgumentException if
           endIndex is less than beginIndex.
           @throws IllegalArgumentException if
           padding is negative.
           @throws IllegalArgumentException if
           maxLen is less than one.
           @throws IllegalArgumentException if
           characterMap is null.
           @throws IllegalArgumentException if
           dotBegin is non-null but empty.
        */
        LumpyString(String str, int beginIndex, int endIndex, int padding,
                    int maxLen, Map<String, String> characterMap, String dotBegin) {
            if (beginIndex < 0) {
                throw new IllegalArgumentException("beginIndex < 0: "
                                                   + beginIndex);
            }
            if (endIndex < beginIndex) {
                throw new IllegalArgumentException("endIndex < beginIndex "
                    + "beginIndex: " + beginIndex + "endIndex: " + endIndex);
            }
            if (padding < 0) {
                throw new IllegalArgumentException("padding < 0: " + padding);
            }
            if (maxLen < 1) {
                throw new IllegalArgumentException("maxLen < 1: " + maxLen);
            }
            if (null == characterMap) {
                throw new IllegalArgumentException("characterMap null");
            }
            if ((null != dotBegin) && (0 == dotBegin.length())) {
                throw new IllegalArgumentException("dotBegin empty");
            }

            // Initial capacity.  Leave some room for %XX lumps.
            // Guaranteed positive.
            int cap = Math.min(2 * (endIndex - beginIndex) + padding + 1,
                               maxLen);
            string = new StringBuffer(cap);
            aux = new byte[cap];
            for (int i = beginIndex; i != endIndex; ++i) {
                String s = str.substring(i, i + 1);
                String lump; // Next lump.
                if (".".equals(s) && (i == beginIndex) && (null != dotBegin)) {
                    lump = dotBegin;
                } else {
                    lump = (String) characterMap.get(s);
                }
                if (null == lump) {
                    if ("%".equals(s) && ((endIndex - i) > 2)
                            && (-1 != Character.digit(str.charAt(i + 1), 16))
                            && (-1 != Character.digit(str.charAt(i + 2), 16))) {

                        // %XX escape; treat as one lump.
                        lump = str.substring(i, i + 3);
                        i += 2;
                    } else {
                        lump = s;
                    }
                }
                if ((string.length() + lump.length()) > maxLen) {
                    assert checkInvariants();
                    return;
                }
                append(lump);
            }
            assert checkInvariants();
        }

        /**
           Converts this LumpyString to a String.
           @return the current string contents
        */
        public String toString() {
            assert checkInvariants();
            return string.toString();
        }

        /**
           Appends one lump to the end of this string.
           @param lump the lump (substring) to append
           @throws IllegalArgumentException if
           lump is null or empty.
        */
        void append(String lump) {
            if (null == lump) {
                throw new IllegalArgumentException("lump null");
            }
            int lumpLen = lump.length();
            if (0 == lumpLen) {
                throw new IllegalArgumentException("lump empty");
            }
            int pos = string.length(); // Current end of string.
            ensureCapacity(pos + lumpLen);
            if (1 == lumpLen) {
                aux[pos] = LUMP_BEGIN | LUMP_END;
            } else {
                assert lumpLen > 1;
                aux[pos] = LUMP_BEGIN;
                ++pos;
                for (int i = lumpLen - 2; 0 != i; --i) {
                    aux[pos] = LUMP_MID;
                    ++pos;
                }
                aux[pos] = LUMP_END;
            }
            string.append(lump);
            assert checkInvariants();
        }

        /**
           Returns the string as a StringBuffer.
           The caller should <em>not</em> modify the return value.
           @return the string
        */
        StringBuffer asStringBuffer() {
            return string;
        }

        /**
           Tests if this string ends with a character.
           @param ch the character to test for
           @return true if and only if this string ends with ch
        */
        boolean endsWith(char ch) {
            assert checkInvariants();
            int len = string.length();
            return (0 != len) && (string.charAt(len - 1) == ch);
        }

        /**
           Prepends one character, as a lump, to this string.
           @param ch the character to prepend
        */
        void prepend(char ch) {
            assert checkInvariants();
            int oldLen = string.length();
            ensureCapacity(1 + oldLen);
            string.insert(0, ch);
            System.arraycopy(aux, 0, aux, 1, oldLen);
            aux[0] = LUMP_BEGIN | LUMP_END;
            assert checkInvariants();
        }

        /**
           Gets the length of this string.
           @return the number of characters in this string
        */
        int length() {
            assert checkInvariants();
            return string.length();
        }

        /**
           If necessary, trims this string to a maximum length.
           Any trimming is done by removing one or more complete
           lumps from the end of this string.
           @param maxLen the new maximum length.
           After trimming, the actual length of this string will be
           at most maxLen.
           @throws IllegalArgumentException if
           maxLen is negative.
        */
        void trimToMax(int maxLen) {
            if (maxLen < 0) {
                throw new IllegalArgumentException("maxLen < 0: " + maxLen);
            }
            assert checkInvariants();
            int cl = string.length(); // Current length.
            if (cl > maxLen) {
                int nl = maxLen; // New length.
                while ((0 != nl) && (LUMP_END != (aux[nl - 1] & LUMP_END))) {
                    --nl;
                }
                for (int i = nl; i != cl; ++i) {
                    aux[i] = 0;
                }
                string.setLength(nl);
            }
            assert checkInvariants();
        }

        /**
           Checks some assertions on the instance variables.
           The intended usage is
           <code>assert checkInvariants();</code>
           so that if assertions are off, no call is made.
           @return true
        */
        private boolean checkInvariants() {

            // There's an aux[] element for every character in the StringBuffer.
            assert aux.length >= string.length()
                : "aux.length: " + aux.length
                + " string.length(): " + string.length();

            // The first character starts a lump.
            assert (0 == string.length())
                || (LUMP_BEGIN == (aux[0] & LUMP_BEGIN))
                : "aux[0]: " + aux[0];

            // The last character ends a lump.
            assert (0 == string.length())
                || (LUMP_END == (aux[string.length() - 1] & LUMP_END))
                : "aux[end]: " + aux[string.length() - 1];
            return true;
        }

        /**
           Ensures that the capacity is at least equal to the specified minimum.
           @param minCapacity the minimum desired capacity
        */
        private void ensureCapacity(int minCapacity) {
            assert checkInvariants();
            if (minCapacity > aux.length) {
                int nc = 2 * aux.length; // New capacity.
                while (nc < minCapacity) {
                    nc *= 2;
                }
                byte[] oldAux = aux;
                aux = new byte[nc];
                System.arraycopy(oldAux, 0, aux, 0, string.length());
            }
            string.ensureCapacity(minCapacity);
            assert checkInvariants();
        }
    }

    /**
       This class is returned by uriToFile.
       It represents a file system path, both as a File and as
       a path relative to the base directory.
    */
    class URIToFileReturn {
        /** The file system path as a File.*/
        private File filePath;

        /** The relative path from baseDir.*/
        private StringBuffer relativePath = new StringBuffer(255);

        /**
           Creates a URIToFileReturn.
           @param baseDir the path to the starting directory
           @param host the host part of the URI, or null if the host name
           should not be part of the path
           @param port the port part of the URI, or -1 if the port
           should not be part of the path
        */
        URIToFileReturn(String baseDir, String host, int port) {

            // The initial path.
            StringBuffer startPath = new StringBuffer(baseDir.length() + 32);
            startPath.append(baseDir);
            if (baseDir.endsWith(File.separator)) {
                assert 1 != baseDir.length();
                startPath.deleteCharAt(startPath.length() - 1);
            }
            if (null != host) {
                startPath.append(File.separatorChar);
                startPath.append(host);
                relativePath.append(host);
            }
            if (port > 0) {
                startPath.append(File.separatorChar);
                startPath.append(port);
                relativePath.append(File.separatorChar);
                relativePath.append(port);
            }
            filePath = new File(startPath.toString());
        }

        /**
           Appends one more segment to this path.
           @param f a File representing the path with the next segment added
           @param nextSegment the next segment
        */
        void append(File f, String nextSegment) {
            filePath = f;
            if (0 != relativePath.length()) {
                relativePath.append(File.separatorChar);
            }
            relativePath.append(nextSegment);
        }

        /**
           Gets this path as a File.
           @return this path
        */
        File getFile() {
            return filePath;
        }

        /**
           Gets this path as a relative path from the base directory.
           @return the relative path
        */
        String getRelativePath() {
            return relativePath.toString();
        }

        /**
           Tests if this path is longer than a given value.
           @param maxLen the value to test
           @return true if and only if this path is longer than maxLen
        */
        boolean longerThan(int maxLen) {
            return filePath.getPath().length() > maxLen;
        }

        /**
           Creates all directories in this path as needed.
           @throws IOException
           if a needed directory could not be created
           @throws IOException
           if a needed directory is not writeable
           @throws IOException
           if a non-directory file exists
           with the same path as a needed directory
        */
        void mkdirs() throws IOException {
            if (!filePath.exists()) {
                if (!filePath.mkdirs()) {
                    throw new IOException("Can not mkdir "
                                          + filePath.getAbsolutePath());
                }
            } else if (!filePath.canWrite()) {
                throw new IOException("Directory " + filePath.getAbsolutePath()
                                      + " not writeable.");
            } else if (!filePath.isDirectory()) {
                throw new IOException("File " + filePath.getAbsolutePath()
                                      + " is not a directory.");
            }
        }
    }
}
