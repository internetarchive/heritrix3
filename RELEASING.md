# Releasing Heritrix

1. Update dependencies. `mvn versions:display-dependency-updates -DprocessDependencyManagementTransitive=false`
2. Prepare release notes in [CHANGELOG.md](CHANGELOG.md)
3. Run slow tests `mvn verify -DrunSlowTests=true -DrunBrowserTests=true`
4. Prepare maven release `mvn release:prepare -Prelease`
5. Perform maven release `mvn release:perform -Prelease`
6. [Publish maven deployment](https://central.sonatype.com/publishing/deployments)
7. Build docker images:
   ```bash
   version=3.10.0
   podman manifest create iipc/heritrix:$version
   podman build --build-arg version=$version --platform linux/amd64,linux/arm64 --manifest iipc/heritrix:$version docker
   podman manifest push --all iipc/heritrix:$version
   podman manifest push --all iipc/heritrix:$version iipc/heritrix:latest
   ```
8. Copy release notes from [CHANGELOG.md](CHANGELOG.md) into [Github release](https://github.com/internetarchive/heritrix3/releases)
9. Announce in #heritrix (IIPC Slack)