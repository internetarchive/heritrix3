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
package org.archive.util.anvl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.io.UTF8Bytes;

/**
 * An ordered {@link List} with 'data' {@link Element} values.
 * ANVLRecords end with a blank line.
 * 
 * @see <a
 * href="http://www.cdlib.org/inside/diglib/ark/anvlspec.pdf">A Name-Value
 * Language (ANVL)</a>
 * @author stack
 */
public class ANVLRecord extends ArrayList<Element> implements UTF8Bytes {
	private static final long serialVersionUID = -4610638888453052958L;
    private static final Logger logger = 
        Logger.getLogger(ANVLRecord.class.getName());

	public static final String MIMETYPE = "application/warc-fields";
	
	public static final ANVLRecord EMPTY_ANVL_RECORD = new ANVLRecord();
    
    /**
     * Arbitrary upper bound on maximum size of ANVL Record.
     * Will throw an IOException if exceed this size.
     */
    public static final long MAXIMUM_SIZE = 1024 * 10;
	
	/**
	 * An ANVL 'newline'.
	 * @see <a href="http://en.wikipedia.org/wiki/CRLF">http://en.wikipedia.org/wiki/CRLF</a>
	 */
    static final String CRLF = "\r\n";
    
    static final String FOLD_PREFIX = CRLF + ' ';
    
    public ANVLRecord() {
        super();
    }

    public ANVLRecord(Collection<? extends Element> c) {
        super(c);
    }

    public ANVLRecord(int initialCapacity) {
        super(initialCapacity);
    }
    
    public boolean addLabel(final String l) {
    	return super.add(new Element(new Label(l)));
    }

    public boolean addLabelValue(final String l, final String v) {
    	try {
    		return super.add(new Element(new Label(l), new Value(v)));
    	} catch (IllegalArgumentException e) {
    		logger.log(Level.WARNING, "bad label " + l + " or value " + v, e);
    		return false;
    	}
    }
    
    @Override
    public String toString() {
        // TODO: What to emit for empty ANVLRecord?
        StringBuilder sb = new StringBuilder();
        for (final Iterator<Element> i = iterator(); i.hasNext();) {
            sb.append(i.next());
            sb.append(CRLF);
        }
        // 'ANVL Records end in a blank line'.
        sb.append(CRLF);
        return sb.toString();
    }
    
    public Map<String, String> asMap() {
        Map<String, String> m = new HashMap<String, String>(size());
        for (final Iterator<Element> i = iterator(); i.hasNext();) {
            Element e = i.next();
            m.put(e.getLabel().toString(),
                e.isValue()? e.getValue().toString(): (String)null);
        }
        return m;
    }
    
    @Override
    public ANVLRecord clone() {
        return (ANVLRecord) super.clone();
    }
    
    /**
     * @return This ANVLRecord as UTF8 bytes.
     */
    public byte [] getUTF8Bytes()
    throws UnsupportedEncodingException {
        return toString().getBytes(UTF8);
    }
    
    /**
     * Parses a single ANVLRecord from passed InputStream.
     * Read as a single-byte stream until we get to a CRLFCRLF which
     * signifies End-of-ANVLRecord. Then parse all read as a UTF-8 Stream.
     * Doing it this way, while requiring a double-scan, it  makes it so do not
     * need to be passed a RepositionableStream or a Stream that supports
     * marking.  Also no danger of over-reading which can happen when we
     * wrap passed Stream with an InputStreamReader for doing UTF-8
     * character conversion (See the ISR class comment).
     * @param is InputStream
     * @return An ANVLRecord instance.
     * @throws IOException
     */
    public static ANVLRecord load(final InputStream is)
    throws IOException {
        // It doesn't look like a CRLF sequence is possible in UTF-8 without
    	// it signifying CRLF: The top bits are set in multibyte characters.
    	// Was thinking of recording CRLF as I was running through this first
    	// parse but the offsets would then be incorrect if any multibyte
    	// characters in the intervening gaps between CRLF.
        boolean isCRLF = false;
        boolean recordStart = false;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        boolean done = false;
        int read = 0;
        for (int c  = -1, previousCharacter; !done;) {
            if (read++ >= MAXIMUM_SIZE) {
                throw new IOException("Read " + MAXIMUM_SIZE +
                    " bytes without finding  \\r\\n\\r\\n " +
                    "End-Of-ANVLRecord");
            }
            previousCharacter = c;
            c = is.read();
            if (c == -1) {
                throw new IOException("End-Of-Stream before \\r\\n\\r\\n " +
                    "End-Of-ANVLRecord:\n" +
                    new String(baos.toByteArray(), UTF8));
            }
            if (isLF((char)c) && isCR((char)previousCharacter)) {
                if (isCRLF) {
                    // If we just had a CRLF, then its two CRLFs and its end of
                    // record.  We're done.
                    done = true;
                } else {
                    isCRLF = true;
                }
            } else if (!recordStart && Character.isWhitespace(c)) {
                // Skip any whitespace at start of ANVLRecord.
                continue;
            } else {
                // Clear isCRLF flag if this character is NOT a '\r'.
                if (isCRLF && !isCR((char)c)) {
                    isCRLF = false;
                }
                // Not whitespace so start record if we haven't already.
                if (!recordStart) {
                    recordStart = true;
                }
            }
            baos.write(c);
        }
        return load(new String(baos.toByteArray(), UTF8));
    }
    
