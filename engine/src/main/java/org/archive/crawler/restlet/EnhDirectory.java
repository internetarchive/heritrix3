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
import org.restlet.data.Reference;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Status;
import org.restlet.resource.Directory;
import org.restlet.resource.ServerResource;

/**
 * Enhanced version of Restlet Directory, which allows the local 
 * filesystem directory to be determined dynamically based on the 
 * request details. Also, via use of an EnhDirectoryResource, adds
 * other capabilities (editing, etc.).
 * 
 * @author gojomo
 */
public abstract class EnhDirectory extends Directory {
    protected IOFileFilter editFilter = FileFilterUtils.falseFileFilter(); 
    protected IOFileFilter pageFilter = FileFilterUtils.falseFileFilter(); 
    protected IOFileFilter tailFilter = FileFilterUtils.falseFileFilter();

    public EnhDirectory(Context context, Reference rootLocalReference) {
        super(context, rootLocalReference);
        // TODO Auto-generated constructor stub
    }

    public EnhDirectory(Context context, String rootUri) {
        super(context, rootUri);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void handle(Request request, Response response) {
        synchronized (this) {
            Reference oldRef = getRootRef();
            setRootRef(determineRootRef(request));
            try {
                super.handle(request, response);
            } finally {
                setRootRef(oldRef);
            }

            // XXX: FileRepresentation.isAvailable() returns false for empty files generating status 204 No Content
            // which confuses browsers. Force it back it 200 OK.
            if (response.getStatus() == Status.SUCCESS_NO_CONTENT) {
                response.setStatus(Status.SUCCESS_OK);
            }
        }
    }

    @Override
    public ServerResource create(Request request, Response response) {
        return new EnhDirectoryResource();
    }

    protected abstract Reference determineRootRef(Request request);

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
