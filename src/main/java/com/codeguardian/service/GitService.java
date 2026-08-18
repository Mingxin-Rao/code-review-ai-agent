package com.codeguardian.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * Git service
 */
@Service
@Slf4j
public class GitService {
    
    private static final String TEMP_DIR_PREFIX = "git_repo_";
    private final Map<String, String> clonedRepos = new HashMap<>(); // gitUrl -> localPath
    
    /**
     * Clone a Git repository into a temporary directory
     * @param gitUrl the Git repository URL
     * @param username username (optional)
     * @param password password/token (optional)
     * @return the local temporary directory path
     */
    public String cloneRepository(String gitUrl, String username, String password) {
        try {
            // check whether it has already been cloned
            if (clonedRepos.containsKey(gitUrl)) {
                String existingPath = clonedRepos.get(gitUrl);
                if (Files.exists(Paths.get(existingPath))) {
                    log.info("Using existing clone: {}", existingPath);
                    return existingPath;
                } else {
                    clonedRepos.remove(gitUrl);
                }
            }
            
            // create a temp directory
            Path tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX);
            String tempDirPath = tempDir.toAbsolutePath().toString();

            // build the git clone command
            // for public repos use the original URL as-is; for private repos, inject credentials
            String cloneUrl = gitUrl;
            if (username != null && !username.trim().isEmpty() && !username.trim().equals("")) {
                // if a username is provided, insert it into the URL
                // parse the URL and insert username/password between scheme and host
                try {
                    java.net.URI uri = new java.net.URI(gitUrl);
                    String scheme = uri.getScheme();
                    String host = uri.getHost();
                    int port = uri.getPort();
                    String path = uri.getPath();
                    String query = uri.getQuery();
                    String fragment = uri.getFragment();
                    
                    // build the URL with credentials
                    StringBuilder urlBuilder = new StringBuilder();
                    urlBuilder.append(scheme).append("://");

                    // insert username and password (special characters must be encoded)
                    String encodedUsername = java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8);
                    if (password != null && !password.trim().isEmpty()) {
                        String encodedPassword = java.net.URLEncoder.encode(password, java.nio.charset.StandardCharsets.UTF_8);
                        urlBuilder.append(encodedUsername).append(":").append(encodedPassword).append("@");
                    } else {
                        urlBuilder.append(encodedUsername).append("@");
                    }
                    
                    // add the host
                    urlBuilder.append(host);

                    // add the port (if any)
                    if (port != -1) {
                        urlBuilder.append(":").append(port);
                    }

                    // add the path
                    if (path != null) {
                        urlBuilder.append(path);
                    }

                    // add the query string (if any)
                    if (query != null && !query.isEmpty()) {
                        urlBuilder.append("?").append(query);
                    }

                    // add the fragment (if any)
                    if (fragment != null && !fragment.isEmpty()) {
                        urlBuilder.append("#").append(fragment);
                    }
                    
                    cloneUrl = urlBuilder.toString();
                } catch (Exception e) {
                    log.warn("Failed to parse URL; falling back to simple substitution: {}", e.getMessage());
                    // if URL parsing fails, fall back to simple substitution
                    if (gitUrl.startsWith("https://")) {
                        String auth = username + (password != null && !password.trim().isEmpty() ? ":" + password : "");
                        cloneUrl = gitUrl.replace("https://", "https://" + auth + "@");
                    } else if (gitUrl.startsWith("http://")) {
                        String auth = username + (password != null && !password.trim().isEmpty() ? ":" + password : "");
                        cloneUrl = gitUrl.replace("http://", "http://" + auth + "@");
                    }
                }
            }
            
            // extract the repo name from the URL (used to create the directory)
            String repoName = gitUrl.substring(gitUrl.lastIndexOf('/') + 1);
            if (repoName.endsWith(".git")) {
                repoName = repoName.substring(0, repoName.length() - 4);
            }
            String repoPath = tempDirPath + File.separator + repoName;
            
            // run git clone (it creates the repoName directory under tempDir)
            ProcessBuilder processBuilder = new ProcessBuilder("git", "clone", cloneUrl, repoPath);
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            // read the output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                log.error("Git clone failed: {}", output.toString());
                // clean up the temp directory
                deleteDirectory(tempDir.toFile());
                throw new RuntimeException("Git clone failed: " + output.toString());
            }
            
            // check whether the cloned directory exists
            File repoDir = new File(repoPath);
            if (repoDir.exists() && repoDir.isDirectory()) {
                clonedRepos.put(gitUrl, repoPath);
                log.info("Git repository cloned successfully: {} -> {}", gitUrl, repoPath);
                return repoPath;
            }

            // if the expected path is missing, try to find another directory
            File[] files = tempDir.toFile().listFiles();
            if (files != null && files.length > 0) {
                File foundDir = files[0];
                if (foundDir.isDirectory()) {
                    clonedRepos.put(gitUrl, foundDir.getAbsolutePath());
                    log.info("Git repository cloned successfully: {} -> {}", gitUrl, foundDir.getAbsolutePath());
                    return foundDir.getAbsolutePath();
                }
            }

            throw new RuntimeException("Git clone finished but no repository directory was found");

        } catch (IOException | InterruptedException e) {
            log.error("Failed to clone Git repository: {}", gitUrl, e);
            throw new RuntimeException("Failed to clone Git repository: " + e.getMessage(), e);
        }
    }
    
    /**
     * List all files under a directory (used to build the file tree)
     * @param directoryPath the directory path
     * @return the list of file paths
     */
    public List<String> getFileList(String directoryPath) {
        return getFileList(directoryPath, null, null);
    }

    /**
     * List all files under a directory (with filtering support)
     * @param directoryPath the directory path
     * @param includePaths include-path configuration (newline-separated)
     * @param excludePaths exclude-path configuration (newline-separated)
     * @return the list of file paths
     */
    public List<String> getFileList(String directoryPath, String includePaths, String excludePaths) {
        // parse the configuration
        List<String> includes = parsePaths(includePaths);
        List<String> excludes = parsePaths(excludePaths);
        
        List<String> fileList = new ArrayList<>();
        try {
            Path dir = Paths.get(directoryPath);
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                throw new IllegalArgumentException("Directory does not exist: " + directoryPath);
            }
            
            try (Stream<Path> paths = Files.walk(dir)) {
                paths.filter(Files::isRegularFile)
                     .forEach(path -> {
                         String relativePath = dir.relativize(path).toString().replace(File.separator, "/");
                         if (isPathIncluded(relativePath, includes, excludes)) {
                             fileList.add(relativePath);
                         }
                     });
            }
            
            return fileList;
        } catch (IOException e) {
            log.error("Failed to list files: {}", directoryPath, e);
            throw new RuntimeException("Failed to list files: " + e.getMessage(), e);
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
                .collect(java.util.stream.Collectors.toList());
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
     * Read the file content
     * @param filePath the full file path
     * @return the file content
     */
    public String readFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("File does not exist: " + filePath);
            }
            return Files.readString(path);
        } catch (IOException e) {
            log.error("Failed to read file: {}", filePath, e);
            throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Delete a directory and all its contents
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}

