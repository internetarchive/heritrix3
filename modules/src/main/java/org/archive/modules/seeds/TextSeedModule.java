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

package org.archive.modules.seeds;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import org.archive.checkpointing.Checkpointable;
import org.archive.checkpointing.RecoverAction;
import org.archive.io.ReadSource;
import org.archive.modules.ProcessorURI;
import org.archive.net.UURI;
import org.archive.spring.WriteTarget;
import org.archive.util.DevUtils;
import org.springframework.beans.factory.annotation.Required;

/**
 * Module that provides a list of seeds from a text source (such
 * as a ConfigFile or ConfigString). 
 *
 * @contributor gojomo
 */
public class TextSeedModule extends SeedModule 
implements ReadSource,
           Serializable, 
           Checkpointable {
    private static final long serialVersionUID = 3L;

    private static final Logger logger =
        Logger.getLogger(TextSeedModule.class.getName());

    /**
     * Text from which to extract seeds
     */
    protected ReadSource textSource = null;
    public ReadSource getTextSource() {
        return textSource;
    }
    @Required
    public void setTextSource(ReadSource seedsSource) {
        this.textSource = seedsSource;
    }

    public TextSeedModule() {
    }

    /**
     * Gets an iterator over all configured seeds. Subclasses
     * which cache seeds in memory can override with more
     * efficient implementation. 
     *
     * @return Iterator, perhaps over a disk file, of seeds
     */
    @Override
    public Iterator<UURI> seedsIterator() {
        return seedsIterator(null);
    }
    
    /**
     * Gets an iterator over all configured seeds. Subclasses
     * which cache seeds in memory can override with more
     * efficient implementation. 
     *
     * @param ignoredItemWriter optional writer to get ignored seed items report
     * @return Iterator, perhaps over a disk file, of seeds
     */
    @Override
    public Iterator<UURI> seedsIterator(Writer ignoredItemWriter) {
        BufferedReader br = new BufferedReader(textSource.obtainReader());
        return new SeedFileIterator(br,ignoredItemWriter);
    }
    
    /**
     * Add a new seed to scope. By default, simply appends
     * to seeds file, though subclasses may handle differently.
     *
     * <p>This method is *not* sufficient to get the new seed 
     * scheduled in the Frontier for crawling -- it only 
     * affects the Scope's seed record (and decisions which
     * flow from seeds). 
     *
     * @param curi CandidateUri to add
     * @return true if successful, false if add failed for any reason
     */
    @Override
    public synchronized boolean addSeed(final ProcessorURI curi) {
        if(!(textSource instanceof WriteTarget)) {
            // TODO: do something else to log seed update
            logger.warning("nowhere to log added seed: "+curi);
        } else {
            try {
                Writer fw = ((WriteTarget)textSource).obtainWriter(true);
                // Write to new (last) line the URL.
                fw.write("\n");
                fw.write("# Heritrix added seed " +
                    ((curi.getVia() != null) ? "redirect from " + curi.getVia():
                        "(JMX)") + ".\n");
                fw.write(curi.toString());
                fw.flush();
                fw.close();
                Iterator<SeedListener> iter = seedListeners.iterator();
                while(iter.hasNext()) {
                    ((SeedListener)iter.next()).addedSeed(curi);
                }
                return true;
            } catch (IOException e) {
                DevUtils.warnHandle(e, "problem writing new seed");
            }
        }
        return false; 
    }
    
    @Override
    public void checkpoint(File dir, List<RecoverAction> actions)
            throws IOException {
        int id = System.identityHashCode(this);
        String backup = "seeds" + id + " .txt";
        backup.getBytes();
        //TODO:SPRINGY
//        FileUtils.copyFile(getSeedsFile().getFile(), new File(dir, backup));
//        actions.add(new SeedModuleRecoverAction(backup, getSeedsFile().getFile()));
    }

//    private static class SeedModuleRecoverAction implements RecoverAction {
//
//        private static final long serialVersionUID = 1L;
//
//        private File target;
//        private String backup;
//        
//        public SeedModuleRecoverAction(String backup, File target) {
//            this.target = target;
//            this.backup = backup;
//        }
//        
//        public void recoverFrom(File checkpointDir, CheckpointRecovery cr)
//                throws Exception {
//            target = new File(cr.translatePath(target.getAbsolutePath()));
//            FileUtils.copyFile(new File(checkpointDir, backup), target); 
//        }
//        
//    }

    public Reader obtainReader() {
        return textSource.obtainReader();
    }
}
