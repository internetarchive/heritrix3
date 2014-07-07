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
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;

/**
 * Wrapper for platform-dependent hard link creation.
 * 
 * @see http://stackoverflow.com/questions/783075/creating-a-hard-link-in-java/3023349#3023349
 */
public class FilesystemLinkMaker {
    
    private static final Logger logger = Logger.getLogger(FilesystemLinkMaker.class.getName()); 
    
    // see https://github.com/twall/jna/blob/master/www/GettingStarted.md
    public interface Kernel32Library extends StdCallLibrary {
        Kernel32Library INSTANCE = (Kernel32Library) (Platform.isWindows() 
                ? Native.loadLibrary("kernel32", Kernel32Library.class)
                : null);
        
        /* http://msdn.microsoft.com/en-us/library/aa379560%28VS.85%29.aspx
         * http://en.wikipedia.org/wiki/Java_Native_Access
         * 
         * typedef struct _SECURITY_ATTRIBUTES {
         *   DWORD  nLength;
         *   LPVOID lpSecurityDescriptor;
         *   BOOL   bInheritHandle;
         * } SECURITY_ATTRIBUTES, *PSECURITY_ATTRIBUTES, *LPSECURITY_ATTRIBUTES;
         */
        public static class LPSECURITY_ATTRIBUTES extends Structure {
            public int nLength;
            public Pointer lpSecurityDescriptor;
            public boolean bInheritHandle;
        }

        /*
         * http://msdn.microsoft.com/en-us/library/aa363860%28VS.85%29.aspx
         * CreateHardLink is a macro that maps to CreateHardLinkA (ansi) or
         * CreateHardLinkW (unicode). In initial testing CreateHardLinkW()
         * worked, but then it stopped working... go figure.
         */
        boolean CreateHardLinkA(String newPath, String existingPath, LPSECURITY_ATTRIBUTES lpSecurityAttributes);
        // boolean CreateHardLinkW(String newPath, String existingPath, LPSECURITY_ATTRIBUTES lpSecurityAttributes);
        
        // http://msdn.microsoft.com/en-us/library/aa363866%28v=VS.85%29.aspx
        boolean CreateSymbolicLinkA(String newPath, String existingPath, LPSECURITY_ATTRIBUTES lpSecurityAttributes);
    }
    
    /**
     * Wrapper over platform-dependent system calls to create a hard link.
     * 
     * @return true on success
     */
    // XXX could handle errors better (examine errno, throw exception...)
    public static boolean makeHardLink(String existingPath, String newPath) {
        try {
            if (Platform.isWindows()) {
                return Kernel32Library.INSTANCE.CreateHardLinkA(newPath, existingPath, null);
            } else {
                int status = CLibrary.INSTANCE.link(existingPath, newPath);
                return status == 0;
            }
        } catch (UnsatisfiedLinkError e) {
            // see https://webarchive.jira.com/browse/HER-1979
            logger.warning("hard links not supported on this platform - " + e);
            return false;
        }
    }
    
    /**
     * Wrapper over platform-dependent system calls to create a symbolic link.
     * 
     * @return true on success
     */
    // XXX could handle errors better (examine errno, throw exception...)
    public static boolean makeSymbolicLink(String existingPath, String newPath) {
        try {
            if (Platform.isWindows()) {
                return Kernel32Library.INSTANCE.CreateSymbolicLinkA(newPath, existingPath, null);
            } else {
                int status = CLibrary.INSTANCE.symlink(existingPath, newPath);
                return status == 0;
            }
        } catch (UnsatisfiedLinkError e) {
            // see https://webarchive.jira.com/browse/HER-1979
            logger.warning("symbolic links not supported on this platform - " + e);
            return false;
        }
    }
    
    public static void main(String[] args) throws IOException {
        File existingPath = File.createTempFile("heritrixHardLinkTestExistingFile", ".tmp");
        File newPath = File.createTempFile("heritrixHardLinkTestNewFile", ".tmp");
        newPath.delete();
        
        if (FilesystemLinkMaker.makeHardLink(existingPath.getAbsolutePath(), newPath.getAbsolutePath())) {
            System.out.println("success - made hard link from " + newPath.getAbsolutePath() + " to " + existingPath.getAbsolutePath());
        } else {
            System.out.println("failed to make hard link from " + newPath.getAbsolutePath() + " to " + existingPath.getAbsolutePath());
        }

        existingPath = File.createTempFile("heritrixSymlinkTestExistingFile", ".tmp");
        newPath = File.createTempFile("heritrixSymlinkTestNewFile", ".tmp");
        newPath.delete();
        
        if (FilesystemLinkMaker.makeSymbolicLink(existingPath.getPath(), newPath.getPath())) {
            System.out.println("success - made symlink from " + newPath.getAbsolutePath() + " to " + existingPath.getAbsolutePath());
        } else {
            System.out.println("failed to make symlink from " + newPath.getAbsolutePath() + " to " + existingPath.getAbsolutePath());
        }
    }
}


