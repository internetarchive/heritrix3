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

package org.archive.io;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;

import com.google.common.base.Charsets;


/**
 * CharSequence interface with addition of a {@link #close()} method.
 *
 * Users of implementations of this interface must call {@link #close()} so
 * implementations get a chance at cleaning up after themselves.
 *
 * @author stack
 * @version $Revision$, $Date$
 */
public interface ReplayCharSequence extends CharSequence, Closeable {

    /** charset to use in replay when declared value 
     * is absent/illegal/unavailable */
    public Charset FALLBACK_CHARSET = Charsets.ISO_8859_1; // TODO: should this be UTF-8?
    
    /**
     * Call this method when done so implementation has chance to clean up
     * resources.
     *
     * @throws IOException Problem cleaning up file system resources.
     */
    public void close() throws IOException;
    
    /**
     * Report count of decoder errors silently eaten during ReplayCharSequence
     * use. May be less than the number of individual decoding anomalies in 
     * underlying content (if decoding method doesn't allow counting individual
     * errors). 
     */
    public long getDecodeExceptionCount(); 
    
    /**
     * Return the first coding-exception encountered, if the count > 0. 
     * @return CharacterCodingException
     */
    public CharacterCodingException getCodingException();

    /**
     * @return false if {@link #close()} has been called
     */
    public boolean isOpen();
    
    /**
     * Return the effective Charset used to create this CharSequence from 
     * (raw byte) source material. 
     */
    public Charset getCharset(); 
}
