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
import java.net.Socket;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;


/**
 * Client for FTP operations.  This class is necessary only because the
 * {@link #_openDataConnection_(int, String)} method is protected in 
 * the superclass, and we need to call it directly to handle directory
 * listings.  (The code that provides directory listings in the 
 * superclass doesn't scale:  It reads the entire directory into
 * an in-memory list).
 * 
 * <p>Additionally, "strict" methods are provided for the other operations
 * we use.  Maddeningly, the superclass never raises exceptions.  If an 
 * FTP operation fails, then the superclass methods generally return false.
 * A developer then needs to check the {@link FTP#getReplyCode()}
 * method to see what actually went wrong.  The "strict" methods provided
 * by this class invoke the superclass method, check if the success flag
 * is false, and then raise an {@link FTPException} with the value of
 * {@link FTP#getReplyCode()}.
 *
 * @author pjack
 */
public class ClientFTP extends FTPClient {
    
    
    /**
     * Constructs a new <code>ClientFTP</code>.
     */
    public ClientFTP() {
    }
    

    /**
     * Connects to the FTP server at the given host and port.
     * 
     * @param host    the host of the FTP server to connect to
     * @param port    the port the FTP server listens on
     * @throws IOException  if the connection cannot be made due to IO error
     * @throws FTPException  if the server refuses the connection
     */
    public void connectStrict(String host, int port) throws IOException {
        this.connect(host, port);
        int reply = this.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            throw new FTPException(reply);
        }
    }

    
    /**
     * Login to the FTP server with the given username and password.
     * 
     * @param user   the username to login under
     * @param pass   the password to use
     * @throws IOException   if a network error occurs
     * @throws FTPException  if the login is rejected by the server
     * @throws org.apache.commons.net.ftp.FTPConnectionClosedException   
     *   if the FTP server prematurely closes the connection (for 
     *   instance, if the client was idle for too long)
     */
    public void loginStrict(String user, String pass) throws IOException {
        boolean r = this.login(user, pass);
        if (!r) {
            throw new FTPException(this.getReplyCode());
        }
    }

    
    /**
     * Tells the FTP server to send binary files.
     *
     * @throws IOException   if a network error occurs
     * @throws FTPException  if the server rejects the command
     * @throws org.apache.commons.net.ftp.FTPConnectionClosedException   
     *   if the FTP server prematurely closes the connection (for 
     *   instance, if the client was idle for too long)
     */
    public void setBinary() throws IOException {
        boolean r = super.setFileType(BINARY_FILE_TYPE);
        if (!r) {
            throw new FTPException(getReplyCode());
        }
    }


    /**
     * Opens a data connection. 
     * 
     * @param command  the data command (eg, RETR or LIST)
     * @param path     the path of the file to retrieve
     * @return  the socket to read data from
     * @throws  IOException  if a network error occurs
     * @throws  FTPException  if a protocol error occurs
     */
    public Socket openDataConnection(int command, String path)
    throws IOException {
        Socket socket = _openDataConnection_(command, path);
        if (socket == null) {
            throw new FTPException(this.getReplyCode());
        }
        return socket;
    }

    
}
