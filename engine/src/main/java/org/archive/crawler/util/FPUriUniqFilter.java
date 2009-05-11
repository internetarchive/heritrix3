/* Copyright (C) 2003 Internet Archive.
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
 * MemFPUURISet.java
 * Created on Oct 1, 2003
 *
 * $Header$
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
    
    LongFPSet fpset;
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