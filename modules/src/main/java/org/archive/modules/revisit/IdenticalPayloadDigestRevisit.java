package org.archive.modules.revisit;

import static org.archive.format.warc.WARCConstants.*;

import java.util.HashMap;
import java.util.Map;

import org.archive.format.warc.WARCConstants;

public class IdenticalPayloadDigestRevisit implements RevisitProfile {
	// Required field
	protected final String payloadDigest;
	
	// Strongly recommended fields
	protected String refersToTargetURI;
	protected String refersToDate;
	
	// Optional fields
	protected String refersToRecordID;
	
	/**
	 * Minimal constructor.
	 * @param payloadDigest The digest of the original record
	 */
	public IdenticalPayloadDigestRevisit(String payloadDigest) {
		if (payloadDigest==null) {
			throw new NullPointerException("PayloadDigest may not be null");
		}
		this.payloadDigest = payloadDigest;
	}
	
		
	@Override
	public String getProfileName() {
		return WARCConstants.PROFILE_REVISIT_IDENTICAL_DIGEST;
	}

	@Override
	public Map<String, String> getWarcHeaders() {
		Map<String, String> headers = new HashMap<String, String>();
		
		// Written automatically by WarcWriterProcessor for all HTTP responses
		// headers.put(HEADER_KEY_PAYLOAD_DIGEST, payloadDigest); 
		
		if (refersToTargetURI!=null) {
			headers.put(HEADER_KEY_REFERS_TO_TARGET_URI, refersToTargetURI);
		}
		
		if (refersToDate!=null) {
			headers.put(HEADER_KEY_REFERS_TO_DATE, refersToDate);
		}
		
		if (refersToRecordID!=null) {
			headers.put(HEADER_KEY_REFERS_TO, refersToRecordID);
		}
		
		return headers;
	}


	public String getRefersToTargetURI() {
		return refersToTargetURI;
	}


	public void setRefersToTargetURI(String refersToTargetURI) {
		this.refersToTargetURI = refersToTargetURI;
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


	public String getPayloadDigest() {
		return payloadDigest;
	}

	
	
}
