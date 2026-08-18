// Code Review page JavaScript

let currentTaskId = null;
let currentFindings = [];
let selectedFindingId = null;

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    // DEBUG & FIX: auto-diagnose the grayed-out Git repository type issue
    try {
        const codeTypeSelect = document.getElementById('codeTypeSelect');
        if (codeTypeSelect) {
            console.log('[DEBUG] Checking codeTypeSelect state...');
            const gitOption = codeTypeSelect.querySelector('option[value="git"]');
            if (gitOption) {
                console.log('[DEBUG] Git option initial state:', {
                    disabled: gitOption.disabled,
                    selected: gitOption.selected,
                    text: gitOption.text,
                    attributes: gitOption.attributes
                });
                
                // Force-enable the Git option to prevent it being accidentally disabled
                if (gitOption.disabled) {
                    console.warn('[FIX] Detected the Git option was disabled; forcing it enabled...');
                    gitOption.disabled = false;
                    gitOption.removeAttribute('disabled');
                    console.log('[FIX] Git option enabled');
                } else {
                    console.log('[DEBUG] Git option is available');
                }
            } else {
                console.error('[DEBUG] Could not find the option with value="git"');
            }
        } else {
            console.error('[DEBUG] Could not find the codeTypeSelect element');
        }
    } catch (e) {
        console.error('[DEBUG] Error while checking the Git option:', e);
    }

    // Initialize the code editor
    initCodeEditor();
    
    // DEBUG: monitor attribute changes on codeTypeSelect
    const codeTypeSelectMonitor = document.getElementById('codeTypeSelect');
    if (codeTypeSelectMonitor) {
        const observer = new MutationObserver((mutations) => {
            mutations.forEach((mutation) => {
                if (mutation.type === 'attributes' && mutation.attributeName === 'disabled') {
                    console.warn('[DEBUG] codeTypeSelect disabled attribute changed to:', codeTypeSelectMonitor.disabled);
                    console.trace('Who disabled codeTypeSelect?');
                    
                    // AUTO-FIX: if not currently reviewing, force-enable
                    const startBtn = document.getElementById('startReviewBtn');
                    const isReviewing = startBtn && startBtn.disabled && startBtn.innerHTML.includes('Reviewing');
                    
                    if (codeTypeSelectMonitor.disabled && !isReviewing) {
                        console.warn('[FIX] Detected codeTypeSelect was unexpectedly disabled; forcing it enabled...');
                        codeTypeSelectMonitor.disabled = false;
                        codeTypeSelectMonitor.removeAttribute('disabled');
                        
                        // Also check the Git option
                        const gitOption = codeTypeSelectMonitor.querySelector('option[value="git"]');
                        if (gitOption && gitOption.disabled) {
                            gitOption.disabled = false;
                            gitOption.removeAttribute('disabled');
                        }
                    }
                }
            });
        });
        
        observer.observe(codeTypeSelectMonitor, { attributes: true });
        console.log('[DEBUG] Started monitoring codeTypeSelect state');
        
        // Also monitor the Git option
        const gitOption = codeTypeSelectMonitor.querySelector('option[value="git"]');
        if (gitOption) {
            const optionObserver = new MutationObserver((mutations) => {
                mutations.forEach((mutation) => {
                    if (mutation.type === 'attributes' && mutation.attributeName === 'disabled') {
                        console.warn('[DEBUG] Git option disabled attribute changed to:', gitOption.disabled);
                        
                        if (gitOption.disabled) {
                            console.warn('[FIX] Detected the Git option was disabled; forcing it enabled...');
                            gitOption.disabled = false;
                            gitOption.removeAttribute('disabled');
                        }
                    }
                });
            });
            optionObserver.observe(gitOption, { attributes: true });
        }
    }
    
    // Initialize the review results area as empty
    initResultsArea();
    
    // Initialize drag-and-drop
    initDragAndDrop();
    
    // Initialize file-tree width resizing
    initFileTreeResizer();
    
    // Initialize code-panel width resizing
    initEditorResizer();
    
    // Listen for review type changes
    const codeTypeSelect = document.getElementById('codeTypeSelect');
    if (codeTypeSelect) {
        // Initial setup
        handleReviewTypeChange(codeTypeSelect.value);
        
        codeTypeSelect.addEventListener('change', function() {
            handleReviewTypeChange(this.value);
        });
    }
    
    const modelSelect = document.getElementById('modelSelect');
    const hasAvailableModelProvidersConfig = document.getElementById('hasAvailableModelProvidersConfig');
    const defaultModelProviderConfig = document.getElementById('defaultModelProviderConfig');
    const hasAvailableModelProviders = hasAvailableModelProvidersConfig
        && String(hasAvailableModelProvidersConfig.value).toLowerCase() === 'true';
    const defaultModelProvider = defaultModelProviderConfig && defaultModelProviderConfig.value
        ? defaultModelProviderConfig.value
        : '';

    if (modelSelect && defaultModelProvider) {
        modelSelect.value = defaultModelProvider;
    } else if (modelSelect && modelSelect.options.length > 0 && !modelSelect.value) {
        modelSelect.selectedIndex = 0;
    }
    
    // Initialize the ruleset selection (based on config)
    const templateSelect = document.getElementById('templateSelect');
    const ruleStandardConfig = document.getElementById('ruleStandardConfig');
    if (templateSelect && ruleStandardConfig && ruleStandardConfig.value) {
        // Try to match the configured value (case-insensitive)
        const configValue = ruleStandardConfig.value.toUpperCase();
        let found = false;
        for (let i = 0; i < templateSelect.options.length; i++) {
            if (templateSelect.options[i].value.toUpperCase() === configValue) {
                templateSelect.selectedIndex = i;
                found = true;
                break;
            }
        }
        
        // If no match found but a value is configured, it may be an alias or need mapping
        if (!found) {
            console.warn('The configured ruleset was not found in the dropdown:', configValue);
        }
    }
    
    const rulesOnlyCheckbox = document.getElementById('rulesOnlyCheckbox');
    const ragEnhancementWrapper = document.getElementById('ragEnhancementWrapper');
    const rulesOnlyLabel = document.querySelector('label[for="rulesOnlyCheckbox"]');
    
    if (rulesOnlyCheckbox) {
        const updateAiControlVisibility = () => {
            const canUseAiReview = hasAvailableModelProviders && !rulesOnlyCheckbox.checked;
            if (modelSelect) {
                modelSelect.style.display = canUseAiReview ? 'block' : 'none';
            }
            if (ragEnhancementWrapper) {
                ragEnhancementWrapper.style.display = canUseAiReview ? 'flex' : 'none';
            }
        };

        if (!hasAvailableModelProviders) {
            rulesOnlyCheckbox.checked = true;
            rulesOnlyCheckbox.disabled = true;
            if (rulesOnlyLabel) {
                rulesOnlyLabel.textContent = 'Rules-only review (no model configured)';
            }
        }

        updateAiControlVisibility();

        rulesOnlyCheckbox.addEventListener('change', function() {
            updateAiControlVisibility();
        });
    }
});

// Initialize the code editor
function initCodeEditor() {
    const editor = document.getElementById('codeEditor');
    const lineNumbers = document.getElementById('lineNumbers');
    if (!editor || !lineNumbers) return;
    
    // Update line numbers
    function updateLineNumbers() {
        // Get the code text (accounting for post-highlight HTML)
        // Use innerHTML to check for a leading blank line (BR element or empty text node)
        let text = '';
        let hasLeadingNewline = false;
        
        // Check whether the first child is a BR element or an empty text node
        if (editor.firstChild) {
            if (editor.firstChild.nodeType === Node.ELEMENT_NODE && 
                editor.firstChild.tagName === 'BR') {
                hasLeadingNewline = true;
            } else if (editor.firstChild.nodeType === Node.TEXT_NODE) {
                const firstText = editor.firstChild.textContent;
                if (firstText === '\n' || firstText === '\r\n' || 
                    (firstText.length > 0 && firstText.charAt(0) === '\n')) {
                    hasLeadingNewline = true;
                }
            }
        }
        
        // Use textContent to get the text (it preserves newlines correctly)
        text = editor.textContent || editor.innerText || '';
        
        // If a leading blank line was detected but textContent lacks it, add it
        if (hasLeadingNewline && text && text.charAt(0) !== '\n') {
            text = '\n' + text;
        }
        
        // If the text is empty, keep at least one line (a blank line)
        if (text === '') {
            text = '\n';
        }
        
        // Use split('\n', -1) to keep all empty strings, including leading and trailing blank lines
        // This ensures the first blank line is recognized correctly
        const lines = text.split('\n', -1);
        const lineCount = lines.length;
        
        // If the code is empty, show at least one line
        const actualLineCount = lineCount === 0 ? 1 : lineCount;
        
        // Generate line numbers starting at line 1 (including blank lines)
        let lineNumbersHtml = '';
        for (let i = 1; i <= actualLineCount; i++) {
            lineNumbersHtml += i;
            if (i < actualLineCount) {
                lineNumbersHtml += '\n';
            }
        }
        lineNumbers.textContent = lineNumbersHtml;
        
        // Sync scroll
        const editorScrollTop = editor.scrollTop || editor.parentElement?.scrollTop || 0;
        lineNumbers.scrollTop = editorScrollTop;
    }
    
    // Scroll-sync function
    function syncScroll() {
        const editorScrollTop = editor.scrollTop || editor.parentElement?.scrollTop || 0;
        lineNumbers.scrollTop = editorScrollTop;
    }
    
    // Initial line-number update
    updateLineNumbers();
    
    // Handle paste: force plain text so styled code doesn't lose line breaks
    editor.addEventListener('paste', function(e) {
        e.preventDefault();
        // Get plain text from the clipboard
        const text = (e.clipboardData || window.clipboardData).getData('text/plain');
        // Insert text (modern browsers handle newlines automatically)
        document.execCommand('insertText', false, text);
        // Trigger the input event to update line numbers and highlighting
        // execCommand usually fires input, but do it just in case
        updateLineNumbers();
        setTimeout(() => {
            highlightCode();
            updateLineNumbers();
        }, 10);
    });
    
    // Listen for input changes
    editor.addEventListener('input', function() {
        updateLineNumbers();
        setTimeout(() => {
            highlightCode();
            updateLineNumbers(); // re-update line numbers after highlighting
        }, 0);
    });
    
    // Listen for scroll to sync the line-number area
    const codeEditorPre = editor.parentElement; // .code-editor-pre
    if (codeEditorPre) {
        codeEditorPre.addEventListener('scroll', syncScroll);
    }
    editor.addEventListener('scroll', syncScroll);
    
    // Initial syntax highlighting
    highlightCode();
}

// Syntax highlighting
function highlightCode() {
    const editor = document.getElementById('codeEditor');
    if (!editor) return;
    
    // Get the code text
    // Prefer innerText to make sure newlines are preserved
    let code = editor.innerText || editor.textContent || '';
    
    // If innerText is empty (some Firefox versions differ), fall back to TreeWalker
    if (!code) {
        const walker = document.createTreeWalker(
            editor,
            NodeFilter.SHOW_TEXT,
            null,
            false
        );
        
        let node;
        while (node = walker.nextNode()) {
            code += node.textContent;
        }
    }
    
    // Use Prism for syntax highlighting
    if (typeof Prism !== 'undefined' && Prism && typeof Prism.languages !== 'undefined' && typeof Prism.highlight === 'function') {
        // Wait for the Java language to load
        if (Prism.languages && Prism.languages.java) {
            try {
                const highlighted = Prism.highlight(code, Prism.languages.java, 'java');
                // Save the current scroll position
                const scrollTop = editor.scrollTop;
                // Save the current cursor position
                const cursorOffset = getCursorPosition(editor);
                
                // Update the highlighted code
                editor.innerHTML = highlighted;
                
                // Restore the scroll position
                editor.scrollTop = scrollTop;
                // Restore the cursor position
                restoreCursorPosition(editor, cursorOffset);
            } catch (e) {
                console.warn('Syntax highlighting failed:', e);
            }
        } else {
            // If the Java language isn't loaded yet, retry after a delay
            setTimeout(function() {
                highlightCode();
            }, 100);
        }
    }
}

// Get the cursor offset relative to plain text
function getCursorPosition(editor) {
    const selection = window.getSelection();
    if (!selection.rangeCount) return 0;
    
    const range = selection.getRangeAt(0);
    const preSelectionRange = range.cloneRange();
    preSelectionRange.selectNodeContents(editor);
    preSelectionRange.setEnd(range.startContainer, range.startOffset);
    return preSelectionRange.toString().length;
}

// Restore the cursor position
function restoreCursorPosition(editor, offset) {
    const selection = window.getSelection();
    const range = document.createRange();
    
    let currentOffset = 0;
    let found = false;
    
    // Walk all text nodes to find the matching position
    const walker = document.createTreeWalker(editor, NodeFilter.SHOW_TEXT, null, false);
    let node;
    
    while (node = walker.nextNode()) {
        const length = node.textContent.length;
        if (currentOffset + length >= offset) {
            range.setStart(node, offset - currentOffset);
            range.setEnd(node, offset - currentOffset); // collapse the cursor
            found = true;
            break;
        }
        currentOffset += length;
    }
    
    if (found) {
        selection.removeAllRanges();
        selection.addRange(range);
    }
}



// Initialize the review results area
function initResultsArea() {
    const resultsContent = document.getElementById('resultsContent');
    const detailsContent = document.getElementById('detailsContent');
    
    if (resultsContent) {
        resultsContent.innerHTML = '';
    }
    
    if (detailsContent) {
        detailsContent.innerHTML = '';
        detailsContent.classList.add('empty');
    }
    
    // Disable the Generate Report button
    const generateReportBtn = document.getElementById('generateReportBtn');
    if (generateReportBtn) {
        generateReportBtn.disabled = true;
    }

    // Reset state
    currentTaskId = null;
    currentFindings = [];
    selectedFindingId = null;
}

// Currently selected file/directory
let selectedFile = null;
let selectedFileContent = '';
let selectedDirectory = null;
let currentFileSource = 'local'; // local, git, server

