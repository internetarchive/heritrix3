/* Copyright (C) 2006 Internet Archive.
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
 *
 * ProcessStatus.java
 * Created on December 13, 2006
 *
 * $Header$
 */
package org.archive.modules;

public class ProcessResult {

    
    final public static ProcessResult PROCEED = 
        new ProcessResult(ProcessStatus.PROCEED);
    
    final public static ProcessResult FINISH =
        new ProcessResult(ProcessStatus.FINISH);
    
    final public static ProcessResult STUCK = 
        new ProcessResult(ProcessStatus.STUCK);
    
    
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
