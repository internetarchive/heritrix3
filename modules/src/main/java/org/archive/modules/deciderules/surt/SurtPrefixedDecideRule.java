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
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.archive.checkpointing.Checkpoint;
import org.archive.checkpointing.Checkpointable;
import org.archive.io.ReadSource;
import org.archive.modules.CrawlURI;
import org.archive.modules.deciderules.DecideResult;
import org.archive.modules.deciderules.PredicatedDecideRule;
import org.archive.modules.seeds.SeedListener;
import org.archive.modules.seeds.SeedModule;
import org.archive.net.UURI;
import org.archive.spring.ConfigFile;
import org.archive.util.SurtPrefixSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextStartedEvent;

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
public class SurtPrefixedDecideRule extends PredicatedDecideRule implements
        SeedListener, ApplicationListener<ApplicationEvent>, Checkpointable,
        BeanNameAware {
    
    private static final long serialVersionUID = 3L;
    private static final Logger logger =
        Logger.getLogger(SurtPrefixedDecideRule.class.getName());

    /**
     * Source file from which to infer SURT prefixes. Any URLs in file will be
     * converted to the implied SURT prefix, and literal SURT prefixes may be
     * listed on lines beginning with a '+' character.
     * 
     * @deprecated redundant now that we have
     *             {@link SurtPrefixedDecideRule#surtsSource}
     */
    public ConfigFile getSurtsSourceFile() {
        if (getSurtsSource() instanceof ConfigFile) {
            return (ConfigFile) getSurtsSource();
        } else {
            return null;
        }
    }
    /** @deprecated */
    public void setSurtsSourceFile(ConfigFile cp) {
        setSurtsSource(cp);
    }

    /**
     * Text from which to infer SURT prefixes. Any URLs will be converted to the
     * implied SURT prefix, and literal SURT prefixes may be listed on lines
     * beginning with a '+' character.
     */
    protected ReadSource surtsSource = null;
    public ReadSource getSurtsSource() {
        return surtsSource;
    }
    public void setSurtsSource(ReadSource surtsSource) {
        this.surtsSource = surtsSource;
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
    protected ConfigFile surtsDumpFile = 
        new ConfigFile("surtsDumpFile","${launchId}/surts.dump");
    public ConfigFile getSurtsDumpFile() {
        return surtsDumpFile;
    }
    public void setSurtsDumpFile(ConfigFile cp) {
        this.surtsDumpFile.merge(cp);
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
        if(seeds!=null) {
            // in case this bean wasn't autowired to listeners (as if an
            // inner bean)
            seeds.addSeedListener(this);
        }
    }
    
    protected SurtPrefixSet surtPrefixes = new SurtPrefixSet();

    public SurtPrefixedDecideRule() {
    }
    
    public void concludedSeedBatch() {
        dumpSurtPrefixSet();
    }
 
    /**
     * Evaluate whether given object's URI is covered by the SURT prefix set
     * 
     * @param object Item to evaluate.
     * @return true if item, as SURT form URI, is prefixed by an item in the set
     */
    @Override
    protected boolean evaluate(CrawlURI uri) {
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
        if (surtPrefixes.containsPrefixOf(candidateSurt)) {
            return true;
        } else {
            return false;
        }
    }

    protected void readPrefixes() {
        buildSurtPrefixSet();
    }
    
    /**
     * Dump the current prefixes in use to configured dump file (if any)
     */
    protected void dumpSurtPrefixSet() {
        // dump surts to file, if appropriate
        String dumpPath = getSurtsDumpFile().getPath();
        if (!StringUtils.isEmpty(dumpPath)) {
            File dump = getSurtsDumpFile().getFile();
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
        if (getSurtsSource() != null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("reading surt prefixes from " + getSurtsSource());
            }
            Reader reader = getSurtsSource().obtainReader();
            try {
                surtPrefixes.importFromMixed(reader, true);
            } finally {
                IOUtils.closeQuietly(reader);
            }
        }
    }

    /**
     * If appropriate, convert seed notification into prefix-addition.
     * 
     * @see org.archive.modules.seeds.SeedListener#addedSeed(org.archive.modules.CrawlURI)
     */
    public void addedSeed(final CrawlURI curi) {
        if(getSeedsAsSurtPrefixes()) {
            surtPrefixes.add(prefixFrom(curi.getURI()));
        }
    }
    
    protected String prefixFrom(String uri) {
        return SurtPrefixSet.prefixFromPlainForceHttp(uri);
    }
    
    
    private static final String DEFAULT_ACCEPT_ADD_PREFIX_DIRECTIVE = "+";
    private static final String DEFAULT_REJECT_ADD_PREFIX_DIRECTIVE = "-";

    /** 
     * Consider nonseed lines as possible SURT prefix directives. If we 
     * are ACCEPTing URIs, expect '+' as the add-SURT directive. If we
     * are REJECTing URIs, expect '-' as the add-SURT directive. 
     * 
     * TODO: perhaps, make directive configurable, so many SeedListener
     * SurtPrefixedDecideRules can all listen for directives meant only 
     * for them. 
     * 
     * @see org.archive.modules.seeds.SeedListener#nonseedLine(java.lang.String)
     */
    public boolean nonseedLine(String line) {
        String effectiveDirective = getEffectiveAddDirective();
        if(line.startsWith(effectiveDirective)) {
            return surtPrefixes.considerAsAddDirective(line.substring(effectiveDirective.length()));
        } else {
            // not a line this instance is interested in
            return false; 
        }
    }
    
    private String getEffectiveAddDirective() {
        if(getDecision()==DecideResult.ACCEPT) {
            return DEFAULT_ACCEPT_ADD_PREFIX_DIRECTIVE;
        }
        if(getDecision()==DecideResult.REJECT) {
            return DEFAULT_REJECT_ADD_PREFIX_DIRECTIVE;
        }
        throw new IllegalArgumentException("decision must be ACCEPT or REJECT");
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextStartedEvent) {
            if (recoveryCheckpoint != null) {
                JSONObject json = recoveryCheckpoint.loadJson(beanName);
                try {
                    JSONArray jsonArray = json.getJSONArray("surtPrefixes");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        surtPrefixes.add(jsonArray.getString(i));
                    }
                } catch (JSONException e) {
                    throw new IllegalStateException(e);
                }
            } else {
                readPrefixes();
            }
        }
    }
    
    // BeanNameAware
    protected String beanName; 
    public void setBeanName(String name) {
        this.beanName = name;
    }

    @Override
    public void startCheckpoint(Checkpoint checkpointInProgress) {
    }
    
    @Override
    public void doCheckpoint(Checkpoint checkpointInProgress)
            throws IOException {
        try {
            JSONObject json = new JSONObject();
            json.put("surtPrefixes", surtPrefixes);
            checkpointInProgress.saveJson(beanName, json);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public void finishCheckpoint(Checkpoint checkpointInProgress) {
    }
    
    protected Checkpoint recoveryCheckpoint;
    @Override
    public void setRecoveryCheckpoint(Checkpoint recoveryCheckpoint) {
        this.recoveryCheckpoint = recoveryCheckpoint;
    }

}//EOC
