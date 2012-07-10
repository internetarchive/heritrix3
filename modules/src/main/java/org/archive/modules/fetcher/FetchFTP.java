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
package org.archive.modules.fetcher;

import static org.archive.modules.CoreAttributeConstants.A_FTP_CONTROL_CONVERSATION;
import static org.archive.modules.CoreAttributeConstants.A_FTP_FETCH_STATUS;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.SocketFactory;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPCommand;
import org.archive.io.RecordingInputStream;
import org.archive.io.ReplayCharSequence;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.Link;
import org.archive.modules.extractor.LinkContext;
import org.archive.net.ClientFTP;
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
    @SuppressWarnings("unused")
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
     * Whether or not to perform an on-the-fly digest hash of retrieved
     * content-bodies.
     */
    {
        setDigestContent(true);
    }
    public boolean getDigestContent() {
        return (Boolean) kp.get("digestContent");
    }
    public void setDigestContent(boolean digest) {
        kp.put("digestContent",digest);
    }
 
    /**
     * Which algorithm (for example MD5 or SHA-1) to use to perform an
     * on-the-fly digest hash of retrieved content-bodies.
     */
    protected String digestAlgorithm = "sha1"; 
    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }
    public void setDigestAlgorithm(String digestAlgorithm) {
        this.digestAlgorithm = digestAlgorithm;
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
    public void setTimeoutSeconds(int timeout) {
        kp.put("timeoutSeconds",timeout);
    }

    /**
     * If the socket is unresponsive for this number of milliseconds, give up.
     * Set to zero for no timeout (Not. recommended. Could hang a thread on an
     * unresponsive server). This timeout is used timing out socket opens and
     * for timing out each socket read. Make sure this value is &lt;
     * {@link #TIMEOUT_SECONDS} for optimal configuration: ensures at least one
     * retry read.
     */
    {
        setSoTimeoutMs(20*1000); // 20 seconds
    }
    public int getSoTimeoutMs() {
        return (Integer) kp.get("soTimeoutMs");
    }
    public void setSoTimeoutMs(int timeout) {
        kp.put("soTimeoutMs",timeout);
    }
     
    /**
     * Constructs a new <code>FetchFTP</code>.
     */
    public FetchFTP() {
    }
    
    @Override
    protected boolean shouldProcess(CrawlURI curi) {
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
    protected void innerProcess(CrawlURI curi) throws InterruptedException {
        curi.setFetchBeginTime(System.currentTimeMillis());
        ClientFTP client = new ClientFTP();
        Recorder recorder = curi.getRecorder();
        
        try {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("attempting to fetch ftp uri: " + curi);
            }
            fetch(curi, client, recorder);
        } catch (IOException e) {
            if (logger.isLoggable(Level.INFO)) {
                logger.info(curi + ": " + e);
            }
            curi.getNonFatalFailures().add(e);
            curi.setFetchStatus(FetchStatusCodes.S_CONNECT_FAILED);
        } finally {
            disconnect(client);
            curi.setFetchCompletedTime(System.currentTimeMillis());
            curi.getData().put(A_FTP_CONTROL_CONVERSATION, client.getControlConversation());
        }
    }

    /**
     * A {@link SocketFactory} much like {@link javax.net.DefaultSocketFactory},
     * except that the createSocket() methods that open connections support a
     * connect timeout.
     */
    public class SocketFactoryWithTimeout extends SocketFactory {
        protected int connectTimeoutMs = 0;
        
        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public Socket createSocket() {
            return new Socket();
        }

        public Socket createSocket(String host, int port) throws IOException,
                UnknownHostException {
            Socket sock = createSocket();
            sock.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            return sock;
        }

        public Socket createSocket(InetAddress host, int port)
                throws IOException {
            Socket sock = createSocket();
            sock.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            return sock;
        }

        public Socket createSocket(String host, int port,
                InetAddress localHost, int localPort) throws IOException,
                UnknownHostException {
            Socket sock = createSocket();
            sock.bind(new InetSocketAddress(localHost, localPort));
            sock.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            return sock;
        }

        public Socket createSocket(InetAddress address, int port,
                InetAddress localAddress, int localPort) throws IOException {
            Socket sock = createSocket();
            sock.bind(new InetSocketAddress(localAddress, localPort));
            sock.connect(new InetSocketAddress(address, port), connectTimeoutMs);
            return sock;
        }         
        
    }

    protected SocketFactoryWithTimeout socketFactory;

    /**
     * Fetches a document from an FTP server.
     * 
     * @param curi      the URI of the document to fetch
     * @param client    the FTPClient to use for the fetch
     * @param recorder  the recorder to preserve the document in
     * @throws IOException  if a network or protocol error occurs
     * @throws InterruptedException  if the thread is interrupted
     */
    private void fetch(CrawlURI curi, ClientFTP client, Recorder recorder) 
    throws IOException, InterruptedException {
        // Connect to the FTP server.
        UURI uuri = curi.getUURI();
        int port = uuri.getPort();
        if (port == -1) {
            port = 21;
        }

        if (socketFactory == null) {
            socketFactory = new SocketFactoryWithTimeout();
        }
        socketFactory.setConnectTimeoutMs(getSoTimeoutMs());
        client.setSocketFactory(socketFactory);
        client.setConnectTimeout(getSoTimeoutMs());
        client.setDefaultTimeout(getSoTimeoutMs());
        client.setDataTimeout(getSoTimeoutMs());
        
        client.connect(uuri.getHost(), port);
        
        client.setSoTimeout(getSoTimeoutMs());  // must be after connect()
        
        // Authenticate.
        String[] auth = getAuth(curi);
        client.login(auth[0], auth[1]);
        
        // The given resource may or may not be a directory.
        // To figure out which is which, execute a CD command to
        // the UURI's path.  If CD works, it's a directory.
        boolean isDirectory = client.changeWorkingDirectory(uuri.getPath());

        // Get a data socket.  This will either be the result of a NLST
        // command for a directory, or a RETR command for a file.
        int command;
        String path;
        if (isDirectory) {
            curi.getAnnotations().add("ftpDirectoryList");
            command = FTPCommand.NLST;
            client.setFileType(FTP.ASCII_FILE_TYPE);
            path = ".";
        } else { 
            command = FTPCommand.RETR;
            client.setFileType(FTP.BINARY_FILE_TYPE);
            path = uuri.getPath();
        }

        client.enterLocalPassiveMode();
        Socket socket = null;

        try {
            socket = client.openDataConnection(command, path);

            // if "227 Entering Passive Mode" these will get reset later
            curi.setFetchStatus(client.getReplyCode());
            curi.getData().put(A_FTP_FETCH_STATUS, client.getReplyStrings()[0]);
        } catch (IOException e) {
            // try it again, see AbstractFrontier.needsRetrying()
            curi.setFetchStatus(FetchStatusCodes.S_CONNECT_LOST);
        }

        // Save the streams in the CURI, where downstream processors
        // expect to find them.
        if (socket != null) {
            if (socket.getSoTimeout() != getSoTimeoutMs()) {
                logger.warning("data socket timeout " + socket.getSoTimeout() + "ms is not expected value " + getSoTimeoutMs() + "ms");
            }
            // Shall we get a digest on the content downloaded?
            boolean digestContent = getDigestContent();
            String algorithm = null; 
            if (digestContent) {
                algorithm = getDigestAlgorithm();
                recorder.getRecordedInput().setDigest(algorithm);
                recorder.getRecordedInput().startDigest();
            } else {
                // clear
                recorder.getRecordedInput().setDigest((MessageDigest)null);
            }
                    
            try {
                saveToRecorder(curi, socket, recorder);
            } finally {
                recorder.close();
                client.closeDataConnection(); // does socket.close()
                curi.setContentSize(recorder.getRecordedInput().getSize());

                // "226 Transfer complete."
                client.getReply();
                curi.setFetchStatus(client.getReplyCode());
                curi.getData().put(A_FTP_FETCH_STATUS, client.getReplyStrings()[0]);
                
                if (isDirectory) {
                    curi.setContentType("text/plain");
                } else {
                    curi.setContentType("application/octet-stream");
                }
                
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("read " + recorder.getRecordedInput().getSize()
                            + " bytes from ftp data socket");
                }

                if (digestContent) {
                    curi.setContentDigest(algorithm,
                        recorder.getRecordedInput().getDigestValue());
                }
            }

            if (isDirectory) {
                extract(curi, recorder);
            }
        } else {
            // no data - without this, content size is -1
            curi.setContentSize(0);
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
    private void saveToRecorder(CrawlURI curi,
            Socket socket, Recorder recorder) 
    throws IOException, InterruptedException {
        recorder.inputWrap(socket.getInputStream());
        recorder.outputWrap(socket.getOutputStream());
        recorder.markContentBegin();

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
    private void extract(CrawlURI curi, Recorder recorder) {
        if (!getExtractFromDirs()) {
            return;
        }
        
        ReplayCharSequence seq = null;
        try {
            seq = recorder.getContentReplayCharSequence();
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
    private void extract(CrawlURI curi, ReplayCharSequence dir) {
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
    private void addExtracted(CrawlURI curi, String file) {
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
    private void addParent(CrawlURI curi) {
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
    private String[] getAuth(CrawlURI curi) {
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
            client.logout();
        } catch (IOException e) {
        }

        if (client.isConnected()) try {
            client.disconnect();
        } catch (IOException e) {
            logger.warning("Could not disconnect from FTP client: " + e);
        }
    }
}
