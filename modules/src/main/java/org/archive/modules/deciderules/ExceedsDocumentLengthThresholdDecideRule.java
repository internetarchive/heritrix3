package org.archive.modules.deciderules;

public class ExceedsDocumentLengthThresholdDecideRule 
extends NotExceedsDocumentLengthTresholdDecideRule {


    private static final long serialVersionUID = 3L;


    boolean test(int contentlength) {
        return contentlength > getContentLengthThreshold();        
    }
    
}
