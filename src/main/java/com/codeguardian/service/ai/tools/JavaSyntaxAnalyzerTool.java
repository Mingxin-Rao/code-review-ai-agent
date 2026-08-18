package com.codeguardian.service.ai.tools;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import com.github.javaparser.ParserConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Java syntax analysis tool.
 *
 * <p>Uses JavaParser to analyse the syntax structure of Java code and report errors.</p>
 * <p>Uses JavaParser to analyze the syntax structure and errors of Java code.</p>
 */
@Component("javaSyntaxAnalysis")
@Description("Analyze the syntax structure and errors of Java code using JavaParser")
@Slf4j
public class JavaSyntaxAnalyzerTool implements Function<JavaSyntaxAnalyzerTool.Request, JavaSyntaxAnalyzerTool.Response> {

    @Override
    public Response apply(Request request) {
        log.info("[Function Calling] AI model requested the Java syntax analysis tool...");
        long startTime = System.currentTimeMillis();

        if (request.code == null || request.code.trim().isEmpty()) {
            log.warn("Java syntax analysis aborted: code is empty");
            return new Response(false, List.of("Code is empty"), 0, new ArrayList<>(), 0, "No code provided");
        }

        try {
            ParserConfiguration configuration = new ParserConfiguration();
            configuration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
            JavaParser javaParser = new JavaParser(configuration);
            log.debug("Parsing Java code...");

            // 1. Try parsing directly as a CompilationUnit
            // 1. Try parsing directly (as a CompilationUnit).
            ParseResult<CompilationUnit> result = javaParser.parse(request.code);

            // 2. On failure, try parsing as class members (wrapped in a class)
            // 2. If that fails, try parsing it as a class member (wrap in a class).
            if (!result.isSuccessful()) {
                log.debug("Direct parse failed; trying to wrap it in a class...");
                String classWrappedCode = "public class DummyWrapper { \n" + request.code + "\n}";
                ParseResult<CompilationUnit> classResult = javaParser.parse(classWrappedCode);

                if (classResult.isSuccessful()) {
                    log.info("Parsing succeeded after wrapping in a class");
                    result = classResult;
                } else {
                    // 3. On further failure, try parsing as a method body (wrapped in a method)
                    // 3. If it still fails, try parsing it as a method body (wrap in a method).
                    log.debug("Wrapping in a class failed; trying to wrap it in a method...");
                    String methodWrappedCode = "public class DummyWrapper { public void dummyMethod() { \n" + request.code + "\n} }";
                    ParseResult<CompilationUnit> methodResult = javaParser.parse(methodWrappedCode);

                    if (methodResult.isSuccessful()) {
                        log.info("Parsing succeeded after wrapping in a method");
                        result = methodResult;
                    }
                }
            }

            Response response = new Response();
            
            if (!result.isSuccessful()) {
                response.valid = false;
                response.problems = result.getProblems().stream()
                        .map(p -> {
                            String location = p.getLocation()
                                .flatMap(l -> l.toRange())
                                .map(r -> String.format("line %d, col %d", r.begin.line, r.begin.column))
                                .orElse("unknown location");
                            return String.format("[%s] %s", location, p.getMessage());
                        })
                        .collect(Collectors.toList());
                response.summary = "Syntax errors found: " + response.problems.size();
                log.warn("Java syntax analysis found {} error(s): {}", response.problems.size(), response.problems);
            } else {
                response.valid = true;
                response.problems = new ArrayList<>();
                result.getResult().ifPresent(cu -> {
                    List<String> methodNames = new ArrayList<>();
                    cu.findAll(MethodDeclaration.class).forEach(method -> {
                        // filter out the wrapper method we added
                        if (!method.getNameAsString().equals("dummyMethod")) {
                            methodNames.add(method.getDeclarationAsString(true, true));
                        }
                    });
                    response.methods = methodNames;
                    response.methodCount = methodNames.size();

                    // filter out the wrapper class
                    long classCount = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                            .filter(c -> !c.getNameAsString().equals("DummyWrapper"))
                            .count();
                    response.classCount = (int) classCount;

                    response.summary = String.format("Analysis succeeded. Found %d class(es) and %d method(s).",
                            response.classCount, response.methodCount);
                });
                log.info("Java syntax analysis succeeded: {} class(es), {} method(s)", response.classCount, response.methodCount);
            }

            log.info("Java syntax analysis complete in {} ms", System.currentTimeMillis() - startTime);
            return response;
        } catch (Exception e) {
            log.error("Exception during JavaParser analysis", e);
            return new Response(false, List.of("Analysis failed: " + e.getMessage()), 0, new ArrayList<>(), 0, "An error occurred during analysis");
        }
    }

    @Data
    @JsonClassDescription("Java syntax analysis request")
    public static class Request {
        @JsonPropertyDescription("The Java source code to analyze")
        @JsonProperty(required = true)
        public String code;
    }

    @Data
    public static class Response {
        public boolean valid;
        public List<String> problems = new ArrayList<>();
        public int methodCount;
        public List<String> methods = new ArrayList<>();
        public int classCount;
        public String summary;

        public Response() {}

        public Response(boolean valid, List<String> problems, int methodCount, List<String> methods, int classCount, String summary) {
            this.valid = valid;
            this.problems = problems;
            this.methodCount = methodCount;
            this.methods = methods;
            this.classCount = classCount;
            this.summary = summary;
        }
    }
}
