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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.archive.util.IoUtils;

/**
 * Executes checkpoints and recovers.
 * 
 * @author pjack
 */
public class Checkpointer {

    final public static String ACTIONS_FILE = "actions.serialized";
    
    final public static String OBJECT_GRAPH_FILE = "object_graph.serialized";
    
    private Checkpointer() {
    }


    public static void checkpoint(/*SheetManager*/Object mgr, File dir) 
    throws IOException {
        List<RecoverAction> actions = new ArrayList<RecoverAction>();
//        for (Checkpointable c: mgr.getCheckpointables()) {
//            c.checkpoint(dir, actions);
//        }
        
        writeObject(new File(dir, ACTIONS_FILE), actions);
        writeObject(new File(dir, OBJECT_GRAPH_FILE), mgr);
    }

    
    private static void writeObject(File f, Object o) 
    throws IOException {
        ObjectOutputStream oout = null;
        try {
            oout = new ObjectOutputStream(new FileOutputStream(f));
            oout.writeObject(o);
        } finally {
            IoUtils.close(oout);
        }
        
    }
    

    @SuppressWarnings("unused")
    private static List<RecoverAction> readActions(File dir) 
    throws IOException {
        File actionsFile = new File(dir, ACTIONS_FILE);
        ObjectInputStream oinp = null;
        try {
            oinp = new ObjectInputStream(
                    new FileInputStream(actionsFile));
            @SuppressWarnings("unchecked")
            List<RecoverAction> actions = (List)oinp.readObject();
            return actions;
        } catch (ClassNotFoundException e) {
            IOException io = new IOException();
            io.initCause(e);
            throw io;
        } finally {
            IoUtils.close(oinp);
        }
    }

//    public static SheetManager recover(File dir, CheckpointRecovery recovery) 
//    throws IOException {
//        List<RecoverAction> actions = readActions(dir);
//        for (RecoverAction action: actions) try {
//            action.recoverFrom(dir, recovery);
//        } catch (Exception e) {
//            IOException io = new IOException();
//            io.initCause(e);
//            throw io;
//        }
//        
//        CheckpointInputStream cinp = null;
//        try {
//            File f = new File(dir, OBJECT_GRAPH_FILE);
//            cinp = new CheckpointInputStream(new FileInputStream(f), recovery);
//            SheetManager mgr = (SheetManager)cinp.readObject();
//            recovery.apply(mgr.getGlobalSheet());
//            return mgr;
//        } catch (ClassNotFoundException e) { 
//            IOException io = new IOException();
//            io.initCause(e);
//            throw io;
//        }finally {
//            IoUtils.close(cinp);
//        }
//    }

}
