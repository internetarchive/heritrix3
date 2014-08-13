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
package org.archive.net;


import java.io.IOException;
import java.io.StringReader;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.net.ProtocolCommandEvent;
import org.apache.commons.net.ProtocolCommandListener;
import org.apache.commons.net.ftp.FTPClient;
import org.archive.util.IterableLineIterator;


/**
 * Client for FTP operations. Saves the commands sent to the server and replies
 * received, which can be retrieved with {@link #getControlConversation()}.
 * 
 * @contributor pjack
 * @contributor nlevitt
 */
public class ClientFTP extends FTPClient implements ProtocolCommandListener {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    // Records the conversation on the ftp control channel. The format is based on "curl -v".
    protected StringBuilder controlConversation;
    protected Socket dataSocket;

    /**
     * Constructs a new <code>ClientFTP</code>.
     */
    public ClientFTP() {
        controlConversation = new StringBuilder();
        addProtocolCommandListener(this);
    }

    /**
     * Opens a data connection.
     * 
     * @param command
     *            the data command (eg, RETR or LIST)
     * @param path
     *            the path of the file to retrieve
     * @return the socket to read data from, or null if server says not found,
     *         permission denied, etc
     * @throws IOException
     *             if a network error occurs
     */
    public Socket openDataConnection(int command, String path)
    throws IOException {
        try {
            dataSocket = _openDataConnection_(command, path);
            if (dataSocket != null) {
                recordAdditionalInfo("Opened data connection to "
                        + dataSocket.getInetAddress().getHostAddress() + ":"
                        + dataSocket.getPort());
            }
            return dataSocket;
        } catch (IOException e) {
            if (getPassiveHost() != null) {
                recordAdditionalInfo("Failed to open data connection to "
                        + getPassiveHost() + ":" + getPassivePort() + ": "
                        + e.getMessage());
            } else {
                recordAdditionalInfo("Failed to open data connection: "
                        + e.getMessage());
            }
            throw e;
        }
    }

    public void closeDataConnection() {
        if (dataSocket != null) {
            String dataHostPort = dataSocket.getInetAddress().getHostAddress()
                    + ":" + dataSocket.getPort();
            try {
                dataSocket.close();
                recordAdditionalInfo("Closed data connection to "
                        + dataHostPort);
            } catch (IOException e) {
                recordAdditionalInfo("Problem closing data connection to "
                        + dataHostPort + ": " + e.getMessage());
            }
        }
    }

    protected void _connectAction_() throws IOException {
        try {
            recordAdditionalInfo("Opening control connection to "
                    + getRemoteAddress().getHostAddress() + ":"
                    + getRemotePort());
            super._connectAction_();
        } catch (IOException e) {
            recordAdditionalInfo("Failed to open control connection to "
                    + getRemoteAddress().getHostAddress() + ":"
                    + getRemotePort() + ": " + e.getMessage());
            throw e;
        }
    }
    
    public void disconnect() throws IOException {
        String remoteHostPort = getRemoteAddress().getHostAddress() + ":"
                + getRemotePort();
        super.disconnect();
        recordAdditionalInfo("Closed control connection to " + remoteHostPort);
    }

    public String getControlConversation() {
        return controlConversation.toString();
    }    
    
    protected void recordControlMessage(String linePrefix, String message) {
        for (String line: new IterableLineIterator(new StringReader(message))) {
            controlConversation.append(linePrefix);
            controlConversation.append(line);
            controlConversation.append(NETASCII_EOL);
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest(linePrefix + line);
            }
        }
    }

    public void protocolCommandSent(ProtocolCommandEvent event) {
        recordControlMessage("> ", event.getMessage());
    }

    public void protocolReplyReceived(ProtocolCommandEvent event) {
        recordControlMessage("< ", event.getMessage());
    }

    // for noting things like successful/unsuccessful connection to data port
    private void recordAdditionalInfo(String message) {
        recordControlMessage("* ", message);
    }
    
    // XXX see https://issues.apache.org/jira/browse/NET-257
    @Override
    public String[] getReplyStrings() {
        return _replyLines.toArray(new String[0]);
    }
}
