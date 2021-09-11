# Security Policy

## The elephant in the room

Heritrix's user interface and configuration format allow authenticated
users to run arbitrary code, edit local files and so forth. Therefore all
Heritrix operators must necessarily considered fully trusted and the UI
carefully protected as per the [security considerations](https://heritrix.readthedocs.io/en/latest/operating.html#security-considerations)
section of the operating manual. While it would certainly be more secure to have
some level of script sandboxing or alternative user access levels it would
likely require substantial work to implement. So while we'd be interested
in efforts to contribute such improvements these risks are well known and
won't be considered new vulnerabilities.

## Supported Versions

We don't currently backport fixes to older versions of Heritrix.

## Reporting a Vulnerability

If you consider the vulnerability low risk, please open a github issue for it.

If you are concerned there's a high risk a Heritrix vulnerability will be
actively exploited if disclosed and feel it is important to give prior notice in 
private to the maintainers and major users you may contact the following teams
who should be able coordinate a response with many other organisations that use
Heritrix via the [IIPC](https://netpreserve.org/):

* [The National Library of Australia's Web Archiving Team](https://pandora.nla.gov.au/contact.html)
* [The UK Web Archive](https://www.webarchive.org.uk/en/ukwa/contact)

Vulnerabilities in services on archive.org or Archive-It should be reported to
the [Internet Archive](https://archive.org/about/contact.php).

## Bug Bounty

The Heritrix project is not currently part of a bug bounty program.
