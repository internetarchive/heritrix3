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
package org.archive.crawler.util;

import java.io.Serializable;

import org.archive.util.fingerprint.LongFPSet;

import st.ata.util.FPGenerator;

/**
 * UriUniqFilter storing 64-bit UURI fingerprints, using an internal LongFPSet
 * instance. 
 * 
 * The passed LongFPSet internal instance may be disk or memory based. Accesses
 * to the underlying LongFPSet are synchronized.
 *
 * @author gojomo
 */
public class FPUriUniqFilter extends SetBasedUriUniqFilter 
implements Serializable {
    private static final long serialVersionUID = 1L;;
     
    private transient FPGenerator fpgen = FPGenerator.std64;
    
    protected LongFPSet fpset;
    public LongFPSet getFpset() {
        return this.fpset;
    }
    public void setFpset(LongFPSet fpset) {
        this.fpset = fpset; 
    }
    
    /**
     * Create FPUriUniqFilter wrapping given long set
     * 
     * @param fpset
     */
    public FPUriUniqFilter(LongFPSet fpset) {
        this.fpset = fpset;
    }
    
    
    public FPUriUniqFilter() {
    }
    
    private long getFp(CharSequence canonical) {
        return fpgen.fp(canonical);
    }

    protected boolean setAdd(CharSequence uri) {
        return fpset.add(getFp(uri));
    }

    protected long setCount() {
        return fpset.count();
    }

    protected boolean setRemove(CharSequence uri) {
        return fpset.remove(getFp(uri));
    }
}