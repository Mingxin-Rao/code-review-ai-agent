// History report page script

let currentPage = 0;
const pageSize = 10;
let totalPages = 0;

document.addEventListener('DOMContentLoaded', function() {
    loadHistory();
    
    // Search when Enter is pressed
    document.getElementById('nameFilter').addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            searchHistory();
        }
    });
});

/**
 * Load history
 */
function loadHistory(page = 0) {
    currentPage = page;

    // Build query parameters
    const params = new URLSearchParams();
    params.append('page', currentPage);
    params.append('size', pageSize);
    
    const name = document.getElementById('nameFilter').value;
    if (name) params.append('name', name);
    
    const scope = document.getElementById('scopeFilter').value;
    if (scope) params.append('reviewType', scope);
    
    // Handle dates
    const startDate = document.getElementById('startDateFilter').value;
    const endDate = document.getElementById('endDateFilter').value;
    
    // If a quick time range is selected
    const timeRange = document.getElementById('timeRangeFilter').value;
    if (timeRange) {
        const now = new Date();
        let start = new Date();
        
        if (timeRange === 'today') {
            start.setHours(0, 0, 0, 0);
        } else if (timeRange === 'week') {
            const day = now.getDay();
            const diff = now.getDate() - day + (day === 0 ? -6 : 1); // adjust when day is sunday
            start.setDate(diff);
            start.setHours(0, 0, 0, 0);
        } else if (timeRange === 'month') {
            start.setDate(1);
            start.setHours(0, 0, 0, 0);
        }
        
        params.append('startTime', formatDateForApi(start));
        params.append('endTime', formatDateForApi(now));
    } else {
        if (startDate) params.append('startTime', startDate + 'T00:00:00');
        if (endDate) params.append('endTime', endDate + 'T23:59:59');
    }
    
    // Send the request
    fetch(`/api/review/history?${params.toString()}`)
        .then(response => response.json())
        .then(data => {
            renderTable(data.content);
            updatePagination(data);
        })
        .catch(error => {
            console.error('Failed to load history:', error);
            alert('Failed to load history. Please try again.');
        });
}

/**
 * Render the table
 */
function renderTable(items) {
    const tbody = document.getElementById('historyTableBody');
    tbody.innerHTML = '';
    
    if (!items || items.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; padding: 20px;">No records</td></tr>';
        return;
    }
    
    items.forEach((item, index) => {
        const tr = document.createElement('tr');
        
        // Serial number
        const serialNum = index + 1 + (currentPage * pageSize);

        // Format the time
        const reviewTime = formatDateTime(item.createdAt);

        // Build issue statistics
        const statsHtml = `
            <div class="issue-stats">
                <span class="stat-item stat-critical" title="Critical">Critical:${item.criticalCount || 0}</span>
                <span class="stat-item stat-high" title="High">High:${item.highCount || 0}</span>
                <span class="stat-item stat-medium" title="Medium">Medium:${item.mediumCount || 0}</span>
                <span class="stat-item stat-low" title="Low">Low:${item.lowCount || 0}</span>
            </div>
        `;
        
    // Scope display: always use the fixed label for the review type
        let scopeDisplay = mapReviewTypeToZh(item.reviewType);
        
        let actionButtons = `
            <a href="#" class="action-btn" onclick="viewReport(${item.taskId})">
                <i class="fas fa-eye"></i> View
            </a>
            <a href="#" class="action-btn" onclick="downloadReport(${item.taskId})">
                <i class="fas fa-download"></i> Download
            </a>
        `;

        if (typeof canAdmin !== 'undefined' && canAdmin) {
            actionButtons += `
                <a href="#" class="action-btn" onclick="deleteTask(${item.taskId})">
                    <i class="fas fa-trash"></i> Delete
                </a>
            `;
        }

        tr.innerHTML = `
            <td>${serialNum}</td>
            <td>${item.taskName || 'Untitled snippet'}</td>
            <td>${scopeDisplay}</td>
            <td>${reviewTime}</td>
            <td>${statsHtml}</td>
            <td>
                ${actionButtons}
            </td>
        `;
        
        tbody.appendChild(tr);
    });
}

/**
 * Update pagination controls
 */
function updatePagination(data) {
    totalPages = data.totalPages;
    const totalElements = data.totalElements;
    
    const paginationInfo = document.getElementById('paginationInfo');
    paginationInfo.textContent = `${totalElements} records total — page ${data.number + 1}/${data.totalPages}`;

    const pageDisplay = document.getElementById('pageDisplay');
    pageDisplay.textContent = `Page ${data.number + 1}/${data.totalPages}`;
    
    const prevBtn = document.querySelector('.prev-btn');
    const nextBtn = document.querySelector('.next-btn');
    
    prevBtn.disabled = data.first;
    nextBtn.disabled = data.last;
}

/**
 * Search
 */
function searchHistory() {
    loadHistory(0);
}

/**
 * Reset filters
 */
function resetFilters() {
    document.getElementById('nameFilter').value = '';
    document.getElementById('scopeFilter').value = '';
    document.getElementById('timeRangeFilter').value = '';
    document.getElementById('startDateFilter').value = '';
    document.getElementById('endDateFilter').value = '';
    loadHistory(0);
}

/**
 * Previous page
 */
function prevPage() {
    if (currentPage > 0) {
        loadHistory(currentPage - 1);
    }
}

/**
 * Next page
 */
function nextPage() {
    if (currentPage < totalPages - 1) {
        loadHistory(currentPage + 1);
    }
}

/**
 * View report
 */
function viewReport(taskId) {
    window.open(`/api/report/${taskId}/html`, '_blank');
}

/**
 * Download report
 */
function downloadReport(taskId) {
    // Trigger PDF download
    const a = document.createElement('a');
    a.href = `/api/report/${taskId}/pdf`;
    a.download = `review_report_${taskId}.pdf`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
}

function deleteTask(taskId) {
    if (!confirm('Delete this history record? This cannot be undone.')) {
        return;
    }

    // Show a loading message
    const loadingMsg = 'Deleting...';

    fetch(`/api/review/task/${taskId}`, { method: 'DELETE' })
        .then(resp => {
            if (!resp.ok) throw new Error('Delete failed');
            // On success, show a message and refresh the list
            alert('Deleted successfully.');
            loadHistory(currentPage);
        })
        .catch(err => {
            alert('Delete failed: ' + err.message);
            console.error(err);
        });
}

/**
 * Format date/time
 */
function formatDateTime(isoString) {
    if (!isoString) return '';
    const date = new Date(isoString);
    return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    }).replace(/\//g, '-');
}

/**
 * Format a date into the format the API requires (yyyy-MM-ddTHH:mm:ss)
 */
function formatDateForApi(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
}

/**
 * Map a review type to a display label
 */
function mapReviewTypeToZh(type) {
    if (!type) return 'Snippet';
    const t = String(type).toUpperCase();
    if (t === 'PROJECT') return 'Whole Project';
    if (t === 'DIRECTORY') return 'Directory';
    if (t === 'FILE') return 'File';
    if (t === 'SNIPPET') return 'Code Snippet';
    if (t === 'GIT') return 'Git Repository';
    return 'Code Snippet';
}
