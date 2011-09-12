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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang.math.LongRange;


/** Utility methods for manipulating files and directories.
 *
 * @contributor John Erik Halse
 * @contributor gojomo
 */
public class FileUtils {
    private static final Logger LOGGER =
        Logger.getLogger(FileUtils.class.getName());
            
    /**
     * Constructor made private because all methods of this class are static.
     */
    private FileUtils() {
        super();
    }
    
    /**
     * Copy the src file to the destination. Deletes any preexisting
     * file at destination. 
     * 
     * @param src
     * @param dest
     * @return True if the extent was greater than actual bytes copied.
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static boolean copyFile(final File src, final File dest)
    throws FileNotFoundException, IOException {
        return copyFile(src, dest, -1, true);
    }
    
    /**
     * Copy up to extent bytes of the source file to the destination.
     * Deletes any preexisting file at destination.
     *
     * @param src
     * @param dest
     * @param extent Maximum number of bytes to copy
     * @return True if the extent was greater than actual bytes copied.
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static boolean copyFile(final File src, final File dest,
        long extent)
    throws FileNotFoundException, IOException {
        return copyFile(src, dest, extent, true);
    }

	/**
     * Copy up to extent bytes of the source file to the destination
     *
     * @param src
     * @param dest
     * @param extent Maximum number of bytes to copy
	 * @param overwrite If target file already exits, and this parameter is
     * true, overwrite target file (We do this by first deleting the target
     * file before we begin the copy).
	 * @return True if the extent was greater than actual bytes copied.
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static boolean copyFile(final File src, final File dest,
        long extent, final boolean overwrite)
    throws FileNotFoundException, IOException {
        boolean result = false;
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Copying file " + src + " to " + dest + " extent " +
                extent + " exists " + dest.exists());
        }
        if (dest.exists()) {
            if (overwrite) {
                dest.delete();
                LOGGER.finer(dest.getAbsolutePath() + " removed before copy.");
            } else {
                // Already in place and we're not to overwrite.  Return.
                return result;
            }
        }
        FileInputStream fis = null;
        FileOutputStream fos = null;
        FileChannel fcin = null;
        FileChannel fcout = null;
        try {
            // Get channels
            fis = new FileInputStream(src);
            fos = new FileOutputStream(dest);
            fcin = fis.getChannel();
            fcout = fos.getChannel();
            if (extent < 0) {
                extent = fcin.size();
            }

            // Do the file copy
            long trans = fcin.transferTo(0, extent, fcout);
            if (trans < extent) {
                result = false;
            }
            result = true; 
        } catch (IOException e) {
            // Add more info to the exception. Preserve old stacktrace.
            // We get 'Invalid argument' on some file copies. See
            // http://intellij.net/forums/thread.jsp?forum=13&thread=63027&message=853123
            // for related issue.
            String message = "Copying " + src.getAbsolutePath() + " to " +
                dest.getAbsolutePath() + " with extent " + extent +
                " got IOE: " + e.getMessage();
            if ((e instanceof ClosedByInterruptException) ||
                    ((e.getMessage()!=null)
                            &&e.getMessage().equals("Invalid argument"))) {
                LOGGER.severe("Failed copy, trying workaround: " + message);
                workaroundCopyFile(src, dest);
            } else {
                IOException newE = new IOException(message);
                newE.initCause(e);
                throw newE;
            }
        } finally {
            // finish up
            if (fcin != null) {
                fcin.close();
            }
            if (fcout != null) {
                fcout.close();
            }
            if (fis != null) {
                fis.close();
            }
            if (fos != null) {
                fos.close();
            }
        }
        return result;
    }
    
    protected static void workaroundCopyFile(final File src,
            final File dest)
    throws IOException {
        FileInputStream from = null;
        FileOutputStream to = null;
        try {
            from = new FileInputStream(src);
            to = new FileOutputStream(dest);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = from.read(buffer)) != -1) {
                to.write(buffer, 0, bytesRead);
            }
        } finally {
            if (from != null) {
                try {
                    from.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (to != null) {
                try {
                    to.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Get a list of all files in directory that have passed prefix.
     *
     * @param dir Dir to look in.
     * @param prefix Basename of files to look for. Compare is case insensitive.
     *
     * @return List of files in dir that start w/ passed basename.
     */
    public static File [] getFilesWithPrefix(File dir, final String prefix) {
        FileFilter prefixFilter = new FileFilter() {
                public boolean accept(File pathname)
                {
                    return pathname.getName().toLowerCase().
                        startsWith(prefix.toLowerCase());
                }
            };
        return dir.listFiles(prefixFilter);
    }

