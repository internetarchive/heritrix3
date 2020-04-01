package org.archive.net;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;

public class ClientSFTP {
	private final Logger logger = Logger.getLogger(getClass().getName());

	protected StringBuilder controlConversation;

	protected Socket dataSocket;
	protected static Session session = null;
	protected static Channel channel = null;
	protected static ChannelSftp channelSFTP = null;

	public ClientSFTP() {
		this.controlConversation = new StringBuilder();
		this.logger.setUseParentHandlers(true);
	}

	public String getControlConversation() {
		return this.controlConversation.toString();
	}

	public void connect(String paramString1, String paramString2, int paramInt, String paramString3) throws SocketException, IOException {
		JSch jSch = new JSch();

		try {
			session = jSch.getSession(paramString1, paramString2, paramInt);
			session.setConfig("StrictHostKeyChecking", "no");
			session.setPassword(paramString3);
			session.connect();
			this.logger.info("Connected to SFTP server " + paramString2 + ":" + paramInt);
			this.controlConversation.append("Connected to SFTP server " + paramString2 + ":" + paramInt);
		} catch (Exception exception) {
			this.logger.info("Unable to connect to SFTP server : " + exception.toString());
			this.controlConversation.append("Unable to connect to SFTP server : " + exception.toString());
			session.disconnect();
		}
	}

	public ChannelSftp openSFTPChannel() throws JSchException {
		channel = session.openChannel("sftp");
		channel.connect();
		channelSFTP = (ChannelSftp) channel;
		this.logger.info("***   SFTP Channel created.   ***");
		boolean bool = channelSFTP.isConnected();
		if (bool)
			this.logger.info("channelSftp connected ");
		return channelSFTP;
	}

	public boolean isConnected() {
		if (channelSFTP != null) {
			return channelSFTP.isConnected();
		}

		return false;
	}

	public ChannelSftp getChannelSftp() {
		return channelSFTP;
	}

	public void exit() {
		session.disconnect();
	}

	public boolean isDirectory(String paramString) throws Exception {
		SftpATTRS sftpATTRS = channelSFTP.stat(paramString);
		return sftpATTRS.isDir();
	}

	public void disconnect() {
		if (channelSFTP != null) {
			channelSFTP.exit();
			this.logger.info("channelSftp exit....");
		}
		if (session != null) {
			session.disconnect();
			this.logger.info("session disconnect....");
		}
		channelSFTP = null;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Map<String, Boolean> getLsFileMap(String paramString) throws Exception {
		HashMap<Object, Object> hashMap = new HashMap<>();

		Vector<ChannelSftp.LsEntry> vector = channelSFTP.ls(paramString);
		for (byte b = 0; b < vector.size(); b++) {
			ChannelSftp.LsEntry lsEntry = vector.get(b);
			String str = lsEntry.getFilename();

			if (!".".equals(str)) {

				if (!"..".equals(str)) {

					String str1 = paramString + "/" + str;
					boolean bool = isDirectory(str1);

					this.logger.info("fileName : " + str);
					hashMap.put(str, Boolean.valueOf(bool));
				}
			}
		}
		return (Map) hashMap;
	}

	protected boolean mkdir(String paramString) throws Exception {
		this.logger.info("channelSftp mkdir: " + paramString);
		channelSFTP.mkdir(paramString);
		return true;
	}

	public boolean cd(String paramString) throws Exception {
		this.logger.info("channelSftp cd : " + paramString);
		channelSFTP.cd(paramString);
		return true;
	}

	protected boolean put(FileInputStream paramFileInputStream, String paramString) throws Exception {
		this.logger.info("channelSftp put: " + paramString);
		channelSFTP.put(paramFileInputStream, paramString);
		return true;
	}

	protected boolean downRemoteSingleFile(String paramString1, String paramString2) throws Exception {
		FileOutputStream fileOutputStream = new FileOutputStream(paramString2);
		channelSFTP.get(paramString1, fileOutputStream);

		this.logger.info("channelSftp download file: " + paramString1);
		return true;
	}

	protected boolean rm(String paramString) throws Exception {
		channelSFTP.rm(paramString);
		return true;
	}
}
