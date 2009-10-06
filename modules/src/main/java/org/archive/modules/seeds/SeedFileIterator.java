/* SeedFileIterator
*
* $Id$
*
* Created on Mar 28, 2005
*
* Copyright (C) 2005 Internet Archive.
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
*/ 
package org.archive.modules.seeds;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.iterator.LineReadingIterator;
import org.archive.util.iterator.RegexLineIterator;
import org.archive.util.iterator.TransformingIteratorWrapper;


/**
 * Iterator wrapper for seeds file on disk. 
 * 
 * @author gojomo
 */
public class SeedFileIterator extends TransformingIteratorWrapper<String,UURI> {
    private static Logger logger =
        Logger.getLogger(SeedFileIterator.class.getName());
    
    BufferedReader input;
    Writer ignored;
    
    /**
     * Construct a SeedFileIterator over the input available
     * from the supplied BufferedReader.
     * @param br BufferedReader from which to get seeds
     */
    public SeedFileIterator(BufferedReader br) {
        this(br,null);
    }

    /**
     * Construct a SeedFileIterator over the input available
     * from the supplied BufferedReader, reporting any nonblank
     * noncomment entries which don't generate a valid seed to
     * the supplied BufferedWriter.
     * 
     * @param inputReader BufferedReader from which to get seeds
     * @param ignoredWriter BufferedWriter to report any ignored input 
     */
    public SeedFileIterator(BufferedReader inputReader, Writer ignoredWriter) {
        super();
        inner = new RegexLineIterator(
                    new LineReadingIterator(inputReader),
                    RegexLineIterator.COMMENT_LINE,
                    RegexLineIterator.NONWHITESPACE_ENTRY_TRAILING_COMMENT,
                    RegexLineIterator.ENTRY);
        input = inputReader;
        ignored = ignoredWriter;
    }
    
    protected UURI transform(String uri) {
        if(! uri.matches("[a-zA-Z][\\w+\\-]+:.*")) { // Rfc2396 s3.1 scheme, 
                                                     // minus '.'
            // Does not begin with scheme, so try http://
            uri = "http://"+uri;
        }
        try {
            // TODO: ignore lines beginning with non-word char
            return UURIFactory.getInstance(uri);
        } catch (URIException e) {
            logger.log(Level.INFO, "line in seed file ignored: "
                    + e.getMessage(), e);
            if(ignored!=null) {
                try {
                    ignored.write(uri+"\n");
                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            }
            return null;
        }
    }
    
    
    /**
     * Clean-up when hasNext() has returned null: close open files. 
     *
     * @see org.archive.util.iterator.TransformingIteratorWrapper#noteExhausted()
     */
    protected void noteExhausted() {
        super.noteExhausted();
        close();
    }
    
    public void close() {
        try {
            if(input!=null) {
                input.close();
            }
            if(ignored!=null) {
                ignored.close();
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}