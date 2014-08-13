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

package org.archive.checkpointing;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;


/**
 * Interface for objects that can checkpoint their state, possibly
 * but not necessarily into the provided Checkpoint instance, on 
 * request.
 * 
 * @contributor pjack
 * @contributor gojomo
 */
public interface Checkpointable {

    /**
     * Note a checkpoint is about to begin. Most beans will ignore,
     * but some can use this to wrap up tasks that shouldn't be 
     * half-done during a checkpoint (and hold a lock to prevent 
     * tasks from beginning during the checkpoint). 
     * 
     * @param checkpointInProgress Checkpoint
     */
    void startCheckpoint(Checkpoint checkpointInProgress);
    
    /**
     * Do the actual checkpoint. Beans should ensure any state that
     * they would need to recover gets saved in an appropriate place. 
     * A moderate amount of state may be saved as a JSONObject into
     * the Checkpoint (which then keeps it in the checkpoint directory.)
     * Larger amounts of state may be stored in a manner private to 
     * the bean, or via other collaborating beans (such as BdbModule) 
     * which checkpoint backgin database state. 
     * 
     * @param checkpointInProgress Checkpoint
     * @throws IOException
     */
    void doCheckpoint(Checkpoint checkpointInProgress) throws IOException;
    
    // 
    /**
     * Cleanup/unlock; need not complete for a checkpoint to be valid.
     * 
     * @param checkpointInProgress Checkpoint
     */
    void finishCheckpoint(Checkpoint checkpointInProgress);
   
    /**
     * Used to inform a bean that it should restore its state from
     * the given Checkpoint when launched (Lifecycle start()). May be
     * autowired or configured after build, but before launch, for
     * example via CheckpointService.setRecoveryCheckpointByName().
     * 
     * @param recoveryCheckpoint Checkpoint
     */
    @Autowired(required=false)
    void setRecoveryCheckpoint(Checkpoint recoveryCheckpoint);
}