// Check whether the path is within the configured scope
function isPathIncluded(path) {
    // In "Directory" mode, ignore the configured scope (the uploaded directory should include all files)
    const codeTypeSelect = document.getElementById('codeTypeSelect');
    if (codeTypeSelect && codeTypeSelect.value === 'directory') {
        return true;
    }

    const includePathsInput = document.getElementById('includePathsConfig');
    const excludePathsInput = document.getElementById('excludePathsConfig');
    
    // If nothing is configured, include everything by default
    if ((!includePathsInput || !includePathsInput.value) && (!excludePathsInput || !excludePathsInput.value)) return true;
    
    const includePaths = (includePathsInput && includePathsInput.value) ? includePathsInput.value.split('\n').filter(p => p.trim() !== '').map(p => p.trim().replace(/\\/g, '/')) : [];
    const excludePaths = (excludePathsInput && excludePathsInput.value) ? excludePathsInput.value.split('\n').filter(p => p.trim() !== '').map(p => p.trim().replace(/\\/g, '/')) : [];
    
    // Normalize path separators to /
    const normalizedPath = path.replace(/\\/g, '/');
    
    // Check exclude paths
    for (const exclude of excludePaths) {
        // If an exclude path matches the start of the current path, or the current path contains it (as a directory)
        if (normalizedPath.startsWith(exclude) || normalizedPath.includes('/' + exclude + '/')) {
            return false;
        }
    }
    
    // Check include paths (if any are configured, the path must match one of them)
    if (includePaths.length > 0) {
        let isIncluded = false;
        for (const include of includePaths) {
            // If the current path starts with an include path, or the include path is a parent of it
            // Note: handle this loosely, since path may be a file's full path
            if (normalizedPath.startsWith(include) || normalizedPath.includes('/' + include + '/')) {
                isIncluded = true;
                break;
            }
        }
        return isIncluded;
    }
    
    return true;
}

// Check and show the server root option
function checkAndShowServerRootOption() {
    const projectRootInput = document.getElementById('projectRootConfig');
    const serverRootOption = document.getElementById('serverRootOption');
    const serverRootPath = document.getElementById('serverRootPath');
    
    if (projectRootInput && projectRootInput.value && projectRootInput.value.trim() !== '' && serverRootOption) {
        serverRootOption.style.display = 'flex';
        if (serverRootPath) {
            serverRootPath.textContent = projectRootInput.value;
        }
    } else if (serverRootOption) {
        serverRootOption.style.display = 'none';
    }
}

// Use the server-configured root directory
function useServerRoot() {
    const projectRootInput = document.getElementById('projectRootConfig');
    if (!projectRootInput || !projectRootInput.value) return;
    
    const rootPath = projectRootInput.value;
    selectedDirectory = rootPath;
    currentFileSource = 'server'; // mark the source as server
    directoryFiles = []; // clear the uploaded file list
    
    const directoryStatus = document.getElementById('directoryStatus');
    const dropZone = document.getElementById('directoryDropZone');
    const serverRootOption = document.getElementById('serverRootOption');
    const directoryContentWrapper = document.getElementById('directoryContentWrapper');
    const fileTreeContent = document.getElementById('fileTreeContent');
    
    if (directoryStatus) {
        directoryStatus.textContent = 'Selected server directory: ' + rootPath;
        directoryStatus.style.color = 'var(--primary-color)';
        directoryStatus.style.fontWeight = 'bold';
    }
    
    if (dropZone) dropZone.style.display = 'none';
    if (serverRootOption) serverRootOption.style.display = 'none';
    
    // Show loading state
    if (directoryContentWrapper) {
        directoryContentWrapper.style.display = 'flex';
    }
    if (fileTreeContent) {
        fileTreeContent.innerHTML = '<div style="padding: 20px; text-align: center; color: var(--text-secondary-color);">Loading server file list...</div>';
    }
    
    // Call the backend to get the file list
    fetch('/api/review/server/list?path=' + encodeURIComponent(rootPath))
        .then(response => response.json())
        .then(data => {
            if (data.code === 200) {
                // Filter the file list
                const filteredFiles = data.data.filter(path => isPathIncluded(path));
                buildFileTreeFromPaths(filteredFiles, rootPath);
            } else {
                alert('Failed to get file list: ' + data.msg);
                if (fileTreeContent) fileTreeContent.innerHTML = '<div style="padding: 20px; text-align: center; color: red;">Load failed</div>';
            }
        })
        .catch(error => {
            console.error('Error fetching file list:', error);
            alert('Failed to get file list. Please check the network or server logs.');
            if (fileTreeContent) fileTreeContent.innerHTML = '<div style="padding: 20px; text-align: center; color: red;">Load failed</div>';
        });
}

// Build a file tree from a list of paths
function buildFileTreeFromPaths(paths, rootPath) {
    const fileTreeContent = document.getElementById('fileTreeContent');
    if (!fileTreeContent) return;
    
    fileTreeContent.innerHTML = '';
    
    // Build the tree structure
    const tree = {};
    paths.forEach(path => {
        // Remove the root path prefix, keep only the relative path
        let relativePath = path;
        if (path.startsWith(rootPath)) {
            relativePath = path.substring(rootPath.length);
            if (relativePath.startsWith('/') || relativePath.startsWith('\\')) {
                relativePath = relativePath.substring(1);
            }
        }
        
        const parts = relativePath.split(/[/\\]/);
        let current = tree;
        
        parts.forEach((part, index) => {
            if (!current[part]) {
                current[part] = index === parts.length - 1 ? null : {};
            }
            current = current[part];
        });
    });
    
    // Render the tree
    const ul = document.createElement('ul');
    ul.className = 'file-tree';
    
    function renderNode(node, parentElement, fullPath) {
        const keys = Object.keys(node).sort((a, b) => {
            // Directories come before files
            const aIsDir = node[a] !== null;
            const bIsDir = node[b] !== null;
            if (aIsDir && !bIsDir) return -1;
            if (!aIsDir && bIsDir) return 1;
            return a.localeCompare(b);
        });
        
        keys.forEach(key => {
            const isDir = node[key] !== null;
            const li = document.createElement('li');
            const itemPath = fullPath ? fullPath + '/' + key : key;
            
            const div = document.createElement('div');
            div.className = 'file-tree-item';
            div.style.paddingLeft = '20px'; // simple indentation
            
            const icon = document.createElement('i');
            icon.className = isDir ? 'fas fa-folder' : 'fas fa-file-code';
            icon.style.marginRight = '8px';
            
            const span = document.createElement('span');
            span.textContent = key;
            
            div.appendChild(icon);
            div.appendChild(span);
            li.appendChild(div);
            
            if (isDir) {
                div.classList.add('folder');
                div.onclick = function(e) {
                    e.stopPropagation();
                    const childrenUl = li.querySelector('ul');
                    if (childrenUl) {
                        if (childrenUl.style.display === 'none') {
                            childrenUl.style.display = 'block';
                            icon.className = 'fas fa-folder-open';
                        } else {
                            childrenUl.style.display = 'none';
                            icon.className = 'fas fa-folder';
                        }
                    }
                };
                
                const childrenUl = document.createElement('ul');
                childrenUl.style.display = 'none';
                childrenUl.style.marginLeft = '20px';
                renderNode(node[key], childrenUl, itemPath);
                li.appendChild(childrenUl);
            } else {
                div.classList.add('file');
                div.onclick = function(e) {
                    e.stopPropagation();
                    // Clear the selection on other files
                    document.querySelectorAll('.file-tree-item.active').forEach(el => el.classList.remove('active'));
                    div.classList.add('active');
                    
                    // Load the file content
                    // If it's a server file, call the API
                    if (currentFileSource === 'server') {
                         const actualPath = rootPath + (rootPath.endsWith('/') ? '' : '/') + itemPath;
                         fetch('/api/review/server/file?path=' + encodeURIComponent(actualPath))
                            .then(response => response.json())
                            .then(data => {
                                if (data.code === 200) {
                                    loadFileContentToEditor(data.data);
                                    // Update the editor title
                                    const title = document.getElementById('directoryEditorTitle');
                                    if (title) title.textContent = key;
                                } else {
                                    alert('Failed to read the file: ' + data.msg);
                                }
                            });
                    }
                };
            }
            
            parentElement.appendChild(li);
        });
    }
    
    renderNode(tree, ul, '');
    fileTreeContent.appendChild(ul);
}

// Handle review type changes
function handleReviewTypeChange(reviewType) {
    console.log('[DEBUG] handleReviewTypeChange called with:', reviewType);
    
    const codeEditorArea = document.getElementById('codeEditorArea');
    const fileSelectArea = document.getElementById('fileSelectArea');
    const directorySelectArea = document.getElementById('directorySelectArea');
    const selectDirectoryBtn = document.getElementById('selectDirectoryBtn');
    const directoryHint = document.getElementById('directoryHint');
    const directoryHintSecondary = document.getElementById('directoryHintSecondary');
    const dropZone = document.getElementById('directoryDropZone');
    const directoryContentWrapper = document.getElementById('directoryContentWrapper');
    const configureGitBtn = document.getElementById('configureGitBtn');
    
    // Hide all areas
    if (codeEditorArea) codeEditorArea.style.display = 'none';
    if (fileSelectArea) fileSelectArea.style.display = 'none';
    if (directorySelectArea) directorySelectArea.style.display = 'none';
    
    // Hide the Git config button (default)
    if (configureGitBtn) {
        configureGitBtn.style.display = 'none';
    }

    // Hide the server root option (default)
    const serverRootOption = document.getElementById('serverRootOption');
    if (serverRootOption) {
        serverRootOption.style.display = 'none';
    }
    
    // Reset the directory-selection area display state
    if (dropZone) dropZone.style.display = 'flex';
    if (selectDirectoryBtn) {
        selectDirectoryBtn.style.display = 'inline-block';
    }
    
    switch(reviewType) {
        case 'snippet':
            if (codeEditorArea) codeEditorArea.style.display = 'flex';
            break;
        case 'file':
            if (fileSelectArea) fileSelectArea.style.display = 'flex';
            break;
        case 'directory':
            if (directorySelectArea) {
                directorySelectArea.style.display = 'flex';
                if (directoryHint) directoryHint.textContent = 'Drag a folder here';
                if (directoryHintSecondary) directoryHintSecondary.textContent = 'or use the button below to choose a folder';
                if (selectDirectoryBtn) {
                    selectDirectoryBtn.textContent = 'Choose Folder';
                    selectDirectoryBtn.style.display = 'inline-block';
                }
                // Show the drop zone, hide the content wrapper
                if (dropZone) dropZone.style.display = 'flex';
                if (directoryContentWrapper) directoryContentWrapper.style.display = 'none';
                
                // In Directory mode, don't show the server root option, since Directory mode isn't bound by the root
                // checkAndShowServerRootOption();
            }
            break;
        case 'project':
            if (directorySelectArea) {
                directorySelectArea.style.display = 'flex';
                if (directoryHint) directoryHint.textContent = 'Drag a folder here';
                if (directoryHintSecondary) directoryHintSecondary.textContent = 'or use the button below to choose the project root';
                if (selectDirectoryBtn) {
                    selectDirectoryBtn.textContent = 'Choose Project Root';
                    selectDirectoryBtn.style.display = 'inline-block';
                }
                // Show the drop zone, hide the content wrapper
                if (dropZone) dropZone.style.display = 'flex';
                if (directoryContentWrapper) directoryContentWrapper.style.display = 'none';
                
                // Check and show the server root option
                checkAndShowServerRootOption();
            }
            break;
        case 'git':
            // For Git repositories, show the config prompt and hide the drag/choose-directory features
            if (directorySelectArea) {
                directorySelectArea.style.display = 'flex';
                if (directoryHint) directoryHint.textContent = 'Configure a Git repository to start the review';
                if (directoryHintSecondary) directoryHintSecondary.textContent = 'Click the button below to configure the Git repository';
                // Show the Git config button
                if (configureGitBtn) {
                    configureGitBtn.style.display = 'inline-block';
                }
                // Hide the drop zone and choose-directory button; show only the config prompt
                if (dropZone) dropZone.style.display = 'none';
                if (directoryContentWrapper) directoryContentWrapper.style.display = 'none';
                // Hide the choose-directory button
                if (selectDirectoryBtn) selectDirectoryBtn.style.display = 'none';
            }
            break;
    }
    
    // Clear the review results
    initResultsArea();

    // If switching to a non-directory/project/Git type, clear the directory code editor
    if (reviewType !== 'directory' && reviewType !== 'project' && reviewType !== 'git') {
        clearDirectoryEditor();
    }
}

// Choose a file
function selectFile() {
    const fileInput = document.getElementById('fileInput');
    if (fileInput) {
        fileInput.click();
    }
}

// Handle file selection
function handleFileSelect(event) {
    const file = event.target.files[0];
    if (file) {
        selectedFile = file;
        const fileStatus = document.getElementById('fileStatus');
        if (fileStatus) {
            fileStatus.textContent = file.name;
        }
        
        // Read the file content and show it in the code editor
        const reader = new FileReader();
        reader.onload = function(e) {
            const fileContent = e.target.result;
            selectedFileContent = fileContent;
            loadFileContentToEditor(fileContent);
        };
        reader.onerror = function() {
            alert('Failed to read the file. Please try again.');
        };
        reader.readAsText(file, 'UTF-8');
    }
}

// Load file content into the code editor
function loadFileContentToEditor(content) {
    const editor = document.getElementById('codeEditor');
    const codeEditorArea = document.getElementById('codeEditorArea');
    const fileSelectArea = document.getElementById('fileSelectArea');
    const codeTypeSelect = document.getElementById('codeTypeSelect');
    
    if (!editor) return;
    
    // Switch to the code editor view
    if (codeEditorArea) {
        codeEditorArea.style.display = 'flex';
    }
    if (fileSelectArea) {
        fileSelectArea.style.display = 'none';
    }
    
    // Don't change the review type; keep the user's choice (e.g. Single File)
    
    // Set the editor content
    // Work around browsers ignoring the first newline inside a pre element
    if (content && (content.startsWith('\n') || content.startsWith('\r'))) {
        editor.textContent = '\n' + content;
    } else {
        editor.textContent = content;
    }
    
    // Update line numbers
    const lineNumbers = document.getElementById('lineNumbers');
    if (lineNumbers) {
        // Use split('\n', -1) to keep all empty strings, including the trailing blank line
        const lines = content.split('\n', -1);
        const lineCount = lines.length;
        const actualLineCount = lineCount === 0 ? 1 : lineCount;
        
        let lineNumbersHtml = '';
        for (let i = 1; i <= actualLineCount; i++) {
            lineNumbersHtml += i;
            if (i < actualLineCount) {
                lineNumbersHtml += '\n';
            }
        }
        lineNumbers.textContent = lineNumbersHtml;
    }
    
    // Re-initialize syntax highlighting
    setTimeout(() => {
        highlightCode();
    }, 100);
}

// Clear the file
function clearFile() {
    selectedFile = null;
    selectedFileContent = '';
    const fileInput = document.getElementById('fileInput');
    const fileStatus = document.getElementById('fileStatus');
    if (fileInput) fileInput.value = '';
    if (fileStatus) fileStatus.textContent = 'No file selected';
}

// Choose a directory
function selectDirectory() {
    const directoryInput = document.getElementById('directoryInput');
    if (directoryInput) {
        directoryInput.click();
    }
}

// Stored directory file list
let directoryFiles = [];

