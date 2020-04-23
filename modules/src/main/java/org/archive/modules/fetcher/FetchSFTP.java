package org.archive.modules.fetcher;

import static org.archive.modules.CoreAttributeConstants.A_FTP_CONTROL_CONVERSATION;
import static org.archive.modules.CoreAttributeConstants.A_FTP_FETCH_STATUS;
import static org.archive.modules.CoreAttributeConstants.A_RUNTIME_EXCEPTION;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.URIException;
import org.archive.io.RecordingInputStream;
import org.archive.io.ReplayCharSequence;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.LinkContext;
import org.archive.net.ClientSFTP;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

public class FetchSFTP extends Processor {

	private static Logger logger = Logger.getLogger(FetchSFTP.class.getName());

	private static Pattern DIR = Pattern.compile("(.+)$", 8);

	/**
	 * The username to send to SFTP servers. By convention, the default value of
	 * "anonymous" is used for publicly available SFTP sites.
	 */
	{
		setUsername("anonymous");
	}

	public String getUsername() {
		return (String) kp.get("username");
	}

	public void setUsername(String username) {
		kp.put("username", username);
	}

	/**
	 * The password to send to SFTP servers. By convention, anonymous users send
	 * their email address in this field.
	 */
	{
		setPassword("password");
	}

	public String getPassword() {
		return (String) kp.get("password");
	}

	public void setPassword(String pw) {
		kp.put("password", pw);
	}

	/**
	 * Set to true to extract further URIs from SFTP directories. Default is true.
	 */
	{
		setExtractFromDirs(true);
	}

	/**
	 * Returns the <code>extract.from.dirs</code> attribute for this
	 * <code>FetchSFTP</code> and the given curi.
	 * 
	 * @return that curi's <code>extract.from.dirs</code>
	 */
	public boolean getExtractFromDirs() {
		return (Boolean) kp.get("extractFromDirs");
	}

	public void setExtractFromDirs(boolean extractFromDirs) {
		kp.put("extractFromDirs", extractFromDirs);
	}

	/**
	 * Set to true to extract the parent URI from all SFTP URIs. Default is true.
	 */
	{
		setExtractParent(true);
	}

	/**
	 * Returns the <code>extract.parent</code> attribute for this
	 * <code>FetchSFTP</code> and the given curi.
	 * 
	 * @return that curi's <code>extract-parent</code>
	 */
	public boolean getExtractParent() {
		return (Boolean) kp.get("extractParent");
	}

	public void setExtractParent(boolean extractParent) {
		kp.put("extractParent", extractParent);
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
		kp.put("digestContent", digest);
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
		kp.put("maxLengthBytes", timeout);
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
		kp.put("maxFetchKBSec", rate);
	}

	/**
	 * If the fetch is not completed in this number of seconds, give up (and
	 * retry later).
	 */
	{
		setTimeoutSeconds(20 * 60); // 20 minutes
	}

	public int getTimeoutSeconds() {
		return (Integer) kp.get("timeoutSeconds");
	}

	public void setTimeoutSeconds(int timeout) {
		kp.put("timeoutSeconds", timeout);
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
		setSoTimeoutMs(20 * 1000); // 20 seconds
	}

	public int getSoTimeoutMs() {
		return (Integer) kp.get("soTimeoutMs");
	}

	public void setSoTimeoutMs(int timeout) {
		kp.put("soTimeoutMs", timeout);
	}

	/**
	 * Constructs a new <code>FetchSFTP</code>.
	 */
	public FetchSFTP() {
		//
	}

	@Override
	protected boolean shouldProcess(CrawlURI curi) {
		if (!curi.getUURI().getScheme().equals("sftp")) {
			return false;
		}

		return true;
	}

