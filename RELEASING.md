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
9. Check the [Docker images workflow](https://github.com/internetarchive/heritrix3/actions/workflows/docker-image.yml) pushed the docker images. It runs when the release is published and waits for the dist tarballs from the previous step. If it failed, rerun it with `gh workflow run docker-image.yml -f version=$version`.
10. Announce in #heritrix (IIPC Slack)