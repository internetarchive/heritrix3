package org.archive.modules.revisit;

import static org.archive.format.warc.WARCConstants.HEADER_KEY_REFERS_TO_TARGET_URI;

import java.util.Map;

import org.archive.format.warc.WARCConstants;

public class IdenticalPayloadDigestRevisit extends AbstractProfile {

	protected final String payloadDigest;
	protected String refersToTargetURI;
		
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
		Map<String, String> headers = super.getWarcHeaders();
		
		// Written automatically by WarcWriterProcessor for all HTTP responses
		// headers.put(HEADER_KEY_PAYLOAD_DIGEST, payloadDigest); 
		
		if (refersToTargetURI!=null) {
			headers.put(HEADER_KEY_REFERS_TO_TARGET_URI, refersToTargetURI);
		}
		
	
		return headers;
	}


	public String getRefersToTargetURI() {
		return refersToTargetURI;
	}


	public void setRefersToTargetURI(String refersToTargetURI) {
		this.refersToTargetURI = refersToTargetURI;
	}


	public String getPayloadDigest() {
		return payloadDigest;
	}
	
}