// Recursively read directory entries (returns a Promise)
function readDirectoryEntries(directoryEntry, fileList, currentPath, rootDirName) {
    return new Promise((resolve, reject) => {
        const directoryReader = directoryEntry.createReader();
        
        const filePromises = [];
        
        function readEntries() {
            directoryReader.readEntries(function(entries) {
                if (entries.length === 0) {
                    // All entries read; wait for all files to finish reading
                    Promise.all(filePromises).then(() => {
                        console.log('All files read; total', fileList.length, 'files');
                        resolve();
                    }).catch(reject);
                    return;
                }
                
                entries.forEach(function(entry) {
                    if (entry.isFile) {
                        // Wrap the file read in a Promise
                        const filePromise = new Promise((fileResolve, fileReject) => {
                            entry.file(function(file) {
                                // Build the relative path from the root directory
                                // currentPath is already a full path (e.g. "docs" or "docs/subdir")
                                let relativePath = '';
                                if (currentPath === rootDirName) {
                                    // File directly under the root directory
                                    relativePath = rootDirName + '/' + entry.name;
                                } else {
                                    // File inside a subdirectory
                                    relativePath = currentPath + '/' + entry.name;
                                }
                                // Set it directly on the file object
                                Object.defineProperty(file, 'webkitRelativePath', {
                                    value: relativePath,
                                    writable: true,
                                    enumerable: true,
                                    configurable: true
                                });
                                file.fullPath = entry.fullPath;
                                fileList.push(file);
                                console.log('Read file:', entry.name, 'path:', relativePath, 'file.webkitRelativePath:', file.webkitRelativePath);
                                fileResolve();
                            }, fileReject);
                        });
                        filePromises.push(filePromise);
                    } else if (entry.isDirectory) {
                        // Recursively read the subdirectory
                        let subPath;
                        if (currentPath === rootDirName) {
                            subPath = rootDirName + '/' + entry.name;
                        } else {
                            subPath = currentPath + '/' + entry.name;
                        }
                        console.log('Read subdirectory:', entry.name, 'path:', subPath);
                        const dirPromise = readDirectoryEntries(entry, fileList, subPath, rootDirName);
                        filePromises.push(dirPromise);
                    }
                });
                
                // Keep reading more entries
                readEntries();
            }, function(error) {
                console.error('Failed to read directory:', error);
                reject(error);
            });
        }
        
        readEntries();
    });
}

// Read a directory and process its file list (entry point)
function readDirectoryAndHandle(directoryEntry) {
    const fileList = [];
    const rootDirName = directoryEntry.name;
    
    console.log('Start reading directory:', rootDirName);
    
    readDirectoryEntries(directoryEntry, fileList, rootDirName, rootDirName)
        .then(() => {
            // All files read; process the file list
            if (fileList.length > 0) {
                // Make sure every file has a correct webkitRelativePath
                fileList.forEach((file) => {
                    if (!file.webkitRelativePath) {
                        // If the path is missing, rebuild it
                        const relativePath = rootDirName + '/' + file.name;
                        Object.defineProperty(file, 'webkitRelativePath', {
                            value: relativePath,
                            writable: true,
                            enumerable: true,
                            configurable: true
                        });
                    }
                    console.log('File path:', file.name, '->', file.webkitRelativePath);
                });
                // Pass the root directory name to handleDirectoryFiles
                handleDirectoryFiles(fileList, rootDirName);
            } else {
                console.warn('No files found in the directory');
                alert('No files found in the directory. Please make sure the directory contains files.');
            }
        })
        .catch((error) => {
            console.error('Failed to read directory:', error);
            alert('Failed to read the directory: ' + error.message);
        });
}

// Handle the directory file list
function handleDirectoryFiles(files, rootDirName = null) {
    console.log('handleDirectoryFiles called with', files.length, 'files');
    
    if (files && files.length > 0) {
        // Save the file list
        directoryFiles = Array.from(files).filter(file => {
            const path = file.webkitRelativePath || file.name;
            return isPathIncluded(path);
        });
        
        if (directoryFiles.length === 0) {
             alert('No matching files found within the configured scope.');
             return;
        }
        
        // If no root directory name was provided, extract it from the first file's path
        if (!rootDirName) {
            const firstFile = directoryFiles[0];
            const path = firstFile.webkitRelativePath || firstFile.name;
            console.log('First file path:', path);
            if (path && path.includes('/')) {
                rootDirName = path.split('/')[0];
            } else {
                // If the path has no slash, the file is at the root; get the name from the dropped directory
                console.warn('Could not extract the root directory name from the file path; using the default');
            }
        }
        
        selectedDirectory = rootDirName;
        currentFileSource = 'local';
        
        // Clear the code editor
        clearDirectoryEditor();
        
        const directoryStatus = document.getElementById('directoryStatus');
        if (directoryStatus && rootDirName) {
            directoryStatus.textContent = rootDirName;
        }
        
        // Build and show the file tree, passing the root directory name
        console.log('Building file tree with root:', rootDirName);
        buildFileTree(directoryFiles, rootDirName);
        
        // Hide the drop zone and show the content wrapper (file tree + code editor)
        const dropZone = document.getElementById('directoryDropZone');
        const directoryContentWrapper = document.getElementById('directoryContentWrapper');
        
        console.log('dropZone:', dropZone);
        console.log('directoryContentWrapper:', directoryContentWrapper);
        
        if (dropZone) {
            dropZone.style.display = 'none';
            console.log('Hidden drop zone');
        }
        if (directoryContentWrapper) {
            directoryContentWrapper.style.display = 'flex';
            console.log('Shown directory content wrapper');
        } else {
            console.error('directoryContentWrapper not found!');
        }
    } else {
        console.warn('No files provided to handleDirectoryFiles');
    }
}

// Handle directory selection
function handleDirectorySelect(event) {
    const files = event.target.files;
    handleDirectoryFiles(files);
}

// Build the file tree (from a list of File objects)
function buildFileTree(files, rootDirName = null) {
    const fileTreeContent = document.getElementById('fileTreeContent');
    if (!fileTreeContent) return;
    
    // Build the file-tree structure
    const tree = {};
    
    // If no root directory name was provided, extract it from the first file's path
    if (!rootDirName && files.length > 0) {
        const firstFile = files[0];
        const path = firstFile.webkitRelativePath || firstFile.name;
        const parts = path.split('/').filter(p => p.length > 0); // filter empty strings
        if (parts.length > 0) {
            rootDirName = parts[0];
        }
    }
    
    console.log('Building file tree, root:', rootDirName);
    console.log('File list:', Array.from(files).map(f => ({ name: f.name, path: f.webkitRelativePath })));
    
    Array.from(files).forEach(file => {
        const path = file.webkitRelativePath || file.name;
        // Filter empty strings to avoid path-parsing errors
        const parts = path.split('/').filter(p => p.length > 0);
        
        if (parts.length === 0) {
            console.warn('File path is empty:', file.name);
            return;
        }
        
        let current = tree;
        
        // If a root directory name is provided, skip the first level (it's rendered separately)
        const startIndex = rootDirName && parts[0] === rootDirName ? 1 : 0;
        
        // Make sure there are enough path segments
        if (startIndex >= parts.length) {
            console.warn('Not enough path levels:', path, 'startIndex:', startIndex, 'parts.length:', parts.length);
            return;
        }
        
        for (let i = startIndex; i < parts.length; i++) {
            const part = parts[i];
            
            if (!part || part.length === 0) {
                console.warn('Path segment is empty:', parts, 'index:', i);
                continue;
            }
            
            if (i === parts.length - 1) {
                // Last segment should be a file
                // Check if a directory with the same name already exists; if so, the path parsing is wrong
                if (current[part] && !(current[part] instanceof File)) {
                    console.error('Path conflict:', part, 'already exists as a directory but is now a file. Path:', path);
                    // Don't overwrite the directory; log the error
                } else {
                    current[part] = file;
                    console.log('Add file:', part, 'path:', path);
                }
            } else {
                // Middle segments should be directories
                // Check if a file with the same name already exists; if so, the path parsing is wrong
                if (current[part] && current[part] instanceof File) {
                    console.error('Path conflict:', part, 'already exists as a file but is now a directory. Path:', path);
                    // Don't overwrite the file; log the error
                } else {
                    if (!current[part]) {
                        current[part] = {};
                        console.log('Create directory:', part);
                    }
                    current = current[part];
                }
            }
        }
    });
    
    console.log('Built file tree structure:', JSON.stringify(Object.keys(tree), null, 2));
    
    // Render the file tree; the first level shows the root directory name
    fileTreeContent.innerHTML = '';
    const treeElement = renderFileTreeWithRoot(tree, '', rootDirName);
    fileTreeContent.appendChild(treeElement);
}

// Render the file tree (with a root directory name)
function renderFileTreeWithRoot(node, path, rootDirName = null, depth = 0, isLast = [], parentPath = '') {
    const ul = document.createElement('ul');
    ul.className = 'file-tree';
    if (depth > 0) {
        ul.classList.add('file-tree-nested');
    }
    
    // At the first level (depth === 0) with a root directory name, add it as the root node
    if (depth === 0 && rootDirName) {
        const rootLi = document.createElement('li');
        rootLi.className = 'file-tree-item folder'; // no 'expanded' class by default, so children are hidden
        rootLi.setAttribute('data-depth', 0);
        
        const hasChildren = Object.keys(node).length > 0;
        const expandIcon = hasChildren ? '<i class="fas fa-chevron-right file-tree-expand-icon"></i>' : '<span style="width: 16px; display: inline-block;"></span>';
        
        const rootContent = document.createElement('div');
        rootContent.style.display = 'flex';
        rootContent.style.alignItems = 'center';
        rootContent.style.cursor = hasChildren ? 'pointer' : 'default';
        rootContent.innerHTML = `
            ${expandIcon}
            <i class="fas fa-folder file-tree-icon folder" style="color: var(--primary-color);"></i>
            <span class="file-tree-name" style="font-weight: 600; color: var(--primary-color);">${escapeHtml(rootDirName)}</span>
        `;
        rootLi.appendChild(rootContent);
        
        if (hasChildren) {
            rootContent.onclick = function(e) {
                e.stopPropagation();
                rootLi.classList.toggle('expanded');
                const expandIconEl = rootLi.querySelector('.file-tree-expand-icon');
                if (expandIconEl) {
                    expandIconEl.classList.toggle('expanded');
                }
            };
        }
        
        // Recursively render children (directory contents) - hidden by default, shown after expanding the root node
        const children = renderFileTreeWithRoot(node, path, null, depth + 1, [true], parentPath);
        children.className = 'file-tree-children';
        // Ensure children are hidden by default (via CSS, since the parent has no 'expanded' class)
        rootLi.appendChild(children);
        
        ul.appendChild(rootLi);
        return ul;
    }
    
    // Sort by name: directories first, then files
    const entries = Object.entries(node).sort((a, b) => {
        const aIsFile = a[1] instanceof File;
        const bIsFile = b[1] instanceof File;
        if (aIsFile && !bIsFile) return 1;
        if (!aIsFile && bIsFile) return -1;
        return a[0].localeCompare(b[0]);
    });
    
    entries.forEach(([name, value], index) => {
        const isLastItem = index === entries.length - 1;
        const li = document.createElement('li');
        li.className = 'file-tree-item';
        li.setAttribute('data-depth', depth);
        
        // Build connector-line HTML - Mac Finder style
        let connectorHtml = '';
        if (depth > 0) {
            // Add a connector line for each level
            for (let i = 0; i < depth; i++) {
                const isLastAtLevel = isLast[i] || false;
                if (isLastAtLevel) {
                    connectorHtml += '<span class="file-tree-connector file-tree-connector-empty"></span>';
                } else {
                    connectorHtml += '<span class="file-tree-connector file-tree-connector-line"></span>';
                }
            }
        }
        
        // Connector for the current item
        const currentConnector = isLastItem ? 'file-tree-connector-last' : 'file-tree-connector-branch';
        
        if (value instanceof File) {
            // File
            li.className += ' file';
            // Add a data attribute with the file path, used for locating
            li.setAttribute('data-file-path', value.webkitRelativePath || value.name);
            const fileContent = document.createElement('div');
            fileContent.style.display = 'flex';
            fileContent.style.alignItems = 'center';
            fileContent.innerHTML = `
                <span class="file-tree-connector-wrapper">
                    ${connectorHtml}
                    <span class="file-tree-connector ${currentConnector}"></span>
                </span>
                <i class="fas fa-file-code file-tree-icon file"></i>
                <span class="file-tree-name">${escapeHtml(name)}</span>
            `;
            li.appendChild(fileContent);
            li.onclick = function(e) {
                e.stopPropagation();
                selectFileNode(li);
                
                // Check whether there's a target line
                const startLine = li.dataset.startLine;
                const endLine = li.dataset.endLine;
                
                if (startLine) delete li.dataset.startLine;
                if (endLine) delete li.dataset.endLine;
                
                loadFileToEditor(value, startLine, endLine);
            };
        } else {
            // Directory
            li.className += ' folder';
            const hasChildren = Object.keys(value).length > 0;
            const expandIcon = hasChildren ? '<i class="fas fa-chevron-right file-tree-expand-icon"></i>' : '<span style="width: 16px; display: inline-block;"></span>';
            
            const folderContent = document.createElement('div');
            folderContent.style.display = 'flex';
            folderContent.style.alignItems = 'center';
            folderContent.innerHTML = `
                <span class="file-tree-connector-wrapper">
                    ${connectorHtml}
                    <span class="file-tree-connector ${currentConnector}"></span>
                </span>
                ${expandIcon}
                <i class="fas fa-folder file-tree-icon folder"></i>
                <span class="file-tree-name">${escapeHtml(name)}</span>
            `;
            li.appendChild(folderContent);
            
            if (hasChildren) {
                folderContent.onclick = function(e) {
                    e.stopPropagation();
                    li.classList.toggle('expanded');
                    const expandIconEl = li.querySelector('.file-tree-expand-icon');
                    if (expandIconEl) {
                        expandIconEl.classList.toggle('expanded');
                    }
                };
            }
            
            // Recursively render children
            const newIsLast = [...isLast, isLastItem];
            const children = renderFileTreeWithRoot(value, path ? `${path}/${name}` : name, null, depth + 1, newIsLast, parentPath ? `${parentPath}/${name}` : name);
            children.className = 'file-tree-children';
            li.appendChild(children);
        }
        
        ul.appendChild(li);
    });
    
    return ul;
}

// Extract the repository name from a Git URL
function extractRepoName(gitUrl) {
    if (!gitUrl) return 'Repository';
    try {
        // Remove the .git suffix
        let repoName = gitUrl.replace(/\.git$/, '');
        // Extract the last path segment
        const parts = repoName.split('/');
        repoName = parts[parts.length - 1];
        // Remove query string and anchor
        repoName = repoName.split('?')[0].split('#')[0];
        return repoName || 'Repository';
    } catch (e) {
        return 'Repository';
    }
}

