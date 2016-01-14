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

package org.archive.modules.forms;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.archive.util.TextUtils;

/**
 * Simple representation of a discovered HTML Form. 
 * 
 * @contributor gojomo
 */
public class HTMLForm {
    public class FormInput {
        public String type;
        public String name;
        public String value;
        public boolean checked = false;
        @Override
        public String toString() {
            String str = "input[@type='" + type+"'][@name='" + name + "'][@value='" + value + "']";
            if (checked) {
                str = str + "[@checked]";
            }
            return str;
        } 
    }
    
    String method;
    String action;
    String enctype;
    
    List<FormInput> allInputs = new ArrayList<FormInput>();
    List<FormInput> candidateUsernameInputs = new ArrayList<FormInput>();
    List<FormInput> candidatePasswordInputs = new ArrayList<FormInput>();

    /**
     * Add a discovered INPUT, tracking it as potential 
     * username/password receiver. 
     * @param type
     * @param name
     * @param value
     * @param checked true if "checked" attribute is present (for radio buttons and checkboxes)
     */
    public void addField(String type, String name, String value, boolean checked) {
        FormInput input = new FormInput();
        input.type = type;
        
        if (isMultipleFormSubmitInputs(type)) {
            return;
        }
        
        // default input type is text per html standard
        if (input.type == null) {
            input.type = "text";
        }
        
        input.name = name;
        input.value = value; 
        allInputs.add(input);
        if("text".equalsIgnoreCase(input.type) || "email".equalsIgnoreCase(input.type)) {
            candidateUsernameInputs.add(input);
        } else if ("password".equalsIgnoreCase(type)) {
            candidatePasswordInputs.add(input);
        }
        input.checked = checked;
    }

    /**
     * Add a discovered INPUT, tracking it as potential 
     * username/password receiver. 
     * @param type
     * @param name
     * @param value
     */
    public void addField(String type, String name, String value) {
        addField(type, name, value, false);
    }
    
    public boolean isMultipleFormSubmitInputs(String type) {
        if (!type.toLowerCase().equals("submit")) return false;
        
        for (FormInput input : allInputs) {
            if (input.type.toLowerCase().equals("submit")) {
                return true;
            }
        }
        
        return false;
    }

    public void setMethod(String method) {
        this.method = method; 
    }

    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public String getEnctype() {
        return enctype;
    }

    public void setEnctype(String enctype) {
        this.enctype = enctype;
    }

    /**
     * For now, we consider a POST form with only 1 password
     * field and 1 potential username field (type text or email)
     * to be a likely login form.
     * 
     * @return boolean likely login form
     */
    public boolean seemsLoginForm() {
        if ("post".equalsIgnoreCase(method)) {
            if (candidatePasswordInputs.size() == 1) {
                if (candidateUsernameInputs.size() == 1) {
                    return true;
                }               
                else if (candidateUsernameInputs.size() > 1) {
                    for (FormInput formInput : candidateUsernameInputs) {
                        if (formInput.name != null && formInput.name.toLowerCase().indexOf("login") > 0) {
                            return true;
                        }
                    }
                }
            }            
        }
        
        return false;
    }

    /**
     * Create the NameValuePair array expected by HttpClient, merging
     * username and password into the appropriate value slots.
     * 
     * @param username
     * @param password
     * @return
     * @deprecated specific to a particular FetchHTTP implementation based on commons-httpclient, use {@link #asFormDataString(String, String)}
     */
    public NameValuePair[] asHttpClientDataWith(String username, String password) {
        ArrayList<NameValuePair> data = new ArrayList<NameValuePair>(allInputs.size());
       
        for (FormInput input : allInputs) {
            if(input == candidateUsernameInputs.get(0)) {
                data.add(new NameValuePair(input.name,username));
            } else if(input == candidatePasswordInputs.get(0)) {
                data.add(new NameValuePair(input.name,password));
            } else if (StringUtils.isNotEmpty(input.name) && StringUtils.isNotEmpty(input.value)) {
                data.add(new NameValuePair(input.name,input.value));
            }
        }
        return data.toArray(new NameValuePair[data.size()]);
    }

    public FormInput getLoginInputFromCandidates() {
        if (candidateUsernameInputs == null) return null;
        
        if (candidateUsernameInputs.size() == 1) return candidateUsernameInputs.get(0);
        
        for (FormInput input : candidateUsernameInputs) {
            if (input.name != null && input.name.toLowerCase().indexOf("login") != -1) {
                return input;
            }
        }
        
        return candidateUsernameInputs.get(0);
    }
    
    public HttpEntity asFormDataMultiPartEntity(String username, String password) {
        MultipartEntityBuilder multiPartEntityBuilder = MultipartEntityBuilder.create();
        multiPartEntityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        
        for (FormInput input : allInputs) {
            if(input == getLoginInputFromCandidates()) {
                multiPartEntityBuilder.addPart(input.name, new StringBody(username, ContentType.MULTIPART_FORM_DATA));
            } else if(input == candidatePasswordInputs.get(0)) {
                multiPartEntityBuilder.addPart(input.name, new StringBody(password, ContentType.MULTIPART_FORM_DATA));
            } else if (StringUtils.isNotEmpty(input.name)) {
                multiPartEntityBuilder.addPart(input.name, new StringBody(StringUtils.isNotEmpty(input.value) ? input.value : "", ContentType.MULTIPART_FORM_DATA));                
            }
        }
       
        return multiPartEntityBuilder.build();
    }
    
    public String asFormDataString(String username, String password) {
        List<String> nameVals = new LinkedList<String>();

        for (FormInput input : allInputs) {
            if(input == candidateUsernameInputs.get(0)) {
                nameVals.add(TextUtils.urlEscape(input.name) + "=" + TextUtils.urlEscape(username));
            } else if(input == candidatePasswordInputs.get(0)) {
                nameVals.add(TextUtils.urlEscape(input.name) + "=" + TextUtils.urlEscape(password));
            } else if (StringUtils.isNotEmpty(input.name)
                    && StringUtils.isNotEmpty(input.value)
                    && (!"radio".equalsIgnoreCase(input.type)
                            && !"checkbox".equals(input.type) || input.checked)) {
                nameVals.add(TextUtils.urlEscape(input.name) + "="
                        + TextUtils.urlEscape(input.value));
            }
        }

        return StringUtils.join(nameVals, '&');
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(); 
        sb.append(method);
        sb.append(" ");
        sb.append(action); 
        for(FormInput input : allInputs) {
            sb.append("\n  ");
            sb.append(input.type);
            sb.append(" ");
            sb.append(input.name);
            sb.append(" ");
            sb.append(input.value);
        }
        return sb.toString();
    }
    
    /**
     * Provide abbreviated annotation, of the form...
     *  "form:Phhpt"
     * 
     * ...where the first capital letter indicates submission
     * type, G[ET] or P[OST], and following lowercase letters
     * types of inputs in order, by their first letter. 
     * 
     * @return String suitable for brief crawl.log annotation
     */
    public String asAnnotation() {
        StringBuilder sb = new StringBuilder(); 
        sb.append("form:");
        sb.append(Character.toUpperCase(method.charAt(0)));
        for(FormInput input : allInputs) {
            sb.append(Character.toLowerCase(input.type.charAt(0)));
        }
        return sb.toString();
    }
}
