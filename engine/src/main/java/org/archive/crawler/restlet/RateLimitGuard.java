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
package org.archive.crawler.restlet;

import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.ext.crypto.DigestAuthenticator;

import java.util.logging.Logger;

/**
 * ChallengeAuthenticator that slows and logs failed authentication attempts, to make
 * brute-force guessing attacks less feasible. 
 * 
 * @author gojomo
 */
public class RateLimitGuard extends DigestAuthenticator {
    private static final int MIN_MS_BETWEEN_ATTEMPTS = 6000;

    private static final Logger logger = Logger.getLogger(RateLimitGuard.class.getName());

    protected long lastFailureTime = 0;
    
    public RateLimitGuard(Context context, String realm, String serverKey) throws IllegalArgumentException {
        super(context, realm, serverKey);
    }

    @Override
    protected boolean authenticate(Request request, Response response) {
        boolean succeeded = super.authenticate(request, response);
        if (!succeeded) {
            logger.warning("authentication failure "+request);
            // wait until at least LAG has passed from last failure
            // holding object lock the whole time, so no other checks
            // can happen in parallel
            long now = System.currentTimeMillis();
            long sleepMs = (lastFailureTime+MIN_MS_BETWEEN_ATTEMPTS)-now;
            if(sleepMs>0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            lastFailureTime = now + sleepMs;
        }
        return succeeded;
    }
}
