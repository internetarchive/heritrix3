/* FetchFTP.java
 *
 * $Id$
 *
 * Created on Jun 5, 2003
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
package org.archive.modules.fetcher;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.URLEncoder;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.net.ftp.FTPCommand;
import org.archive.io.RecordingInputStream;
import org.archive.io.ReplayCharSequence;
import org.archive.modules.Processor;
import org.archive.modules.ProcessorURI;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.Link;
import org.archive.modules.extractor.LinkContext;
import org.archive.net.ClientFTP;
import org.archive.net.FTPException;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;


/**
 * Fetches documents and directory listings using FTP.  This class will also
 * try to extract FTP "links" from directory listings.  For this class to
 * archive a directory listing, the remote FTP server must support the NLIST
 * command.  Most modern FTP servers should.
 * 
 * @author pjack
 *
 */
public class FetchFTP extends Processor  {
    private static final long serialVersionUID = 1L;

    /** Logger for this class. */
    private static Logger logger = Logger.getLogger(FetchFTP.class.getName());

    /** Pattern for matching directory entries. */
    private static Pattern DIR = 
     Pattern.compile("(.+)$", Pattern.MULTILINE);

    
    /**
     * The username to send to FTP servers. By convention, the default value of
     * "anonymous" is used for publicly available FTP sites.
     */
    {
        setUsername("anonymous");
    }
    public String getUsername() {
        return (String) kp.get("username");
    }
    public void setUsername(String username) {
        kp.put("username",username);
    }

    /**
     * The password to send to FTP servers. By convention, anonymous users send
     * their email address in this field.
     */
    {
        setPassword("password");
    }
    public String getPassword() {
        return (String) kp.get("password");
    }
    public void setPassword(String pw) {
        kp.put("password",pw);
    }

    /**
     * Set to true to extract further URIs from FTP directories. Default is
     * true.
     */
    {
        setExtractFromDirs(true);
    }
    /**
     * Returns the <code>extract.from.dirs</code> attribute for this
     * <code>FetchFTP</code> and the given curi.
     * 
     * @param curi  the curi whose attribute to return
     * @return  that curi's <code>extract.from.dirs</code>
     */
    public boolean getExtractFromDirs() {
        return (Boolean) kp.get("extractFromDirs");
    }
    public void setExtractFromDirs(boolean extractFromDirs) {
        kp.put("extractFromDirs",extractFromDirs);
    }
    
    /**
     * Set to true to extract the parent URI from all FTP URIs. Default is true.
     */
    {
        setExtractParent(true);
    }
    /**
     * Returns the <code>extract.parent</code> attribute for this
     * <code>FetchFTP</code> and the given curi.
     * 
     * @param curi  the curi whose attribute to return
     * @return  that curi's <code>extract-parent</code>
     */
    public boolean getExtractParent() {
        return (Boolean) kp.get("extractParent");
    }
    public void setExtractParent(boolean extractParent) {
        kp.put("extractParent",extractParent);
    }

    /**
     * Maximum length in bytes to fetch. Fetch is truncated at this length. A
     * value of 0 means no limit.
     */
    {
        setMaxLengthBytes(0L); // no limit
    }
    public long getMaxLengthBytes() {
        return (Long) kp.get("maxLengthBytes");
    }
    public void setMaxLengthBytes(long timeout) {
        kp.put("maxLengthBytes",timeout);
    }
    
    /**
     * The maximum KB/sec to use when fetching data from a server. The default
     * of 0 means no maximum.
     */
    {
        setMaxFetchKBSec(0); // no limit
    }
    public int getMaxFetchKBSec() {
        return (Integer) kp.get("maxFetchKBSec");
    }
    public void setMaxFetchKBSec(int rate) {
        kp.put("maxFetchKBSec",rate);
    }
    
    /**
     * If the fetch is not completed in this number of seconds, give up (and
     * retry later).
     */
    {
         setTimeoutSeconds(20*60); // 20 minutes
     }
     public int getTimeoutSeconds() {
         return (Integer) kp.get("timeoutSeconds");
     }
     public void setTimeoutSeconds(Integer timeout) {
         kp.put("timeoutSeconds",timeout);
     }

    /**
     * Constructs a new <code>FetchFTP</code>.
     */
    public FetchFTP() {
    }

    
    @Override
    protected boolean shouldProcess(ProcessorURI curi) {
        if (!curi.getUURI().getScheme().equals("ftp")) {
            return false;
        }

        return true;
    }

