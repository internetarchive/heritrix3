package org.archive.modules.revisit;

import java.util.Map;

public interface RevisitProfile {

	public String getProfileName();
	
	public Map<String, String> getWarcHeaders();
	
}
