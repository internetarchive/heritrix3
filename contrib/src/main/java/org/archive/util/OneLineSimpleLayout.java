package org.archive.util;

import java.util.logging.LogRecord;

import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;

public class OneLineSimpleLayout extends Layout {

    private OneLineSimpleLogger logger = new OneLineSimpleLogger();

    @Override
    public void activateOptions() {
    }

    @Override
    public String format(LoggingEvent event) {
        java.util.logging.Level level = convertLevel(event.getLevel());

        LogRecord logRecord = new LogRecord(level, event.getMessage().toString());
        logRecord.setLoggerName(event.getLoggerName());
        logRecord.setMillis(event.getTimeStamp());
        logRecord.setSourceClassName(event.getLoggerName());
        logRecord.setSourceMethodName(event.getLocationInformation().getMethodName());
        logRecord.setThreadID((int) Thread.currentThread().getId());

        return logger.format(logRecord);
    }

    protected java.util.logging.Level convertLevel(org.apache.log4j.Level log4jLevel) {
        switch (log4jLevel.toInt()) {
        case org.apache.log4j.Level.TRACE_INT:
            return java.util.logging.Level.FINER;
        case org.apache.log4j.Level.DEBUG_INT:
            return java.util.logging.Level.FINE;
        case org.apache.log4j.Level.INFO_INT:
            return java.util.logging.Level.INFO;
        case org.apache.log4j.Level.WARN_INT:
            return java.util.logging.Level.WARNING;
        case org.apache.log4j.Level.ERROR_INT:
            return java.util.logging.Level.SEVERE;
        case org.apache.log4j.Level.FATAL_INT:
            return java.util.logging.Level.SEVERE;
        default:
            return java.util.logging.Level.ALL;
        }
    }

    @Override
    public boolean ignoresThrowable() {
        return true;
    }

}
