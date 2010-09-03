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
package org.archive.crawler.framework;

import org.archive.checkpointing.Checkpoint;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class CheckpointValidator implements Validator {

    @Override
    @SuppressWarnings("unchecked")
    public boolean supports(Class cls) {
        return Checkpoint.class.isAssignableFrom(cls);
    }

    @Override
    public void validate(Object target, Errors errors) {
        Checkpoint cp = ((CheckpointService)target).getRecoveryCheckpoint();
        if(cp==null) {
            return; 
        }
        if(!Checkpoint.hasValidStamp(cp.getCheckpointDir().getFile())) {
            errors.rejectValue(
                "recoveryCheckpoint.checkpointDir",
                null,
                "Configured recovery checkpoint "+cp.getName()
                +" incomplete: lacks valid stamp file.");
        }
    }

}
