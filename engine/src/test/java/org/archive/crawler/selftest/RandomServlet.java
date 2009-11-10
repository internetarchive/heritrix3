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
package org.archive.crawler.selftest;

import java.io.IOException;
import java.io.Writer;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * @author pjack
 *
 */
public class RandomServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    
    private int maxHops = 3;
    
    private int minPort = 7000;
    
    private int maxPort = 7010;

    private String host = "127.0.0.1";
    
    private String pathRoot = "random";


    public RandomServlet() {
    }


    public String getHost() {
        return host;
    }


    public void setHost(String host) {
        this.host = host;
    }


    public int getMaxHops() {
        return maxHops;
    }


    public void setMaxHops(int maxHops) {
        this.maxHops = maxHops;
    }


    public int getMaxPort() {
        return maxPort;
    }


    public void setMaxPort(int maxPort) {
        this.maxPort = maxPort;
    }


    public int getMinPort() {
        return minPort;
    }


    public void setMinPort(int minPort) {
        this.minPort = minPort;
    }


    public String getPathRoot() {
        return pathRoot;
    }


    public void setPathRoot(String pathRoot) {
        this.pathRoot = pathRoot;
    }



    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
    throws ServletException, IOException {
        resp.setContentType("text/html");
        RandomServletLinkWriter rslw = new RandomServletLinkWriter();
     
        rslw.setHost(host);
        rslw.setPathRoot(pathRoot);
        rslw.setMaxHops(maxHops);
        rslw.setPortRange(minPort, maxPort);
        rslw.setPathInfo(req.getPathInfo());
        rslw.write(resp.getWriter());
    }

}


class RandomServletLinkWriter {

    
    final private static long SEED_BASE = -4739205649012677248L;

    final private static int MAX_LINKS = 50;
 
    String host;
    String pathRoot;
    
    int pathValue;
    Random random;
    Writer writer;
    int maxHops;
    int minPort;
    int maxPort;
    
    
    public void setHost(String host) {
        this.host = host;
    }
    
    
    public void setPathRoot(String pathRoot) {
        this.pathRoot = pathRoot;
    }
    
    
    public void setMaxHops(int maxHops) {
        this.maxHops = maxHops;
    }
    
    
    public void setPortRange(int min, int max) {
        this.minPort = min;
        this.maxPort = max;
    }
    
    
    public void setPathInfo(String pathInfo) {
        this.pathValue = fromPath(pathInfo);
        long seed = SEED_BASE * (long)pathValue;
        this.random = new Random(seed);
    }
    
    
    public void write(Writer writer) throws IOException {
        this.writer = writer;
        
        for (int i = minPort; i < maxPort; i++) {
            writePortLinks(i);
        }        
    }
    
    
    private void writePortLinks(int port) throws IOException {
        writeLink(port, pathValue - 1);
        writeLink(port, pathValue + 1);
        
        int max = random.nextInt(MAX_LINKS);
        for (int i = 0; i < max; i++) {
            writeLink(port, random.nextInt(max(maxHops)));
        }
    }
    
    
    private int max(int digits) {
        int r = 1;
        for (; digits > 0; digits--) {
            r *= 10;
        }
        return r;        
    }
    
    
    private void writeLink(int port, int value) throws IOException {
        if (value < 0) { 
            return;
        }
        
        if (value >= max(maxHops)) {
            return;
        }
        
        String path = toPath(value);
        
        StringBuilder sb = new StringBuilder();
        sb.append("<a href=\"http://").append(host).append(':').append(port);
        sb.append('/').append(pathRoot).append('/').append(path);
        sb.append("\">link ").append(value).append("</a>\n");
        writer.write(sb.toString());
        writer.flush();
    }


    public static String toPath(int value) {
        if (value < 0) {
            throw new IllegalArgumentException();
        }
        if (value == 0) {
            return "0";
        }
        String r = "";
        while (value > 0) {
            r += (value % 10) + "/";
            value = value / 10;
        }
        return r;
    }

    
    public static int fromPath(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.length() == 0) {
            return 0;
        }
        StringBuilder sb = new StringBuilder();
        String[] digits = path.split("/");
        for (int i = 0; i < digits.length; i++) {
            sb.insert(0, digits[i].charAt(0));
        }
        return Integer.parseInt(sb.toString());
    }
}
