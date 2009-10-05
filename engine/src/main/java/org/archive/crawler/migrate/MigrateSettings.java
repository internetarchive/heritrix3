package org.archive.crawler.migrate;

import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;

public class MigrateSettings {
	
	public void instanceMain(String[] args) throws Exception {
		
		File orderXmlFile = new File(args[0]);
		File newConfigDir = new File(args[1]);
		File defaultConfigDir = new File("dist/src/main/conf/jobs");

		try {

			// copy defaults.xml to configPath
			File srcDefaultsXml = new File(defaultConfigDir,"defaults.xml");
			File tgtDefaultsXml = new File(newConfigDir,"defaults.xml");
			FileUtils.copyFile(srcDefaultsXml, tgtDefaultsXml);

			// copy default profile crawler-beans.xml to configPath
			File srcCrawlerBeansXml = new File(defaultConfigDir,"profile-crawler-beans.xml");
			File tgtCrawlerBeansXml = new File(newConfigDir,"crawler-beans.xml");
			FileUtils.copyFile(srcCrawlerBeansXml, tgtCrawlerBeansXml);

			// parse order XML
			if (!orderXmlFile.canRead()) {
				throw new Exception("can not read file: " 
						+ orderXmlFile.getAbsolutePath());
			} else {
				Document orderXmlDom = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder().parse(orderXmlFile);
			}

		} catch (Exception e) {
			System.out.println("Caught Exception: " + e.getMessage());
		}
	}
		
	public static void main(String[] args) throws Exception {
		new MigrateSettings().instanceMain(args);
	}
	
}
