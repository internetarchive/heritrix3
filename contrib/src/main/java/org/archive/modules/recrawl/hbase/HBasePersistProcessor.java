package org.archive.modules.recrawl.hbase;

import org.archive.modules.recrawl.AbstractPersistProcessor;
import org.springframework.beans.factory.annotation.Required;

/**
 * A base class for processors for keeping de-duplication data in HBase.
 * <p>table schema is currently fixed. all data stored in single column
 * family {@code f}, with following columns:
 * <ul>
 * <li>{@code s}: fetch status (as integer text)</li>
 * <li>{@code d}: content digest (with {@code sha1:} prefix, Base32 text)</li>
 * <li>{@code e}: ETag (enclosing quotes stripped)</li>
 * <li>{@code m}: last-modified date-time (as integer timestamp, binary format)</li>
 * <li>{@code z}: do-not-crawl flag - loader discards URL if this column has non-empty value.</li> 
 * </ul>
 * </p>
 * @contributor kenji
 */
public abstract class HBasePersistProcessor extends AbstractPersistProcessor {

	protected HBaseTable table;
	public void setTable(HBaseTable table) {
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

	public HBasePersistProcessor() {
		super();
	}
}
