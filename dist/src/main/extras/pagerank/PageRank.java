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

public class PageRank {

public static class MapClass extends MapReduceBase implements Mapper {

	private Text toUrl = new Text();
	private Text toUrlList = new Text();
	private Text fromUrl = new Text();
	
    private Text prValue = new Text();
	

	public void map(WritableComparable key, Writable value, OutputCollector output, Reporter reporter)

		throws IOException {

			String inputString = ((Text)value).toString();

            String[] splitString = inputString.split("\t");

            fromUrl.set(splitString[0].trim());
            
            String prValString = splitString[1].trim();
            
            String toUrlListString = splitString[2].trim();
            
            toUrlList.set(toUrlListString);
            
            String newToUrlListString = toUrlListString.substring(2); 
           
            if(!(newToUrlListString.equals(""))) {
                    
                String[] toUrls = newToUrlListString.split(",");
    
            
            double outdegree = toUrls.length;
          
            
            Double prDoubleValue = new Double(prValString);
            
            if(outdegree > 0 ) {
            
                    double val = prDoubleValue.doubleValue() / outdegree;
            
                    String valString = Double.toString(val);
        
                    prValue.set(valString);

                    
                    for(int i = 0; i < outdegree; i++) {

				        toUrl.set(toUrls[i].trim());
                        
                        output.collect(toUrl,prValue); 
                
                       // System.out.println("tourl - " + toUrls[i] + " - " + valString); 
                 }

            }

            }
                
			output.collect(fromUrl, toUrlList);
            
            //System.out.println("fromurl - " + splitString[0] + " - " + toUrlListString); 


        }

}

public static class Reduce extends MapReduceBase implements Reducer {

	public void reduce(WritableComparable key, Iterator values, OutputCollector output, Reporter

			reporter) throws IOException {

		Text toUrlList = null;


		String toUrlListString = "O:";

        double value = 0.0;


		while (values.hasNext()) {

                String readValue = values.next().toString();

			    if(readValue.startsWith("O:")) {

                        toUrlListString = readValue;

                        //System.out.println("tourllist" + toUrlListString);
			} else {
                
                    Double val = new Double(readValue);
                
                    value+=val.doubleValue();
                        
                    //System.out.println("value is" + val);
           
			}

		}

        value*=0.85;
        value+=0.15;
        
        String finalOutput = "";

        //no outlink, delete accumulated values, keep random jump
        if(toUrlListString.equals("O:")) {
        
                value=0.15;

        }
        
        finalOutput+=Double.toString(value)+"\t"+toUrlListString;
        
        //System.out.println("final op" + finalOutput);
		
        output.collect(key, new Text(finalOutput));
            
    }
}



public static void main(String[] args) throws IOException {

	JobConf conf = new JobConf(PageRank.class);

	conf.setJobName("pageRank");

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
