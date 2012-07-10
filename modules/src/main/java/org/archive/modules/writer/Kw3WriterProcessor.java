/* Created on 2006-okt-03
*
* Copyright (C) 2006 National Library of Sweden.
*
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU Lesser General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/

package org.archive.modules.writer;

import static org.archive.modules.writer.Kw3Constants.ARCHIVE_TIME_KEY;
import static org.archive.modules.writer.Kw3Constants.COLLECTION_KEY;
import static org.archive.modules.writer.Kw3Constants.CONTENT_LENGTH_KEY;
import static org.archive.modules.writer.Kw3Constants.CONTENT_MD5_KEY;
import static org.archive.modules.writer.Kw3Constants.HARVESTER_KEY;
import static org.archive.modules.writer.Kw3Constants.HEADER_LENGTH_KEY;
import static org.archive.modules.writer.Kw3Constants.HEADER_MD5_KEY;
import static org.archive.modules.writer.Kw3Constants.IP_ADDRESS_KEY;
import static org.archive.modules.writer.Kw3Constants.STATUS_CODE_KEY;
import static org.archive.modules.writer.Kw3Constants.URL_KEY;
import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.io.ReplayInputStream;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.ServerCache;
import org.archive.spring.ConfigPath;
import org.archive.util.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Processor module that writes the results of successful fetches to
 * files on disk. These files are MIME-files of the type used by the
 * Swedish National Library's Kulturarw3 web harvesting [http://www.kb.se/kw3/].
 *  
 * Each URI gets written to its own file and has a path consisting of:
 * <ul>
 *  <li> A dir named with the first two chars of the website's md5. </li>
 *  <li> A dir named after the website. </li>
 *  <li> 'current' - a dir indicating that this is the directory being written
 *                   to by the ongoing crawl. </li>
 *  <li> A file on the format <md5 of url>.<fetchtime in seconds> </li>
 * </ul>
 * Example: '/53/www.kb.se/current/6879ad79c0ccf886ee8ca55d80e5d6a1.1169211837'
 * 
 * The MIME-file itself consists of three parts:
 * <ul>
 *  <li> 1. ArchiveInfo - Metadata about the file and its content. </li>
 *  <li> 2. Header - The HTTP response header. </li>
 *  <li> 3. Content - The HTTP response content, plus content-type. </li>
 * </ul>
 * 
 * @author oskar
 */
public class Kw3WriterProcessor extends Processor {


  @SuppressWarnings("unused")
  private static final long serialVersionUID = 3L;
  
  private static String COLON = ":";
  private static String WS = " ";
  private static String LF = "\n";
  
  /**
   * Logger.
   */
  private static final Logger logger =
      Logger.getLogger(Kw3WriterProcessor.class.getName());


  /**
   * Top-level directory for archive files.
   */
  protected ConfigPath path = new ConfigPath("Kw3Writer subdirectory","arcs");
  public ConfigPath getPath() {
      return this.path;
  }
  public void setPath(ConfigPath s) {
      this.path = s; 
  }
  
  /**
   * Max size for each file.
   */
  protected long maxFileSizeBytes = 100000000L;
  public long getMaxFileSizeBytes() {
      return maxFileSizeBytes;
  }
  public void setMaxFileSizeBytes(long maxFileSizeBytes) {
      this.maxFileSizeBytes = maxFileSizeBytes;
  }
  
  /**
   * Should permissions be changed for the newly created dirs.
   */
  protected boolean chmod = false; 
  public boolean getChmod() {
      return chmod;
  }
  public void setChmod(boolean chmod) {
      this.chmod = chmod;
  }

  /**
   * What should the permissions be set to. Given as three octal digits, as to
   * the UNIX 'chmod' command. Ex. 777 for all permissions to everyone.
   */
  protected String chmodValue = "777";
  public String getChmodValue() {
      return this.chmodValue;
  }
  public void setChmodValue(String s) {
      this.chmodValue = s; 
  }


  /**
   * Max size for each file.Key for the maximum ARC bytes to write attribute.
   */
  public static final String ATTR_MAX_BYTES_WRITTEN = "total-bytes-to-write";
  
  
  /**
   * Name of collection.
   */
  protected String collection = "kw3";
  public String getCollection() {
      return this.collection;
  }
  public void setCollection(String s) {
      this.collection = s; 
  }

  /**
   * Name of the harvester that is used for the web harvesting.
   */
  protected String harvester = "heritrix";
  public String getHarvester() {
      return this.harvester;
  }
  public void setHarvester(String s) {
      this.harvester = s; 
  }
  
  /**
   * The server cache to use.
   */
  protected ServerCache serverCache;
  public ServerCache getServerCache() {
      return this.serverCache;
  }
  @Autowired
  public void setServerCache(ServerCache serverCache) {
      this.serverCache = serverCache;
  }