	/**
	 * Processes the given URI. If the given URI is not an SFTP URI, then
	 * this method does nothing. Otherwise an attempt is made to connect
	 * to the SFTP server.
	 * 
	 * <p>
	 * If the connection is successful, an attempt will be made to CD to
	 * the path specified in the URI. If the remote CD command succeeds,
	 * then it is assumed that the URI represents a directory. If the
	 * CD command fails, then it is assumed that the URI represents
	 * a file.
	 * 
	 * @param curi the curi to process
	 * @throws InterruptedException if the thread is interrupted during
	 *             processing
	 */
	@Override
	protected void innerProcess(CrawlURI curi) throws InterruptedException {
		curi.setFetchBeginTime(System.currentTimeMillis());
		ClientSFTP client = new ClientSFTP();
		Recorder recorder = curi.getRecorder();

		try {
			if (logger.isLoggable(Level.FINE)) {
				logger.fine("attempting to fetch sftp uri: " + curi);
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
	 * Fetches a document from an SFTP server.
	 * 
	 * @param curi the URI of the document to fetch
	 * @param client the SFTPClient to use for the fetch
	 * @param recorder the recorder to preserve the document in
	 * @throws IOException if a network or protocol error occurs
	 * @throws InterruptedException if the thread is interrupted
	 */
	private void fetch(CrawlURI curi, ClientSFTP client, Recorder recorder)
			throws IOException, InterruptedException {
		// Connect to the FTP server.
		UURI uuri = curi.getUURI();
		int port = uuri.getPort();
		if (port == -1) {
			port = 22;
		}

		String[] arrayOfString = getAuth(curi);
		client.connect(arrayOfString[0], uuri.getHost(), port, arrayOfString[1]);

		ChannelSftp channelSftp = null;

		try {
			channelSftp = client.openSFTPChannel();
		} catch (JSchException jSchException) {
			jSchException.printStackTrace();
			curi.getData().put(A_RUNTIME_EXCEPTION, jSchException);
		}

		boolean isDirectory = false;
		try {
			client.cd(uuri.getPath());
			isDirectory = client.isDirectory(uuri.getPath());
		} catch (SftpException sftpException) {

			try {
				client.cd("/");
			} catch (Exception exception) {
				logger.severe("cannot cd /");
				curi.getData().put(A_RUNTIME_EXCEPTION, sftpException);
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			curi.getData().put(A_RUNTIME_EXCEPTION, exception);
		}

		if (isDirectory) {
			curi.getAnnotations().add("sftpDirectoryList");
		}

		if (channelSftp != null) {

			boolean digestContent = getDigestContent();
			String algorithm = null;
			if (digestContent) {
				algorithm = getDigestAlgorithm();
				recorder.getRecordedInput().setDigest(algorithm);
				recorder.getRecordedInput().startDigest();
			} else {
				recorder.getRecordedInput().setDigest((MessageDigest) null);
			}

			try {
				if (isDirectory) {
					saveDirectoryToRecorder(curi, channelSftp, recorder);

					curi.setFetchStatus(226);
					curi.getData().put(A_FTP_FETCH_STATUS, "226 Directory send OK.");
				} else {
					saveFileToRecorder(curi, channelSftp, recorder);

					curi.setFetchStatus(226);
					curi.getData().put(A_FTP_FETCH_STATUS, "226 File send OK.");
				}
			} catch (SftpException sftpException) {
				logger.severe("error while getting " + curi + " :" + sftpException);
				curi.setFetchStatus(-3);
				curi.getData().put(A_FTP_FETCH_STATUS, "SFTP error.");
			} finally {

				recorder.close();
				client.disconnect();
				curi.setContentSize(recorder.getRecordedInput().getSize());

				if (isDirectory) {
					curi.setContentType("text/plain");
				} else {
					curi.setContentType("application/octet-stream");
				}

				if (logger.isLoggable(Level.INFO)) {
					logger.fine("read " + recorder.getRecordedInput().getSize() + " bytes from sftp data socket");
				}

				if (digestContent) {
					curi.setContentDigest(algorithm, recorder.getRecordedInput().getDigestValue());
				}
			}
			curi.getData().put(A_RUNTIME_EXCEPTION, client.getControlConversation());
			if (isDirectory) {
				extract(curi, recorder);
			}
		}

		addParent(curi);
	}

	@SuppressWarnings("unchecked")
	private void saveDirectoryToRecorder(CrawlURI paramCrawlURI, ChannelSftp paramChannelSftp, Recorder paramHttpRecorder)
			throws IOException, InterruptedException {
		try {
			Vector<LsEntry> vector = paramChannelSftp.ls(paramCrawlURI.getUURI().getPath());
			StringBuilder stringBuilder = new StringBuilder();
			for (LsEntry lsEntry : vector) {
				stringBuilder.append(lsEntry.getFilename());
				stringBuilder.append('\n');
			}
			ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(stringBuilder.toString().getBytes());

			paramCrawlURI.setRecorder(paramHttpRecorder);
			paramHttpRecorder.inputWrap(byteArrayInputStream);
			paramHttpRecorder.outputWrap(paramChannelSftp.getOutputStream());
			paramHttpRecorder.markContentBegin();

			long softMax = 0;
			long hardMax = getMaxLengthBytes();
			long timeout = (long) getTimeoutSeconds() * 1000L;
			int maxRate = getMaxFetchKBSec();

			RecordingInputStream recordingInputStream = paramHttpRecorder.getRecordedInput();
			recordingInputStream.setLimits(hardMax, timeout, maxRate);
			recordingInputStream.readFullyOrUntil(softMax);
		} catch (SftpException sftpException) {
			logger.severe("ls : " + paramCrawlURI.getUURI().getPath() + " not a path");
		}
	}

	private void saveFileToRecorder(CrawlURI paramCrawlURI, ChannelSftp paramChannelSftp, Recorder paramHttpRecorder)
			throws IOException, InterruptedException, SftpException {
		String str = new String(paramCrawlURI.getUURI().getPath());

		InputStream inputStream = paramChannelSftp.get(str);

		paramCrawlURI.setRecorder(paramHttpRecorder);
		paramHttpRecorder.inputWrap(inputStream);
		paramHttpRecorder.outputWrap(paramChannelSftp.getOutputStream());
		paramHttpRecorder.markContentBegin();

		long softMax = 0;
		long hardMax = getMaxLengthBytes();
		long timeout = (long) getTimeoutSeconds() * 1000L;
		int maxRate = getMaxFetchKBSec();

		RecordingInputStream recordingInputStream = paramHttpRecorder.getRecordedInput();
		recordingInputStream.setLimits(hardMax, timeout, maxRate);
		recordingInputStream.readFullyOrUntil(softMax);
	}

	private void extract(CrawlURI paramCrawlURI, Recorder paramHttpRecorder) {
		if (!getExtractFromDirs()) {
			return;
		}

		ReplayCharSequence replayCharSequence = null;
		try {
			replayCharSequence = paramHttpRecorder.getContentReplayCharSequence();
			extract(paramCrawlURI, replayCharSequence);
		} catch (IOException iOException) {
			logger.log(Level.SEVERE, "IO error during extraction.", iOException);
		} catch (RuntimeException runtimeException) {
			logger.log(Level.SEVERE, "IO error during extraction.", runtimeException);
		} finally {
			close(replayCharSequence);
		}
	}

	private void extract(CrawlURI paramCrawlURI, ReplayCharSequence paramReplayCharSequence) {
		Matcher matcher = DIR.matcher((CharSequence) paramReplayCharSequence);

		while (matcher.find()) {
			String str = matcher.group(1);

			addExtracted(paramCrawlURI, str);
		}
	}

	/**
	 * Adds an extracted filename to the curi. A new URI will be formed
	 * by taking the given curi (which should represent the directory the
	 * file lives in) and appending the file.
	 * 
	 * @param curi the curi to store the discovered link in
	 * @param file the filename of the discovered link
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
			CrawlURI link = curi.createCrawlURI(n, LinkContext.NAVLINK_MISC, Hop.NAVLINK);
			curi.getOutLinks().add(link);
		} catch (URIException e) {
			logger.log(Level.WARNING, "URI error during extraction.", e);
		}
	}

	private void addParent(CrawlURI curi) {
		if (!getExtractParent()) {
			return;
		}
		UURI uuri = curi.getUURI();
		try {
			if (uuri.getPath().equals("/")) {
				return;
			}

			String scheme = uuri.getScheme();
			String auth = uuri.getEscapedAuthority();
			String path = uuri.getEscapedCurrentHierPath();
			UURI parent = UURIFactory.getInstance(scheme + "://" + auth + path);

			CrawlURI link = curi.createCrawlURI(parent, LinkContext.NAVLINK_MISC,
					Hop.NAVLINK);
			curi.getOutLinks().add(link);
		} catch (URIException uRIException) {
			logger.log(Level.WARNING, "URI error during extraction.", (Throwable) uRIException);
		}
	}

	/**
	 * Returns the username and password for the given URI. This method
	 * always returns an array of length 2. The first element in the returned
	 * array is the username for the URI, and the second element is the
	 * password.
	 * 
	 * <p>
	 * If the URI itself contains the username and password (i.e., it looks
	 * like <code>sftp://username:password@host/path</code>) then that username
	 * and password are returned.
	 * 
	 * <p>
	 * Otherwise the settings system is probed for the <code>username</code>
	 * and <code>password</code> attributes for this <code>SFTPFetch</code>
	 * and the given <code>curi</code> context. The values of those
	 * attributes are then returned.
	 * 
	 * @param curi the curi whose username and password to return
	 * @return an array containing the username and password
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
				result[0] = userinfo.substring(0, p);
				result[1] = userinfo.substring(p + 1);
				return result;
			}
		}
		result[0] = getUsername();
		result[1] = getPassword();
		return result;
	}

	private static void close(ReplayCharSequence paramReplayCharSequence) {
		if (paramReplayCharSequence == null) {
			return;
		}
		try {
			paramReplayCharSequence.close();
		} catch (IOException iOException) {
			logger.log(Level.WARNING, "IO error closing ReplayCharSequence.", iOException);
		}
	}

	private static void disconnect(ClientSFTP paramClientSFTP) {
		if (paramClientSFTP.isConnected())
			paramClientSFTP.disconnect();
	}
}
