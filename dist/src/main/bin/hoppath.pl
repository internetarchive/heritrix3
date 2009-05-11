#!/usr/bin/env perl

# Script that parses crawl.log and prints out path to passed in URI.
# This script works by first sorting the crawl.log to a file named
# flattened.crawl.log which it writes to current directory.  It then runs
# its queries against this file (If the crawl.log is changed, you'll need
# to remove the cache flattened_crawl.log file).
#
# ASSUMPTIONS:
# + This script was tested with perl 5.8 on debian.
# + Script expects unix 'sort' in path, and probably a linux sort at that
# (Takes a '-k' flag).  Adjust the '$SORT' variable below if your sort acts
# differently or is in an alternate location.
#
# $Id$
#
use strict;

# External dependency.  Change this variable to point at your local 'sort'
# install.
my $SORT = "sort -k4,4";

# Usage message.
my $USAGE = <<END;
Usage: hoppath.pl crawl.log URI_PREFIX
  crawl.log    Full-path to Heritrix crawl.log instance.
  URI_PREFIX   URI we're querying about. Must begin 'http(s)://' or 'dns:'.
               Wrap this parameter in quotes to avoid shell interpretation
               of any '&' present in URI_PREFIX.
END

# Make sure of the command-line arguments.
my $CRAWLLOG = shift;
my $URLPREFIX = shift;
die "$USAGE" unless $CRAWLLOG and $URLPREFIX;
die "Error: $CRAWLLOG does not exist.\n${USAGE}" unless (-e $CRAWLLOG);
die "Error: $CRAWLLOG is not readable.\n${USAGE}" unless (-r $CRAWLLOG);
die "Error: $URLPREFIX does not have http(s) or dns prefix.\n${USAGE}" 
    unless $URLPREFIX =~ m|^(?:(?:https?://)\|(?:dns:))|;


# Go to work.
my $sortedLogFile = checkCache();
search($sortedLogFile, $URLPREFIX);


# Make flattened and sorted crawl.log unless one already exists.
# TODO: add some smarts to it (e.g. the crawl.log may have changed out from
# under the flattened representation).
sub checkCache {
    # Name of sorted file.
    my $sortedLogFile = "flattened_crawl.log";
    # Does sorted file exist?  If not, make it.
    if (! -f $sortedLogFile) {
        open (FH, "< $CRAWLLOG")
            or die "Couldn't open file $CRAWLLOG: $!.\n";
        open (FLATLOG, "| $SORT > $sortedLogFile")
            or die "Couldn't open filehandle to $sortedLogFile: $!.\n";
        print STDOUT "Sorting crawl log file and saving to $sortedLogFile.\n" .
            "May take a few minutes (This is only done once!).\n";
        my $line;
        while (<FH>) {
            # Collapse field spaces.
            tr/ //s;
            print FLATLOG;
        }
        close(FH);
        close(FLATLOG);
    }
    return $sortedLogFile;
}

# Search in cached flattened log file for requested url prefix
sub search {
    my ($sortedLogFile, $query) = @_;
    my @stack;
    my $exactMatch = 0;
    open (FLATLOG, $sortedLogFile) or die "Failed open of $sortedLogFile: $!\n";
    LINES: while (<FLATLOG>) {
        # Split the line in to constituent parts.
        chomp;
        my @parts = split(/ /, $_);
        my $uri = $parts[3];
        if ($exactMatch? $uri eq $query: $uri =~ m|$query|) {
            # Get last path character
            my $path = $parts[4];
            my $pathchar = $path? (split(//, $path))[-1]: ''; 
            # Push url onto stack.
            push @stack, "$parts[0] $pathchar $uri\n";
            # Rewind and search for this url's referrer.
            $query = $parts[5];
            if (not $query) {
                # If no referrer, we're done.
                last LINES;
            }
            $exactMatch = 1;
            seek(FLATLOG, 0, 0) or die "Failed rewind: $!.\n";
        }
    }

    # Print out results.
    if (not @stack) {
        print STDOUT "URL prefix not found: $URLPREFIX.\n";
        exit 0;
    } else {
        my $spaces = '';
        while (@stack) {
            my @parts = split(/ /, pop(@stack));
            my $line = formatDate($parts[0]) . $spaces;
            for (my $i = 1; $i < scalar @parts; $i++) {
                $line .= ' ' . $parts[$i];
            }
            print STDOUT "$line";
            $spaces .= " ";
        }
   }
}

# Date formatting.
sub formatDate {
    my @p = split(//, shift);
    return "$p[0]$p[1]$p[2]$p[3]-$p[4]$p[5]-$p[6]$p[7]-$p[8]$p[9]-" .
        "$p[10]$p[11]-$p[12]$p[14]";
}
