/*
 *  This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual
 *  contributors.
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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

    @BiDiJson.TypeName("undefined")
    record UndefinedValue() implements RemoteValue, LocalValue {
        @Override
        public String javaValue() {
            return null;
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
