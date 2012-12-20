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
package org.archive.net;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

import org.apache.commons.httpclient.URIException;
import org.archive.url.UsableURI;

import com.esotericsoftware.kryo.CustomSerialization;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.serialize.StringSerializer;

/**
 * Usable URI. The bulk of the functionality of this class has moved to
 * {@link UsableURI} in the archive-commons project. This class adds Kryo
 * serialization.
 */
public class UURI extends UsableURI implements CustomSerialization {

    private static final long serialVersionUID = -8946640480772772310L;

    public UURI(String fixup, boolean b, String charset) throws URIException {
        super(fixup, b, charset);
    }

    public UURI(UsableURI base, UsableURI relative) throws URIException {
        super(base, relative);
    }
    
    /* needed for kryo serialization */
    protected UURI() {
        super();
    }

    @Override
    public void writeObjectData(Kryo kryo, ByteBuffer buffer) {
        StringSerializer.put(buffer, toCustomString());
    }

    @Override
    public void readObjectData(Kryo kryo, ByteBuffer buffer) {
        try {
            parseUriReference(StringSerializer.get(buffer), true);
        } catch (URIException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void writeObject(ObjectOutputStream stream) throws IOException {
        stream.writeUTF(toCustomString());
    }

    private void readObject(ObjectInputStream stream) throws IOException,
            ClassNotFoundException {
        parseUriReference(stream.readUTF(), true);
    }
}
