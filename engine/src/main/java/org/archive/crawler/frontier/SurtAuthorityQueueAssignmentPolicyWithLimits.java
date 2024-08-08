package org.archive.crawler.frontier;

import org.archive.crawler.spring.SurtPrefixesSheetAssociation;
import org.archive.net.UURI;

/**
 * A variation on @link {@link SurtAuthorityQueueAssignmentPolicy} that allows
 * the operator (per sheet) to specify the maximum number of SURT segments 
 * to use for the queue name.
 *
 */
public class SurtAuthorityQueueAssignmentPolicyWithLimits extends SurtAuthorityQueueAssignmentPolicy {
	private static final long serialVersionUID = 3L;

	public static final String LIMIT = "limit";

	// Default limit to -1 (no limit enforced)
	{
		setLimit(-1);
	}

	/**
	 * Set the maximum number of surt segments to include in the queue name.
	 * <p>
	 * E.g. if limit is set to <code>2</code> than the following assignments are
	 * made: <br/>
	 * <code>com,example, -> com,example,</code> <br/>
	 * <code>com,example,www, -> com,example,</code> <br/>
	 * <code>com,example,subdomain, -> com,example,</code> <br/>
	 * <code>com,example,subdomain,www, -> com,example,</code> <br/>
	 * <code>com,otherdomain, -> com,otherdomain,</code> <br/>
	 * <p>
	 * <strong>Note:</strong> No accommodation is made for TLDs, like
	 * <code>.co.uk</code> that always use two levels. Operators should use use
	 * {@link SurtPrefixesSheetAssociation} sheets to apply these limits
	 * appropriately if crawling a mixture of TLDs with and without the mandatory
	 * second level or only apply the limit on specific domains.
	 * 
	 * @param limit The limit on number of domains to use in assigning a queue name
	 *              to a URI.
	 */
	public void setLimit(int limit) {
		kp.put(LIMIT, limit);
	}

	public int getLimit() {
		return (Integer) kp.get(LIMIT);
	}

	@Override
	protected String getCoreKey(UURI basis) {
		int limit = (Integer) kp.get(LIMIT);
		return getLimitedSurtAuthority(super.getCoreKey(basis), limit);
	}

	protected String getLimitedSurtAuthority(String surt, int limit) {
		if (limit <= 0) {
			return surt;
		}
		String domainPart = surt;
		String portPart = "";
		int indexOfHash = surt.indexOf('#');
		if (indexOfHash > -1) {
			domainPart = surt.substring(0, indexOfHash);
			portPart = surt.substring(indexOfHash);
		}
		String[] segments = domainPart.split(",");
		if (limit >= segments.length) {
			return surt;
		}
		// More domains are present than allowed.
		StringBuilder limitedSurt = new StringBuilder();
		for (int i = 0; i < limit; i++) {
			limitedSurt.append(segments[i]);
			limitedSurt.append(",");
		}
		limitedSurt.append(portPart);
		return limitedSurt.toString();
	}
}
