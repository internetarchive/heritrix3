package org.archive.crawler.migrate;

import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathExpression;

import org.archive.util.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class MigrateSettings {
	
	private File defaultConfigDir = new File("/Users/steve/Documents/workspace-3.4.2/h3/dist/src/main/conf/jobs/profile-defaults"); // TODO: fixxx
	private File defaultBeansXmlFile = new File(defaultConfigDir,"profile-crawler-beans.cxml");

	private File srcOrderXmlFile;
	private File destConfigDir;
	private File targetBeansXmlFile;

	private Document srcOrderXmlDom;
	private Document targetBeansXmlDom;

	public void instanceMain(String[] args) throws Exception {
		
		srcOrderXmlFile = new File(args[0]);
		destConfigDir = new File(args[1]);
		targetBeansXmlFile = new File(destConfigDir,"crawler-beans.cxml");
		
		try {
			
			FileUtils.copyFile(defaultBeansXmlFile, targetBeansXmlFile);
					
			setSrcXmlDom(srcOrderXmlFile);
			domFindOrderXmlControllerStrings(srcOrderXmlDom);
			domFindOrderXmlScopeElements(srcOrderXmlDom);
			xpathFindOrderXmlScopeNode(srcOrderXmlDom);

			setTargetXmlDom(targetBeansXmlFile);
			System.out.println("targetBeansXmlDom = " + targetBeansXmlDom.getLocalName());
			unRollNode(targetBeansXmlDom.getElementsByTagName("property"));

			migrateMetadata();
				
		} catch (Exception e) {
			System.out.println("Caught Exception: " + e.getMessage());
		}
	}
	
	private void unRollNode(NodeList node) {
		System.out.println("unRollNode " + node.toString()); // + node.getLength());
		for (int i=0; i<node.getLength(); i++) {
			System.out.println(node.item(i).getNodeName()
					+ " " + node.item(i).getNodeValue()
					+ " " + node.item(i).getTextContent());
			for ( int j=0; j<node.item(i).getChildNodes().getLength(); j++) {
				Node beanChild = node.item(i).getChildNodes().item(j);
				System.out.println(" > " + beanChild.getNodeName()
						+ " " + beanChild.getNodeValue()
						+ " " + beanChild.getTextContent());
			}
		}
	}
	
	private void setBeanProperty(String id, String property, String value) 
	throws Exception {
		System.out.println("setBeanProperty("+id+","+property+","+value+")");

		// String xpathStr = "//bean[@id=\""+id+"\"]//property[@name=\""+property+"\"]";
		String xpathStr = "//bean[@id=\"" + id + "\"]";
		System.out.println("  xpathStr = " + xpathStr);
		NodeList node = getBeanNode(xpathStr);
		unRollNode(node);
		
	}
	
	private NodeList getBeanNode(String xpath) throws Exception {
		return getDocNode(targetBeansXmlDom,xpath);
	}
	
	private NodeList getDocNode(Document doc, String str) throws Exception {
		System.out.println("getDocNode(" + doc.getLocalName() + "," + str + " )");
		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();
        try {
    		XPathExpression expr = xpath.compile(str);
    		NodeList list = (NodeList) expr.evaluate(doc,XPathConstants.NODE);
    		System.out.println("xpath \"" + str + "\" found " + list.getLength() + " members");
    		return list;
        } catch (XPathExpressionException e) {
        	System.out.println("XPathExpressionException!");
            return null;
        }
	}
	
	private String getDocString(Document doc, String str) {
		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();
        try {
            XPathExpression expr = xpath.compile(str);
            return expr.evaluate(doc);
        } catch (XPathExpressionException e) {
            return null;
        }
	}
	
	// JOB_NAME => order_meta_name = bean_metadata_jobName
	// DESCRIPTION => order_meta_description = bean_metadata_description
	private void migrateMetadata() throws Exception {

		String order_meta_name = getDocString(srcOrderXmlDom,"//meta/name");
		String order_meta_description = getDocString(srcOrderXmlDom,"//meta/description");
		System.out.println("order_meta/name = " + order_meta_name);
		System.out.println("order_meta/description = " + order_meta_description);
		
//		NodeList bean_simpleOverrides = getBeanNode("//bean[@=\"simpleOverrides\"]");
//		unRollNode(bean_simpleOverrides);

		setBeanProperty("simpleOverrides","jobName",order_meta_name);
		// setBeanProperty("metadata","description",order_meta_description);
	}
	
	// CONTACT_URL => map_http-headers_from = bean_metadata_operatorContactUrl
	// HVERSION => map_http-headers_user-agent=" heritrix/(.*) " => 
	private void migrateHttpHeaders() throws Exception {
	}

//	private void migrateControllerStrings()
//	private void migrateScope()
	
	private void setTargetXmlDom(File file) throws Exception {
		DocumentBuilderFactory beansXmlFactory = DocumentBuilderFactory.newInstance();
        beansXmlFactory.setNamespaceAware(true); // never forget this!
		targetBeansXmlDom = beansXmlFactory.newDocumentBuilder().parse(file);
	}
	
	private void setSrcXmlDom(File file) throws Exception {
		DocumentBuilderFactory orderXmlFactory = DocumentBuilderFactory.newInstance();
        orderXmlFactory.setNamespaceAware(true); // never forget this!
		srcOrderXmlDom = orderXmlFactory.newDocumentBuilder().parse(file);
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
