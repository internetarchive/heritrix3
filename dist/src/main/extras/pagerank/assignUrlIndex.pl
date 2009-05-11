#!/usr/bin/env perl

$prev = "";
$index = -1;
while(<STDIN>) {
	$line = $_;
	chomp($line);
	($from,$to) = split(/ /,$line);
	
	if($from ne $prev) {
		++$index;
	}

	print "$index\t$from\t$to\n";
	$prev = $from;
}

