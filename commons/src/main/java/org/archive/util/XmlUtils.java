package org.archive.util;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class XmlUtils {
    /**
     * Creates a DocumentBuilderFactory with features set to prevent XXE attacks.
     */
    public static DocumentBuilderFactory newXxeSafeDocumentBuilderFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        enableXxeProtection(factory);
        return factory;
    }

    /**
     * Configures a given DocumentBuilderFactory instance to enable protections
     * against XML External Entity (XXE) attacks. See
     * <a href="https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#jaxp-documentbuilderfactory-saxparserfactory-and-dom4j">OWASP XML External Entity Prevention Cheat Sheet</a>.
     */
    public static void enableXxeProtection(DocumentBuilderFactory factory) throws ParserConfigurationException {
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
    }
}
