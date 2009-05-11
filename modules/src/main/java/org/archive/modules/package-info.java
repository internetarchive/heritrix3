/**
 * The beginnings of a refactored settings framework.  It's very similar to 
 * the previous settings framework, except that it's (A) way easier, 
 * (B) testable, (C) typesafe at compile-time, (D) more extensible and
 * (E) it supports the use of Processors outside of a Heritrix context.
 * 
 * <p>Implementors still have to define their attributes and register them
 * with a central store, but this is much easier, and the central store is
 * much more lightweight.  It is actually a single static class, 
 * {@link org.archive.state.KeyManager}.  That package defines the way to
 * create and register the context-sensitive attributes for a class.
 * 
 * <p>CrawlURI is dead, at least as far as Processor implementors are 
 * concerned.  Instead, each ProcessorChain category is going to have its
 * own ProcessorURI subinterface.  For instance, extractors have 
 * ExtractorURI.  These ProcessorURI interfaces define a subset of the 
 * CrawlURI API we know and love.  The ExtractorURI provides methods for 
 * accessing the ReplayInputStream (or CharSequence), but does
 * not provide access to the actual HttpRecorder.  A FetchURI would provide
 * access to the HttpRecorder, but not to the linkExtractorFinished methods.
 * And so on.  The idea is for implementors to have a dozen or so URI
 * methods to contend with, instead of over a hundred.
 * 
 * <p>CrawlURI is now an interface that extends ExtractorURI, FetchURI and 
 * so on, making a CrawlURI something that can be passed to any processor,
 * like we're used to.
 * 
 * <p>To isolate concerns, any previously global state should be moved into
 * the ProcessorURI interfaces.  For instance, the addLocalizedError method
 * from CrawlController was moved into ProcessorURI.  An implementation that
 * is bound to Heritrix can delegate the call to the CrawlController; but
 * a CrawlController is not <i>necessary</i> to implement the method.  The
 * DefaultExtractorURI implementation just saves the local error messages
 * in a list that can be queried later.
 * 
 * <p>Which makes DefaultExtractorURI something that can be passed to an
 * extractor in a unit test.  See {@link ExtractorCSSTest} for a quick and
 * dirty example.  DefaultExtractorURI instances are created with inline
 * test data as the "fetched" content, and run through the process method
 * of ExtractorCSS.  The tests then examine the outLinks of the URI to make
 * sure the correct links were extracted.  The tests also examine the various
 * error log and annotation collections to ensure there were no unanticipated
 * side-effects of processing.  Note the unit test runs without setting up
 * a full Heritrix application, without loading any XML files and without 
 * starting any special threads.
 * 
 * <p>This proof-of-concept prototype doesn't deal with JMX -- I would need
 * to wade through quite a bit of org.archive.crawler.framework to get a 
 * working prototype of that.  But hopefully the approach to take is obvious.
 * KeyManager maps classes to their defined attributes.  Some other class
 * can adapt those defined attributes to OpenMBeanAttributeInfo instance.
 * 
 * <p>Which is probably what I'd do next, if we all agree that this is a good
 * direction to proceed in.
 */
package org.archive.modules;
