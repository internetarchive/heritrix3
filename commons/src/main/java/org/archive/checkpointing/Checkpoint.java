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
package org.archive.checkpointing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.archive.spring.ConfigPath;
import org.archive.util.ArchiveUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;

/**
 * Represents a single checkpoint, by its name and main store directory.
 * 
 * @contributor gojomo
 */
public class Checkpoint implements InitializingBean {
    private final static Logger LOGGER =
        Logger.getLogger(Checkpoint.class.getName());

    /** format for serial numbers */
    public static final DecimalFormat INDEX_FORMAT = new DecimalFormat("00000");
    /** Name of file written with timestamp into valid checkpoints */
    public static final String VALIDITY_STAMP_FILENAME = "valid";
    
    String name; 
    String shortName; 
    boolean success = false;
    
    /**
     * Checkpoints directory; either an absolute path, or relative to the 
     * CheckpointService's checkpointsDirectory (which will be inserted as
     * the COnfigPath base before the Checkpoint is consulted). 
     */
    protected ConfigPath checkpointDir = 
        new ConfigPath("checkpoint directory","");
    public ConfigPath getCheckpointDir() {
        return checkpointDir;
    }
    @Required // if used in Spring context
    public void setCheckpointDir(ConfigPath checkpointsDir) {
        this.checkpointDir = checkpointsDir;
    }
    
    public Checkpoint() {
    }

    /**
     * Use immediately after instantiation to fill-in a Checkpoint 
     * created outside Spring configuration.
     * 
     * @param checkpointsDir
     * @param nextCheckpointNumber
     * @throws IOException 
     */
    public void generateFrom(ConfigPath checkpointsDir, int nextCheckpointNumber) throws IOException {
        getCheckpointDir().setBase(checkpointsDir);
        getCheckpointDir().setPath(
                "cp" 
                + INDEX_FORMAT.format(nextCheckpointNumber) 
                + "-" 
                + ArchiveUtils.get14DigitDate());
        org.archive.util.FileUtils.ensureWriteableDirectory(getCheckpointDir().getFile());
        afterPropertiesSet();
    }
    
    public void afterPropertiesSet() {
        name = checkpointDir.getFile().getName();
        shortName = name.substring(0, name.indexOf("-"));
    }
    
    public void setSuccess(boolean b) {
        success = b; 
    }
    public boolean getSuccess() {
        return success;
    }
    
    public String getName() {
        return name; 
    }
    public String getShortName() {
        return shortName; 
    }
    
    public void writeValidity(String stamp) {
        if(!success) {
            return;
        }
        File valid = new File(checkpointDir.getFile(), VALIDITY_STAMP_FILENAME);
        try {
            FileUtils.writeStringToFile(valid, stamp);
        } catch (IOException e) {
            valid.delete();
        }
    }
 
    public void saveJson(String beanName, JSONObject json) {
        try {
            File targetFile = new File(getCheckpointDir().getFile(),beanName);
            FileUtils.writeStringToFile(
                    targetFile,
                    json.toString());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,"unable to save checkpoint JSON state of "+beanName,e);
            setSuccess(false); 
        }
    }
    
    public JSONObject loadJson(String beanName) {
        File sourceFile = new File(getCheckpointDir().getFile(),beanName);
        try {
            return new JSONObject(FileUtils.readFileToString(sourceFile));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public BufferedWriter saveWriter(String beanName, String extraName) throws IOException {
        try {
            File targetFile = new File(getCheckpointDir().getFile(),beanName+"-"+extraName);
            return new BufferedWriter(new FileWriter(targetFile)); 
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,"unable to save checkpoint writer state "+extraName+" of "+beanName,e);
            setSuccess(false); 
            throw e;
        }
    }
    
    public BufferedReader loadReader(String beanName, String extraName) throws IOException {
        File sourceFile = new File(getCheckpointDir().getFile(),beanName+"-"+extraName);
        return new BufferedReader(new FileReader(sourceFile));
    }
    
    public static boolean hasValidStamp(File checkpointDirectory) {
        return (new File(checkpointDirectory,Checkpoint.VALIDITY_STAMP_FILENAME)).exists();
    }
}