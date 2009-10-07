package org.archive.crawler.migrate;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class MigrateH1to3Tool {
    static DocumentBuilder DOCUMENT_BUILDER;
    static {
        try {
            DOCUMENT_BUILDER = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
    }

    // TODO: fixxx
	protected File templateH3job = new File("/Users/steve/Documents/workspace-3.4.2/h3/dist/src/main/conf/jobs/profile-defaults"); 
	protected File defaultBeansXmlFile = new File(templateH3job,"profile-crawler-beans.cxml");

	protected File sourceOrderXmlFile;
	protected File destinationH3JobDir;
	protected File targetBeansXmlFile;

	protected Document sourceOrderXmlDom;
	protected Document targetBeansXmlDom;

	public void instanceMain(String[] args) throws Exception {
		
		sourceOrderXmlFile = new File(args[0]);
		destinationH3JobDir = new File(args[1]);
        if(args.length>2) {
            templateH3job = new File(args[2]); 
        }
        
		targetBeansXmlFile = new File(destinationH3JobDir,"crawler-beans.cxml");

		FileUtils.copyFile(defaultBeansXmlFile, targetBeansXmlFile);
				
        sourceOrderXmlDom = DOCUMENT_BUILDER.parse(sourceOrderXmlFile);
        
		domFindOrderXmlControllerStrings(sourceOrderXmlDom);
		domFindOrderXmlScopeElements(sourceOrderXmlDom);
		xpathFindOrderXmlScopeNode(sourceOrderXmlDom);

        targetBeansXmlDom = DOCUMENT_BUILDER.parse(targetBeansXmlFile);
		System.out.println("targetBeansXmlDom = " + targetBeansXmlDom.getLocalName());
		unRollNode(targetBeansXmlDom.getElementsByTagName("property"));

		migrateMetadata();

	}

    protected void unRollNode(NodeList node) {
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

	protected void setBeanProperty(String id, String property, String value) 
	throws Exception {
		System.out.println("setBeanProperty("+id+","+property+","+value+")");

		// String xpathStr = "//bean[@id=\""+id+"\"]//property[@name=\""+property+"\"]";
		String xpathStr = "//bean[@id=\"" + id + "\"]";
		System.out.println("  xpathStr = " + xpathStr);
		NodeList node = getBeanNode(xpathStr);
		unRollNode(node);
		
		}
	
	protected NodeList getBeanNode(String xpath) throws Exception {
		return getDocNode(targetBeansXmlDom,xpath);
	}

	protected NodeList getDocNode(Document doc, String str) throws Exception {
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
	
	protected String getDocString(Document doc, String str) {
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
	protected void migrateMetadata() throws Exception {

		String order_meta_name = getDocString(sourceOrderXmlDom,"//meta/name");
		String order_meta_description = getDocString(sourceOrderXmlDom,"//meta/description");
		System.out.println("order_meta/name = " + order_meta_name);
		System.out.println("order_meta/description = " + order_meta_description);
		
//		NodeList bean_simpleOverrides = getBeanNode("//bean[@=\"simpleOverrides\"]");
//		unRollNode(bean_simpleOverrides);

		setBeanProperty("simpleOverrides","jobName",order_meta_name);
		// setBeanProperty("metadata","description",order_meta_description);
	}
	
	// CONTACT_URL => map_http-headers_from = bean_metadata_operatorContactUrl
	// HVERSION => map_http-headers_user-agent=" heritrix/(.*) " => 
	protected void migrateHttpHeaders() throws Exception {
	}

//	protected void migrateControllerStrings()
//	protected void migrateScope()

	protected void xpathFindOrderXmlScopeNode(Document orderXmlDom) throws Exception {
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
	
	protected void domFindOrderXmlScopeElements(Document orderXmlDom) {
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
	
	protected void domFindOrderXmlControllerStrings(Document orderXmlDom) {
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
		new MigrateH1to3Tool().instanceMain(args);
	}
	
    public static Map<String,String> flattenH1Order(String path) throws Exception {
        return flattenH1Order(DOCUMENT_BUILDER.parse(new File(path)));
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
        Map<String,String> flattened = new HashMap<String,String>();
        XPathExpression xpath = XPathFactory.newInstance().newXPath().compile("//text()");
        NodeList nodes = (NodeList) xpath.evaluate(h1order,XPathConstants.NODESET);
        for(int i = 0; i< nodes.getLength(); i++) {
            Node node = nodes.item(i); 
            if(StringUtils.isNotBlank(node.getTextContent())) {
                String pseudoXPath = getPseudoXpath(node.getParentNode());
                pseudoXPath = pseudoXPath.replaceFirst("/crawl-order", "/");
                
                System.out.println(
                        pseudoXPath
                        +" "+node.getTextContent());
                
                flattened.put(getPseudoXpath(node.getParentNode()), node.getTextContent());
            }
        }
        System.err.println(flattened.size());
        System.err.println(flattened);
        
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