  private static String BOUNDARY_START = "KulturArw3_";
  
  /**
   * Constructor.
   */
  public Kw3WriterProcessor() {
  }
  
  protected boolean shouldProcess(CrawlURI curi) {
      // Only successful fetches are written.
      if (!isSuccess(curi)) { 
          return false;
      }
      
      // Only http and https schemes are supported.
      String scheme = curi.getUURI().getScheme().toLowerCase();
      if (!"http".equalsIgnoreCase(scheme) 
              && !"https".equalsIgnoreCase(scheme)) {
          return false;                
      }
      
      return true;
  }


  protected void innerProcess(CrawlURI curi) {      
      // Write the MIME-file
      try {
          writeMimeFile(curi);
      } catch (IOException e) {
          logger.log(Level.WARNING, "i/o error", e);
      }      
  }
  
  /**
   * The actual writing of the Kulturarw3 MIME-file.
   * 
   * The MIME-file consists of three parts:
   * 1. ArchiveInfo - Metadata about the file and its content.
   * 2. Header - The HTTP response header.
   * 3. Content - The HTTP response content, plus content-type.
   * 
   * For more on this format, see '?'.
   */
  protected void writeMimeFile(CrawlURI curi) throws IOException {
      ReplayInputStream ris = null;
      OutputStream out = null;
                
      try {
          String boundary = BOUNDARY_START + stringToMD5(curi.toString());
          ris = curi.getRecorder().getRecordedInput().
              getReplayInputStream();
          out = initOutputStream(curi);
          
          // Part 1: Archive info
          writeArchiveInfoPart(boundary, curi, ris, out);

          // Part 2: Header info + HTTP header
          writeHeaderPart(boundary, ris, out);

          // Part 3: Content info + HTTP content
          writeContentPart(boundary, curi, ris, out);

          // And finally the terminator string
          String terminator = "\n--" + boundary + "--\n";
          out.write(terminator.getBytes());
      } finally {
          if (ris != null)
              ris.close();
          if (out != null)
              out.close();
      }
  }
  
  /**
   * Get the OutputStream for the file to write to.
   * 
   * It has a path consisting of:
   * 1. A dir named with the first two chars of the website's md5.
   * 2. A dir named after the website.
   * 3. 'current' - a dir indicating that this is the directory being written
   *                to by the ongoing crawl. 
   * 4. A file on the format <md5 of url>.<fetchtime in seconds>
   * 
   * Example: '/53/www.kb.se/current/6879ad79c0ccf886ee8ca55d80e5d6a1.1169211837'            
   */
  protected OutputStream initOutputStream(CrawlURI curi) throws IOException {
      String uri = curi.toString();
      int port = curi.getUURI().getPort();
      String host = (port == 80 || port <= 0) ?
              curi.getUURI().getHost() : curi.getUURI().getHost() + ":" + port;
      long fetchTime = curi.getFetchBeginTime() / 1000;
             
      String md5 = stringToMD5(host);
      File dir = new File(getPath().getFile(), md5.substring(0, 2) + "/" + host +
              "/current");
      if (!dir.exists()) {
          FileUtils.ensureWriteableDirectory(dir);
          if (this.chmod)
              chmods(dir, getPath().getFile());
      }
      md5 = stringToMD5(uri);
      File arcFile = new File(dir, md5 + "." + fetchTime);
      return new FastBufferedOutputStream(new FileOutputStream(arcFile));       
  }
  
  protected void writeArchiveInfoPart(String boundary, CrawlURI curi,
          ReplayInputStream ris, OutputStream out)
          throws IOException {
      // Get things we need to write in this part
      String uri = curi.toString();
      String ip = getHostAddress(curi);
      long headerLength = ris.getHeaderSize();
      long contentLength = ris.getContentSize();
      long archiveTime = System.currentTimeMillis() / 1000; // Fetchtime in seconds
      int statusCode = curi.getFetchStatus();
      String headerMd5 = null;
      Object contentMd5 = null;       
      
      // Get headerMd5
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ris.readHeaderTo(baos);
      headerMd5 = stringToMD5(baos.toString());              
      
      // Get contentMd5
      contentMd5 = curi.getContentDigest();
      if (contentMd5 != null)
          contentMd5 = getHexString((byte[]) contentMd5);
      
      StringBuffer buffer = new StringBuffer();
      buffer.append("MIME-version: 1.1" + LF);
      buffer.append("Content-Type: multipart/mixed; boundary=" + boundary + LF);
      buffer.append("HTTP-Part: ArchiveInfo" + LF);
      buffer.append(COLLECTION_KEY + COLON + WS + this.collection + LF);
      buffer.append(HARVESTER_KEY + COLON + WS + this.harvester + LF);
      buffer.append(URL_KEY + COLON + WS + uri + LF);
      buffer.append(IP_ADDRESS_KEY + COLON + WS + ip + LF);
      buffer.append(HEADER_LENGTH_KEY + COLON + WS + headerLength + LF);
      buffer.append(HEADER_MD5_KEY + COLON + WS + headerMd5 + LF);
      buffer.append(CONTENT_LENGTH_KEY + COLON + WS + contentLength + LF);
      buffer.append(CONTENT_MD5_KEY + COLON + WS + contentMd5 + LF);
      buffer.append(ARCHIVE_TIME_KEY + COLON + WS+ archiveTime + LF);
      buffer.append(STATUS_CODE_KEY + COLON + WS + statusCode + LF + LF);       
      out.write(buffer.toString().getBytes());       
  }
  
