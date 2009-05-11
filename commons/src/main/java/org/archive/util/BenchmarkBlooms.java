/* BenchmarkBlooms
*
* $Id$
*
* Created on Jun 30, 2005
*
* Copyright (C) 2005 Internet Archive
*
* This file is part of the Heritrix web crawler (crawler.archive.org).
*
* Heritrix is free software; you can redistribute it and/or modify
* it under the terms of the GNU Lesser Public License as published by
* the Free Software Foundation; either version 2.1 of the License, or
* any later version.
*
* Heritrix is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Lesser Public License for more details.
*
* You should have received a copy of the GNU Lesser Public License
* along with Heritrix; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package org.archive.util;

/**
 * Simple benchmarking of different BloomFilter
 * implementations.
 * 
 * Take care when interpreting results; the effect of GC,
 * dynamic compilation, and any other activity on test 
 * machine may affect relative time tallies in unpredictable
 * ways.
 * 
 * @author Gordon Mohr
 */
public class BenchmarkBlooms {

	public static void main(String[] args) {
		(new BenchmarkBlooms()).instanceMain(args);
	}
	
	public void instanceMain(String[] args) {
		int reps = 
			(args.length > 0) ? Integer.parseInt(args[0]) : 3;
		int n_expected = 
			(args.length > 1) ? Integer.parseInt(args[1]) : 10000000;
		int d_hashes = 
			(args.length > 2) ? Integer.parseInt(args[2]) : 22;
		int adds = 
		    	(args.length > 3) ? Integer.parseInt(args[3]) : 5000000;
	    String prefix = 
	    	(args.length > 4) ? args[4] : "http://www.archive.org/";
	    
	    System.out.println(
	    		"reps="+reps+" n_expected="+n_expected+
				" d_hashes="+d_hashes+" adds="+adds+" prefix="+prefix);
	    
	    BloomFilter bloom64;
	    BloomFilter bloom32;
        BloomFilter bloom32split;
	    BloomFilter bloom32p2;
        BloomFilter bloom32p2split;
		for (int r=0;r<reps;r++) {
			bloom32 = new BloomFilter32bit(n_expected,d_hashes);
			testBloom(bloom32,adds,prefix);
			bloom32=null;
            bloom32split = new BloomFilter32bitSplit(n_expected,d_hashes);
            testBloom(bloom32split,adds,prefix);
            bloom32split=null;
			bloom64 = new BloomFilter64bit(n_expected,d_hashes);
			testBloom(bloom64,adds,prefix);
			bloom64=null;
			bloom32p2 = new BloomFilter32bp2(n_expected,d_hashes);
			testBloom(bloom32p2,adds,prefix);
			bloom32p2=null;
            bloom32p2split = new BloomFilter32bp2Split(n_expected,d_hashes);
            testBloom(bloom32p2split,adds,prefix);
            bloom32p2split=null;
		}
	}
	
	/**
	 * @param bloom
	 * @param prefix
	 * @param adds
	 * @param d_hashes
	 */
	private void testBloom(BloomFilter bloom, int adds, String prefix) {
		System.gc();
		long startTime = System.currentTimeMillis();
		long falsePositives = 0;
		for(int i = 0; i<adds; i++) {
			if(!bloom.add(prefix+Integer.toString(i))) {
				falsePositives++;
			}
		}
		long finishTime = System.currentTimeMillis();
		System.out.println(bloom.getClass().getName()+": "
				+(finishTime-startTime)+"ms "
				+bloom.getSizeBytes()+"bytes "
				+falsePositives+"false");
	}
}
