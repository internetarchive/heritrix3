package org.archive.modules.seeds;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.archive.checkpointing.RecoverAction;
import org.archive.modules.ProcessorURI;
import org.archive.net.UURI;

public abstract class SeedModule implements Serializable, Iterable<UURI> {

    /**
     * Whether to reread the seeds specification, whether it has changed or not,
     * every time any configuration change occurs. If true, seeds are reread
     * even when (for example) new domain overrides are set. Rereading the seeds
     * can take a long time with large seed lists.
     */
    protected boolean rereadSeedsOnConfig = true;
    public boolean getRereadSeedsOnConfig() {
        return rereadSeedsOnConfig;
    }
    public void setRereadSeedsOnConfig(boolean rereadSeedsOnConfig) {
        this.rereadSeedsOnConfig = rereadSeedsOnConfig;
    }

    protected Set<SeedListener> seedListeners = 
        new HashSet<SeedListener>();

    protected void publishSeedsRefreshed() {
        for (SeedListener l: seedListeners) {
            l.seedsRefreshed();
        }
    }

    public SeedModule() {
        super();
    }
    
    public Iterator<UURI> iterator() {
        return seedsIterator();
    }
    
    public abstract boolean addSeed(final ProcessorURI curi);

    public abstract Iterator<UURI> seedsIterator(Writer ignoredItemWriter);

    public abstract Iterator<UURI> seedsIterator();

    public abstract void checkpoint(File dir, List<RecoverAction> actions) throws IOException;

    /**
     * Take note of a situation (such as settings edit) where
     * involved reconfiguration (such as reading from external
     * files) may be necessary.
     */
    public void noteReconfiguration() {
        // TODO: further improve this so that case with hundreds of
        // thousands or millions of seeds works better without requiring
        // this specific settings check 
        if (getRereadSeedsOnConfig()) {
            publishSeedsRefreshed();
        }
    }

    public void addSeedListener(SeedListener sl) {
        seedListeners.add(sl);
    }
}