package org.archive.modules.revisit;

import static org.archive.format.warc.WARCConstants.HEADER_KEY_REFERS_TO;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_REFERS_TO_DATE;

import java.util.HashMap;
import java.util.Map;

import org.archive.util.ArchiveUtils;

public abstract class AbstractProfile implements RevisitProfile {

	protected long refersToDate=1L; //1L is the default fetchBeganTime in CrawlURI
	protected String refersToRecordID;

	@Override
	public Map<String, String> getWarcHeaders() {
		Map<String, String> headers = new HashMap<String, String>();
		
		if (refersToDate!=1L) {
			headers.put(HEADER_KEY_REFERS_TO_DATE, ArchiveUtils.getLog14Date(refersToDate));
		}
		
		if (refersToRecordID!=null) {
			headers.put(HEADER_KEY_REFERS_TO, "<" + refersToRecordID + ">");
		}
		
		return headers;
	}
	
	public long getRefersToDate() {
		return refersToDate;
	}

	public void setRefersToDate(long refersToDate) {
		this.refersToDate = refersToDate;
	}


	public String getRefersToRecordID() {
		return refersToRecordID;
	}


	public void setRefersToRecordID(String refersToRecordID) {
		this.refersToRecordID = refersToRecordID;
	}



}
