#!/usr/bin/env perl

use strict;


my $LFLAG = "-L";
my $CFLAG = "-C";
my $RFLAG = "-R";

my $lFlagDir = "logs";
my $cFlagDir = "configurations";
my $rFlagDir = "reports";
my $v = 0;

my $tarFile;
my $tarFlags = "ch";
my $compress = 0;
my %flags = ($LFLAG => $lFlagDir, $CFLAG => $cFlagDir, $RFLAG => $rFlagDir);

my $crawlName = shift;
my $manifestFile = shift;

die "Could not find $manifestFile: ($!)\n" if ( ! -f $manifestFile);

while (@ARGV > 0) {
    if ($ARGV[0] eq "-f") {
	shift;
	$tarFile = shift;
	$tarFlags = $tarFlags . "f $tarFile";
    }elsif ($ARGV[0] eq "-z") {
	shift;
	$tarFlags = "z" . $tarFlags;
    }elsif ($ARGV[0] eq $LFLAG) {
	shift;
        $lFlagDir = shift;
    }elsif ($ARGV[0] eq $RFLAG){
	shift;
	$rFlagDir = shift;
    }elsif ($ARGV[0] eq $CFLAG){
	shift;
	$cFlagDir = shift;
    }elsif ($ARGV[0] eq "--help"){
	shift;
	USAGE();
    }else {
	# any other flag
	my $key = shift;
	$flags{$key} = shift;
    }
}

# Dump output to stdout if the tar file is not specified.
$tarFlags .= "O" if (! $tarFile );

open (FH, "< $manifestFile")
    or die "Could not open $manifestFile: ($!)\n";

# Create directory structure with symbolic links to files found in the crawl manifest file.
if (! -d $crawlName){
    mkdir($crawlName);
}

my $file;
my $dir;
while (<FH>) {
    chomp;
    my @parts = split;
    if (scalar @parts != 2) {
	warn "Illegal format in $manifestFile ($_)\n";
	next;
    }
    my @chars = split (//,$parts[0]);
    next if ($chars[1] eq "-");
    $dir = $flags{"-$chars[0]"};
    mkdir("$crawlName/$dir") if (! -d "$crawlName/$dir");
    $parts[1] =~ /^.+\/([^\/]+)$/;

    if ($1) {
	$file = $1;
    } else {
	warn "Could not recognize a file in: $_\n";
    }

    symlink($parts[1], "$crawlName/$dir/$file")
	or die "Could not create link to $parts[1]: ($!)\n"; 
} 

# tar up the directory structure.
my $cmd = "tar -$tarFlags $crawlName";
print "Running $cmd\n" if ($v);
!system($cmd)
    or die "Running command $cmd failed: ($!)\n";

# clean up

`rm -rf $crawlName`;

sub USAGE {
    print "heri_manifest_bundle crawl_name manifest_file [-f output_tar_file] [-z] [ -flag directory]\n";
    print "\t -f output tar file. If omitted output to stdout.\n";
    print "\t -z compress tar file with gzip\n";
    print "\t -flag is any upper case letter. Default values C, L, and are R are set to configuration, logs and reports.\n";
    print "Example:\n\theri_manifest_bundle crawl-manifest.txt -f /0/test-crawl/manifest-bundle.tar -z -F filters\n";
}
