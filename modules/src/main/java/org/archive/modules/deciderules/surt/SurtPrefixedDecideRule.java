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

package org.archive.modules.deciderules.surt;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.archive.io.ReadSource;
import org.archive.modules.ProcessorURI;
import org.archive.modules.deciderules.PredicatedDecideRule;
import org.archive.modules.seeds.SeedListener;
import org.archive.modules.seeds.SeedModule;
import org.archive.net.UURI;
import org.archive.util.SurtPrefixSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;



/**
 * Rule applies configured decision to any URIs that, when 
 * expressed in SURT form, begin with one of the prefixes
 * in the configured set. 
 * 
 * The set can be filled with SURT prefixes implied or
 * listed in the seeds file, or another external file. 
 *
 * The "also-check-via" option to implement "one hop off" 
 * scoping derives from a contribution by Shifra Raffel
 * of the California Digital Library. 
 * 
 * @author gojomo
 */
public class SurtPrefixedDecideRule extends PredicatedDecideRule 
        implements SeedListener, Lifecycle {

    private static final long serialVersionUID = 3L;

    private static final Logger logger =
        Logger.getLogger(SurtPrefixedDecideRule.class.getName());


    /**
     * Source file from which to infer SURT prefixes. Any URLs in file will be
     * converted to the implied SURT prefix, and literal SURT prefixes may be
     * listed on lines beginning with a '+' character.
     */
    protected String surtsSourceFile = "";
    public String getSurtsSourceFile() {
        return surtsSourceFile;
    }
    public void setSurtsSourceFile(String surtsSourceFile) {
        this.surtsSourceFile = surtsSourceFile;
    }

    /**
     * Should seeds also be interpreted as SURT prefixes.
     */
    protected boolean seedsAsSurtPrefixes = true; 
    public boolean getSeedsAsSurtPrefixes() {
        return seedsAsSurtPrefixes;
    }
    public void setSeedsAsSurtPrefixes(boolean seedsAsSurtPrefixes) {
        this.seedsAsSurtPrefixes = seedsAsSurtPrefixes;
    }


    /**
     * Dump file to save SURT prefixes actually used: Useful debugging SURTs.
     */
    protected String surtsDumpFile = "";
    public String getSurtsDumpFile() {
        return surtsDumpFile;
    }
    public void setSurtsDumpFile(String surtsDumpFile) {
        this.surtsDumpFile = surtsDumpFile;
    }
    

    /**
     * Whether to rebuild the internal structures from source files (including
     * seeds if appropriate) every time any configuration change occurs. If
     * true, rule is rebuilt from sources even when (for example) unrelated new
     * domain overrides are set. Rereading large source files can take a long
     * time.
     */
    protected boolean rebuildOnReconfig = true; 
    public boolean getRebuildOnReconfig() {
        return rebuildOnReconfig;
    }
    public void setRebuildOnReconfig(boolean rebuildOnReconfig) {
        this.rebuildOnReconfig = rebuildOnReconfig;
    }

    /**
     * Whether to also make the configured decision if a URI's 'via' URI (the
     * URI from which it was discovered) in SURT form begins with any of the
     * established prefixes. For example, can be used to ACCEPT URIs that are
     * 'one hop off' URIs fitting the SURT prefixes. Default is false.
     */
    {
        setAlsoCheckVia(false);
    }
    public boolean getAlsoCheckVia() {
        return (Boolean) kp.get("alsoCheckVia");
    }
    public void setAlsoCheckVia(boolean checkVia) {
        kp.put("alsoCheckVia", checkVia);
    }

    protected SeedModule seeds;
    public SeedModule getSeeds() {
        return this.seeds;
    }
    @Autowired
    public void setSeeds(SeedModule seeds) {
        this.seeds = seeds;
    }
    
    protected SurtPrefixSet surtPrefixes = null;

    /**
     * Usual constructor. 
     */
    public SurtPrefixedDecideRule() {
    }

    
    public void start() {
        if(isRunning()) {
            return;
        }
        this.readPrefixes();
    }
    
    public boolean isRunning() {
        return surtPrefixes != null; 
    }
    
    public void stop() {
        surtPrefixes = null; 
    }

    /**
     * Evaluate whether given object's URI is covered by the SURT prefix set
     * 
     * @param object Item to evaluate.
     * @return true if item, as SURT form URI, is prefixed by an item in the set
     */
    @Override
    protected boolean evaluate(ProcessorURI uri) {
        if (getAlsoCheckVia()) {
            if (innerDecide(uri.getVia())) {
                return true;
            }
        }

        return innerDecide(uri.getUURI());
    }
    
    
    private boolean innerDecide(UURI uuri) {
        String candidateSurt;
        candidateSurt = SurtPrefixSet.getCandidateSurt(uuri);
        if (candidateSurt == null) {
            return false;
        }
        if (getPrefixes().containsPrefixOf(candidateSurt)) {
            return true;
        } else {
            return false;
        }
    }


    /**
     * Synchronized get of prefix set to use
     * 
     * @return SurtPrefixSet to use for check
     */
    private synchronized SurtPrefixSet getPrefixes() {
        if (surtPrefixes == null) {
            readPrefixes();
        }
        return surtPrefixes;
    }

    protected void readPrefixes() {
        buildSurtPrefixSet();
        dumpSurtPrefixSet();
    }
    
    /**
     * Dump the current prefixes in use to configured dump file (if any)
     */
    protected void dumpSurtPrefixSet() {
        // dump surts to file, if appropriate
        String dumpPath = getSurtsDumpFile();
        if (!StringUtils.isEmpty(dumpPath)) {
            File dump = new File(dumpPath);
            try {
                FileWriter fw = new FileWriter(dump);
                try {
                    surtPrefixes.exportTo(fw);
                } finally {
                    fw.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Construct the set of prefixes to use, from the seed list (
     * which may include both URIs and '+'-prefixed directives).
     */
    protected void buildSurtPrefixSet() {
        SurtPrefixSet newSurtPrefixes = new SurtPrefixSet();
        Reader fr = null;

        // read SURTs from file, if appropriate
        String sourcePath = getSurtsSourceFile();        
        if (!StringUtils.isEmpty(sourcePath)) {
            File source = new File(sourcePath);
            try {
                fr = new FileReader(source);
                try {
                    newSurtPrefixes.importFromMixed(fr, true);
                } finally {
                    fr.close();
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE,"Problem reading SURTs source file: "+e,e);
                // continue: operator will see severe log message or alert
            }
        }
        
        // interpret seeds as surts, if appropriate
        boolean deduceFromSeeds = getSeedsAsSurtPrefixes();
        if(deduceFromSeeds) {
            if(seeds instanceof ReadSource) {
                // scan text
                fr = ((ReadSource)seeds).getReader();
                newSurtPrefixes.importFromMixed(fr, deduceFromSeeds);
                IOUtils.closeQuietly(fr);
            }  else {
                // just deduce from URIs
                for(UURI u : seeds) {
                    newSurtPrefixes.addFromPlain(u.toCustomString());
                }
            }
        }
        surtPrefixes = newSurtPrefixes;
    }

    /**
     * Re-read prefixes after an update.
     */
    public synchronized void noteReconfiguration(/*KeyChangeEvent event*/) {
        if (getRebuildOnReconfig()) {
            readPrefixes();
        }
        // TODO: make conditional on file having actually changed,
        // perhaps by remembering mod-time
    }

    public synchronized void addedSeed(final ProcessorURI curi) {
        SurtPrefixSet newSurtPrefixes = (SurtPrefixSet) surtPrefixes.clone();
        newSurtPrefixes.add(prefixFrom(curi.toString()));
        surtPrefixes = newSurtPrefixes;
    }
    
    public void seedsRefreshed() {
        // TODO update?        
    }
    
    protected String prefixFrom(String uri) {
        return SurtPrefixSet.prefixFromPlainForceHttp(uri);
    }
}//EOC
