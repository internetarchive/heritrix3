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

import org.apache.commons.lang.StringUtils;
import org.archive.modules.ProcessorURI;
import org.archive.modules.deciderules.DecideResult;
import org.archive.modules.deciderules.PredicatedDecideRule;
import org.archive.modules.seeds.SeedListener;
import org.archive.modules.seeds.SeedModule;
import org.archive.net.UURI;
import org.archive.spring.ConfigFile;
import org.archive.util.SurtPrefixSet;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

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
implements 
    SeedListener,
    InitializingBean
{
    private static final long serialVersionUID = 3L;
    private static final Logger logger =
        Logger.getLogger(SurtPrefixedDecideRule.class.getName());


    /**
     * Source file from which to infer SURT prefixes. Any URLs in file will be
     * converted to the implied SURT prefix, and literal SURT prefixes may be
     * listed on lines beginning with a '+' character.
     * TODO: consider changing to ReadSource, analogous to TextSeedModule, 
     * allowing inline specification? 
     */
    protected ConfigFile surtsSourceFile = 
        new ConfigFile("surtsSourceFile","");
    public ConfigFile getSurtsSourceFile() {
        return surtsSourceFile;
    }
    public void setSurtsSourceFile(ConfigFile cp) {
        this.surtsSourceFile.merge(cp);
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
        new ConfigFile("surtsDumpFile","");
    public ConfigFile getSurtsDumpFile() {
        return surtsDumpFile;
    }
    public void setSurtsDumpFile(ConfigFile cp) {
        this.surtsDumpFile.merge(cp);
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
        if(seeds!=null) {
            // in case this bean wasn't autowired to listeners (as if an
            // inner bean)
            seeds.addSeedListener(this);
        }
    }
    
    protected SurtPrefixSet surtPrefixes = new SurtPrefixSet();

    public SurtPrefixedDecideRule() {
    }

    public void afterPropertiesSet() throws Exception {
        readPrefixes();
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
        if (surtPrefixes.containsPrefixOf(candidateSurt)) {
            return true;
        } else {
            return false;
        }
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
        Reader fr = null;

        // read SURTs from file, if appropriate
        String sourcePath = getSurtsSourceFile().getPath();        
        if (!StringUtils.isEmpty(sourcePath)) {
            File source = getSurtsSourceFile().getFile();
            try {
                fr = new FileReader(source);
                try {
                    surtPrefixes.importFromMixed(fr, true);
                } finally {
                    fr.close();
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE,"Problem reading SURTs source file: "+e,e);
                // continue: operator will see severe log message or alert
            }
        }
    }

    /**
     * If appropriate, convert seed notification into prefix-addition.
     * 
     * @see org.archive.modules.seeds.SeedListener#addedSeed(org.archive.modules.ProcessorURI)
     */
    public void addedSeed(final ProcessorURI curi) {
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
    


}//EOC