// Build the file tree (from a list of file paths, for Git repositories)
function buildGitFileTreeFromPaths(filePaths, basePath, repoName = null) {
    const fileTreeContent = document.getElementById('fileTreeContent');
    if (!fileTreeContent) return;
    
    console.log('Building Git file tree with base path:', basePath);
    
    // If no repository name is provided, extract it from gitConfig
    if (!repoName && gitConfig.url) {
        repoName = extractRepoName(gitConfig.url);
    }
    
    // Build the file-tree structure
    const tree = {};
    
    filePaths.forEach(filePath => {
        const parts = filePath.split('/');
        let current = tree;
        
        parts.forEach((part, index) => {
            if (index === parts.length - 1) {
                // File
                // Build the full path (using the system path separator)
                const pathSeparator = basePath.includes('\\') ? '\\' : '/';
                const normalizedBasePath = basePath.replace(/[/\\]+$/, '');
                const normalizedFilePath = filePath.replace(/\//g, pathSeparator);
                current[part] = {
                    path: filePath,
                    fullPath: normalizedBasePath + pathSeparator + normalizedFilePath
                };
            } else {
                // Directory
                if (!current[part]) {
                    current[part] = {};
                }
                current = current[part];
            }
        });
    });
    
    // Render the file tree; the first level shows the repository name
    fileTreeContent.innerHTML = '';
    const treeElement = renderFileTreeFromPaths(tree, basePath, '', 0, [], '', repoName);
    fileTreeContent.appendChild(treeElement);
    
    console.log('Git file tree built successfully');
}

// Render file tree nodes (from a path structure, for Git repositories)
function renderFileTreeFromPaths(node, basePath, path, depth = 0, isLast = [], parentPath = '', repoName = null) {
    const ul = document.createElement('ul');
    ul.className = 'file-tree';
    if (depth > 0) {
        ul.classList.add('file-tree-nested');
    }
    
    // At the first level (depth === 0) with a repository name, add it as the root node
    if (depth === 0 && repoName) {
        const repoLi = document.createElement('li');
        repoLi.className = 'file-tree-item folder';
        repoLi.setAttribute('data-depth', 0);
        
        const hasChildren = Object.keys(node).length > 0;
        const expandIcon = hasChildren ? '<i class="fas fa-chevron-right file-tree-expand-icon"></i>' : '<span style="width: 16px; display: inline-block;"></span>';
        
        const repoContent = document.createElement('div');
        repoContent.style.display = 'flex';
        repoContent.style.alignItems = 'center';
        repoContent.innerHTML = `
            ${expandIcon}
            <i class="fas fa-code-branch file-tree-icon folder" style="color: var(--primary-color);"></i>
            <span class="file-tree-name" style="font-weight: 600; color: var(--primary-color);">${escapeHtml(repoName)}</span>
        `;
        repoLi.appendChild(repoContent);
        
        if (hasChildren) {
            repoContent.onclick = function(e) {
                e.stopPropagation();
                repoLi.classList.toggle('expanded');
                const expandIconEl = repoLi.querySelector('.file-tree-expand-icon');
                if (expandIconEl) {
                    expandIconEl.classList.toggle('expanded');
                }
            };
        }
        
        // Recursively render children (repository contents)
        const children = renderFileTreeFromPaths(node, basePath, path, depth + 1, [true], parentPath, null);
        children.className = 'file-tree-children';
        repoLi.appendChild(children);
        
        ul.appendChild(repoLi);
        return ul;
    }
    
    // Sort by name: directories first, then files
    const entries = Object.entries(node).sort((a, b) => {
        const aIsFile = a[1].path !== undefined;
        const bIsFile = b[1].path !== undefined;
        if (aIsFile && !bIsFile) return 1;
        if (!aIsFile && bIsFile) return -1;
        return a[0].localeCompare(b[0]);
    });
    
    entries.forEach(([name, value], index) => {
        const isLastItem = index === entries.length - 1;
        const li = document.createElement('li');
        li.className = 'file-tree-item';
        li.setAttribute('data-depth', depth);
        
        // Build connector-line HTML - Mac Finder style
        let connectorHtml = '';
        if (depth > 0) {
            // Add a connector line for each level
            for (let i = 0; i < depth; i++) {
                const isLastAtLevel = isLast[i] || false;
                if (isLastAtLevel) {
                    connectorHtml += '<span class="file-tree-connector file-tree-connector-empty"></span>';
                } else {
                    connectorHtml += '<span class="file-tree-connector file-tree-connector-line"></span>';
                }
            }
        }
        
        // Connector for the current item
        const currentConnector = isLastItem ? 'file-tree-connector-last' : 'file-tree-connector-branch';
        
        if (value.path !== undefined) {
            // File
            li.className += ' file';
            // Add a data attribute with the file path, used for locating
            li.setAttribute('data-file-path', value.path);
            const fileContent = document.createElement('div');
            fileContent.style.display = 'flex';
            fileContent.style.alignItems = 'center';
            fileContent.innerHTML = `
                <span class="file-tree-connector-wrapper">
                    ${connectorHtml}
                    <span class="file-tree-connector ${currentConnector}"></span>
                </span>
                <i class="fas fa-file-code file-tree-icon file"></i>
                <span class="file-tree-name">${escapeHtml(name)}</span>
            `;
            li.appendChild(fileContent);
            li.onclick = function(e) {
                e.stopPropagation();
                selectFileNode(li);
                
                // Check whether there's a target line
                const startLine = li.dataset.startLine;
                const endLine = li.dataset.endLine;
                
                if (startLine) delete li.dataset.startLine;
                if (endLine) delete li.dataset.endLine;
                
                loadFileFromPath(value.fullPath, name, startLine, endLine);
            };
        } else {
            // Directory
            li.className += ' folder';
            const hasChildren = Object.keys(value).length > 0;
            const expandIcon = hasChildren ? '<i class="fas fa-chevron-right file-tree-expand-icon"></i>' : '<span style="width: 16px; display: inline-block;"></span>';
            
            const folderContent = document.createElement('div');
            folderContent.style.display = 'flex';
            folderContent.style.alignItems = 'center';
            folderContent.innerHTML = `
                <span class="file-tree-connector-wrapper">
                    ${connectorHtml}
                    <span class="file-tree-connector ${currentConnector}"></span>
                </span>
                ${expandIcon}
                <i class="fas fa-folder file-tree-icon folder"></i>
                <span class="file-tree-name">${escapeHtml(name)}</span>
            `;
            li.appendChild(folderContent);
            
            if (hasChildren) {
                folderContent.onclick = function(e) {
                    e.stopPropagation();
                    li.classList.toggle('expanded');
                    const expandIconEl = li.querySelector('.file-tree-expand-icon');
                    if (expandIconEl) {
                        expandIconEl.classList.toggle('expanded');
                    }
                };
            }
            
            // Recursively render children
            const newIsLast = [...isLast, isLastItem];
            const children = renderFileTreeFromPaths(value, basePath, path ? `${path}/${name}` : name, depth + 1, newIsLast, parentPath ? `${parentPath}/${name}` : name, null);
            children.className = 'file-tree-children';
            li.appendChild(children);
        }
        
        ul.appendChild(li);
    });
    
    return ul;
}

// Load a file into the editor from a path (for Git and server projects)
async function loadFileFromPath(filePath, fileName, startLine = null, endLine = null) {
    try {
        let url = '';
        if (currentFileSource === 'server') {
            url = `/api/review/server/file?path=${encodeURIComponent(filePath)}`;
        } else {
            url = `/api/review/git/file?path=${encodeURIComponent(filePath)}`;
        }

        // Call the backend API to read the file content
        const response = await fetch(url);
        
        if (!response.ok) {
            throw new Error('Failed to read the file');
        }
        
        const result = await response.json();
        const content = result.content || '';
        
        loadFileContentToDirectoryEditor(content, fileName, startLine, endLine);
    } catch (error) {
        console.error('Failed to read file:', error);
        alert('Failed to read the file: ' + error.message);
    }
}

// Style for the selected file node
function selectFileNode(node) {
    // Clear all selection states
    document.querySelectorAll('.file-tree-item.selected').forEach(item => {
        item.classList.remove('selected');
    });
    
    // Add the selection state
    if (node) {
        node.classList.add('selected');
    }
}

// Render file tree nodes
function renderFileTree(node, path, depth = 0, isLast = [], parentPath = '') {
    const ul = document.createElement('ul');
    ul.className = 'file-tree';
    if (depth > 0) {
        ul.classList.add('file-tree-nested');
    }
    
    // Sort by name: directories first, then files
    const entries = Object.entries(node).sort((a, b) => {
        const aIsFile = a[1] instanceof File;
        const bIsFile = b[1] instanceof File;
        if (aIsFile && !bIsFile) return 1;
        if (!aIsFile && bIsFile) return -1;
        return a[0].localeCompare(b[0]);
    });
    
    entries.forEach(([name, value], index) => {
        const isLastItem = index === entries.length - 1;
        const li = document.createElement('li');
        li.className = 'file-tree-item';
        li.setAttribute('data-depth', depth);
        
        // Build connector-line HTML - Mac Finder style
        let connectorHtml = '';
        if (depth > 0) {
            // Add a connector line for each level
            for (let i = 0; i < depth; i++) {
                const isLastAtLevel = isLast[i] || false;
                if (isLastAtLevel) {
                    connectorHtml += '<span class="file-tree-connector file-tree-connector-empty"></span>';
                } else {
                    connectorHtml += '<span class="file-tree-connector file-tree-connector-line"></span>';
                }
            }
        }
        
        // Connector for the current item
        const currentConnector = isLastItem ? 'file-tree-connector-last' : 'file-tree-connector-branch';
        
        if (value instanceof File) {
            // File
            li.className += ' file';
            const fileContent = document.createElement('div');
            fileContent.style.display = 'flex';
            fileContent.style.alignItems = 'center';
            fileContent.innerHTML = `
                <span class="file-tree-connector-wrapper">
                    ${connectorHtml}
                    <span class="file-tree-connector ${currentConnector}"></span>
                </span>
                <i class="fas fa-file-code file-tree-icon file"></i>
                <span class="file-tree-name">${escapeHtml(name)}</span>
            `;
            li.appendChild(fileContent);
            li.onclick = function(e) {
                e.stopPropagation();
                selectFileNode(li);
                
                // Check whether there's a target line
                const startLine = li.dataset.startLine;
                const endLine = li.dataset.endLine;
                
                if (startLine) delete li.dataset.startLine;
                if (endLine) delete li.dataset.endLine;
                
                loadFileToEditor(value, startLine, endLine);
            };
        } else {
            // Directory
            li.className += ' folder';
            const hasChildren = Object.keys(value).length > 0;
            const expandIcon = hasChildren ? '<i class="fas fa-chevron-right file-tree-expand-icon"></i>' : '<span style="width: 16px; display: inline-block;"></span>';
            
            const folderContent = document.createElement('div');
            folderContent.style.display = 'flex';
            folderContent.style.alignItems = 'center';
            folderContent.innerHTML = `
                <span class="file-tree-connector-wrapper">
                    ${connectorHtml}
                    <span class="file-tree-connector ${currentConnector}"></span>
                </span>
                ${expandIcon}
                <i class="fas fa-folder file-tree-icon folder"></i>
                <span class="file-tree-name">${escapeHtml(name)}</span>
            `;
            li.appendChild(folderContent);
            
            if (hasChildren) {
                folderContent.onclick = function(e) {
                    e.stopPropagation();
                    li.classList.toggle('expanded');
                    const expandIconEl = li.querySelector('.file-tree-expand-icon');
                    if (expandIconEl) {
                        expandIconEl.classList.toggle('expanded');
                    }
                };
            }
            
            // Recursively render children
            const newIsLast = [...isLast, isLastItem];
            const children = renderFileTree(value, path ? `${path}/${name}` : name, depth + 1, newIsLast, parentPath ? `${parentPath}/${name}` : name);
            children.className = 'file-tree-children';
            li.appendChild(children);
        }
        
        ul.appendChild(li);
    });
    
    return ul;
}

// Load a file into the editor
function loadFileToEditor(file, startLine = null, endLine = null) {
    const reader = new FileReader();
    reader.onload = function(e) {
        const fileContent = e.target.result;
        loadFileContentToDirectoryEditor(fileContent, file.name, startLine, endLine);
    };
    reader.onerror = function() {
        alert('Failed to read the file. Please try again.');
    };
    reader.readAsText(file, 'UTF-8');
}

// Load file content into the directory code editor (right side)
function loadFileContentToDirectoryEditor(content, fileName, startLine = null, endLine = null) {
    const editor = document.getElementById('directoryCodeEditor');
    const editorTitle = document.getElementById('directoryEditorTitle');
    const lineNumbers = document.getElementById('directoryLineNumbers');
    const gitInfoDisplay = document.getElementById('gitInfoDisplay');
    
    if (!editor) return;

    // Update the Git info display
    if (gitInfoDisplay) {
        if (currentFileSource === 'git' && gitConfig.url) {
            gitInfoDisplay.textContent = `Git URL: ${gitConfig.url}`;
            gitInfoDisplay.style.display = 'inline-block';
            gitInfoDisplay.title = gitConfig.url;
        } else {
            gitInfoDisplay.style.display = 'none';
        }
    }
    
    // Remove the old highlight - search only within the current editor's container
    const container = editor.closest('.code-editor-container');
    if (container) {
        const existingHighlight = container.querySelector('.code-line-highlight');
        if (existingHighlight) {
            existingHighlight.remove();
        }
    }

    // Update the title
    if (editorTitle && fileName) {
        editorTitle.textContent = fileName;
    }
    
    // Set the editor content
    // Work around browsers ignoring the first newline inside a pre element
    if (content && (content.startsWith('\n') || content.startsWith('\r'))) {
        editor.textContent = '\n' + content;
    } else {
        editor.textContent = content;
    }
    
    // Update line numbers
    if (lineNumbers) {
        // Use split('\n', -1) to keep all empty strings, including the trailing blank line
        const lines = content.split('\n', -1);
        const lineCount = lines.length;
        const actualLineCount = lineCount === 0 ? 1 : lineCount;
        
        let lineNumbersHtml = '';
        for (let i = 1; i <= actualLineCount; i++) {
            lineNumbersHtml += i;
            if (i < actualLineCount) {
                lineNumbersHtml += '\n';
            }
        }
        lineNumbers.textContent = lineNumbersHtml || '1';
        
        // Sync scroll - bound to the wrapper
        const editorWrapper = editor.closest('.code-editor-wrapper');
        if (editorWrapper) {
            editorWrapper.onscroll = function() {
                lineNumbers.scrollTop = editorWrapper.scrollTop;
            };
            // Initial sync
            lineNumbers.scrollTop = editorWrapper.scrollTop;
        }
    }
    
    // Re-initialize syntax highlighting
    // Add a small delay to ensure DOM updates finish
    setTimeout(() => {
        highlightDirectoryCode(startLine, endLine);
    }, 200);
}

// Highlight a code range
function highlightCodeRange(startLine, endLine) {
    if (!startLine) return;
    
    const editor = document.getElementById('directoryCodeEditor');
    if (!editor) return;
    
    // Search only within the current editor's container
    // Use closest to find the nearest container instead of parentElement directly
    const container = editor.closest('.code-editor-container');
    if (!container) return;
    
    // Remove the existing highlight
    const existingHighlight = container.querySelector('.code-line-highlight');
    if (existingHighlight) {
        existingHighlight.remove();
    }
    
    // Make sure the line numbers are numeric
    const start = parseInt(startLine);
    let end = endLine ? parseInt(endLine) : start;
    
    if (isNaN(start) || start < 1) return;
    if (isNaN(end) || end < start) end = start;
    
    // Create the highlight element
    const highlight = document.createElement('div');
    highlight.className = 'code-line-highlight';
    
    // Add a border to make it more visible
    highlight.style.border = '1px solid rgba(255, 215, 0, 0.5)';
    
    // Compute position and height
    // padding: 20px, lineHeight: 14px * 1.6 = 22.4px
    const lineHeight = 22.4;
    const top = 20 + (start - 1) * lineHeight;
    const height = (end - start + 1) * lineHeight;
    
    highlight.style.top = top + 'px';
    highlight.style.height = height + 'px';
    
    console.log(`Adding highlight: line ${start}-${end}, top=${top}, height=${height}`);
    
    // Insert before the editor (as a background)
    // container holds line-numbers and pre.code-editor-pre
    // We need to insert before the pre, or absolutely position it in the container
    // container is relatively positioned
    const preElement = container.querySelector('.code-editor-pre');
    if (preElement) {
        container.insertBefore(highlight, preElement);
    } else {
        container.appendChild(highlight);
    }
    
    // Scroll to the highlight (center the region)
    const editorWrapper = editor.closest('.code-editor-wrapper');
    if (editorWrapper) {
        const wrapperHeight = editorWrapper.clientHeight;
        // Compute the Y coordinate of the region's center
        const centerY = top + height / 2;
        const scrollTarget = Math.max(0, centerY - wrapperHeight / 2);
        
        console.log(`Scrolling to line ${start}: top=${top}, scrollTarget=${scrollTarget}`);
        
        // Use requestAnimationFrame to scroll on the next frame
        requestAnimationFrame(() => {
            editorWrapper.scrollTo({
                top: scrollTarget,
                behavior: 'smooth'
            });
        });
    }
}

function highlightSnippetCodeRange(startLine, endLine) {
    if (!startLine) return;
    const editor = document.getElementById('codeEditor');
    if (!editor) return;
    const container = editor.closest('.code-editor-container');
    if (!container) return;
    const existingHighlight = container.querySelector('.code-line-highlight');
    if (existingHighlight) {
        existingHighlight.remove();
    }
    const start = parseInt(startLine);
    let end = endLine ? parseInt(endLine) : start;
    if (isNaN(start) || start < 1) return;
    if (isNaN(end) || end < start) end = start;
    const highlight = document.createElement('div');
    highlight.className = 'code-line-highlight';
    highlight.style.border = '1px solid rgba(255, 215, 0, 0.5)';
    const lineHeight = 22.4;
    const top = 20 + (start - 1) * lineHeight;
    const height = (end - start + 1) * lineHeight;
    highlight.style.top = top + 'px';
    highlight.style.height = height + 'px';
    const preElement = container.querySelector('.code-editor-pre');
    if (preElement) {
        container.insertBefore(highlight, preElement);
    } else {
        container.appendChild(highlight);
    }
    const editorWrapper = editor.closest('.code-editor-wrapper');
    if (editorWrapper) {
        const wrapperHeight = editorWrapper.clientHeight;
        const centerY = top + height / 2;
        const scrollTarget = Math.max(0, centerY - wrapperHeight / 2);
        requestAnimationFrame(() => {
            editorWrapper.scrollTo({ top: scrollTarget, behavior: 'smooth' });
        });
    }
}

// Clear the directory code editor
function clearDirectoryEditor() {
    const editor = document.getElementById('directoryCodeEditor');
    const editorTitle = document.getElementById('directoryEditorTitle');
    const lineNumbers = document.getElementById('directoryLineNumbers');
    const gitInfoDisplay = document.getElementById('gitInfoDisplay');
    
    // Use a more precise selector
    if (editor) {
        const container = editor.closest('.code-editor-container');
        if (container) {
            const existingHighlight = container.querySelector('.code-line-highlight');
            if (existingHighlight) {
                existingHighlight.remove();
            }
        }
    }
    
    if (editor) {
        editor.textContent = '';
        editor.innerHTML = '';
    }
    
    if (editorTitle) {
        editorTitle.textContent = 'Code Preview';
    }

    // Update the Git info display
    if (gitInfoDisplay) {
        if (currentFileSource === 'git' && gitConfig.url) {
            gitInfoDisplay.textContent = `Git URL: ${gitConfig.url}`;
            gitInfoDisplay.style.display = 'inline-block';
            gitInfoDisplay.title = gitConfig.url;
        } else {
            gitInfoDisplay.style.display = 'none';
            gitInfoDisplay.textContent = '';
        }
    }
    
    if (lineNumbers) {
        lineNumbers.textContent = '1';
    }
    
    // Re-initialize syntax highlighting
    setTimeout(() => {
        highlightDirectoryCode();
    }, 100);
}

// Syntax highlighting - directory code editor
function highlightDirectoryCode(startLine = null, endLine = null) {
    const editor = document.getElementById('directoryCodeEditor');
    if (!editor) return;
    
    // Get the code text (extracted from all text nodes)
    let code = '';
    const walker = document.createTreeWalker(
        editor,
        NodeFilter.SHOW_TEXT,
        null,
        false
    );
    
    let node;
    while (node = walker.nextNode()) {
        code += node.textContent;
    }
    
    // If there are no text nodes, get the text content directly
    if (!code) {
        code = editor.textContent || editor.innerText || '';
    }
    
    // Use Prism for syntax highlighting
    if (typeof Prism !== 'undefined' && Prism && typeof Prism.languages !== 'undefined' && typeof Prism.highlight === 'function') {
        if (Prism.languages && Prism.languages.java) {
            try {
                const highlighted = Prism.highlight(code, Prism.languages.java, 'java');
                // Save the current scroll position
                const scrollTop = editor.scrollTop;
                
                // Update the highlighted code
                editor.innerHTML = highlighted;
                
                // Restore the scroll position
                editor.scrollTop = scrollTop;
                
                // If there's a target line, highlight it
                if (startLine) {
                    highlightCodeRange(startLine, endLine);
                }
            } catch (e) {
                console.warn('Syntax highlighting failed:', e);
            }
        } else {
            // If the Java language isn't loaded yet, retry after a delay
            setTimeout(function() {
                highlightDirectoryCode(startLine, endLine);
            }, 100);
        }
    } else if (startLine) {
        // Even without Prism, still try to highlight the line
        highlightCodeRange(startLine, endLine);
    }
}

// Clear the directory
function clearDirectory() {
    selectedDirectory = null;
    directoryFiles = [];
    currentFileSource = 'local'; // Reset to default
    const directoryInput = document.getElementById('directoryInput');
    const directoryStatus = document.getElementById('directoryStatus');
    const dropZone = document.getElementById('directoryDropZone');
    const directoryContentWrapper = document.getElementById('directoryContentWrapper');
    const fileTreeContent = document.getElementById('fileTreeContent');
    
    if (directoryInput) directoryInput.value = '';
    if (directoryStatus) directoryStatus.textContent = 'Nothing selected';
    if (dropZone) dropZone.style.display = 'flex';
    if (directoryContentWrapper) directoryContentWrapper.style.display = 'none';
    if (fileTreeContent) fileTreeContent.innerHTML = '';
    
    // Re-check whether to show the server root option
    checkAndShowServerRootOption();
    
    // Clear the code editor
    clearDirectoryEditor();
}

// Git repository configuration
let gitConfig = {
    url: '',
    username: '',
    password: '',
    localPath: '' // local path after cloning
};

// Show the Git config modal
function showGitConfigModal() {
    const modal = document.getElementById('gitConfigModal');
    const urlInput = document.getElementById('gitUrlInput');
    const usernameInput = document.getElementById('gitUsernameInput');
    const passwordInput = document.getElementById('gitPasswordInput');
    
    if (modal) {
        // Fill in existing config
        if (urlInput) urlInput.value = gitConfig.url || '';
        if (usernameInput) usernameInput.value = gitConfig.username || '';
        if (passwordInput) passwordInput.value = gitConfig.password || '';
        
        modal.style.display = 'flex';
        
        // Click outside the modal to close
        modal.onclick = function(e) {
            if (e.target === modal) {
                closeGitConfigModal();
            }
        };
    }
}

// Close the Git config modal
function closeGitConfigModal() {
    console.log('Closing the Git config modal...');
    
    // Force-remove any possible overlay layers
    const modals = document.querySelectorAll('.modal-overlay');
    console.log(`Found ${modals.length} overlay element(s)`);
    
    modals.forEach((modal, index) => {
        console.log(`Hiding overlay ${index}:`, modal.id);
        modal.style.display = 'none';
        // Extra safety: set the style attribute directly
        modal.setAttribute('style', 'display: none !important');
    });
    
    const modal = document.getElementById('gitConfigModal');
    if (modal) {
        console.log('Hiding gitConfigModal');
        modal.style.display = 'none';
        modal.style.setProperty('display', 'none', 'important');
    } else {
        console.warn('gitConfigModal element not found');
    }
    
    // Check whether the body has any locking styles
    document.body.style.overflow = '';
    document.body.classList.remove('modal-open');
    console.log('Reset body overflow');
}

// Close the modal on the ESC key
document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
        console.log('ESC key pressed; trying to close the modal');
        closeGitConfigModal();
    }
});

