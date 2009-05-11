/* 
 * Copyright (C) 2007 Internet Archive.
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
 * CheckpointInputStream.java
 *
 * Created on Mar 6, 2007
 *
 * $Id:$
 */

package org.archive.checkpointing;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.URI;


/**
 * Object input stream that provides information useful during checkpoint
 * recovery.
 * 
 * @author pjack
 */
public class CheckpointInputStream extends ObjectInputStream
implements CheckpointRecovery {


    final private CheckpointRecovery recovery;
    
    
    public CheckpointInputStream(InputStream input,
            CheckpointRecovery recovery) throws IOException {
        super(input);
        this.recovery = recovery;
    }

    
    public String getRecoveredJobName() {
        return recovery.getRecoveredJobName();
    }

//    public <T> void setState(Object module, Key<T> key, T value) {
//        recovery.setState(module, key, value);
//    }


    public String translatePath(String path) {
        return recovery.translatePath(path);
    }


    public URI translateURI(URI uri) {
        return recovery.translateURI(uri);
    }


//    public void apply(SingleSheet global) {
//        throw new UnsupportedOperationException();
//    }
}
