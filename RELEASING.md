# Releasing Heritrix

1. Update dependencies. `mvn versions:display-dependency-updates -DprocessDependencyManagementTransitive=false`
2. Prepare release notes in [CHANGELOG.md](CHANGELOG.md)
3. Run slow tests `mvn verify -DrunSlowTests=true -DrunBrowserTests=true`
4. Prepare maven release `mvn release:prepare -Prelease`
5. Perform maven release `mvn release:perform -Prelease`
6. [Publish maven deployment](https://central.sonatype.com/publishing/deployments)
7. Copy release notes from [CHANGELOG.md](CHANGELOG.md) into [Github release](https://github.com/internetarchive/heritrix3/releases)
8. Attach {dist,contrib}/target/*-dist{.tar.gz,.zip} to Github release.
   ```bash
   version=$(git describe --tags --abbrev=0) &&
   gh release upload $version target/checkout/dist/target/heritrix-$version-dist.zip target/checkout/dist/target/heritrix-$version-dist.tar.gz target/checkout/contrib/target/heritrix-contrib-$version-dist.tar.gz
   ```
9. Build docker images (from the dist tarballs uploaded to the Github release in the previous step):
   ```bash
   version=$(git describe --tags --abbrev=0) &&
   podman manifest create iipc/heritrix:$version &&
   podman build --build-arg version=$version --platform linux/amd64,linux/arm64 --manifest iipc/heritrix:$version docker &&
   podman manifest push --all iipc/heritrix:$version &&
   podman manifest push --all iipc/heritrix:$version iipc/heritrix:latest &&
   podman manifest create iipc/heritrix:$version-contrib &&
   podman build --build-arg version=$version --target contrib --platform linux/amd64,linux/arm64 --manifest iipc/heritrix:$version-contrib docker &&
   podman manifest push --all iipc/heritrix:$version-contrib &&
   podman manifest push --all iipc/heritrix:$version-contrib iipc/heritrix:contrib
   ```
10. Announce in #heritrix (IIPC Slack)