package org.archive.crawler.migrate;

import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

import org.archive.crawler.Heritrix;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.archive.crawler.Heritrix;

public class MigrateSettings {
	
	public void instanceMain(String[] args) throws Exception {
		
		File orderXmlFile = new File(args[0]);
		File newConfigDir = new File(args[1]);
		// File defaultConfigDir = new File(getResourceAsStream("dist/src/main/conf/jobs"));
		File defaultConfigDir = new File("dist/src/main/conf/jobs");

		try {
			
			// copy defaults.xml to configPath
//			File srcDefaultsXml = new File(defaultConfigDir,"defaults.xml");
//			File tgtDefaultsXml = new File(newConfigDir,"defaults.xml");
//			FileUtils.copyFile(srcDefaultsXml, tgtDefaultsXml);

			// copy default profile crawler-beans.xml to configPath
//			File srcCrawlerBeansXml = new File(defaultConfigDir,"profile-crawler-beans.xml");
//			File tgtCrawlerBeansXml = new File(newConfigDir,"crawler-beans.xml");
//			FileUtils.copyFile(srcCrawlerBeansXml, tgtCrawlerBeansXml);

			// parse order XML
			if (!orderXmlFile.canRead()) {
				throw new Exception("can not read file: " 
						+ orderXmlFile.getAbsolutePath());
			} else {
				Document orderXmlDom = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder().parse(orderXmlFile);
				
				Element crawlOrder = orderXmlDom.getDocumentElement();

				// (controller) strings
				NodeList strings = crawlOrder.getElementsByTagName("string");
				for (int i=0; i<strings.getLength(); i++) {
					if (strings.item(i).getParentNode().getNodeName() == "controller") {
						if (strings.item(i).hasAttributes()) {
							Node s = strings.item(i);
							System.out.println(s.getParentNode().getNodeName()
									+ "/"
									+ s.getNodeName()
									+ "[" + i + "]" 										
									+ "@" 
									+ s.getAttributes().item(0).getNodeName()
									+ "["
									+ s.getAttributes().item(0).getNodeValue() 
									+ "]"
									+ " = "
									+ s.getTextContent());
						}
					}
				}
				
				// newObjects
				NodeList newObjects = crawlOrder.getElementsByTagName("newObject");
				for (int i=0; i<newObjects.getLength(); i++) {
					if (newObjects.item(i).hasAttributes()) {
						Node name = newObjects.item(i).getAttributes().getNamedItem("name");
						
						// scope
						if (name.getNodeValue().equalsIgnoreCase("scope")) {
							String npath = newObjects.item(i).getNodeName()
							+ "[" + i + "][@" + name.getNodeName() 
							+ "=\"" + name.getNodeValue() + "\"]";
							System.out.println("==== DOM search {" + npath + "} ====");
							NodeList scope = newObjects.item(i).getChildNodes(); 
							for (int j=0; j<scope.getLength(); j++) {
								if (scope.item(j).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
									System.out.println("  " + scope.item(j).getNodeName());
								}
							}
						}
						
					}
				}
				
				// use Xpath to extract scope node
		        XPathFactory factory = XPathFactory.newInstance();
		        XPath xpath = factory.newXPath();
				String xp = "//controller/newObject[@name=\"scope\"]";
				System.out.print("==== XPATH {" + xp + "} ====\n");
	            XPathExpression expr = xpath.compile(xp);
	            NodeList scope = (NodeList) expr.evaluate(orderXmlDom,XPathConstants.NODE);
				for (int j=0; j< scope.getLength(); j++) {
					if (scope.item(j).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
						System.out.println("  " + scope.item(j).getNodeName());
					}
				}
				
			}

		} catch (Exception e) {
			System.out.println("Caught Exception: " + e.getMessage());
		}
	}
		
	public static void main(String[] args) throws Exception {
		new MigrateSettings().instanceMain(args);
	}
	
}
