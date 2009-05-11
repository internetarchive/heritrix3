package org.archive.io;

import java.io.IOException;

public class SeekReaderCharSequence implements CharSequence {

    
    final private SeekReader reader;
    final private int size;
    

    public SeekReaderCharSequence(SeekReader reader, int size) {
        this.reader = reader;
        this.size = size;
    }
    
    
    public int length() {
        return size;
    }
    
    
    public char charAt(int index) {
        if ((index < 0) || (index >= length())) {
            throw new IndexOutOfBoundsException(Integer.toString(index));
        }
        try {
            reader.position(index);
            int r = reader.read();
            if (r < 0) {
                throw new IllegalStateException("EOF");
            }
            return (char)reader.read();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    
    public CharSequence subSequence(int start, int end) {
        return new CharSubSequence(this, start, end);
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        try {
            reader.position(0);
            for (int ch = reader.read(); ch >= 0; ch = reader.read()) {
                sb.append((char)ch);
            }
            return sb.toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