// Global error handling to prevent the UI from freezing
window.addEventListener('error', function(e) {
    console.error('Global error caught:', e.error);
    // If the page is obstructed, force the modal closed
    const modal = document.getElementById('gitConfigModal');
    if (modal && getComputedStyle(modal).display !== 'none') {
        console.warn('Detected the modal was still open when the error occurred; forcing it closed...');
        closeGitConfigModal();
    }
});

// Save the Git config and download the code
async function saveGitConfig() {
    console.log('Saving the Git config...');
    const urlInput = document.getElementById('gitUrlInput');
    const usernameInput = document.getElementById('gitUsernameInput');
    const passwordInput = document.getElementById('gitPasswordInput');
    
    if (!urlInput || !urlInput.value.trim()) {
        alert('Please enter the Git repository URL');
        return;
    }
    
    gitConfig.url = urlInput.value.trim();
    // Username and password are optional; public repos don't need them
    gitConfig.username = usernameInput ? usernameInput.value.trim() : '';
    gitConfig.password = passwordInput ? passwordInput.value.trim() : '';
    
    console.log('Git config saved; closing the modal...');
    closeGitConfigModal();
    
    // Show the download progress bar
    const progressContainer = document.getElementById('downloadProgressContainer');
    const progressFill = document.getElementById('downloadProgressFill');
    const progressText = document.getElementById('downloadProgressText');
    const directoryStatus = document.getElementById('directoryStatus');
    
    if (progressContainer) {
        progressContainer.style.display = 'block';
    }
    if (progressFill) {
        progressFill.style.width = '0%';
    }
    if (progressText) {
        progressText.textContent = 'Connecting to the Git repository...';
    }
    if (directoryStatus) {
        directoryStatus.textContent = 'Downloading...';
    }
    
    // Simulate download progress (Git clone is synchronous, so we fake progress)
    let progress = 0;
    const progressInterval = setInterval(() => {
        progress += Math.random() * 15;
        if (progress > 90) progress = 90; // cap at 90% until the actual work finishes
        if (progressFill) {
            progressFill.style.width = progress + '%';
        }
        if (progressText) {
            if (progress < 30) {
                progressText.textContent = 'Connecting to the Git repository...';
            } else if (progress < 60) {
                progressText.textContent = 'Downloading code...';
            } else {
                progressText.textContent = 'Processing files...';
            }
        }
    }, 200);
    
    try {
        console.log('Sending the Git clone request...');
        // Call the backend API to download the Git code
        const response = await fetch('/api/review/git/clone', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                gitUrl: gitConfig.url,
                gitUsername: gitConfig.username,
                gitPassword: gitConfig.password
            })
        });
        
        console.log('Git clone request finished, status code:', response.status);
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || 'Failed to download Git code');
        }
        
        const result = await response.json();
        console.log('Git clone result:', result);
        
        if (!result.success) {
            throw new Error(result.error || 'Failed to download Git code');
        }
        
        // Clear the progress simulation
        clearInterval(progressInterval);
        
        // Finish the progress bar
        if (progressFill) {
            progressFill.style.width = '100%';
        }
        if (progressText) {
            progressText.textContent = 'Download complete!';
        }
        
        // Save the local path
        gitConfig.localPath = result.localPath;
        selectedDirectory = result.localPath;
        currentFileSource = 'git';
        
        // Build the file tree from the file list
        if (result.fileList && result.fileList.length > 0) {
            console.log('Building file tree, file count:', result.fileList.length);
            
            // DEBUG: check codeTypeSelect state
            const codeTypeSelect = document.getElementById('codeTypeSelect');
            if (codeTypeSelect) {
                console.log('[DEBUG] codeTypeSelect state before building the file tree:', codeTypeSelect.disabled);
            }

            // Extract the repository name
            const repoName = extractRepoName(gitConfig.url);
            buildGitFileTreeFromPaths(result.fileList, result.localPath, repoName);
            
            // DEBUG: check codeTypeSelect state
            if (codeTypeSelect) {
                console.log('[DEBUG] codeTypeSelect state after building the file tree:', codeTypeSelect.disabled);
                // Force-correct
                if (codeTypeSelect.disabled) {
                    console.warn('[FIX] Detected codeTypeSelect was disabled after building the file tree; fixing...');
                    codeTypeSelect.disabled = false;
                    codeTypeSelect.removeAttribute('disabled');
                }
            }
            
            // Hide the drop zone and show the content wrapper (file tree + code editor)
            const dropZone = document.getElementById('directoryDropZone');
            const directoryContentWrapper = document.getElementById('directoryContentWrapper');
            
            console.log('Switching display areas...');
            if (dropZone) dropZone.style.display = 'none';
            if (directoryContentWrapper) directoryContentWrapper.style.display = 'flex';
            
            // Clear the code editor
            clearDirectoryEditor();
        }
        
        // Update status
        if (directoryStatus) {
            directoryStatus.textContent = `Downloaded: ${gitConfig.url}`;
        }
        
        // Hide the progress bar (after a short delay so the user sees 100%)
        setTimeout(() => {
            if (progressContainer) {
                progressContainer.style.display = 'none';
            }
            // Make sure the modal is closed again, to handle edge cases
            console.log('Delayed check of the modal close state...');
            closeGitConfigModal();
            // Force-remove any overlay layers again to prevent auto-graying
            document.querySelectorAll('.modal-overlay').forEach(el => {
                el.style.display = 'none';
                el.setAttribute('style', 'display: none !important');
            });
        }, 1000);
        
    } catch (error) {
        console.error('Failed to download Git code:', error);
        
        // Make sure the modal is closed so the user can retry
        console.log('An error occurred; forcing the modal closed');
        closeGitConfigModal();
        
        // Clear the progress simulation
        clearInterval(progressInterval);
        
        // Show the error state
        if (progressFill) {
            progressFill.style.width = '100%';
            progressFill.style.background = '#f85149';
        }
        if (progressText) {
            progressText.textContent = 'Download failed: ' + error.message;
            progressText.style.color = '#f85149';
        }
        
        alert('Failed to download Git code: ' + error.message);
        if (directoryStatus) {
            directoryStatus.textContent = 'Download failed';
        }
        
        // Hide the progress bar (after a short delay)
        setTimeout(() => {
            if (progressContainer) {
                progressContainer.style.display = 'none';
            }
        }, 3000);
    }
}

