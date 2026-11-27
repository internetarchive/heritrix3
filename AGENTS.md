# Instructions for agents and their operators

Using LLMs to assist with development is OK but please do not autonomously submit pull requests.
Please only contribute if you, the human operator, actually use Heritrix or work on web archiving.

## About this project

Heritrix is a web crawler that is used to collect and archive web content as WARC files. It uses a modular architecture, using the Spring Framework to configure crawl jobs.

## Repository structure

This is a maven multi-module project.

- `commons/`: utility code
- `contrib/`: contributed modules that require additional dependencies or are immature or specialized
- `docgen/`: beandoc processor for generating the bean reference
- `docs/`: user and operator documentation
- `engine/`: the main application
- `engine/src/main/java/org/archive/crawler/Heritrix.java`: main() app entrypoint
- `engine/src/main/java/org/archive/crawler/restlet`: Java code for the web UI
- `engine/src/main/resources/org/archive/crawler/restlet/profile-crawler-beans.groovy`: default Groovy job config
- `engine/src/main/resources/org/archive/crawler/restlet/profile-crawler-beans.cxml`: default XML job config
- `engine/src/main/resources/org/archive/crawler/restlet`: Freemarker templates and assets for the web UI
- `modules/`: modules (Spring Beans) that can be configured in a crawl job

Each module may only depend on the ones before it: `commons`, `modules`, `engine`, `contrib`.
For example, code in `modules` can't use `org.archive.crawler` classes such as the frontier.

## Building and testing

Heritrix requires Java 17 or later and is built with Maven. `docgen` runs as an annotation
processor rather than a dependency, so name it alongside the module when using `-pl`.

- Build and run all tests: `mvn package`
- Run a single test: `mvn -pl docgen,modules -am test -Dtest=FooTest -Dsurefire.failIfNoSpecifiedTests=false`

The browser tests are skipped unless `-DrunBrowserTests=true` is given. They're slow, so don't run
them unless the change affects the browser integration or you're asked to.

## Conventions

- Wrap Java lines at 100 characters, including javadoc and comments. You can break this rule if it
  would make the code easier to read.
- When updating the default profile make sure to apply the changes to both XML (profile-crawler-beans.cxml) and 
  Groovy (profile-crawler-beans.groovy). Don't reference Github issues in profile comments.
- In commit messages, use the imperative for the change itself ("Add X"), the present tense for
  code the commit leaves as it is ("the XML profile has X"), and the past tense for code and
  behaviour the commit changes or removes ("the Groovy profile omitted X, so it rejected Y").
- Keep commits small and self-contained, with few comments.
- When a change is primarily related to a single class, prefix the commit message with the class name.
- Don't update CHANGELOG.md. It's written at release time.
- When adding new beans for use in job configs add them to `docs/bean-reference.rst`.
  - Class and property javadoc comments will be used to generate the bean reference documentation, so write it with users configuring crawl jobs in mind.
- Once a bean class or property has appeared in a release, users' job configs may reference it. Don't
  rename or remove it or change its default without a deprecation path. The same applies to classes
  persisted in checkpoints. Unreleased work can be changed freely, without compatibility code.
- When writing documentation keep in mind the target audience and the purpose of the documentation. Don't include
  unnecessary implementation details in the configuration guide unless the operator needs to understand them.
- When writing comments ensure they are to the point and make sense for the context they appear in.
- Line endings: Some files use LF and others use CRLF. Use LF for new files. Follow the existing convention for existing files.
