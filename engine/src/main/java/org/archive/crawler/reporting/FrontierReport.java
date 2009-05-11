package org.archive.crawler.reporting;

import java.io.PrintWriter;

public class FrontierReport extends Report {

    @Override
    public void write(PrintWriter writer) {
        if(stats.controller.getFrontier().isEmpty()) {
            writer.println("frontier empty");
        } else {
            stats.controller.getFrontier().reportTo("nonempty", writer);
        }
    }

    @Override
    public String getFilename() {
        return "frontier-report.txt";
    }

}
