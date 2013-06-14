package org.archive.modules.recrawl.hbase;

import java.io.IOException;

import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.archive.modules.recrawl.PersistOnlineProcessor;
import org.springframework.context.Lifecycle;

/**
 * base class for different types of HBaseTable Spring bean implementations.
 * @contributor kenji
 * @contributor nlevitt
 *
 */
public abstract class HBaseTableBean implements Lifecycle {

    protected String htableName = PersistOnlineProcessor.URI_HISTORY_DBNAME;
    protected HBase hbase = new HBase();
    protected transient boolean isRunning = false;

    // <backward-compatibility>
    public void setName(String name) {
        this.htableName = name;
    }

    public String getName() {
        return htableName;
    }
    // </backward-compatibility>
    
    /**
     * set name of single HTable this instance accesses.
     * @param htableName
     */
    public void setHtableName(String htableName) {
        this.htableName = htableName;
    }
    public String getHtableName() {
        return htableName;
    }

    public HBaseTableBean() {
        super();
    }

    public void setHbase(HBase hbase) {
        this.hbase = hbase;
    }

    public HBase getHbase() {
        return hbase;
    }

    public abstract void put(Put p) throws IOException;

    public abstract Result get(Get g) throws IOException;
    
    public abstract HTableDescriptor getHtableDescriptor() throws IOException;

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void start() {
        isRunning = true;
    }

    @Override
    public synchronized void stop() {
        isRunning = false;
    }

}