    /** Get a @link java.io.FileFilter that filters files based on a regular
     * expression.
     *
     * @param regex the regular expression the files must match.
     * @return the newly created filter.
     */
    public static IOFileFilter getRegexFileFilter(String regex) {
        // Inner class defining the RegexFileFilter
        class RegexFileFilter implements IOFileFilter {
            Pattern pattern;

            protected RegexFileFilter(String re) {
                pattern = Pattern.compile(re);
            }

            public boolean accept(File pathname) {
                return pattern.matcher(pathname.getName()).matches();
            }

            public boolean accept(File dir, String name) {
                return accept(new File(dir,name));
            }
        }

        return new RegexFileFilter(regex);
    }
    
    /**
     * Test file exists and is readable.
     * @param f File to test.
     * @exception FileNotFoundException If file does not exist or is not unreadable.
     */
    public static File assertReadable(final File f) throws FileNotFoundException {
        if (!f.exists()) {
            throw new FileNotFoundException(f.getAbsolutePath() +
                " does not exist.");
        }

        if (!f.canRead()) {
            throw new FileNotFoundException(f.getAbsolutePath() +
                " is not readable.");
        }
        
        return f;
    }
    
    /**
     * @param f File to test.
     * @return True if file is readable, has uncompressed extension,
     * and magic string at file start.
     * @exception IOException If file not readable or other problem.
     */
    public static boolean isReadableWithExtensionAndMagic(final File f, 
            final String uncompressedExtension, final String magic)
    throws IOException {
        boolean result = false;
        FileUtils.assertReadable(f);
        if(f.getName().toLowerCase().endsWith(uncompressedExtension)) {
            FileInputStream fis = new FileInputStream(f);
            try {
                byte [] b = new byte[magic.length()];
                int read = fis.read(b, 0, magic.length());
                fis.close();
                if (read == magic.length()) {
                    StringBuffer beginStr
                        = new StringBuffer(magic.length());
                    for (int i = 0; i < magic.length(); i++) {
                        beginStr.append((char)b[i]);
                    }
                    
                    if (beginStr.toString().
                            equalsIgnoreCase(magic)) {
                        result = true;
                    }
                }
            } finally {
                fis.close();
            }
        }

        return result;
    }
    
    /**
     * Turn path into a File, relative to context (which may be ignored 
     * if path is absolute). 
     * 
     * @param context File context if path is relative
     * @param path String path to make into a File
     * @return File created
     */
    public static File maybeRelative(File context, String path) {
        File f = new File(path);
        if(f.isAbsolute()) {
            return f;
        }
        return new File(context, path);
    }
    
    /**
     * Load Properties instance from a File
     * 
     * @param file
     * @return Properties
     * @throws IOException
     */
    public static Properties loadProperties(File file) throws IOException {
        FileInputStream finp = new FileInputStream(file);
        try {
            Properties p = new Properties();
            p.load(finp);
            return p;
        } finally {
            ArchiveUtils.closeQuietly(finp);
        }
    }
    
