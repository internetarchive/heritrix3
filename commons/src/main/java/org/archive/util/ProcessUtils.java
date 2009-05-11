/* ProcessUtils.java
 *
 * $Id$
 *
 * Created Jul 19, 2005
 *
 * Copyright (C) 2005 Internet Archive.
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
package org.archive.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class to run an external process.
 * @author stack
 * @version $Date$ $Revision$
 */
public class ProcessUtils {
    private static final Logger LOGGER =
        Logger.getLogger(ProcessUtils.class.getName());
    
    protected ProcessUtils() {
        super();
    }
    
    /**
     * Thread to gobble up an output stream.
     * See http://www.javaworld.com/javaworld/jw-12-2000/jw-1229-traps.html
     */
    protected class StreamGobbler extends Thread {
        private final InputStream is;
        private final StringBuffer sink = new StringBuffer();

        StreamGobbler(InputStream is, String name) {
            this.is = is;
            setName(name);
        }

        public void run() {
            try {
                BufferedReader br =
                    new BufferedReader(new InputStreamReader(this.is));
                for (String line = null; (line = br.readLine()) != null;) {
                    this.sink.append(line);
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        
        public String getSink() {
            return this.sink.toString();
        }
    }
    
    /**
     * Data structure to hold result of a process exec.
     * @author stack
     * @version $Date$ $Revision$
     */
    public class ProcessResult {
        private final String [] args;
        private final int result;
        private final String stdout;
        private final String stderr;
            
        protected ProcessResult(String [] args, int result, String stdout,
                    String stderr) {
            this.args = args;
            this.result = result;
            this.stderr = stderr;
            this.stdout = stdout;
        }
            
        public int getResult() {
            return this.result;
        }
            
        public String getStdout() {
            return this.stdout;
        }
            
        public String getStderr() {
            return this.stderr;
        }
                
        public String toString() {
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < this.args.length; i++) {
                sb.append(this.args[i]);
                sb.append(", ");
            }
            return sb.toString() + " exit code: " + this.result +
                ((this.stderr != null && this.stderr.length() > 0)?
                    "\nSTDERR: " + this.stderr: "") +
                ((this.stdout != null && this.stdout.length() > 0)?
                    "\nSTDOUT: " + this.stdout: "");
        }
    }
        
    /**
     * Runs process.
     * @param args List of process args.
     * @return A ProcessResult data structure.
     * @throws IOException If interrupted, we throw an IOException. If non-zero
     * exit code, we throw an IOException (This may need to change).
     */
    public static ProcessUtils.ProcessResult exec(String [] args)
    throws IOException {
        Process p = Runtime.getRuntime().exec(args);
        ProcessUtils pu = new ProcessUtils();
        // Gobble up any output.
        StreamGobbler err = pu.new StreamGobbler(p.getErrorStream(), "stderr");
        err.setDaemon(true);
        err.start();
        StreamGobbler out = pu.new StreamGobbler(p.getInputStream(), "stdout");
        out.setDaemon(true);
        out.start();
        int exitVal;
        try {
            exitVal = p.waitFor();
        } catch (InterruptedException e) {
            throw new IOException("Wait on process " + args + " interrupted: "
                + e.getMessage());
        }
        ProcessUtils.ProcessResult result =
            pu.new ProcessResult(args, exitVal, out.getSink(), err.getSink());
        if (exitVal != 0) {
            throw new IOException(result.toString());
        } else if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info(result.toString());
        }
        return result;
    }
}
