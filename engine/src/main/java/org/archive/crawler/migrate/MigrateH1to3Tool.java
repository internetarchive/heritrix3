/*
 *  This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual 
 *  contributors. 
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.archive.crawler.migrate;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Utility class which takes a H1 order.xml and creates a similar
 * H3 job directory, with as many simple settings converted over
 * (as top-of-crawler-beans overrides) as possible at this time.
 * 
 * (Future versions will handle more complicated H1 settings
 * customizations, such as per-host overrides or choices of 
 * alternate implementing classes for Scope, Processors, etc.)
 * 
 * @contributor siznax
 * @contributor gojomo
 */
public class MigrateH1to3Tool {

    protected Document sourceOrderXmlDom;

    static DocumentBuilder DOCUMENT_BUILDER;

    static {
        try {
            DOCUMENT_BUILDER = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        new MigrateH1to3Tool().instanceMain(args);
    }

    public void instanceMain(String[] args) throws Exception {

        if(args.length != 2) {
            printHelp();
            return;
        }

        String sourceOrderXmlFileArg = args[0];
        String destinationH3JobDirArg = args[1];

        File sourceOrderXmlFile = new File(sourceOrderXmlFileArg);
        if (!sourceOrderXmlFile.isFile()) {
            System.err.println("ERROR sourceOrderXmlFileArg is not a file: " + sourceOrderXmlFileArg);
            System.exit(1);
        }
        File destinationH3JobDir = new File(destinationH3JobDirArg);

        org.archive.util.FileUtils.ensureWriteableDirectory(destinationH3JobDir);

        System.out.println("H1 source: "+sourceOrderXmlFile.getAbsolutePath()); 
        System.out.println("H3 destination: "+destinationH3JobDir.getAbsolutePath());
        
        System.out.print("Migrating settings...");
        
        InputStream inStream = getClass().getResourceAsStream(
                "/org/archive/crawler/migrate/migrate-template-crawler-beans.cxml");
        String template = IOUtils.toString(inStream);
        inStream.close(); 

        Map<String,String> migrateH1toH3Map = getMigrateMap(); 

        try {
            sourceOrderXmlDom = DOCUMENT_BUILDER.parse(sourceOrderXmlFile);
        } catch (SAXException e) {
            System.err.println("ERROR caught exception parsing input file: " 
                    + e.getMessage() + "\n");
            e.printStackTrace();
        }

        Map<String,String> h1simpleSettings = flattenH1Order(sourceOrderXmlDom);

        List<String> notApplicable = new ArrayList<String>(); 
        List<String> needsAttention = new ArrayList<String>();
        int migrated = 0; 
        StringBuilder sb = new StringBuilder(); 
        for(String key : h1simpleSettings.keySet()) {
            String beanPath = migrateH1toH3Map.get(key);
            String value = h1simpleSettings.get(key);
            System.out.print("."); 
            if(beanPath==null) {
                // no equivalence rule
                needsAttention.add(key+" "+value);  
                continue; 
            } 
            if (beanPath.startsWith("$")) {
                // rule indicates not-available/not-applicable
                notApplicable.add(key+" "+value);
                continue;
            }  
            if (beanPath.startsWith("*")) {
                // TODO: needs special handling
                if(beanPath.equals("*metadata.userAgentTemplate")) {
                    splitH1userAgent(value,sb); 
                    migrated += 2; 
                } else {
                    needsAttention.add(key+" "+value);  
                }
                continue;
            } 
            if (beanPath.startsWith("^")) {
                // uppercase to new enum-style
                value = value.toUpperCase();
                beanPath = beanPath.substring(1);
            }
            sb
            .append(beanPath)
            .append("=")
            .append(value)
            .append("\n");
            migrated++; 
        }


        System.out.println();
        System.out.println(); 

        // patch all overrides derived from H1 into H3 template
        String beansCxml = template.replace("###MIGRATE_OVERRIDES###", sb.toString());

        File targetBeansXmlFile = new File(destinationH3JobDir,"crawler-beans.cxml");
        FileUtils.writeStringToFile(targetBeansXmlFile, beansCxml);

        File sourceSeedsTxtFile = new File(sourceOrderXmlFile.getParentFile(), "seeds.txt");
        File destinationSeedsTxtFile = new File(destinationH3JobDir, "seeds.txt");

        if (!sourceSeedsTxtFile.isFile()) {
            System.err.println("ERROR sourceSeedsTxtFile not found: " + sourceSeedsTxtFile);
            System.exit(1);
        }

        FileUtils.copyFile(sourceSeedsTxtFile,destinationSeedsTxtFile);

        System.out.println(notApplicable.size()+" settings skipped as not-applicable");
        System.out.println("These are probably harmless, but if the following settings were");
        System.out.println("important to your crawl process, investigate other options."); 
        listProblems(notApplicable);
        System.out.println();
        System.out.println(needsAttention.size()+" settings may need attention");
        System.out.println("Please review your original crawl and the created H3 job, for each");
        System.out.println("of the following, and manually update as needed.");
        listProblems(needsAttention);
        System.out.println();
        System.out.println(migrated +" H1 settings successfully migrated to H3 configuration");
        System.out.println();
        System.out.println("Review your converted crawler-beans.cxml at:");
        System.out.println(targetBeansXmlFile.getAbsolutePath());

    }

    protected void listProblems(List<String> problems) {
        for(String problem : problems) {
            System.out.println(" "+problem); 
        }
    }

    protected void printHelp() {
        System.out.println(
            "Usage: takes two arguments. First argument is path to a " +
            "Heritrix 1.X order.xml, second argument is path for a new " +
            "Heritrix 3.X job directory. Will generate a basic H3 job " +
            "with as many of the H1 settings replicated as currently " +
            "possible."); 
    }

    protected void splitH1userAgent(String userAgent, StringBuilder sb) {
        String originalUrl = userAgent.replaceAll(
                "^.*?\\+(http://[^)]*).*$",
                "$1");
        String newTemplate = userAgent.replace(originalUrl,"@OPERATOR_CONTACT_URL@");
        // TODO: catch, change outdated version info? 
        sb
         .append("metadata.operatorContactUrl=")
         .append(originalUrl)
         .append("\n")
         .append("metadata.userAgentTemplate=")
         .append(newTemplate)
         .append("\n");
    }

    protected Map<String, String> getMigrateMap() throws IOException {
        Map<String,String> map = new HashMap<String,String>();
        InputStream inStream = getClass().getResourceAsStream(
                "/org/archive/crawler/migrate/H1toH3.map");
        LineIterator iter = IOUtils.lineIterator(inStream, "UTF-8");
        while(iter.hasNext()) {
            String[] fields = iter.nextLine().split("\\|");
            map.put(fields[1], fields[0]);
        }
        inStream.close();
        return map;
    }
    
    /**
     * Given a Document, return a Map of all non-blank simple text 
     * nodes, keyed by the pseudo-XPath to their parent element. 
     * 
     * @param h1order Document to extract Map
     * @return Map<String,String> Xpath-like-String -> non-blank text content
     * @throws XPathExpressionException
     */
    public static Map<String,String> flattenH1Order(Document h1order) throws XPathExpressionException {
        Map<String,String> flattened = new LinkedHashMap<String,String>();
        XPathExpression xpath = XPathFactory.newInstance().newXPath().compile("//text()");
        NodeList nodes = (NodeList) xpath.evaluate(h1order,XPathConstants.NODESET);
        for(int i = 0; i< nodes.getLength(); i++) {
            Node node = nodes.item(i); 
            if(StringUtils.isNotBlank(node.getTextContent())) {
                String pseudoXPath = getPseudoXpath(node.getParentNode());
                pseudoXPath = pseudoXPath.replaceFirst("/crawl-order", "/");
                
//                System.out.println(
//                        pseudoXPath
//                        +" "+node.getTextContent());
                
                flattened.put(pseudoXPath, node.getTextContent());
            }
        }
//        System.err.println(flattened.size());
//        System.err.println(flattened);
        
        return flattened;
    }

    /**
     * Given a node, give back an XPath-like string that addresses it. 
     * (For our constrained order.xml files, it is a valid and unique
     * XPath, but the simple approach used here might not generate 
     * unique XPaths on all XML.
     * 
     * @param node node to get pseudo-XPath
     * @return String pseudo-XPath
     */
    protected static String getPseudoXpath(Node node) {
        String pseudoXpath = "";
        Node currentNode = node; 
        while(currentNode.getParentNode()!=null) {
            String thisSegment = currentNode.getNodeName();
            if(currentNode.getAttributes().getNamedItem("name")!=null) {
                thisSegment = 
                    "*[@"
                    +currentNode.getAttributes().getNamedItem("name")
                    +"]";
            }
            pseudoXpath = "/" + thisSegment + pseudoXpath;
            currentNode = currentNode.getParentNode();
        }
        return pseudoXpath;
    }
}
