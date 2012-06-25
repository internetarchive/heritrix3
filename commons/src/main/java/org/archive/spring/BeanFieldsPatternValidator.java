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

package org.archive.spring;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.BeanWrapperImpl;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class BeanFieldsPatternValidator implements Validator {
    public class PropertyPatternRule {
        protected String propertyName;
        protected Pattern requiredPattern; 
        protected String errorMessage;
        public PropertyPatternRule(String name, String pat, String msg) {
            propertyName = name;
            requiredPattern = Pattern.compile(pat);
            errorMessage = msg.replace("@@", pat);
        }
        public void test(BeanWrapperImpl wrapper, Errors errors) {
            Matcher m = requiredPattern.matcher(
                    (CharSequence)wrapper.getPropertyValue(propertyName));
            if(!m.matches()) {
                errors.rejectValue(propertyName, null, errorMessage);
            }
        }

    }

    protected Class<?> clazz;
    protected List<PropertyPatternRule> rules; 
    
    public BeanFieldsPatternValidator(Class<?> clazz, String ... fieldsPatterns) {
        this.clazz = clazz;
        if((fieldsPatterns.length % 3)!=0) {
            throw new IllegalArgumentException(
                    "variable arguments must be multiple of 3");
        }
        rules = new ArrayList<PropertyPatternRule>(); 
        for(int i = 0; i < fieldsPatterns.length; i=i+3) {
            rules.add(new PropertyPatternRule(fieldsPatterns[i],fieldsPatterns[i+1],fieldsPatterns[i+2]));
        }
    }

    public boolean supports(Class<?> cls) {
        return this.clazz.isAssignableFrom(cls);
    }

    public void validate(Object target, Errors errors) {
        BeanWrapperImpl w = new BeanWrapperImpl(target);
        for(PropertyPatternRule rule : rules) {
            rule.test(w,errors);
        }
    }

}