    /**
     * Store Properties instance to a File
     * @param p
     * @param file destination File
     * @throws IOException
     */
    public static void storeProperties(Properties p, File file) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        try {
            p.store(fos,"");
        } finally {
            ArchiveUtils.closeQuietly(fos);
        }
    }

    // TODO: comment
    public static boolean moveAsideIfExists(File file) throws IOException {
        if(!file.exists()) {
            return true; 
        }
        String newName = 
            file.getCanonicalPath() + "." 
            + ArchiveUtils.get14DigitDate(file.lastModified());
        boolean retVal = file.renameTo(new File(newName));
        if(!retVal) {
            LOGGER.warning("unable to move aside: "+file+" to "+newName);
        }
        return retVal;

    }

    /**
     * Retrieve a number of lines from the file around the given 
     * position, as when paging forward or backward through a file. 
     * 
     * @param file File to retrieve lines
     * @param position offset to anchor lines
     * @param signedDesiredLineCount lines requested; if negative, 
     *        want this number of lines ending with a line containing
     *        the position; if positive, want this number of lines,
     *        all starting at or after position. 
     * @param lines List<String> to insert found lines
     * @param lineEstimate int estimate of line size, 0 means use default
     *        of 128
     * @return LongRange indicating the file offsets corresponding to 
     *         the beginning of the first line returned, and the point
     *         after the end of the last line returned
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public static LongRange pagedLines(File file, long position,
            int signedDesiredLineCount, List<String> lines, int lineEstimate)
            throws IOException {
        // consider negative positions as from end of file; -1 = last byte
        if (position < 0) {
            position = file.length() + position; 
        }
        
        // calculate a reasonably sized chunk likely to have all desired lines
        if(lineEstimate == 0) {
            lineEstimate = 128; 
        }
        int desiredLineCount = Math.abs(signedDesiredLineCount);
        long startPosition;
        long fileEnd = file.length();
        int bufferSize = (desiredLineCount + 5) * lineEstimate; 
        if(signedDesiredLineCount>0) {
            // reading forward; include previous char in case line-end
            startPosition = position - 1;
        } else {
            // reading backward
            startPosition = position - bufferSize + (2 * lineEstimate);
        }
        if(startPosition<0) {
            startPosition = 0; 
        }
        if(startPosition+bufferSize > fileEnd) {
            bufferSize = (int)(fileEnd - startPosition); 
        }

        // read that reasonable chunk
        FileInputStream fis = new FileInputStream(file);
        fis.getChannel().position(startPosition); 
        byte[] buf = new byte[bufferSize];
        ArchiveUtils.readFully(fis, buf);
        IOUtils.closeQuietly(fis);
        
        // find all line starts fully in buffer
        // (positions after a line-end, per line-end definition in 
        // BufferedReader.readLine)
        LinkedList<Integer> lineStarts = new LinkedList<Integer>();
        if(startPosition==0) {
            lineStarts.add(0);
        }
        boolean atLineEnd = false; 
        boolean eatLF = false; 
        int i; 
        for(i = 0; i < bufferSize; i++) {
            if ((char) buf[i] == '\n' && eatLF) {
                eatLF = false;
                continue;
            }
            if(atLineEnd) {
                atLineEnd = false; 
                lineStarts.add(i);
                if(signedDesiredLineCount<0 && startPosition+i > position) {
                    // reached next line past position, read no more
                    break;
                }
            }
            if ((char) buf[i] == '\r') {
                atLineEnd = true; 
                eatLF = true; 
                continue;
            }
            if ((char) buf[i] == '\n') {
                atLineEnd = true; 
            }
        }
        if(startPosition+i == fileEnd) {
            // add phantom lineStart after end
            lineStarts.add(bufferSize);
        }
        int foundFullLines = lineStarts.size()-1;

        // if found no lines
        if(foundFullLines<1) {
            if(signedDesiredLineCount>0) {
                if(startPosition+bufferSize == fileEnd) {
                    // nothing more to read: return nothing
                    return new LongRange(fileEnd,fileEnd);
                } else {
                    // retry with larger lineEstimate
                    return pagedLines(file, position, signedDesiredLineCount, lines, Math.max(bufferSize,lineEstimate));
                }
                
            } else {
                // try again with much larger line estimate
                // TODO: fail gracefully before growing to multi-MB buffers
                return pagedLines(file, position, signedDesiredLineCount, lines, bufferSize);
            }
        }
                
        // trim unneeded lines
        while(signedDesiredLineCount>0 && startPosition+lineStarts.getFirst()<position) {
            // discard lines starting before desired position
            lineStarts.removeFirst(); 
        }
        while(lineStarts.size()>desiredLineCount+1) {
            if (signedDesiredLineCount < 0 && (startPosition+lineStarts.get(1) <= position) ) { 
                // discard from front until reach line containing target position
                lineStarts.removeFirst();
            } else {
                lineStarts.removeLast();
            }
        }
        int firstLine =  lineStarts.getFirst();
        int partialLine =  lineStarts.getLast(); 
        LongRange range = new LongRange(startPosition + firstLine, startPosition + partialLine); 
        List<String> foundLines = 
            IOUtils.readLines(new ByteArrayInputStream(buf,firstLine,partialLine-firstLine));

        if(foundFullLines<desiredLineCount && signedDesiredLineCount < 0 && startPosition > 0) {
            // if needed and reading backward, read more lines from earlier
            range = expandRange(
                        range,
                        pagedLines(file, 
                                   range.getMinimumLong()-1, 
                                   signedDesiredLineCount+foundFullLines, 
                                   lines, 
                                   bufferSize/foundFullLines));
            
        }
        
        lines.addAll(foundLines); 
        
        if(signedDesiredLineCount < 0 && range.getMaximumLong() < position) {
            // did not get line containining start position
            range = expandRange(
                        range,
                        pagedLines(file,
                                   partialLine,
                                   1,
                                   lines,
                                   bufferSize/foundFullLines));
        }
        
        if(signedDesiredLineCount > 0 && foundFullLines < desiredLineCount && range.getMaximumLong() < fileEnd) {
            // need more forward lines
            range = expandRange(
                    range,
                    pagedLines(file,
                               range.getMaximumLong(),
                               desiredLineCount - foundFullLines,
                               lines,
                               bufferSize/foundFullLines));
        }
        
        return range; 
    }

    public static LongRange expandRange(LongRange range1, LongRange range2) {
        return new LongRange(Math.min(range1.getMinimumLong(), range2.getMinimumLong()),
                             Math.max(range1.getMaximumLong(), range2.getMaximumLong()));
        
    }

    public static LongRange pagedLines(File file, long position, int signedDesiredLongCount, List<String> lines) throws IOException {
        return pagedLines(file, position, signedDesiredLongCount, lines, 0);
    }
    
    /**
     * Delete the file now -- but in the event of failure, keep trying
     * in the future. 
     * 
     * VERY IMPORTANT: Do not use with any file whose name/path may be 
     * reused, because the lagged delete could then wind up deleting the
     * newer file. Essentially, only to be used with uniquely-named temp
     * files. 
     * 
     * Necessary because some platforms (looking at you, 
     * JVM-on-Windows) will have deletes fail because of things like 
     * file-mapped buffers remaining, and there's no explicit way to 
     * unmap a buffer. (See 6-year-old Sun-stumping Java bug
     * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4724038 )
     * We just have to wait and retry. 
     * 
     * (Why not just File.deleteOnExit? There could be an arbitrary, 
     * unbounded number of files in such a situation, that are only 
     * deletable a few seconds or minutes after our first attempt.
     * Waiting for JVM exist could mean disk exhaustion. It's also
     * unclear if the native FS class implementations of deleteOnExit
     * use RAM per pending file.)
     * 
     * @param fileToDelete
     */
    public static synchronized void deleteSoonerOrLater(File fileToDelete) {
        pendingDeletes.add(fileToDelete);
        // if things are getting out of hand, force gc/finalization
        if(pendingDeletes.size()>50) {
            LOGGER.warning(">50 pending Files to delete; forcing gc/finalization");
            System.gc();
            System.runFinalization();
        }
        // try all pendingDeletes
        Iterator<File> iter = pendingDeletes.listIterator();
        while(iter.hasNext()) {
            File pending = iter.next(); 
            if(pending.delete()) {
                iter.remove();
            }
        }
        // if things are still out of hand, complain loudly
        if(pendingDeletes.size()>50) {
            LOGGER.severe(">50 pending Files to delete even after gc/finalization");
        }
    }
    static LinkedList<File> pendingDeletes = new LinkedList<File>();

    /**
     * Read the entire stream to EOF into the passed file.
     * Closes <code>is</code> when done or if an exception.
     * @param is Stream to read.
     * @param toFile File to write to.
     * @throws IOException 
     */
    public static long readFullyToFile(InputStream is, File toFile)
            throws IOException {
        OutputStream os = org.apache.commons.io.FileUtils.openOutputStream(toFile); 
        try {
            return IOUtils.copyLarge(is, os);
        } finally {
            IOUtils.closeQuietly(os); 
            IOUtils.closeQuietly(is);
        }
    }

    /**
     * Ensure writeable directory.
     *
     * If doesn't exist, we attempt creation.
     *
     * @param dir Directory to test for exitence and is writeable.
     *
     * @return The passed <code>dir</code>.
     *
     * @exception IOException If passed directory does not exist and is not
     * createable, or directory is not writeable or is not a directory.
     */
    public static File ensureWriteableDirectory(String dir)
    throws IOException {
        return FileUtils.ensureWriteableDirectory(new File(dir));
    }

    /**
     * Ensure writeable directories.
     *
     * If doesn't exist, we attempt creation.
     *
     * @param dirs List of Files to test.
     *
     * @return The passed <code>dirs</code>.
     *
     * @exception IOException If passed directory does not exist and is not
     * createable, or directory is not writeable or is not a directory.
     */
    public static List<File> ensureWriteableDirectory(List<File> dirs)
    throws IOException {
        for (Iterator<File> i = dirs.iterator(); i.hasNext();) {
             FileUtils.ensureWriteableDirectory(i.next());
        }
        return dirs;
    }

    /**
     * Ensure writeable directory.
     *
     * If doesn't exist, we attempt creation.
     *
     * @param dir Directory to test for exitence and is writeable.
     *
     * @return The passed <code>dir</code>.
     *
     * @exception IOException If passed directory does not exist and is not
     * createable, or directory is not writeable or is not a directory.
     */
    public static File ensureWriteableDirectory(File dir)
    throws IOException {
        if (!dir.exists()) {
            boolean success = dir.mkdirs();
            if (!success) {
                throw new IOException("Failed to create directory: " + dir);
            }
        } else {
            if (!dir.canWrite()) {
                throw new IOException("Dir " + dir.getAbsolutePath() +
                    " not writeable.");
            } else if (!dir.isDirectory()) {
                throw new IOException("Dir " + dir.getAbsolutePath() +
                    " is not a directory.");
            }
        }
    
        return dir;
    } 

    public static File tryToCanonicalize(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file;
        }
    }
}