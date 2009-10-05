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

package org.archive.modules;

/**
 * Returned by a Processor's process method to indicate the status of the
 * process.  
 * 
 * @contributor pjack
 */
public class ProcessResult {
    public enum ProcessStatus {
        /**
         * The URI was processed normally, and no special action needs to 
         * be taken by the framework.
         */
        PROCEED,

        /**
         * The Processor believes that the ProcessorURI is invalid, or 
         * otherwise incapable of further processing at this time. The 
         * chain should skip subsequent processors, returning the URI.
         */
        FINISH,

        /**
         * The Processor has specified the next processor for the URI.  The 
         * china should skip forward to that processor instead of the reguarly
         * scheduled next processor.
         */
        JUMP,
    }
    
    final public static ProcessResult PROCEED = 
        new ProcessResult(ProcessStatus.PROCEED);
    
    final public static ProcessResult FINISH =
        new ProcessResult(ProcessStatus.FINISH);
    
    
    final private ProcessStatus status;
    final private String jumpTarget;
    
    
    private ProcessResult(ProcessStatus status) {
        this(status, null);
    }
    
    
    private ProcessResult(ProcessStatus status, String jumpName) {
        this.status = status;
        this.jumpTarget = jumpName;
    }
    
    
    public ProcessStatus getProcessStatus() {
        return status;
    }
    
    
    public String getJumpTarget() {
        return jumpTarget;
    }
    
    
    public static ProcessResult jump(String jumpTarget) {
        return new ProcessResult(ProcessStatus.JUMP, jumpTarget);
    }
}
