#!/usr/bin/env perl

while(<STDIN>) {
	$line = $_;
	chomp($line);

	($fromUrl,@toUrls) = split(/\t/,$line);
	
	$toUrlString = "O:";
	$toUrlString.= join ",",@toUrls;
	
	print "$fromUrl\t1\t$toUrlString\n";
}