// Clear the editor
function clearEditor() {
    // Check whether we're in single-file mode
    const codeTypeSelect = document.getElementById('codeTypeSelect');
    const reviewType = codeTypeSelect ? codeTypeSelect.value : 'snippet';
    
    if (reviewType === 'file') {
        // In file mode, clear the file selection and switch back to the file-select view
        clearFile();
        
        const codeEditorArea = document.getElementById('codeEditorArea');
        const fileSelectArea = document.getElementById('fileSelectArea');
        
        if (codeEditorArea) codeEditorArea.style.display = 'none';
        if (fileSelectArea) fileSelectArea.style.display = 'flex';
    }

    const editor = document.getElementById('codeEditor');
    if (editor) {
        // Clear the editor content
        editor.textContent = '';
        editor.innerHTML = '';
        
        // Update line numbers (same logic as initCodeEditor)
        const lineNumbers = document.getElementById('lineNumbers');
        if (lineNumbers) {
            const text = editor.textContent || editor.innerText || '';
            // Use split('\n', -1) to keep all empty strings, including the trailing blank line
            const lines = text.split('\n', -1);
            const lineCount = lines.length;
            const actualLineCount = lineCount === 0 ? 1 : lineCount;
            
            let lineNumbersHtml = '';
            for (let i = 1; i <= actualLineCount; i++) {
                lineNumbersHtml += i;
                if (i < actualLineCount) {
                    lineNumbersHtml += '\n';
                }
            }
            lineNumbers.textContent = lineNumbersHtml || '1';
        }
        
        // Re-initialize syntax highlighting
        highlightCode();
    }
}

// Initialize file-tree width resizing
function initFileTreeResizer() {
    const resizeHandle = document.getElementById('fileTreeResizeHandle');
    const fileTreeContainer = document.getElementById('fileTreeContainer');
    
    if (!resizeHandle || !fileTreeContainer) return;
    
    let isResizing = false;
    let startX = 0;
    let startWidth = 0;
    
    resizeHandle.addEventListener('mousedown', function(e) {
        isResizing = true;
        startX = e.clientX;
        startWidth = fileTreeContainer.offsetWidth;
        document.body.style.cursor = 'col-resize';
        document.body.style.userSelect = 'none';
        e.preventDefault();
        e.stopPropagation();
    });
    
    document.addEventListener('mousemove', function(e) {
        if (!isResizing) return;
        
        const diff = e.clientX - startX;
        const newWidth = startWidth + diff;
        const minWidth = 150;
        const maxWidth = 600;
        
        if (newWidth >= minWidth && newWidth <= maxWidth) {
            fileTreeContainer.style.width = newWidth + 'px';
        }
        e.preventDefault();
    });
    
    document.addEventListener('mouseup', function() {
        if (isResizing) {
            isResizing = false;
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
        }
    });
}

// Initialize code-panel width resizing
function initEditorResizer() {
    const resizeHandle = document.getElementById('editorResizeHandle');
    const editorPanel = document.getElementById('editorPanel');
    const resultsPanel = document.getElementById('resultsPanel');

    if (!resizeHandle || !editorPanel || !resultsPanel) return;

    let isResizing = false;
    let startX = 0;
    let startWidth = 0; // Initial width of resultsPanel

    resizeHandle.addEventListener('mousedown', function(e) {
        isResizing = true;
        startX = e.clientX;
        startWidth = resultsPanel.offsetWidth;
        document.body.style.cursor = 'col-resize';
        document.body.style.userSelect = 'none';
        e.preventDefault();
        e.stopPropagation();
    });

    document.addEventListener('mousemove', function(e) {
        if (!isResizing) return;

        const diff = startX - e.clientX; // Calculate difference from right to left
        let newWidth = startWidth + diff;
        const minWidth = 300; // Minimum width for results panel
        const maxWidth = window.innerWidth * 0.75; // Max 75% of window width

        if (newWidth >= minWidth && newWidth <= maxWidth) {
            resultsPanel.style.width = newWidth + 'px';
        }
        e.preventDefault();
    });

    document.addEventListener('mouseup', function() {
        if (isResizing) {
            isResizing = false;
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
        }
    });
}

// Initialize drag-and-drop
function initDragAndDrop() {
    const fileDropZone = document.getElementById('fileDropZone');
    const directoryDropZone = document.getElementById('directoryDropZone');
    
    // File drag-and-drop
    if (fileDropZone) {
        fileDropZone.addEventListener('dragover', function(e) {
            e.preventDefault();
            e.stopPropagation();
            fileDropZone.classList.add('drag-over');
        });
        
        fileDropZone.addEventListener('dragleave', function(e) {
            e.preventDefault();
            e.stopPropagation();
            fileDropZone.classList.remove('drag-over');
        });
        
        fileDropZone.addEventListener('drop', function(e) {
            e.preventDefault();
            e.stopPropagation();
            fileDropZone.classList.remove('drag-over');
            
            const files = e.dataTransfer.files;
            if (files && files.length > 0) {
                const file = files[0];
                selectedFile = file;
                const fileStatus = document.getElementById('fileStatus');
                if (fileStatus) {
                    fileStatus.textContent = file.name;
                }
                
                // Read the file content and show it in the code editor
                const reader = new FileReader();
                reader.onload = function(e) {
                    const fileContent = e.target.result;
                    loadFileContentToEditor(fileContent);
                };
                reader.onerror = function() {
                    alert('Failed to read the file. Please try again.');
                };
                reader.readAsText(file, 'UTF-8');
            }
        });
    }
    
    // Directory drag-and-drop
    if (directoryDropZone) {
        directoryDropZone.addEventListener('dragover', function(e) {
            e.preventDefault();
            e.stopPropagation();
            directoryDropZone.classList.add('drag-over');
        });
        
        directoryDropZone.addEventListener('dragleave', function(e) {
            e.preventDefault();
            e.stopPropagation();
            directoryDropZone.classList.remove('drag-over');
        });
        
        directoryDropZone.addEventListener('drop', function(e) {
            e.preventDefault();
            e.stopPropagation();
            directoryDropZone.classList.remove('drag-over');
            
            const files = e.dataTransfer.files;
            if (files && files.length > 0) {
                // Check for webkitRelativePath; if present, the directory came from the file picker
                const firstFile = files[0];
                if (firstFile.webkitRelativePath) {
                    // Handle directory drop - use the file list directly
                    handleDirectoryFiles(files);
                } else {
                    // Try using DataTransferItemList to read the directory contents
                    const items = e.dataTransfer.items;
                    if (items && items.length > 0) {
                        const item = items[0];
                        if (item.webkitGetAsEntry) {
                            const entry = item.webkitGetAsEntry();
                            if (entry && entry.isDirectory) {
                                // Recursively read all files in the directory
                                readDirectoryAndHandle(entry);
                            } else if (entry && entry.isFile) {
                                // Single file - handle it as a file list
                                entry.file(function(file) {
                                    handleDirectoryFiles([file]);
                                });
                            }
                        } else {
                            // Fallback: use the file list directly
                            handleDirectoryFiles(files);
                        }
                    } else {
                        // Fallback: use the file list directly
                        handleDirectoryFiles(files);
                    }
                }
            } else {
                // Try using DataTransferItemList
                const items = e.dataTransfer.items;
                if (items && items.length > 0) {
                    const item = items[0];
                    if (item.webkitGetAsEntry) {
                        const entry = item.webkitGetAsEntry();
                        if (entry && entry.isDirectory) {
                            // Recursively read all files in the directory
                            readDirectoryAndHandle(entry);
                        } else if (entry && entry.isFile) {
                            entry.file(function(file) {
                                handleDirectoryFiles([file]);
                            });
                        }
                    }
                }
            }
        });
    }
}

// Check and show the server root option
function checkAndShowServerRootOption() {
    const projectRootInput = document.getElementById('projectRootConfig');
    const serverRootOption = document.getElementById('serverRootOption');
    const serverRootPath = document.getElementById('serverRootPath');
    
    if (projectRootInput && projectRootInput.value && projectRootInput.value.trim() !== '' && serverRootOption) {
        serverRootOption.style.display = 'flex';
        if (serverRootPath) {
            serverRootPath.textContent = projectRootInput.value;
        }
    } else if (serverRootOption) {
        serverRootOption.style.display = 'none';
    }
}

// Use the server-configured root directory
async function useServerRoot() {
    const projectRootInput = document.getElementById('projectRootConfig');
    if (!projectRootInput || !projectRootInput.value) return;
    
    const rootPath = projectRootInput.value;
    selectedDirectory = rootPath;
    currentFileSource = 'server';
    directoryFiles = []; // clear the uploaded file list
    
    const directoryStatus = document.getElementById('directoryStatus');
    const dropZone = document.getElementById('directoryDropZone');
    const serverRootOption = document.getElementById('serverRootOption');
    const directoryContentWrapper = document.getElementById('directoryContentWrapper');
    
    // Show loading state
    if (directoryStatus) {
        directoryStatus.textContent = 'Fetching server file list...';
        directoryStatus.style.color = 'var(--text-color)';
    }
    
    try {
        // Get the server file list
        const response = await fetch('/api/review/server/list');
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || 'Failed to get file list');
        }
        
        const result = await response.json();
        
        if (!result.success) {
            throw new Error(result.error || 'Failed to get file list');
        }
        
        // Update the status text
        if (directoryStatus) {
            directoryStatus.textContent = 'Selected server directory: ' + rootPath;
            // Add styling to make it stand out
            directoryStatus.style.color = 'var(--primary-color)';
            directoryStatus.style.fontWeight = 'bold';
        }
        
        // Hide the selection area to indicate it's selected
        if (dropZone) dropZone.style.display = 'none';
        if (serverRootOption) serverRootOption.style.display = 'none';
        
        // Show the content area
        if (directoryContentWrapper) directoryContentWrapper.style.display = 'flex';
        
        // Build the file tree
        if (result.fileList && result.fileList.length > 0) {
            // Use the directory name as repoName
            let repoName = 'Project Root';
            const separator = rootPath.includes('\\') ? '\\' : '/';
            const parts = rootPath.split(separator).filter(p => p.length > 0);
            if (parts.length > 0) {
                repoName = parts[parts.length - 1];
            }
            
            buildGitFileTreeFromPaths(result.fileList, rootPath, repoName);
            
            // Clear the code editor
            clearDirectoryEditor();
        } else {
            alert('No files found in this directory');
        }
        
    } catch (error) {
        console.error('Failed to get the server file list:', error);
        if (directoryStatus) {
            directoryStatus.textContent = 'Failed to get file list: ' + error.message;
            directoryStatus.style.color = '#f85149';
        }
        alert('Failed to get file list: ' + error.message);
    }
}

// Poll task status
async function pollTaskStatus(taskId) {
    const maxRetries = 600; // 10-minute timeout
    const interval = 1000; // 1-second interval
    
    const startBtn = document.getElementById('startReviewBtn');
    
    for (let i = 0; i < maxRetries; i++) {
        try {
            // Add a timestamp to prevent caching
            const response = await fetch(`/api/review/task/${taskId}?t=${new Date().getTime()}`);
            if (response.ok) {
                const task = await response.json();
                console.log(`Task ${taskId} status: ${task.status}`);
                
                // Update the button to show progress
                if (startBtn) {
                    let statusText = '';
                    if (task.status === 'PENDING') statusText = ' (Queued)';
                    else if (task.status === 'RUNNING') statusText = ''; // 'Reviewing...' already implies in-progress, no extra text needed
                    else if (task.status === 'COMPLETED') statusText = ' (Completed)';
                    else if (task.status === 'FAILED') statusText = ' (Failed)';

                    startBtn.innerHTML = `<i class="fas fa-spinner fa-spin"></i> Reviewing...${statusText}`;
                }
                
                if (task.status === 'COMPLETED') {
                    // Task complete; load the results
                    await loadFindings(taskId);
                    return;
                } else if (task.status === 'FAILED') {
                    throw new Error(task.errorMessage || 'Review task failed');
                }
            }
        } catch (error) {
            console.warn('Error while polling task status:', error);
            // If it's the FAILED error we threw, rethrow immediately without retrying
            if (error.message && (error.message.includes('Review task failed') || error.message.includes('timed out'))) {
                throw error;
            }
        }
        
        // Wait, then keep polling
        await new Promise(resolve => setTimeout(resolve, interval));
    }

    throw new Error('Review task timed out; please check the results later in History');
}

