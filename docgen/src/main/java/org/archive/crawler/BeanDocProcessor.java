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

package org.archive.crawler;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.source.tree.*;
import com.sun.source.util.Trees;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.StandardLocation;
import java.beans.Introspector;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes("*")
public class BeanDocProcessor extends AbstractProcessor {
    private Trees trees;
    private Map<String, Bean> classes = new LinkedHashMap<>();
    private final Pattern DOC_CLEANUP = Pattern.compile("^\\s*@(?:author|version|see).*", Pattern.MULTILINE);

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.trees = Trees.instance(processingEnv);
    }

    private String getDocComment(Element element) {
        var path = trees.getPath(element);
        if (path == null) return null; // no source available
        String docComment = trees.getDocComment(path);
        if (docComment == null) return null;
        docComment = docComment.replace("\n ", "\n"); // remove indent
        return DOC_CLEANUP.matcher(docComment).replaceAll("").trim();
    }

    private class FieldInfo {
        private String docComment;
        private Object initializer;

        public FieldInfo(VariableElement field) {
            VariableTree vt = (VariableTree) trees.getTree(field);
            if (vt != null) {
                ExpressionTree init = vt.getInitializer();
                if (init instanceof LiteralTree lit) {
                    initializer = lit.getValue();
                }
            }

            docComment = getDocComment(field);
        }
    }

    public class Bean {
        public final String superclass;
        public final String description;
        public final HashMap<String, Property> properties;

        Bean(TypeElement type) {
            superclass = getSuperclass(type);
            description = getDocComment(type);
            properties = getProperties(type);
        }
    }

    public class Property {
        @JsonProperty("default")
        public Object defaultValue;
        public String description;
        public String type;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        for (var root : round.getRootElements()) {
            if (root.getKind().isClass()) {
                processClass((TypeElement) root);
            }
        }

        if (round.processingOver()) {
            writeJsonOutput();
        }

        return false;
    }

    private void writeJsonOutput() {
        var objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        try (Writer writer = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "",
                "META-INF/heritrix-beans.json").openWriter()) {
            objectMapper.writeValue(writer, classes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processClass(TypeElement type) {
        String className = processingEnv.getElementUtils().getBinaryName(type).toString();
        classes.put(className, new Bean(type));
    }

    private HashMap<String, Property> getProperties(TypeElement type) {
        // Visit fields
        var fields = new HashMap<String, FieldInfo>();
        for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            fields.put(field.getSimpleName().toString(), new FieldInfo(field));
        }

        // Visit initializers like `setMyProperty(5)`
        var initializers = new HashMap<String, Object>();
        ClassTree classTree = trees.getTree(type);
        if (classTree != null) {
            for (Tree member : classTree.getMembers()) {
                if (member.getKind() == Tree.Kind.BLOCK) {
                    BlockTree block = (BlockTree) member;
                    if (block.isStatic()) continue; // skip static initializers
                    for (StatementTree stmt : block.getStatements()) {
                        if (stmt instanceof ExpressionStatementTree est
                            && est.getExpression() instanceof MethodInvocationTree mit) {
                            ExpressionTree select = mit.getMethodSelect();
                            if (select instanceof IdentifierTree identifier) {
                                String name = identifier.getName().toString();
                                if (name.startsWith("set") && mit.getArguments().size() == 1) {
                                    String prop = Introspector.decapitalize(name.substring(3));
                                    ExpressionTree arg = mit.getArguments().get(0);
                                    if (arg instanceof LiteralTree lit) {
                                        initializers.put(prop, lit.getValue());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Visit setters
        var properties = new HashMap<String, Property>();
        for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
            String methodName = method.getSimpleName().toString();
            if (methodName.startsWith("set")
                && method.getModifiers().contains(javax.lang.model.element.Modifier.PUBLIC)
                && method.getParameters().size() == 1) {
                String propertyName = Introspector.decapitalize(methodName.substring(3));

                var property = new Property();
                property.description = getDocComment(method);
                property.type = method.getParameters().get(0).asType().toString();

                var field = fields.get(propertyName);
                if (field != null) {
                    if (property.description == null) property.description = field.docComment;
                    if (field.initializer != null) property.defaultValue = field.initializer;
                }

                var initValue = initializers.get(propertyName);
                if (initValue != null) property.defaultValue = initValue;

                properties.put(propertyName, property);
            }
        }
        return properties;
    }

    private static String getSuperclass(TypeElement type) {
        TypeMirror superType = type.getSuperclass();
        if (superType == null || superType.getKind() != TypeKind.DECLARED) return null;
        TypeElement superElement = (TypeElement) ((DeclaredType) superType).asElement();
        String superName = superElement.getQualifiedName().toString();
        if (superName.equals("java.lang.Object")) return null;
        return superName;
    }
}
