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

package org.archive.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;
import org.archive.util.ArchiveUtils;
import org.archive.util.FileUtils;


/**
 * Replays the bytes recorded from a RecordingInputStream or
 * RecordingOutputStream.
 *
 * This InputStream supports mark and reset.
 *
 * @author gojomo
 */
public class ReplayInputStream extends SeekInputStream
{
    private static final int DEFAULT_BUFFER_SIZE = 256*1024; // 256KiB
    private BufferedSeekInputStream diskStream;
    private byte[] buffer;
    private long position;

    /**
     * Total size of stream content.
     *
     * Size of data to replay.
     */
    private long size = -1;

    /**
     * Where the response body starts, if marked
     */
    protected long responseBodyStart = -1;


    /**
     * Constructor.
     *
     * @param buffer Buffer to read from.
     * @param size Size of data to replay.
     * @param responseBodyStart Start of the response body.
     * @param backingFilename Backing file that sits behind the buffer.  If
     * <code>size<code> > than buffer then we go to backing file to read
     * data that is beyond buffer.length.
     *
     * @throws IOException If we fail to open an input stream on
     * backing file.
     */
    public ReplayInputStream(byte[] buffer, long size, long responseBodyStart,
            String backingFilename)
        throws IOException
    {
        this(buffer, size, backingFilename);
        this.responseBodyStart = responseBodyStart;
    }

    /**
     * Constructor.
     *
     * @param buffer Buffer to read from.
     * @param size Size of data to replay.
     * @param backingFilename Backing file that sits behind the buffer.  If
     * <code>size<code> > than buffer then we go to backing file to read
     * data that is beyond buffer.length.
     * @throws IOException If we fail to open an input stream on
     * backing file.
     */
    public ReplayInputStream(byte[] buffer, long size, String backingFilename)
        throws IOException
    {
        this.buffer = buffer;
        this.size = size;
        if (size > buffer.length) {
            setupDiskStream(new File(backingFilename));
        }
    }

    protected void setupDiskStream(File backingFile) throws IOException {
        RandomAccessInputStream rais = new RandomAccessInputStream(backingFile); 
        diskStream = new BufferedSeekInputStream(rais, 4096);
    }

    File backingFile; 
    
    /**
     * Create a ReplayInputStream from the given source stream. Requires 
     * reading the entire stream (and possibly overflowing to a temporary
     * file). Primary reason for doing so would be to have a repositionable
     * version of the original stream's contents.
     * 
     * If created via this constructor, use the destroy() method to ensure
     * prompt deletion of any associated tmp file when done. 
     * 
     * @param fillStream
     * @throws IOException
     */
    public ReplayInputStream(InputStream fillStream) throws IOException {
        this.buffer = new byte[DEFAULT_BUFFER_SIZE];
        long count = ArchiveUtils.readFully(fillStream, buffer);
        if(fillStream.available()>0) {
            this.backingFile = File.createTempFile("tid"+Thread.currentThread().getId(), "ris");
            count += FileUtils.readFullyToFile(fillStream, backingFile);
            setupDiskStream(backingFile);
        }
        this.size = count; 
    }
    
    /**
     * Close & destroy any internally-generated temporary files. 
     */
    public void destroy() {
        IOUtils.closeQuietly(this); 
        if(backingFile!=null) {
            FileUtils.deleteSoonerOrLater(backingFile); 
        }
    }

    public long setToResponseBodyStart() throws IOException {
        position(responseBodyStart);
        return this.position;
    }
    

    /* (non-Javadoc)
     * @see java.io.InputStream#read()
     */
    public int read() throws IOException {
        if (position == size) {
            return -1; // EOF
        }
        if (position < buffer.length) {
            // Convert to unsigned int.
            int c = buffer[(int) position] & 0xFF;
            position++;
            return c;
        }
        int c = diskStream.read();
        if (c >= 0) {
            position++;
        }
        return c;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.io.InputStream#read(byte[], int, int)
     */
    public int read(byte[] b, int off, int len) throws IOException {
        if (position == size) {
            return -1; // EOF
        }
        if (position < buffer.length) {
            int toCopy = (int)Math.min(size - position,
                Math.min(len, buffer.length - position));
            System.arraycopy(buffer, (int)position, b, off, toCopy);
            if (toCopy > 0) {
                position += toCopy;
            }
            return toCopy;
        }
        // into disk zone
        int read = diskStream.read(b,off,len);
        if(read>0) {
            position += read;
        }
        return read;
    }

    public void readFullyTo(OutputStream os) throws IOException {
        byte[] buf = new byte[4096];
        int c = read(buf);
        while (c != -1) {
            os.write(buf,0,c);
            c = read(buf);
        }
    }
    
    /*
     * Like 'readFullyTo', but only reads the header-part.
     * Starts from the beginning each time it is called.
     */
    public void readHeaderTo(OutputStream os) throws IOException {
        position = 0;
        byte[] buf = new byte[(int)responseBodyStart];
        int c = read(buf,0,buf.length);
        if(c != -1) {
            os.write(buf,0,c);
        }
    }

    /*
     * Like 'readFullyTo', but only reads the content-part.
     */
    public void readContentTo(OutputStream os) throws IOException {
        setToResponseBodyStart();
        byte[] buf = new byte[4096];
        int c = read(buf);
        while (c != -1) {
            os.write(buf,0,c);
            c = read(buf);            
        }
    }
    
    /**
     * Convenience method to copy content out to target stream. 
     * @param os stream to write content to
     * @param maxSize maximum count of bytes to copy
     * @throws IOException
     */
    public void readContentTo(OutputStream os, long maxSize) throws IOException {
        setToResponseBodyStart();
        byte[] buf = new byte[4096];
        int c = read(buf);
        long tot = 0;
        while (c != -1 && tot < maxSize) {
            os.write(buf,0,c);
            c = read(buf);
            tot += c;
        }
    }

    /* (non-Javadoc)
     * @see java.io.InputStream#close()
     */
    public void close() throws IOException {
        super.close();
        if(diskStream != null) {
            diskStream.close();
        }
    }

    /**
     * Total size of stream content.
     * @return Returns the size.
     */
    public long getSize()
    {
        return size;
    }
    
    /**
     * Total size of header.
     * @return the size of the header.
     */
    public long getHeaderSize()
    {
        return responseBodyStart;
    }
    
    /**
     * Total size of content.
     * @return the size of the content.
     */
    public long getContentSize()
    {
        return size - responseBodyStart;
    }

    /**
     * @return Amount THEORETICALLY remaining (TODO: Its not theoretical
     * seemingly.  The class implemetentation depends on it being exact).
     */
    public long remaining() {
        return size - position;
    }
    

    /**
     * Reposition the stream.
     * 
     * @param p  the new position for this stream
     * @throws IOException  if an IO error occurs
     */
    public void position(long p) throws IOException {
        if (p < 0) {
            throw new IOException("Negative seek offset.");
        }
        if (p > size) {
            throw new IOException("Desired position exceeds size.");
        }
        if (p < buffer.length) {
            // Only seek file if necessary
            if (position > buffer.length) {
                diskStream.position(0);
            }
        } else {
            diskStream.position(p - buffer.length);
        }
        this.position = p;
    }
    
    
    public long position() throws IOException {
        return position;
    }
    
    // package private
    byte[] getBuffer() {
        return buffer;
    }
}
