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

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.restlet.Context;
import org.restlet.Handler;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;

/**
 * Enhanced version of Restlet Directory, which allows the local 
 * filesystem directory to be determined dynamically based on the 
 * request details. Also, via use of an EnhDirectoryResource, adds
 * other capabilities (editting, etc.).
 * 
 * @contributor gojomo
 */
public abstract class EnhDirectory extends org.restlet.Directory {
    IOFileFilter editFilter = FileFilterUtils.falseFileFilter(); 
    IOFileFilter pageFilter = FileFilterUtils.falseFileFilter(); 
    IOFileFilter tailFilter = FileFilterUtils.falseFileFilter();
    
    public EnhDirectory(Context context, Reference rootLocalReference) {
        super(context, rootLocalReference);
        // TODO Auto-generated constructor stub
    }

    public EnhDirectory(Context context, String rootUri) {
        super(context, rootUri);
        // TODO Auto-generated constructor stub
    }

    @Override
    public Handler findTarget(Request request, Response response) {
        Handler retVal; 
        synchronized(this) {
            Reference oldRef = getRootRef();
            setRootRef(determineRootRef(request));
            try {
                retVal = new EnhDirectoryResource(this, request, response);
            } catch (IOException ioe) {
                getLogger().log(Level.WARNING,
                        "Unable to find the directory's resource", ioe);
                retVal = null;
            }
            setRootRef(oldRef); 
        }
        return retVal;
    }

    abstract Reference determineRootRef(Request request);

    public boolean allowsEdit(File file) {
        return editFilter.accept(file);
    }

    public void setEditFilter(IOFileFilter fileFilter) {
        editFilter = fileFilter; 
    }

    public boolean allowsPaging(File file) {
        // TODO: limit? 
        return true;
    }

    
}
