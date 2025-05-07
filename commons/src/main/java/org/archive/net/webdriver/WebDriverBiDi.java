package org.archive.net.webdriver;

public interface WebDriverBiDi {
    <T extends BiDiModule> T module(Class<T> moduleClass);

    default BrowsingContext browsingContext() {
        return module(BrowsingContext.class);
    }

    default Session session() {
        return module(Session.class);
    }

    default Network network() {
        return module(Network.class);
    }

    default Script script() {
        return module(Script.class);
    }
}
