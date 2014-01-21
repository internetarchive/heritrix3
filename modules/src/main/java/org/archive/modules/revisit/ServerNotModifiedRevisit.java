package org.archive.modules.revisit;

import static org.archive.format.warc.WARCConstants.*;

import java.util.HashMap;
import java.util.Map;

import org.archive.format.warc.WARCConstants;

public class ServerNotModifiedRevisit implements RevisitProfile {
	
	protected String refersToDate;
	protected String refersToRecordID;
	
	// From HTTP response
	protected String eTag;
	protected String lastModified;
	
	/**
	 * Minimal constructor.
	 */
	public ServerNotModifiedRevisit() {
	}
	
		
	@Override
	public String getProfileName() {
		return WARCConstants.PROFILE_REVISIT_NOT_MODIFIED;
	}

	@Override
	public Map<String, String> getWarcHeaders() {
		Map<String, String> headers = new HashMap<String, String>();
		
		if (refersToDate!=null) {
			headers.put(HEADER_KEY_REFERS_TO_DATE, refersToDate);
		}
		
		if (refersToRecordID!=null) {
			headers.put(HEADER_KEY_REFERS_TO, refersToRecordID);
		}
		
		if (eTag!=null) {
			headers.put(HEADER_KEY_ETAG, eTag);
		}
		
		if (lastModified!=null) {
			headers.put(HEADER_KEY_LAST_MODIFIED, lastModified);
		}
		
		return headers;
	}


	public String getRefersToDate() {
		return refersToDate;
	}


	public void setRefersToDate(String refersToDate) {
		this.refersToDate = refersToDate;
	}


	public String getRefersToRecordID() {
		return refersToRecordID;
	}


	public void setRefersToRecordID(String refersToRecordID) {
		this.refersToRecordID = refersToRecordID;
	}


	public String geteTag() {
		return eTag;
	}


	public void seteTag(String eTag) {
		this.eTag = eTag;
	}


	public String getLastModified() {
		return lastModified;
	}


	public void setLastModified(String lastModified) {
		this.lastModified = lastModified;
	}

	
}