    /**
     * Processes the given URI.  If the given URI is not an FTP URI, then
     * this method does nothing.  Otherwise an attempt is made to connect
     * to the FTP server.
     * 
     * <p>If the connection is successful, an attempt will be made to CD to 
     * the path specified in the URI.  If the remote CD command succeeds, 
     * then it is assumed that the URI represents a directory.  If the
     * CD command fails, then it is assumed that the URI represents
     * a file.
     * 
     * <p>For directories, the directory listing will be fetched using
     * the FTP LIST command, and saved to the HttpRecorder.  If the
     * <code>extract.from.dirs</code> attribute is set to true, then
     * the files in the fetched list will be added to the curi as
     * extracted FTP links.  (It was easier to do that here, rather
     * than writing a separate FTPExtractor.)
     * 
     * <p>For files, the file will be fetched using the FTP RETR
     * command, and saved to the HttpRecorder.
     * 
     * <p>All file transfers (including directory listings) occur using
     * Binary mode transfer.  Also, the local passive transfer mode
     * is always used, to play well with firewalls.
     * 
     * @param curi  the curi to process
     * @throws InterruptedException  if the thread is interrupted during
     *   processing
     */
    @Override
    protected void innerProcess(ProcessorURI curi) throws InterruptedException {
        curi.setFetchBeginTime(System.currentTimeMillis());
        Recorder recorder = curi.getRecorder();
        ClientFTP client = new ClientFTP();
        
        try {
            fetch(curi, client, recorder);
        } catch (FTPException e) {
            logger.log(Level.SEVERE, "FTP server reported problem.", e);
            curi.setFetchStatus(e.getReplyCode());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "IO Error during FTP fetch.", e);
            curi.setFetchStatus(FetchStatusCodes.S_CONNECT_LOST);
        } finally {
            disconnect(client);
            curi.setContentSize(recorder.getRecordedInput().getSize());
            curi.setFetchCompletedTime(System.currentTimeMillis());
        }
    }


    /**
     * Fetches a document from an FTP server.
     * 
     * @param curi      the URI of the document to fetch
     * @param client    the FTPClient to use for the fetch
     * @param recorder  the recorder to preserve the document in
     * @throws IOException  if a network or protocol error occurs
     * @throws InterruptedException  if the thread is interrupted
     */
    private void fetch(ProcessorURI curi, ClientFTP client, Recorder recorder) 
    throws IOException, InterruptedException {
        // Connect to the FTP server.
        UURI uuri = curi.getUURI();
        int port = uuri.getPort();
        if (port == -1) {
            port = 21;
        }
        client.connectStrict(uuri.getHost(), port);
        
        // Authenticate.
        String[] auth = getAuth(curi);
        client.loginStrict(auth[0], auth[1]);
        
        // The given resource may or may not be a directory.
        // To figure out which is which, execute a CD command to
        // the UURI's path.  If CD works, it's a directory.
        boolean dir = client.changeWorkingDirectory(uuri.getPath());
        if (dir) {
            curi.setContentType("text/plain");
        }
        
        // TODO: A future version of this class could use the system string to
        // set up custom directory parsing if the FTP server doesn't support 
        // the nlist command.
        if (logger.isLoggable(Level.FINE)) {
            String system = client.getSystemName();
            logger.fine(system);
        }
        
        // Get a data socket.  This will either be the result of a NLIST
        // command for a directory, or a RETR command for a file.
        int command = dir ? FTPCommand.NLST : FTPCommand.RETR;
        String path = dir ? "." : uuri.getPath();
        client.enterLocalPassiveMode();
        client.setBinary();
        Socket socket = client.openDataConnection(command, path);
        curi.setFetchStatus(client.getReplyCode());

        // Save the streams in the CURI, where downstream processors
        // expect to find them.
        try {
            saveToRecorder(curi, socket, recorder);
        } finally {
            recorder.close();
            close(socket);
        }

        curi.setFetchStatus(200);
        if (dir) {
            extract(curi, recorder);
        }
        addParent(curi);
    }
    
    
    /**
     * Saves the given socket to the given recorder.
     * 
     * @param curi      the curi that owns the recorder
     * @param socket    the socket whose streams to save
     * @param recorder  the recorder to save them to
     * @throws IOException  if a network or file error occurs
     * @throws InterruptedException  if the thread is interrupted
     */
    private void saveToRecorder(ProcessorURI curi,
            Socket socket, Recorder recorder) 
    throws IOException, InterruptedException {
        recorder.markContentBegin();
        recorder.inputWrap(socket.getInputStream());
        recorder.outputWrap(socket.getOutputStream());

        // Read the remote file/dir listing in its entirety.
        long softMax = 0;
        long hardMax = getMaxLengthBytes();
        long timeout = (long)getTimeoutSeconds() * 1000L;
        int maxRate = getMaxFetchKBSec();
        RecordingInputStream input = recorder.getRecordedInput();
        input.setLimits(hardMax, timeout, maxRate); 
        input.readFullyOrUntil(softMax);
    }
    
    
    /**
     * Extract FTP links in a directory listing.
     * The listing must already be saved to the given recorder.
     * 
     * @param curi      The curi to save extracted links to
     * @param recorder  The recorder containing the directory listing
     */
    private void extract(ProcessorURI curi, Recorder recorder) {
        if (!getExtractFromDirs()) {
            return;
        }
        
        ReplayCharSequence seq = null;
        try {
            seq = recorder.getReplayCharSequence();
            extract(curi, seq);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "IO error during extraction.", e);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "IO error during extraction.", e);
        } finally {
            close(seq);
        }
    }
    
    
    /**
     * Extracts FTP links in a directory listing.
     * 
     * @param curi  The curi to save extracted links to
     * @param dir   The directory listing to extract links from
     * @throws URIException  if an extracted link is invalid
     */
    private void extract(ProcessorURI curi, ReplayCharSequence dir) {
        logger.log(Level.FINEST, "Extracting URIs from FTP directory.");
        Matcher matcher = DIR.matcher(dir);
        while (matcher.find()) {
            String file = matcher.group(1);
            addExtracted(curi, file);
        }
    }


    /**
     * Adds an extracted filename to the curi.  A new URI will be formed
     * by taking the given curi (which should represent the directory the
     * file lives in) and appending the file.
     * 
     * @param curi  the curi to store the discovered link in
     * @param file  the filename of the discovered link
     */
    private void addExtracted(ProcessorURI curi, String file) {
        try {
            file = URLEncoder.encode(file, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Found " + file);
        }
        String base = curi.toString();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        try {
            UURI n = UURIFactory.getInstance(base + "/" + file);
            Link link = new Link(curi.getUURI(), n, LinkContext.NAVLINK_MISC, Hop.NAVLINK);
            curi.getOutLinks().add(link);
        } catch (URIException e) {
            logger.log(Level.WARNING, "URI error during extraction.", e);            
        }
    }
    

    /**
     * Extracts the parent URI from the given curi, then adds that parent
     * URI as a discovered link to the curi. 
     * 
     * <p>If the <code>extract-parent</code> attribute is false, then this
     * method does nothing.  Also, if the path of the given curi is 
     * <code>/</code>, then this method does nothing.
     * 
     * <p>Otherwise the parent is determined by eliminated the lowest part
     * of the URI's path.  Eg, the parent of <code>ftp://foo.com/one/two</code>
     * is <code>ftp://foo.com/one</code>.
     * 
     * @param curi  the curi whose parent to add
     */
    private void addParent(ProcessorURI curi) {
        if (!getExtractParent()) {
            return;
        }
        UURI uuri = curi.getUURI();
        try {
            if (uuri.getPath().equals("/")) {
                // There's no parent to add.
                return;
            }
            String scheme = uuri.getScheme();
            String auth = uuri.getEscapedAuthority();
            String path = uuri.getEscapedCurrentHierPath();
            UURI parent = UURIFactory.getInstance(scheme + "://" + auth + path);

            Link link = new Link(uuri, parent, LinkContext.NAVLINK_MISC, 
                    Hop.NAVLINK);
            curi.getOutLinks().add(link);
        } catch (URIException e) {
            logger.log(Level.WARNING, "URI error during extraction.", e);
        }
    }

    /**
     * Returns the username and password for the given URI.  This method
     * always returns an array of length 2.  The first element in the returned
     * array is the username for the URI, and the second element is the
     * password.
     * 
     * <p>If the URI itself contains the username and password (i.e., it looks
     * like <code>ftp://username:password@host/path</code>) then that username
     * and password are returned.
     * 
     * <p>Otherwise the settings system is probed for the <code>username</code>
     * and <code>password</code> attributes for this <code>FTPFetch</code>
     * and the given <code>curi</code> context.  The values of those 
     * attributes are then returned.
     * 
     * @param curi  the curi whose username and password to return
     * @return  an array containing the username and password
     */
    private String[] getAuth(ProcessorURI curi) {
        String[] result = new String[2];
        UURI uuri = curi.getUURI();
        String userinfo;
        try {
            userinfo = uuri.getUserinfo();
        } catch (URIException e) {
            assert false;
            logger.finest("getUserinfo raised URIException.");
            userinfo = null;
        }
        if (userinfo != null) {
            int p = userinfo.indexOf(':');
            if (p > 0) {
                result[0] = userinfo.substring(0,p);
                result[1] = userinfo.substring(p + 1);
                return result;
            }
        }
        result[0] = getUsername();
        result[1] = getPassword();
        return result;
    }
    
    


    /**
     * Quietly closes the given socket.
     * 
     * @param socket  the socket to close
     */
    private static void close(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            logger.log(Level.WARNING, "IO error closing socket.", e);
        }
    }


    /**
     * Quietly closes the given sequence.
     * If an IOException is raised, this method logs it as a warning.
     * 
     * @param seq  the sequence to close
     */
    private static void close(ReplayCharSequence seq) {
        if (seq == null) {
            return;
        }
        try {
            seq.close();
        } catch (IOException e) {
            logger.log(Level.WARNING, "IO error closing ReplayCharSequence.", 
             e);
        }
    }

    
    /**
     * Quietly disconnects from the given FTP client.
     * If an IOException is raised, this method logs it as a warning.
     * 
     * @param client  the client to disconnect
     */
    private static void disconnect(ClientFTP client) {
        if (client.isConnected()) try {
            client.disconnect();
        } catch (IOException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Could not disconnect from FTP client: " 
                 + e.getMessage());
            }
        }        
    }
}
