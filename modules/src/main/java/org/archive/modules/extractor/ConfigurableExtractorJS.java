package org.archive.modules.extractor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p>Subclasses the standard ExtractorJS to add some configuration option. All options default to
 * the standard behavior of {@link ExtractorJS}. All configuration options can be overridden on
 * a per-sheet basis.</p>
 * 
 * <p>
 * The configuration options are:
 * <ul>
 *   <li>
 *   	<b>strict</b>: Enables strict mode where only non-relative URLs are extracted. Any 
 *      extracted potential URLs not starting with a scheme are ignored.
 *   </li>
 *   <li>
 *   	<b>maximumCandidateLength</b>: Maximum length of extracted potential URLs. Any string longer than
 *      this will be ignored. 
 *   </li>
 *   <li>
 *   	<b>rejectRelativeIgnoreSet</b>: A set of literal strings that should never be considered.
 *      Any extracted potential URLs matching a value in this set will be ignored. 
 *   </li>
 *   <li>
 *   	<b>rejectRelativeMatchingRegexList</b>: A list of regular expressions. Any extracted 
 *      potential URLs matching a regular expression on this list will be ignored. 
 *   </li>
 * </ul>
 * </p> 
 */
public class ConfigurableExtractorJS extends ExtractorJS {
	
    /** If true, then only extract non-relative paths */
    public boolean getStrict() {
        return (Boolean) kp.get("strictMode");
    }
    public void setStrict(boolean strict) {
        kp.put("strictMode", strict);
    }

    /** Maximum length of extracted potential URLs */
    public int getMaximumCandidateLength() {
        return (Integer) kp.get("maximumCandidateLength");
    }
    public void setMaximumCandidateLength(int maximumCandidateLength) {
        kp.put("maximumCandidateLength", maximumCandidateLength);
    }
    {
    	setMaximumCandidateLength(-1);
    }

    
    /**
     * The list of regular expressions to evalute potential <em>relative</em> url against, rejecting any that match.
     */
    private List<String> rejectRelativeMatchingRegexList = new ArrayList<>();
    private List<Pattern> rejectRelativeMatchingRegexListPatterns;

    public List<String> getRejectRelativeMatchingRegexList() {
        return rejectRelativeMatchingRegexList;
    }
    public void setRejectRelativeMatchingRegexList(List<String> patterns) {
    	rejectRelativeMatchingRegexList = patterns;
    	rejectRelativeMatchingRegexListPatterns = new ArrayList<>();
    	for (String p : patterns) {
    		rejectRelativeMatchingRegexListPatterns.add(Pattern.compile(p, Pattern.CASE_INSENSITIVE));
    	}
    }
    public void addRejectRelativeMatchingRegex(String pattern) {
		rejectRelativeMatchingRegexListPatterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
    }
    


    /**
     * The list of literal strings that should never be extracted as a potential <em>relative</em> url 
     */
    private Set<String> rejectRelativeIgnoreSet = new HashSet<>();
    
    public List<String> getRejectRelativeIgnoreList() {
        return rejectRelativeIgnoreSet.stream().collect(Collectors.toList());
    }
    public void setRejectRelativeIgnoreList(Set<String> ignoreStrings) {
    	rejectRelativeIgnoreSet.clear();
    	rejectRelativeIgnoreSet.addAll(ignoreStrings);
    }
    public void addRejectRelativeIgnoreList(String ignoreString) {
		rejectRelativeIgnoreSet.add(ignoreString);
    }
    
    
    @Override
    protected boolean shouldAddUri(String candidate) {
    	return passesMaxLength(candidate) &&
    			passesStrictMode(candidate) && 
    			super.shouldAddUri(candidate) &&  
        		!isOnRejectList(candidate);
    }
    
    
    protected boolean passesMaxLength(String candidate) {
    	int max = getMaximumCandidateLength();
    	return max <= 0 || candidate.length() <= max;
    }
    
    protected boolean passesStrictMode(String candidate) {
    	return !getStrict() || hasScheme(candidate);
    }
    
    protected boolean hasScheme(String candidate) {
    	return candidate.startsWith("http://") || candidate.startsWith("https://");
    }
    
    protected boolean isOnRejectList(String candidate) {
		if (hasScheme(candidate)) {
			// Absolute path. Assume it is ok.
			return false;
		}

		// Filter using literal blacklist
		if (rejectRelativeIgnoreSet.contains(candidate)) {
			return true;
		}

		// Filter using regex blacklist
		for (Pattern p : rejectRelativeMatchingRegexListPatterns) {
			if (p.matcher(candidate).matches()) {
				return true;
			}
		}

		return false;
	}
    
}
