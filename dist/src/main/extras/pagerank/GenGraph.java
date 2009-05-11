package org.apache.hadoop.examples;

import java.io.*;
import java.util.*;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.MapReduceBase;

public class GenGraph {

public static class MapClass extends MapReduceBase implements Mapper {

	private Text index = new Text();
	private Text toUrl = new Text();
	private Text fromUrl = new Text();
	

	public void map(WritableComparable key, Writable value, OutputCollector output, Reporter reporter)

		throws IOException {

			String indexedUrlToFrom = ((Text)value).toString();
			String[] splitString = indexedUrlToFrom.split("\t");

            
            index.set("-" + splitString[0]);

			toUrl.set(splitString[1]);

			output.collect(toUrl, index);
			
			//System.out.println("map output (tourl,index) = (" + toUrl + "," + index  + ")");
			
			
			index.set(splitString[0]);

			for(int i = 2; i < splitString.length; i++) {

                    String fromUrlString = splitString[i];
                    
			//to avoid self loops
                    if(!(fromUrlString.equals(splitString[1]))) {
                        
                            fromUrl.set(fromUrlString);

                            output.collect(fromUrl, index);
                    }

			}

		}

}

public static class Reduce extends MapReduceBase implements Reducer {

	public void reduce(WritableComparable key, Iterator values, OutputCollector output, Reporter

			reporter) throws IOException {

		TreeSet<Integer> treeSet = new TreeSet<Integer>();

		Text marker = null;

		boolean foundMarker = false;

		String currentPIN = null;

		while (values.hasNext()) {

			currentPIN = values.next().toString();

			if(currentPIN.charAt(0) == '-') {

                    if(foundMarker == false) { 
	        			marker = new Text(currentPIN.substring(1));
			        	foundMarker = true;
                    }

			} else {

				treeSet.add(new Integer(currentPIN));

			}

		}

		if(foundMarker) {

			Iterator iterator = treeSet.iterator();

			String finalOutput = "";

			//read in first value, again tab hack
			if(iterator.hasNext()) {

				 finalOutput += iterator.next().toString();
			}

			
			while(iterator.hasNext()) {

				finalOutput += '\t' + iterator.next().toString();

			}

			output.collect(marker, new Text(finalOutput));
			//System.out.println("reduce output (marker,finaloutput) = (" + marker + "," + finalOutput  + ")");

		}

	}

}

public static void main(String[] args) throws IOException {

	JobConf conf = new JobConf(GenGraph.class);

	conf.setJobName("genGraph");

	conf.setOutputKeyClass(Text.class);

	conf.setOutputValueClass(Text.class);

	conf.setMapperClass(MapClass.class);

	//conf.setCombinerClass(Reduce.class);

	conf.setReducerClass(Reduce.class);

	conf.setInputPath(new Path(args[0].trim()));

	conf.setOutputPath(new Path(args[1].trim()));

	JobClient.runJob(conf);

}

}