// Start the review
async function startReview() {
    const codeTypeSelect = document.getElementById('codeTypeSelect');
    const reviewType = codeTypeSelect ? codeTypeSelect.value : 'snippet';
    const editor = document.getElementById('codeEditor');
    
    // For directory and project types, no need to check the code editor content
    if (reviewType === 'directory' || reviewType === 'project') {
        // Check whether a directory has been selected
        if (!selectedDirectory && (!directoryFiles || directoryFiles.length === 0)) {
            alert('Please select a directory or project first');
            return;
        }
    } else if (reviewType === 'git') {
        // Check the Git config
        if (!gitConfig.url || !gitConfig.url.trim()) {
            alert('Please configure the Git repository first');
            showGitConfigModal();
            return;
        }
    } else {
        // For other types, check the code editor content
        let inputContent = '';
        if (editor) {
            // If there's highlighting, get the plain text
            const tempDiv = document.createElement('div');
            tempDiv.innerHTML = editor.innerHTML;
            inputContent = tempDiv.textContent || tempDiv.innerText || '';
        }
        
        if (!inputContent.trim()) {
            alert('Please enter some content before starting the review');
            return;
        }
    }
    
    const startBtn = document.getElementById('startReviewBtn');
    if (startBtn) {
        startBtn.disabled = true;
        startBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Reviewing...';
    }

    // Clear the previous review results
    initResultsArea();

    // Clear the selection in the code editor
    if (editor) {
        const selection = window.getSelection();
        selection.removeAllRanges();
    }
    
    try {
        const modelSelect = document.getElementById('modelSelect');
        const modelProvider = modelSelect && modelSelect.value ? modelSelect.value : '';
        
        let apiEndpoint = '';
        let requestBody = {};
        
        let inputContent = '';
        if (editor && (reviewType === 'snippet' || reviewType === 'git')) {
            // For snippet, file, and Git types, get content from the editor
            // Use the same logic as updateLineNumbers so the leading blank line is included
            let text = '';
            let hasLeadingNewline = false;
            
            // Check whether the first child is a BR element or an empty text node
            if (editor.firstChild) {
                if (editor.firstChild.nodeType === Node.ELEMENT_NODE && 
                    editor.firstChild.tagName === 'BR') {
                    hasLeadingNewline = true;
                } else if (editor.firstChild.nodeType === Node.TEXT_NODE) {
                    const firstText = editor.firstChild.textContent;
                    if (firstText === '\n' || firstText === '\r\n' || 
                        (firstText.length > 0 && firstText.charAt(0) === '\n')) {
                        hasLeadingNewline = true;
                    }
                }
            }
            
            // Use textContent to get the text (it preserves newlines correctly)
            text = editor.textContent || editor.innerText || '';
            
            // If a leading blank line was detected but textContent lacks it, add it
            if (hasLeadingNewline && text && text.charAt(0) !== '\n') {
                text = '\n' + text;
            }
            
            // Add line numbers to the code (format: "lineNo: code")
            const lines = text.split('\n', -1);
            const lineCount = lines.length;
            const actualLineCount = lineCount === 0 ? 1 : lineCount;
            
            let codeWithLineNumbers = '';
            for (let i = 1; i <= actualLineCount; i++) {
                const lineContent = lines[i - 1] || '';
                codeWithLineNumbers += i + ': ' + lineContent;
                if (i < actualLineCount) {
                    codeWithLineNumbers += '\n';
                }
            }
            
            inputContent = codeWithLineNumbers;
        }
        
        switch(reviewType) {
            case 'snippet':
                apiEndpoint = '/api/review/snippet';
                requestBody = {
                    codeSnippet: inputContent,
                    language: 'java',
                    reviewType: 'SNIPPET',
                    modelProvider: modelProvider
                };
                break;
            case 'file':
                apiEndpoint = '/api/review/file';
                // Verify a file has been selected
                if (!selectedFile || !selectedFileContent) {
                    alert('Please select a file to review first');
                    return;
                }
                requestBody = {
                    filePath: selectedFile.name,
                    files: [{ path: selectedFile.name.replace(/\\/g, '/'), content: selectedFileContent }],
                    reviewType: 'FILE',
                    modelProvider: modelProvider
                };
                break;
            case 'directory':
                apiEndpoint = '/api/review/directory';
                requestBody = {
                    directoryPath: selectedDirectory || '',
                    reviewType: 'DIRECTORY',
                    modelProvider: modelProvider
                };
                
                // If files were uploaded, add them to the request body
                if (typeof directoryFiles !== 'undefined' && directoryFiles && directoryFiles.length > 0) {
                    // Read all file contents in parallel
                    const filePromises = directoryFiles.map(file => {
                        return new Promise((resolve, reject) => {
                            const reader = new FileReader();
                            reader.onload = (e) => {
                                resolve({
                                    path: file.webkitRelativePath || file.name,
                                    content: e.target.result
                                });
                            };
                            reader.onerror = () => resolve(null); // ignore files that fail to read
                            reader.readAsText(file);
                        });
                    });
                    
                    const loadedFiles = await Promise.all(filePromises);
                    requestBody.files = loadedFiles.filter(f => f !== null);
                    try {
                        const javaPkgs = requestBody.files
                            .filter(f => typeof f.path === 'string' && f.path.toLowerCase().endsWith('.java') && typeof f.content === 'string')
                            .map(f => {
                                const m = f.content.match(/^[\s\S]*?\bpackage\s+([A-Za-z0-9_.]+)\s*;/m);
                                return m && m[1] ? m[1] : null;
                            })
                            .filter(p => p);
                        if (javaPkgs.length > 0) {
                            const pkgSegments = javaPkgs.map(p => p.split('.'));
                            const minLen = pkgSegments.reduce((acc, seg) => Math.min(acc, seg.length), pkgSegments[0].length);
                            const common = [];
                            for (let i = 0; i < minLen; i++) {
                                const seg = pkgSegments[0][i];
                                let allSame = true;
                                for (let j = 1; j < pkgSegments.length; j++) {
                                    if (pkgSegments[j][i] !== seg) { allSame = false; break; }
                                }
                                if (!allSame) break;
                                common.push(seg);
                            }
                            if (common.length > 0) {
                                const inferred = 'src/main/java/' + common.join('/');
                                // Try to extract the project prefix from the file path (before src/main/java)
                                let projectPrefix = '';
                                for (const f of requestBody.files) {
                                    const p = (f.path || '').replace(/\\/g, '/');
                                    const idx = p.indexOf('/src/main/java/');
                                    if (idx > 0) {
                                        projectPrefix = p.substring(0, idx);
                                        // Strip any leading slash or spaces
                                        projectPrefix = projectPrefix.replace(/^\/+|\/+$/g, '');
                                        break;
                                    }
                                }
                                let combined = inferred;
                                if (projectPrefix) {
                                    combined = projectPrefix + '/' + inferred;
                                }
                                requestBody.directoryPath = combined;
                            }
                        }
                    } catch (e) {}
                    console.log('Added ' + requestBody.files.length + ' files to the request');
                }
                break;
            case 'project':
                apiEndpoint = '/api/review/project';
                requestBody = {
                    projectPath: selectedDirectory || '',
                    reviewType: 'PROJECT',
                    modelProvider: modelProvider
                };
                
                // If files were uploaded, add them to the request body
                if (typeof directoryFiles !== 'undefined' && directoryFiles && directoryFiles.length > 0) {
                    // Read all file contents in parallel
                    const filePromises = directoryFiles.map(file => {
                        return new Promise((resolve, reject) => {
                            const reader = new FileReader();
                            reader.onload = (e) => {
                                resolve({
                                    path: file.webkitRelativePath || file.name,
                                    content: e.target.result
                                });
                            };
                            reader.onerror = () => resolve(null); // ignore files that fail to read
                            reader.readAsText(file);
                        });
                    });
                    
                    const loadedFiles = await Promise.all(filePromises);
                    requestBody.files = loadedFiles.filter(f => f !== null);
                    console.log('Added ' + requestBody.files.length + ' files to the request');
                }
                break;
            case 'git':
                apiEndpoint = '/api/review/git';
                requestBody = {
                    gitUrl: gitConfig.url,
                    username: gitConfig.username,
                    password: gitConfig.password,
                    projectPath: gitConfig.localPath, // must pass the local path
                    reviewType: 'GIT',
                    modelProvider: modelProvider
                };
                break;
            default:
                throw new Error('Unsupported review type');
        }
        
        // Get the ruleset and the rules-only option
        const templateSelect = document.getElementById('templateSelect');
        const rulesOnlyCheckbox = document.getElementById('rulesOnlyCheckbox');
        const ragEnhancementCheckbox = document.getElementById('ragEnhancementCheckbox');
        
        if (templateSelect && templateSelect.value) {
            requestBody.ruleTemplate = templateSelect.value;
        }
        
        if (rulesOnlyCheckbox && rulesOnlyCheckbox.checked) {
            requestBody.rulesOnly = true;
        }
        
        // Enable RAG when "Rules-only review" is unchecked and "RAG Enhancement" is checked
        if (ragEnhancementCheckbox && (!rulesOnlyCheckbox || !rulesOnlyCheckbox.checked)) {
            requestBody.enableRag = ragEnhancementCheckbox.checked;
        }
        
        const response = await fetch(apiEndpoint, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(requestBody)
        });
        
        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || 'Review failed');
        }
        
        const result = await response.json();
        currentTaskId = result.taskId;
        
        // Poll task status
        await pollTaskStatus(result.taskId);
        
    } catch (error) {
        console.error('Review failed:', error);
        alert('Review failed: ' + (error.message || 'Unknown error'));
    } finally {
        if (startBtn) {
            startBtn.disabled = false;
            startBtn.innerHTML = '<i class="fas fa-play"></i> Start Review';
        }
    }
}

// Load the findings from the review
async function loadFindings(taskId) {
    try {
        // Add a timestamp to prevent caching
        const response = await fetch(`/api/review/task/${taskId}/findings?t=${new Date().getTime()}`);
        if (!response.ok) {
            throw new Error('Failed to load the issue list');
        }
        
        const findings = await response.json();
        
        // Use the real data returned by the API
        if (findings && findings.length > 0) {
            // Get the max-issues-to-display config
            const maxIssuesInput = document.getElementById('maxIssuesConfig');
            let displayFindings = findings;
            
            if (maxIssuesInput && maxIssuesInput.value) {
                const maxIssues = parseInt(maxIssuesInput.value);
                if (!isNaN(maxIssues) && maxIssues > 0 && findings.length > maxIssues) {
                    console.log(`Limiting displayed issues: ${maxIssues} (total: ${findings.length})`);
                    displayFindings = findings.slice(0, maxIssues);
                }
            }
            
            currentFindings = findings; // keep the full list for other uses (e.g. statistics)
            renderFindings(displayFindings);
            
            // Enable the Generate Report button
            const generateReportBtn = document.getElementById('generateReportBtn');
            if (generateReportBtn) {
                generateReportBtn.disabled = false;
            }
        } else {
            // If no issues were found, show the empty state
            const resultsContent = document.getElementById('resultsContent');
            if (resultsContent) {
                resultsContent.innerHTML = `<div class="empty-state">No issues found (task ID: ${taskId})</div>`;
            }
            const detailsContent = document.getElementById('detailsContent');
            if (detailsContent) {
                detailsContent.innerHTML = '';
                detailsContent.classList.add('empty');
            }
            
            // Disable the Generate Report button (no issues)
            const generateReportBtn = document.getElementById('generateReportBtn');
            if (generateReportBtn) {
                generateReportBtn.disabled = true;
            }
        }
        
    } catch (error) {
        console.error('Failed to load the issue list:', error);
        alert('Failed to load the issue list: ' + (error.message || 'Unknown error'));
    }
}

// Load test data
function loadTestFindings() {
    // Get the actual line-number mapping for the code panel
    // The actual code shown in the code panel:
    // 1. import java.sql.*;
    // 2. import java.util.ArrayList;
    // 3. (blank line)
    // 4. public class UserService {
    // 5.     public User findUser(String username) {
    // 6.         String sql = "SELECT * FROM users WHERE name = '" + username + "'"; //
    // 7.         // ... other code
    // 8.     }
    // 9.     (blank line)
    // 10.     public void printUser(User user) {
    // 11.         System.out.println(user.toString()); // NPE
    // 12.     }
    // 13.     (blank line)
    // 14.     public void connect() {
    // 15.         String pwd = "P@ssword"; // hard-coded
    // 16.         conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/app",
    // 17.         // ... other code
    // 18.     }
    // 19. }
    const testFindings = [
        {
            id: 1,
            severity: 'CRITICAL',
            title: 'SQL injection risk',
            location: 'UserService.java:6',
            startLine: 6,
            description: 'User input is concatenated directly into the SQL statement, which can lead to SQL injection attacks.',
            diff: `- String sql = "SELECT * FROM users WHERE name = " + username;
+ String sql = "SELECT * FROM users WHERE name = ?";
+ PreparedStatement ps = conn.prepareStatement(sql);
+ ps.setString(1, username);
+ ResultSet rs = ps.executeQuery();`
        },
        {
            id: 2,
            severity: 'HIGH',
            title: 'Potential null pointer exception',
            location: 'UserService.java:11',
            startLine: 11,
            description: 'Calling a method directly on a possibly-null object can cause a NullPointerException.',
            diff: `- System.out.println(user.toString());
+ if (user != null) {
+   System.out.println(user.toString());
+ }`
        },
        {
            id: 3,
            severity: 'MEDIUM',
            title: 'Hard-coded password',
            location: 'UserService.java:15',
            startLine: 15,
            description: 'The password is hard-coded in the source, creating a leak risk.',
            diff: `- String pwd = "P@ssword";
+ String pwd = System.getenv("DB_PASSWORD");`
        },
        {
            id: 4,
            severity: 'LOW',
            title: 'Unused import',
            location: 'UserService.java:2',
            startLine: 2,
            description: 'The imported java.util.ArrayList is not used anywhere.',
            diff: `- import java.util.ArrayList;`
        },
        {
            id: 5,
            severity: 'MEDIUM',
            title: 'Database resources not closed',
            location: 'UserService.java:6-7',
            startLine: 6,
            endLine: 7,
            description: 'Statement/ResultSet is not closed in a finally block or try-with-resources, which can leak resources.',
            diff: `- Statement stmt = conn.createStatement();
- ResultSet rs = stmt.executeQuery(sql);
+ try (PreparedStatement ps = conn.prepareStatement(sql);
+      ResultSet rs = ps.executeQuery()) {
+     // business logic
+ }`
        }
    ];
    
    currentFindings = testFindings;
    renderFindings(testFindings);
}

// Get the actual line number in the code panel (to match the line numbers in the issue list)
// Since the code sent to the AI matches what's shown in the code panel (including the first blank line),
// the line numbers returned by the AI should already be correct and need no adjustment
function getActualLineNumber(aiLineNumber) {
    // Return the AI's line number directly, since the content already matches
    return aiLineNumber;
}

// Render the issue list
function renderFindings(findings) {
    const resultsContent = document.getElementById('resultsContent');
    const detailsContent = document.getElementById('detailsContent');
    
    if (!resultsContent) return;
    
    if (!findings || findings.length === 0) {
        resultsContent.innerHTML = '<div class="empty-state">No issues yet</div>';
        if (detailsContent) {
            detailsContent.innerHTML = '';
            detailsContent.classList.add('empty');
        }
        return;
    }
    
    // Remove the empty-state class
    if (detailsContent) {
        detailsContent.classList.remove('empty');
    }
    
    resultsContent.innerHTML = findings.map(finding => {
        const severityClass = getSeverityClass(finding.severity);
        const severityIcon = getSeverityIcon(finding.severity);
        const severityLabel = getSeverityLabel(finding.severity);
        const location = finding.location || '';
        
        // Parse the location: extract class name and line number
        let className = '';
        let lineNumber = '';
        if (location) {
            const parts = location.split(':');
            if (parts.length >= 2) {
                className = parts[0];
                // Extract the line number (may be a single number or a range, e.g. "7" or "7-9")
                const linePart = parts.slice(1).join(':');
                const lineMatch = linePart.match(/(\d+)(?:-(\d+))?/);
                if (lineMatch) {
                    const startLine = parseInt(lineMatch[1]);
                    const endLine = lineMatch[2] ? parseInt(lineMatch[2]) : startLine;
                    // Use the actual line numbers (to stay consistent with the code panel)
                    const actualStartLine = getActualLineNumber(startLine);
                    const actualEndLine = getActualLineNumber(endLine);
                    if (actualStartLine === actualEndLine) {
                        lineNumber = actualStartLine.toString();
                    } else {
                        lineNumber = `${actualStartLine}-${actualEndLine}`;
                    }
                } else {
                    lineNumber = linePart;
                }
            } else {
                className = location;
            }
        } else if (finding.startLine) {
            // If there's no location but there is a startLine, use startLine
            const actualStartLine = getActualLineNumber(finding.startLine);
            const actualEndLine = finding.endLine ? getActualLineNumber(finding.endLine) : actualStartLine;
            if (actualStartLine === actualEndLine) {
                lineNumber = actualStartLine.toString();
            } else {
                lineNumber = `${actualStartLine}-${actualEndLine}`;
            }
        }
        
        // For CRITICAL, use a combined icon (shield + check)
        let iconHtml = '';
        if (finding.severity === 'CRITICAL') {
            iconHtml = '<i class="fas fa-shield-alt finding-icon finding-icon-shield"></i><i class="fas fa-check finding-icon finding-icon-check"></i>';
        } else {
            iconHtml = `<i class="fas ${severityIcon} finding-icon"></i>`;
        }
        
        return `
            <div class="finding-item severity-${severityClass}" 
                 onclick="selectFinding(${finding.id})" 
                 data-finding-id="${finding.id}">
                <div class="finding-indicator-bar"></div>
                <div class="finding-content-row">
                    <div class="finding-icon-wrapper">
                        ${iconHtml}
                    </div>
                    <div class="finding-title">${escapeHtml(finding.title || 'Unknown issue')}</div>
                    <span class="finding-severity-badge ${severityClass}">${severityLabel}</span>
                    <span class="finding-class-name">${escapeHtml(className)}</span>
                    <span class="finding-line-number">${escapeHtml(lineNumber)}</span>
                </div>
            </div>
        `;
    }).join('');
}

