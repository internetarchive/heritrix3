import org.archive.spring.PathSharingContextTest

beans {
    bean1(PathSharingContextTest.Bean1) {
        name = "groovy"
    }
    bean2(PathSharingContextTest.Bean2)
}