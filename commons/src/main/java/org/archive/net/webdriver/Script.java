package org.archive.net.webdriver;

import org.springframework.cglib.core.Local;

import java.util.List;
import java.util.stream.Collectors;

// https://www.w3.org/TR/webdriver-bidi/#module-script
public interface Script extends BiDiModule {
    EvaluateResult evaluate(String expression, Target target, boolean awaitPromise);

    default EvaluateResultSuccess evaluateOrThrow(String expression, Target target, boolean awaitPromise) {
        var result = evaluate(expression, target, awaitPromise);
        if (result instanceof EvaluateResultSuccess success) {
            return success;
        } else if (result instanceof EvaluateResultException exception) {
            throw new RuntimeException(exception.exceptionDetails().text());
        } else {
            throw new RuntimeException("Unexpected EvaluateResult: " + result);
        }
    }

    EvaluateResult callFunction(String functionDeclaration, Target target, boolean awaitPromise, List<LocalValue> arguments);

    sealed interface EvaluateResult permits EvaluateResultSuccess, EvaluateResultException {
    }

    @BiDiJson.TypeName("success")
    record EvaluateResultSuccess(RemoteValue result, Realm realm) implements EvaluateResult {
    }

    @BiDiJson.TypeName("exception")
    record EvaluateResultException(ExceptionDetails exceptionDetails, Realm realm) implements EvaluateResult {
    }

    record ExceptionDetails(String text /* TODO */) {
    }

    sealed interface LocalValue {
        static LocalValue from(Object object) {
            if (object instanceof Number number) return new NumberValue(number);
            throw new IllegalArgumentException("Unsupported local value type: " + object.getClass());
        }
    }

    sealed interface RemoteValue {
        Object javaValue();
    }

    @BiDiJson.TypeName("number")
    record NumberValue(Number value) implements RemoteValue, LocalValue {
        @Override
        public Number javaValue() {
            return value;
        }
    }

    @BiDiJson.TypeName("string")
    record StringValue(String value) implements RemoteValue, LocalValue {
        @Override
        public String javaValue() {
            return value;
        }
    }

    @BiDiJson.TypeName("array")
    record ArrayRemoteValue(List<RemoteValue> value) implements RemoteValue, LocalValue {
        @Override
        public List<?> javaValue() {
            return value.stream().map(RemoteValue::javaValue).collect(Collectors.toList());
        }
    }

    sealed interface Target {
    }

    record ContextTarget(BrowsingContext.Context context, String sandbox) implements Target {
    }

    record RealmTarget(Realm realm) implements Target {
    }

    record Realm(String id) implements BiDiJson.Identifier {
    }

}