// Locate a file in the file tree
function locateFileInTree(filePath, startLine = null, endLine = null) {
    if (!filePath) return;

    // Try to normalize the path: use / consistently and drop the leading /
    const normalizedPath = filePath.replace(/\\/g, '/').replace(/^\/+/, '');
    const fileName = normalizedPath.split('/').pop();
    
    // Find all file nodes
    const fileNodes = document.querySelectorAll('li.file-tree-item.file');
    let targetNode = null;
    let maxMatchLen = 0;
    
    console.log('Locating file:', filePath, 'normalized path:', normalizedPath, 'line:', startLine, '-', endLine);
    
    // Find the best match
    fileNodes.forEach(node => {
        let nodePath = node.getAttribute('data-file-path');
        if (!nodePath) return;
        
        // Normalize the node path
        nodePath = nodePath.replace(/\\/g, '/').replace(/^\/+/, '');
        
        // 1. Exact match
        if (nodePath === normalizedPath) {
            targetNode = node;
            maxMatchLen = 9999; // highest priority
            return;
        }
        
        // 2. Suffix match (e.g. finding path is "main/java/...", tree is "src/main/java/...")
        // or finding path is "src/main/java/...", tree is "main/java/..."
        if ((nodePath.endsWith('/' + normalizedPath) || nodePath.endsWith(normalizedPath) || 
             normalizedPath.endsWith('/' + nodePath) || normalizedPath.endsWith(nodePath))) {
             
            // Compute match length; longer is better
            const matchLen = Math.min(nodePath.length, normalizedPath.length);
            if (matchLen > maxMatchLen) {
                targetNode = node;
                maxMatchLen = matchLen;
            }
        }
        
        // 3. Filename-only match (as a last resort)
        if ((nodePath.endsWith('/' + fileName) || nodePath === fileName) && fileName.length > 0) {
             if (!targetNode && maxMatchLen === 0) {
                 targetNode = node;
                 maxMatchLen = 1; // low priority
             }
        }
    });
    
    if (targetNode) {
        console.log('Found file node:', targetNode.getAttribute('data-file-path'));
        
        // Store the target line numbers in node data attributes
        if (startLine) {
            targetNode.dataset.startLine = startLine;
        } else {
            delete targetNode.dataset.startLine;
        }
        
        if (endLine) {
            targetNode.dataset.endLine = endLine;
        } else {
            delete targetNode.dataset.endLine;
        }
        
        // 1. Expand all parent directories
        let parent = targetNode.parentElement;
        while (parent) {
            // Find the parent li.folder
            if (parent.tagName === 'UL') {
                const parentLi = parent.parentElement;
                if (parentLi && parentLi.tagName === 'LI' && parentLi.classList.contains('folder')) {
                    if (!parentLi.classList.contains('expanded')) {
                        parentLi.classList.add('expanded');
                        const expandIcon = parentLi.querySelector('.file-tree-expand-icon');
                        if (expandIcon) {
                            expandIcon.classList.add('expanded');
                        }
                    }
                }
            }
            parent = parent.parentElement;
        }
        
        // 2. Scroll into view
        setTimeout(() => {
            targetNode.scrollIntoView({ behavior: 'smooth', block: 'center' });
            
            // 3. Trigger a click to load the file content
            // Find the div inside the li (the clickable area)
            const fileContentDiv = targetNode.querySelector('div');
            if (fileContentDiv) {
                fileContentDiv.click();
            }
        }, 100);
    } else {
        console.warn('File not found in the file tree:', filePath);
    }
}

// Select an issue
function selectFinding(findingId) {
    selectedFindingId = findingId;

    // Update the selection state
    document.querySelectorAll('.finding-item').forEach(item => {
        item.classList.remove('selected');
    });
    
    const selectedItem = document.querySelector(`[data-finding-id="${findingId}"]`);
    if (selectedItem) {
        selectedItem.classList.add('selected');
    }
    
    // Show the issue details
    const finding = currentFindings.find(f => f.id === findingId);
    if (finding) {
        renderFindingDetails(finding);

        // Extract the line numbers
        let startLine = null;
        let endLine = null;
        
        if (finding.line) {
             startLine = finding.line;
        } else if (finding.startLine) {
             startLine = finding.startLine;
             endLine = finding.endLine;
        } else if (finding.location) {
             const parts = finding.location.split(':');
             if (parts.length >= 2) {
                 const linePart = parts[1]; // "7" or "7-9"
                 const lineMatch = linePart.match(/(\d+)(?:-(\d+))?/);
                 if (lineMatch) {
                     startLine = parseInt(lineMatch[1]);
                     endLine = lineMatch[2] ? parseInt(lineMatch[2]) : null;
                 }
             }
        }
        
        const codeTypeSelect = document.getElementById('codeTypeSelect');
        const reviewType = codeTypeSelect ? codeTypeSelect.value : 'snippet';
        if (reviewType === 'snippet') {
            highlightSnippetCodeRange(startLine, endLine);
        } else if (finding.location) {
            const filePath = finding.location.split(':')[0];
            locateFileInTree(filePath, startLine, endLine);
        } else if (finding.fileName) {
            locateFileInTree(finding.fileName, startLine, endLine);
        }
    }
}

// Render the issue details
function renderFindingDetails(finding) {
    const detailsContent = document.getElementById('detailsContent');
    if (!detailsContent) return;
    
    // Remove the empty-state class
    detailsContent.classList.remove('empty');
    
    const severityClass = getSeverityClass(finding.severity);
    const severityLabel = getSeverityLabel(finding.severity);
    const location = finding.location || '';
    
    // Parse the location: extract class name and line number
    let className = '';
    let lineNumber = '';
    if (location) {
        const parts = location.split(':');
        if (parts.length >= 2) {
            className = parts[0];
            // Extract the line number (may be a single number or a range, e.g. "7" or "7-9")
            const linePart = parts.slice(1).join(':');
            const lineMatch = linePart.match(/(\d+)(?:-(\d+))?/);
            if (lineMatch) {
                const startLine = parseInt(lineMatch[1]);
                const endLine = lineMatch[2] ? parseInt(lineMatch[2]) : startLine;
                // Use the actual line numbers (to stay consistent with the code panel)
                const actualStartLine = getActualLineNumber(startLine);
                const actualEndLine = getActualLineNumber(endLine);
                if (actualStartLine === actualEndLine) {
                    lineNumber = actualStartLine.toString();
                } else {
                    lineNumber = `${actualStartLine}-${actualEndLine}`;
                }
            } else {
                lineNumber = linePart;
            }
        } else {
            className = location;
        }
    } else if (finding.startLine) {
        // If there's no location but there is a startLine, use startLine
        const actualStartLine = getActualLineNumber(finding.startLine);
        const actualEndLine = finding.endLine ? getActualLineNumber(finding.endLine) : actualStartLine;
        if (actualStartLine === actualEndLine) {
            lineNumber = actualStartLine.toString();
        } else {
            lineNumber = `${actualStartLine}-${actualEndLine}`;
        }
    }
    
    let diffHtml = '';
    if (finding.diff) {
        const diffLines = finding.diff.split('\n');
        diffHtml = diffLines.map(line => {
            const trimmedLine = line.trim();
            if (trimmedLine.startsWith('-')) {
                return `<div class="diff-line removed">${escapeHtml(line)}</div>`;
            } else if (trimmedLine.startsWith('+')) {
                return `<div class="diff-line added">${escapeHtml(line)}</div>`;
            } else if (trimmedLine.length > 0) {
                return `<div class="diff-line">${escapeHtml(line)}</div>`;
            }
            return '';
        }).filter(line => line.length > 0).join('');
    }
    
    const codeTypeSelect = document.getElementById('codeTypeSelect');
    const reviewType = codeTypeSelect ? codeTypeSelect.value : null;
    let codeSampleHtml = '';
    if (reviewType === 'snippet' || reviewType === 'file') {
        let fullCode = '';
        const editorEl = document.getElementById('codeEditor');
        if (editorEl && editorEl.textContent) {
            fullCode = editorEl.textContent;
        }
        if (fullCode && fullCode.length > 0) {
            codeSampleHtml = `
            <div class="detail-item">
                <div class="detail-label">Code sample</div>
                <div class="detail-value">
                    <pre class="code-editor-pre"><code class="language-java">${escapeHtml(fullCode)}</code></pre>
                </div>
            </div>`;
        }
    }

    detailsContent.innerHTML = `
        <div class="detail-item">
            <div class="detail-title">
                <span class="detail-title-text">${escapeHtml(finding.title || 'Unknown issue')}</span>
            </div>
        </div>
        
        <div class="detail-item">
            <div class="detail-meta-row">
                <span class="severity-badge ${severityClass}">${severityLabel}</span>
                <span class="detail-class-name">${escapeHtml(className)}</span>
                <span class="detail-line-number">${escapeHtml(lineNumber)}</span>
            </div>
        </div>
        
        <div class="detail-item">
            <div class="detail-label">Description</div>
            <div class="detail-value detail-description">${escapeHtml(finding.description || 'No description')}</div>
        </div>
        
        ${finding.diff ? `
        <div class="detail-item">
            <div class="detail-label">Suggested fix (Diff)</div>
            <div class="detail-value">
                <div class="detail-diff">${diffHtml}</div>
            </div>
        </div>
        ` : ''}
        ${codeSampleHtml}
    `;
}

// Generate the report
async function generateReport() {
    if (!currentTaskId) {
        alert('Please complete the code review first');
        return;
    }
    
    try {
        const resp = await fetch(`/api/report/${currentTaskId}`, { method: 'POST' });
        if (!resp.ok) {
            const err = await resp.text();
            throw new Error(err || 'Failed to generate the report');
        }
        window.location.href = `/report/${currentTaskId}`;
    } catch (error) {
        console.error('Failed to generate the report:', error);
        alert('Failed to generate the report. Please try again later.');
    }
}

// Get the severity CSS class
function getSeverityClass(severity) {
    if (!severity) return 'low';
    const s = severity.toUpperCase();
    if (s === 'CRITICAL') return 'critical';
    if (s === 'HIGH') return 'high';
    if (s === 'MEDIUM') return 'medium';
    return 'low';
}

// Get the severity icon
function getSeverityIcon(severity) {
    if (!severity) return 'fa-chart-bar';
    const s = severity.toUpperCase();
    if (s === 'CRITICAL') return 'fa-shield-alt'; // shield icon (matches the combined icon in renderFindings)
    if (s === 'HIGH') return 'fa-bug'; // bug icon
    if (s === 'MEDIUM') return 'fa-cog'; // gear icon
    return 'fa-chart-bar'; // chart icon
}

// Get the severity text
function getSeverityText(severity) {
    if (!severity) return 'Low';
    const s = severity.toUpperCase();
    if (s === 'CRITICAL') return 'Critical';
    if (s === 'HIGH') return 'High';
    if (s === 'MEDIUM') return 'Medium';
    return 'Low';
}

// Get the severity label text (used in issue details)
function getSeverityLabel(severity) {
    if (!severity) return 'Low';
    const s = severity.toUpperCase();
    if (s === 'CRITICAL') return 'Critical';
    if (s === 'HIGH') return 'High';
    if (s === 'MEDIUM') return 'Medium';
    return 'Low';
}

// Escape HTML
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}


// Log out
async function logout() {
    if (!confirm('Are you sure you want to log out?')) {
        return;
    }
    try {
        const resp = await fetch('/api/auth/logout', { method: 'POST' });
        window.location.href = '/login';
    } catch (e) {
        window.location.href = '/login';
    }
}

// Load sample code (contains known issues)
function loadSampleCode() {
    const sampleCode = `import java.sql.*;
import java.util.ArrayList;

public class UserService {

    // This method contains a SQL injection vulnerability
    public User findUser(String username) {
        // SQL injection risk: string concatenation
        String sql = "SELECT * FROM users WHERE name = '" + username + "'"; 
        
        try {
            // Hard-coded credentials
            Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/app", "root", "password");
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            // ...
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    // This method has a null pointer exception risk
    public void printUser(User user) {
        System.out.println(user.toString()); // NPE risk
    }
    
    // This method contains a hard-coded password
    public void connect() {
        String pwd = "P@ssword"; // hard-coded password
        // ...
    }
    
    // Intentional syntax error: extra brackets or missing symbols that can make static analysis fail to parse
    // public void brokenMethod() {
    //    if (true) {
    //        System.out.println("Error");
    //    }
    // } 
    // } <--- Uncomment this line to test how syntax errors affect Semgrep
}
`;
    
    const editor = document.getElementById('codeEditor');
    if (editor) {
        // Handle line breaks so it displays correctly in the pre
        editor.textContent = sampleCode;
        
        // Trigger highlighting
        if (typeof highlightCode === 'function') {
            highlightCode();
        }
        
        // Update line numbers
        const lineNumbers = document.getElementById('lineNumbers');
        if (lineNumbers) {
             const lines = sampleCode.split('\n');
             let lineNumbersHtml = '';
             for (let i = 1; i <= lines.length; i++) {
                 lineNumbersHtml += i + (i < lines.length ? '\n' : '');
             }
             lineNumbers.textContent = lineNumbersHtml;
             
             // If the simple line-number update above doesn't work, call the generic updateLineNumbers
             // But updateLineNumbers isn't exposed here, so we rely on the logic in initCodeEditor
             // A better approach is to dispatch an input event
             editor.dispatchEvent(new Event('input', { bubbles: true }));
        }
    }
}
