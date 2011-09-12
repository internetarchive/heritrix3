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
package org.archive.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
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
            throw new IOException("Wait on process " + Arrays.toString(args) + " interrupted: "
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
