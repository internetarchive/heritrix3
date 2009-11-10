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
package org.archive.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

import junit.framework.TestCase;

/**
 * Tests (and 
 * @author gojomo
 */
public class InterruptibleCharSequenceTest extends TestCase {
    // this regex takes many seconds to fail on the input
    // (~20 seconds on 2Ghz Athlon64 JDK 1.6)
    public static String BACKTRACKER = "^(((((a+)*)*)*)*)*$";
    public static String INPUT = "aaaaab";
    
    /**
     * Development-time benchmarking of InterruptibleCharSequence in
     * regex use. (Rename 'xest' to 'test' if wanted as unit test, 
     * but never actually fails anything -- just measures.)
     * 
     * For reference the regex "^(((((a+)*)*)*)*)*$" requires 
     * 239,286,636 charAt(s) to fail on "aaaaab", which takes
     * around 20 seconds on a 2Ghz Athlon64(x2) with JDK 1.6. 
     * The runtime overhead of checking interrupt status in this
     * extreme case is around 5% in my tests.
     */
    public void xestOverhead() {
        String regex = BACKTRACKER;
        String inputNormal = INPUT;
        InterruptibleCharSequence inputWrapped = new InterruptibleCharSequence(inputNormal); 
        // warm up 
        tryMatch(inputNormal,regex);
        tryMatch(inputWrapped,regex);
        // inputWrapped.counter=0;
        int trials = 5; 
        long stringTally = 0;
        long icsTally = 0; 
        for(int i = 1; i <= trials; i++) {
            System.out.println("trial "+i+" of "+trials);
            long start = System.currentTimeMillis();
            System.out.print("String ");
            tryMatch(inputNormal,regex);
            long end = System.currentTimeMillis();
            System.out.println(end-start); 
            stringTally += (end-start); 
            start = System.currentTimeMillis();
            System.out.print("InterruptibleCharSequence ");
            tryMatch(inputWrapped,regex);
            end = System.currentTimeMillis();
            System.out.println(end-start); 
            //System.out.println(inputWrapped.counter+" steps");
            //inputWrapped.counter=0;
            icsTally += (end-start); 
        }
        System.out.println("InterruptibleCharSequence took "+((float)icsTally)/stringTally+" longer."); 
    }
    
    public boolean tryMatch(CharSequence input, String regex) {
        return Pattern.matches(regex,input);
    }
    
    public Thread tryMatchInThread(final CharSequence input, final String regex, final BlockingQueue<Object> atFinish) {
        Thread t = new Thread() { 
            public void run() { 
                boolean result; 
                try {
                    result = tryMatch(input,regex); 
                } catch (Exception e) {
                    atFinish.offer(e);
                    return;
                }
                atFinish.offer(result);
            } 
        };
        t.start();
        return t; 
    }
    
    public void testNoninterruptible() throws InterruptedException {
        BlockingQueue<Object> q = new LinkedBlockingQueue<Object>();
        Thread t = tryMatchInThread(INPUT, BACKTRACKER, q);
        Thread.sleep(1000);
        t.interrupt();
        Object result = q.take(); 
        assertTrue("mismatch uncompleted",Boolean.FALSE.equals(result));
    }
    
    public void testInterruptibility() throws InterruptedException {
        BlockingQueue<Object> q = new LinkedBlockingQueue<Object>();
        Thread t = tryMatchInThread(new InterruptibleCharSequence(INPUT), BACKTRACKER, q);
        Thread.sleep(500);
        t.interrupt();
        Object result = q.take(); 
        if(result instanceof Boolean) {
            System.err.println(result+" match beat interrupt");
        }
        assertTrue("exception not thrown",result instanceof RuntimeException);
    }
}