  protected void writeHeaderPart(String boundary, ReplayInputStream ris,
          OutputStream out) 
          throws IOException {
      StringBuffer buffer = new StringBuffer();
      buffer.append("--" + boundary + LF);
      buffer.append("Content-Type: text/plain; charset=\"US-ascii\"" + LF);
      buffer.append("HTTP-Part: Header" + LF + LF );
      out.write(buffer.toString().getBytes());
      ris.readHeaderTo(out);       
  }
  
  protected void writeContentPart(String boundary, CrawlURI curi,
          ReplayInputStream ris, OutputStream out) 
          throws IOException {
      // Get things we need to write in this part
      String uri = curi.toString();
      String contentType = curi.getContentType();
      long contentLength = ris.getContentSize();      
      // Only write content if there is some
      if (contentLength == 0)   return;
             
      StringBuffer buffer = new StringBuffer();
      buffer.append("--" + boundary + LF);
      buffer.append("Content-Type: " + contentType + LF);
      buffer.append("HTTP-Part: Content" + LF + LF);
      out.write(buffer.toString().getBytes());
      
      if (contentLength > getMaxFileSizeBytes()) {
          ris.readContentTo(out, getMaxFileSizeBytes());
          logger.info(" Truncated url: " + uri + ", Size: " + contentLength +
                  ", Content-type: " + contentType);
      } else {
          ris.readContentTo(out);
      }
  }

  // --- Private helper functions --- //
  /*
   * Get a MD5 checksum based on a String. 
   */ 
  private String stringToMD5(String str) {
      try {
          byte b[] = str.getBytes();
          MessageDigest md = MessageDigest.getInstance("MD5");
          md.update(b);
          byte[] digest = md.digest();
          return getHexString(digest);
      } catch (NoSuchAlgorithmException e) {
          logger.log(Level.WARNING, "md5 error", e);
      } 
      return null;
  }

  /* 
   * Fast convert a byte array to a hex string with possible leading zero.
   */
  private String getHexString(byte[] b) {
      StringBuffer sb = new StringBuffer();
      for (int i = 0; i < b.length; i++) {
          String tmp = Integer.toHexString(b[i] & 0xff);
          if (tmp.length() < 2)
              sb.append("0" + tmp);
          else
              sb.append(tmp);
      }
      return sb.toString();
  }

  /* 
   * Chmods for all newly created directories.
   */
  private void chmods(File dir, File arcsDir) {
      String topdir = arcsDir.getAbsolutePath();
      chmod(dir, this.chmodValue);
      File parent = dir.getParentFile();
      while (!parent.getAbsolutePath().equalsIgnoreCase((topdir))) {
          chmod(parent, this.chmodValue);
          parent = parent.getParentFile();
      }
      
  }

  /* 
   * Chmod for a specific file or directory.
   */
  private void chmod(File file, String permissions) {
      Process proc = null;
      try {
          proc = Runtime.getRuntime().exec("chmod " + permissions + " " +
                  file.getAbsolutePath());
          proc.waitFor();
          proc.getInputStream().close();
          proc.getOutputStream().close();
          proc.getErrorStream().close();
      } catch (IOException e) {
          logger.log(Level.WARNING, "chmod failed", e);
      } catch (InterruptedException e) {
          logger.log(Level.WARNING, "chmod failed", e);
      }
  }

  private String getHostAddress(CrawlURI curi) {
      CrawlHost h = serverCache.getHostFor(curi.getUURI());
      if (h == null) {
          throw new NullPointerException("Crawlhost is null for " + curi + " " +
                  curi.getVia());
      }
      InetAddress a = h.getIP();
      if (a == null) {
          throw new NullPointerException("Address is null for " + curi + " " +
             curi.getVia() + ". Address " +
                 ((h.getIpFetched() == CrawlHost.IP_NEVER_LOOKED_UP) ?
                     "was never looked up." :
                     (System.currentTimeMillis() - h.getIpFetched()) + " ms ago."));
      }
      return h.getIP().getHostAddress();
  }
}