    /** 
     * Parse passed String for an ANVL Record.
     * Looked at writing javacc grammer but preprocessing is required to
     * handle folding: See
     * https://javacc.dev.java.net/servlets/BrowseList?list=users&by=thread&from=56173.
     * Looked at Terence Parr's ANTLR.  More capable.  Can set lookahead count.
     * A value of 3 would help with folding.  But its a pain defining UNICODE
     * grammers -- needed by ANVL -- and support seems incomplete
     * anyways: http://www.doc.ic.ac.uk/lab/secondyear/Antlr/lexer.html#unicode.
     * For now, go with the below hand-rolled parser.
     * @param s String with an ANVLRecord.
     * @return ANVLRecord parsed from passed String.
     * @throws IOException 
     */
    public static ANVLRecord load(final String s)
    throws IOException {
        ANVLRecord record = new ANVLRecord();
        boolean inValue = false, inLabel = false, inComment = false, 
            inNewLine = false;
        String label = null;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0;  i < s.length(); i++) {
            char c = s.charAt(i);
           
            // Assert I can do look-ahead.
            if ((i + 1) > s.length()) {
                throw new IOException("Premature End-of-ANVLRecord:\n" +
                    s.substring(i));
            }
            
            // If at LF of a CRLF, just go around again. Eat up the LF.
            if (inNewLine && isLF(c)) {
                continue;
            }
            
            // If we're at a CRLF and we were just on one, exit. Found Record.
            if (inNewLine && isCR(c) && isLF(s.charAt(i + 1))) {
                break;
            }
            
            // Check if we're on a fold inside a Value. Skip multiple white
            // space after CRLF. 
            if (inNewLine && inValue && Character.isWhitespace(c)) {
                continue;
            }
            
            // Else set flag if we're at a CRLF.
            inNewLine = isCR(c) && isLF(s.charAt(i + 1));
            
            if (inNewLine) {
                if (inComment) {
                    inComment = false;
                } else if (label != null && !inValue) {
					// Label only 'data element'.
					record.addLabel(label);
					label = null;
					sb.setLength(0);
				} else if (inValue) {
					// Assert I can do look-ahead past current CRLF.
					if ((i + 3) > s.length()) {
						throw new IOException("Premature End-of-ANVLRecord "
							+ "(2):\n" + s.substring(i));
					}
					if (!isCR(s.charAt(i + 2)) && !isLF(s.charAt(i + 3))
							&& Character.isWhitespace(s.charAt(i + 2))) {
						// Its a fold.  Let it go around. But add in a CRLF and
						// space and do it here.  We don't let CRLF fall through
						// to the sb.append on the end of this loop.
						sb.append(CRLF);
						sb.append(' ');
					} else {
						// Next line is a new SubElement, a new Comment or
						// Label.
						record.addLabelValue(label, sb.toString());
						sb.setLength(0);
						label = null;
						inValue = false;
					}
				} else {
					// We're whitespace between label and value or whitespace
					// before we've figured whether label or comment.
				}
				// Don't let the '\r' or CRLF through.
				continue;
			}
            
            if (inComment) {
            	continue;
            } else if (inLabel) {
            	if (c == Label.COLON) {
            		label = sb.toString();
            		sb.setLength(0);
            		inLabel = false;
            		continue;
            	}
            } else {
            	if (!inLabel && !inValue && !inComment) {
            		// We have no state. Figure one.
            		if (Character.isWhitespace(c)) {
            			// If no state, and whitespace, skip. Don't record.
            			continue;
            		} else if (label == null && c == '#') {
            			inComment = true;
            			// Don't record comments.
            			continue;
            		} else if (label == null) {
            			inLabel = true;
            		} else {
            			inValue = true;
            		}
            	}
            }
			sb.append(c);
        }
        return record;
    }
    
    /**
     * @return Count of ANVLRecord bytes. Be careful, an empty ANVLRecord is
     * CRLFCRLF so is of size 4.  Also, expensive, since it makes String of
     * the record so it can count bytes.
     */
    public synchronized int getLength() {
        int length = -1;
        try {
            length = getUTF8Bytes().length;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return length;
    }
    
    public static boolean isCROrLF(final char c) {
        return isCR(c) || isLF(c);
    }
    
    public static boolean isCR(final char c) {
        return c == ANVLRecord.CRLF.charAt(0);
    }
    
    public static boolean isLF(final char c) {
        return c == ANVLRecord.CRLF.charAt(1);
    }
}