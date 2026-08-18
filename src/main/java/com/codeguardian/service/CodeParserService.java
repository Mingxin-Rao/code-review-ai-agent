package com.codeguardian.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.util.Arrays;
import java.util.Collections;

/**
 * Code parsing service
 */
@Service
@Slf4j
public class CodeParserService {

    private final ExecutorService executor = Executors.newFixedThreadPool(20);

    @jakarta.annotation.PreDestroy
    public void destroy() {
        executor.shutdown();
    }
    
    /**
     * Read the file content
     */
    public String readFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("File does not exist: " + path.toAbsolutePath() + " (input path: " + filePath + ")");
            }
            return Files.readString(path);
        } catch (IOException e) {
            log.error("Failed to read file: {}", filePath, e);
            throw new RuntimeException("Failed to read file: " + filePath, e);
        }
    }
    
    /**
     * Read all code files under a directory
     */
    public String readDirectory(String directoryPath) {
        try {
            Path dir = Paths.get(directoryPath);
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                // check whether src/directoryPath exists and offer a hint
                String suggestion = "";
                try {
                    Path srcDir = Paths.get("src", directoryPath);
                    if (Files.exists(srcDir) && Files.isDirectory(srcDir)) {
                        suggestion = " Found " + srcDir.toAbsolutePath() + "; did you mean src/" + directoryPath + " ?";
                    }
                } catch (Exception ignored) {}

                throw new IllegalArgumentException("Directory does not exist: " + dir.toAbsolutePath() + " (input path: " + directoryPath + ")." + suggestion);
            }

            List<Path> files;
            try (Stream<Path> paths = Files.walk(dir)) {
                files = paths.filter(Files::isRegularFile)
                        .filter(this::isCodeFile)
                        .collect(Collectors.toList());
            }

            if (files.isEmpty()) {
                return "";
            }

            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (Path p : files) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return Files.readString(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, executor)
                        .exceptionally(ex -> {
                            log.warn("Failed to read file: {}", p, ex);
                            return null;
                        }));
            }

            StringBuilder content = new StringBuilder();
            for (int i = 0; i < futures.size(); i++) {
                String fileContent = futures.get(i).join();
                if (fileContent != null) {
                    content.append("=== ")
                            .append(files.get(i).toString())
                            .append(" ===\n")
                            .append(fileContent)
                            .append("\n\n");
                }
            }
            return content.toString();
        } catch (IOException e) {
            log.error("Failed to read directory: {}", directoryPath, e);
            throw new RuntimeException("Failed to read directory: " + directoryPath, e);
        }
    }
    
    /**
     * Read the project code
     */
    public String readProject(String projectPath) {
        return readDirectory(projectPath);
    }
    
    /**
     * Scan all code-file paths under a directory
     */
    public List<Path> scanDirectory(String directoryPath) {
        return scanDirectory(directoryPath, null, null);
    }

    /**
     * Scan all code-file paths under a directory (with filtering support)
     */
    public List<Path> scanDirectory(String directoryPath, String includePaths, String excludePaths) {
        try {
            if (directoryPath == null || directoryPath.trim().isEmpty()) {
                throw new IllegalArgumentException("Directory path must not be empty");
            }

            Path dir = Paths.get(directoryPath).toAbsolutePath().normalize();
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                throw new IllegalArgumentException("Directory does not exist: " + directoryPath);
            }

            log.info("Scanning directory: {}", dir);

            // parse the configuration
            List<String> includes = parsePaths(includePaths);
            List<String> excludes = parsePaths(excludePaths);

            try (Stream<Path> paths = Files.walk(dir)) {
                return paths.filter(Files::isRegularFile)
                        .filter(this::isCodeFile)
                        .filter(path -> {
                            String relativePath = dir.relativize(path).toString().replace(java.io.File.separator, "/");
                            return isPathIncluded(relativePath, includes, excludes);
                        })
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            log.error("Failed to scan directory: {}", directoryPath, e);
            throw new RuntimeException("Failed to scan directory: " + directoryPath, e);
        }
    }

    private List<String> parsePaths(String pathsConfig) {
        if (pathsConfig == null || pathsConfig.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(pathsConfig.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.replace("\\", "/")) // normalize separators
                .collect(Collectors.toList());
    }

    private boolean isPathIncluded(String path, List<String> includes, List<String> excludes) {
        // check exclude paths
        for (String exclude : excludes) {
            if (path.startsWith(exclude) || path.contains("/" + exclude + "/")) {
                return false;
            }
        }

        // check include paths
        if (!includes.isEmpty()) {
            boolean isIncluded = false;
            for (String include : includes) {
                if (path.startsWith(include) || path.contains("/" + include + "/")) {
                    isIncluded = true;
                    break;
                }
            }
            return isIncluded;
        }
        
        return true;
    }

    /**
     * Determine whether a file is a code file
     */
    private boolean isCodeFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".java") ||
               fileName.endsWith(".js") ||
               fileName.endsWith(".ts") ||
               fileName.endsWith(".py") ||
               fileName.endsWith(".go") ||
               fileName.endsWith(".rs") ||
               fileName.endsWith(".cpp") ||
               fileName.endsWith(".c") ||
               fileName.endsWith(".cs") ||
               fileName.endsWith(".php") ||
               fileName.endsWith(".rb") ||
               fileName.endsWith(".swift") ||
               fileName.endsWith(".kt");
    }
}
