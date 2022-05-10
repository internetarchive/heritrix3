package org.archive.modules.warc;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

public abstract class BaseWARCRecordBuilder implements WARCRecordBuilder {
    public static URI generateRecordID() {
        try {
            return new URI("urn:uuid:" + UUID.randomUUID());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e); // impossible 
        }
    }
}
