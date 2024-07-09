package org.archive.crawler.frontier;

import org.archive.crawler.spring.SurtPrefixesSheetAssociation;
import org.archive.net.UURI;

/**
 * A variation on @link {@link HostnameQueueAssignmentPolicy} that allows the
 * operator (per sheet) to specify the maximum number of domains and sub-domains
 * to use for the queue name.
 *
 */
public class HostnameQueueAssignmentPolicyWithLimits extends HostnameQueueAssignmentPolicy {
	private static final long serialVersionUID = 3L;

	public static final String LIMIT = "limit";

	// Default limit to -1 (no limit enforced)
	{
		setLimit(-1);
	}

	/**
	 * Set the maximum number of domains and sub-domains to include in the queue
	 * name.
	 * <p>
	 * E.g. if limit is set to <code>2</code> than the following assignments are
	 * made: <br/>
	 * <code>example.com -> example.com</code> <br/>
	 * <code>www.example.com -> example.com</code> <br/>
	 * <code>subdomain.example.com -> example.com</code> <br/>
	 * <code>www.subdomain.example.com -> example.com</code> <br/>
	 * <code>otherdomain.com -> otherdomain.com</code> <br/>
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
		return getLimitedHostname(super.getCoreKey(basis), limit);
	}

	protected String getLimitedHostname(String hostname, int limit) {
		if (limit <= 0) {
			return hostname;
		}

		String[] domains = hostname.split("\\.");
		if (limit >= domains.length) {
			return hostname;
		}
		// More domains are present than allowed.
		StringBuilder limitedHostname = new StringBuilder();
		for (int i = domains.length - limit; i < domains.length - 1; i++) {
			limitedHostname.append(domains[i]);
			limitedHostname.append(".");
		}
		limitedHostname.append(domains[domains.length - 1]);
		return limitedHostname.toString();
	}
}
