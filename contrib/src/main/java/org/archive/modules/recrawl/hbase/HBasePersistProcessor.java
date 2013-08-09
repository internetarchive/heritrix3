package org.archive.modules.recrawl.hbase;

import org.archive.modules.CrawlURI;
import org.archive.modules.recrawl.AbstractPersistProcessor;
import org.springframework.beans.factory.annotation.Required;

/**
 * A base class for processors for keeping de-duplication data in HBase.
 * Table schema is defined by {@link RecrawlDataSchema} implementation. 
 * @contributor kenji
 */
public abstract class HBasePersistProcessor extends AbstractPersistProcessor {

    protected HBaseTableBean table;
    @Required
    public void setTable(HBaseTableBean table) {
        this.table = table;
    }

    protected RecrawlDataSchema schema;
    public RecrawlDataSchema getSchema() {
        return schema;
    }
    @Required
    public void setSchema(RecrawlDataSchema schema) {
        this.schema = schema;
    }

    protected byte[] rowKeyForURI(CrawlURI curi) {
        return schema.rowKeyForURI(curi);
    }
}
