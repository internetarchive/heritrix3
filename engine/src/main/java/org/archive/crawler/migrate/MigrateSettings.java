package org.archive.crawler.migrate;

import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathExpression;

import org.archive.util.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class MigrateSettings {
	
	private File orderXmlFile;
	private File destConfigDir;
	private File defaultConfigDir = new File("/Users/steve/Documents/workspace-3.4.2/h3/jobs/profile-defaults/"); // TODO: fixxx
	private File destDefaultBeansXmlFile;
	private File destCrawlerBeansXmlFile;
	private Document orderXmlDom;
	private Document defaultBeansXmlDom;

	public void instanceMain(String[] args) throws Exception {
		
		orderXmlFile = new File(args[0]);
		destConfigDir = new File(args[1]);
		destDefaultBeansXmlFile = new File(destConfigDir,"defaults.xml"); 
		destCrawlerBeansXmlFile = new File(destConfigDir,"crawler-beans.cxml");
		
		try {
			
			FileUtils.copyFile(new File(defaultConfigDir,"defaults.xml"), destDefaultBeansXmlFile);
			FileUtils.copyFile(new File(defaultConfigDir,"profile-crawler-beans.cxml"), destCrawlerBeansXmlFile);
					
			// parse order XML
			if (!orderXmlFile.canRead()) {
				throw new Exception("can not read file: " 
						+ orderXmlFile.getAbsolutePath());
			} else {

				setOrderXmlDom(orderXmlFile);

				domFindOrderXmlControllerStrings(orderXmlDom);
				domFindOrderXmlScopeElements(orderXmlDom);
				xpathFindOrderXmlScopeNode(orderXmlDom);
				
				setDefaultBeansXmlDom(destDefaultBeansXmlFile);
				
			}

		} catch (Exception e) {
			System.out.println("Caught Exception: " + e.getMessage());
		}
	}

	private void setDefaultBeansXmlDom(File destDefaultBeansXmlFile) throws Exception {
		DocumentBuilderFactory defaultBeansXmlFactory = DocumentBuilderFactory.newInstance();
        defaultBeansXmlFactory.setNamespaceAware(true); // never forget this!
		this.defaultBeansXmlDom = defaultBeansXmlFactory.newDocumentBuilder().parse(destDefaultBeansXmlFile);
	}
	
	private void setOrderXmlDom(File orderXmlFile) throws Exception {
		DocumentBuilderFactory orderXmlFactory = DocumentBuilderFactory.newInstance();
        orderXmlFactory.setNamespaceAware(true); // never forget this!
		this.orderXmlDom = orderXmlFactory.newDocumentBuilder().parse(orderXmlFile);
	}
	
	private void xpathFindOrderXmlScopeNode(Document orderXmlDom) throws Exception {
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
	
	private void domFindOrderXmlScopeElements(Document orderXmlDom) {
		Element crawlOrder = orderXmlDom.getDocumentElement();
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
	}
	
	private void domFindOrderXmlControllerStrings(Document orderXmlDom) {
		Element crawlOrder = orderXmlDom.getDocumentElement();
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
	}
	
	public static void main(String[] args) throws Exception {
		new MigrateSettings().instanceMain(args);
	}
	
}
