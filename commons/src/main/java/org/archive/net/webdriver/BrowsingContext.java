package org.archive.net.webdriver;

// https://w3c.github.io/webdriver-bidi/#module-browsingContext
public interface BrowsingContext extends BiDiModule {
    CreateResult create(CreateType type);

    NavigateResult navigate(Context context, String url, ReadinessState wait);

    void close(Context context);

    enum CreateType {tab, window}

    enum ReadinessState {none, interactive, complete}

    record CreateResult(Context context) {
    }

    record Load(Context context, Navigation navigation, long timestamp, String url) implements BiDiEvent {
    }

    record NavigateResult(Navigation navigation, String url) {
    }

    // This is browsingContext.BrowsingContext in the standard, but Java doesn't let us name it that.
    record Context(String id) implements BiDiJson.Identifier {
    }

    record Navigation(String id) implements BiDiJson.Identifier {
    }
}
