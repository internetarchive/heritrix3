package org.archive.modules.revisit;

import static org.archive.format.warc.WARCConstants.HEADER_KEY_REFERS_TO;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_REFERS_TO_DATE;

import java.util.HashMap;
import java.util.Map;

import org.archive.util.ArchiveUtils;

public abstract class AbstractProfile implements RevisitProfile {

	protected String refersToDate; 
	protected String refersToRecordID;

	@Override
	public Map<String, String> getWarcHeaders() {
		Map<String, String> headers = new HashMap<String, String>();
		
		if (refersToDate!=null) {
			headers.put(HEADER_KEY_REFERS_TO_DATE, refersToDate);
		}
		
		if (refersToRecordID!=null) {
			headers.put(HEADER_KEY_REFERS_TO, "<" + refersToRecordID + ">");
		}
		
		return headers;
	}
	
	/**
	 * Set the refers to date
	 * @param refersToDate Must be a string representation of a data conforming to 
	 *    W3C/ISO8601 format, assuming UTC. Format is yyyy-MM-dd'T'HH:mm:ss'Z'
	 *    
	 * @see ArchiveUtils#getLog14Date(java.util.Date)
	 */
	public void setRefersToDate(String refersToDate) {
		this.refersToDate = refersToDate;
	}
	
	public String getRefersToDate() {
		return refersToDate;
	}

	/**
	 * Set the refers to date
	 * @param refersToDate 
	 */
	public void setRefersToDate(long refersToDate) {
		this.refersToDate = ArchiveUtils.getLog14Date(refersToDate);
	}


	public String getRefersToRecordID() {
		return refersToRecordID;
	}


	public void setRefersToRecordID(String refersToRecordID) {
		this.refersToRecordID = refersToRecordID;
	}



}
