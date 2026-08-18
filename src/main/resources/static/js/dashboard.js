// Dashboard chart initialization

// Chart 1: Code health score (doughnut chart)
function initHealthScoreChart() {
    const ctx = document.getElementById('healthScoreChart');
    if (!ctx) return;
    
    // Apply height config
    if (window.dashboardConfig && window.dashboardConfig.chartHeight) {
        const wrapper = ctx.parentElement;
        if (wrapper && wrapper.classList.contains('chart-wrapper')) {
            wrapper.style.height = window.dashboardConfig.chartHeight + 'px';
        }
    }
    
    // Compute ring thickness (cutout = 100% - thickness%)
    let cutout = '70%';
    if (window.dashboardConfig && window.dashboardConfig.ringThickness) {
        const thickness = Math.max(5, Math.min(90, window.dashboardConfig.ringThickness));
        cutout = (100 - thickness) + '%';
    }

    const healthScore = (window.dashboardData && window.dashboardData.healthScore !== undefined) 
        ? window.dashboardData.healthScore 
        : 100;
        
    // Choose color based on score
    let scoreColor = '#10B981'; // green
    if (healthScore < 60) scoreColor = '#EF4444'; // red
    else if (healthScore < 80) scoreColor = '#F97316'; // orange

    new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: ['Healthy', 'Unhealthy'],
            datasets: [{
                data: [healthScore, 100 - healthScore],
                backgroundColor: [
                    scoreColor, // dynamic color
                    '#30363d'  // gray background
                ],
                borderWidth: 0,
                cutout: cutout
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    display: false
                },
                tooltip: {
                    enabled: true,
                    callbacks: {
                        label: function(context) {
                            return context.label + ': ' + context.parsed + '%';
                        }
                    }
                }
            }
        },
        plugins: [{
            id: 'centerText',
            beforeDraw: function(chart) {
                const ctx = chart.ctx;
                const centerX = chart.chartArea.left + (chart.chartArea.right - chart.chartArea.left) / 2;
                const centerY = chart.chartArea.top + (chart.chartArea.bottom - chart.chartArea.top) / 2;
                
                ctx.save();
                // Draw the black background box
                ctx.fillStyle = '#000000';
                ctx.fillRect(centerX - 35, centerY - 15, 70, 30);
                
                // Draw the color swatch
                ctx.fillStyle = scoreColor;
                ctx.fillRect(centerX - 30, centerY - 10, 12, 12);
                
                // Draw the score text
                ctx.fillStyle = '#ffffff';
                ctx.font = 'bold 14px sans-serif';
                ctx.textAlign = 'left';
                ctx.textBaseline = 'middle';
                ctx.fillText(healthScore.toString(), centerX - 15, centerY - 2);
                
                ctx.restore();
            }
        }]
    });
}

// Chart 2: Issue distribution (vertical bar chart)
function initProblemDistributionChart() {
    const ctx = document.getElementById('problemDistributionChart');
    if (!ctx) return;
    
    const distObj = (window.dashboardData && window.dashboardData.problemDistribution) 
        ? window.dashboardData.problemDistribution 
        : { critical: 0, high: 0, medium: 0, low: 0 };
        
    const dataValues = [
        distObj.critical || 0, 
        distObj.high || 0, 
        distObj.medium || 0, 
        distObj.low || 0
    ];
    
    // Compute the max value to set the Y axis dynamically
    const maxVal = Math.max(...dataValues, 10); // at least 10

    new Chart(ctx, {
        type: 'bar',
        data: {
            labels: ['Critical', 'High', 'Medium', 'Low'],
            datasets: [{
                label: 'Issue count',
                data: dataValues,
                backgroundColor: [
                    '#EF4444', // red - Critical
                    '#F97316', // orange - High
                    '#EAB308', // yellow - Medium
                    '#10B981'  // green - Low
                ],
                borderRadius: 8,
                borderSkipped: false
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    display: false
                },
                tooltip: {
                    enabled: true,
                    callbacks: {
                        label: function(context) {
                            return 'Count: ' + context.parsed.y;
                        }
                    }
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    suggestedMax: maxVal + 2,
                    ticks: {
                        color: '#6b6b6b',
                        stepSize: Math.ceil(maxVal / 5)
                    },
                    grid: {
                        color: '#2d2d2d'
                    }
                },
                x: {
                    ticks: {
                        color: '#6b6b6b'
                    },
                    grid: {
                        display: false
                    }
                }
            }
        }
    });
}

// Chart 3: Project issue statistics (stacked bar chart)
function initProjectStatisticsChart() {
    const ctx = document.getElementById('projectStatisticsChart');
    if (!ctx) return;
    
    const stats = (window.dashboardData && window.dashboardData.projectStats) 
        ? window.dashboardData.projectStats 
        : [];
        
    const labels = stats.map(s => s.projectName);
    const criticalData = stats.map(s => s.criticalCount);
    const highData = stats.map(s => s.highCount);
    const mediumData = stats.map(s => s.mediumCount);
    const lowData = stats.map(s => s.lowCount);
    
    // Compute the max total for the Y axis
    let maxTotal = 0;
    stats.forEach(s => {
        const total = s.criticalCount + s.highCount + s.mediumCount + s.lowCount;
        if (total > maxTotal) maxTotal = total;
    });
    maxTotal = Math.max(maxTotal, 10);

    new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [
                {
                    label: 'Critical',
                    data: criticalData,
                    backgroundColor: '#EF4444'
                },
                {
                    label: 'High',
                    data: highData,
                    backgroundColor: '#F97316'
                },
                {
                    label: 'Medium',
                    data: mediumData,
                    backgroundColor: '#EAB308'
                },
                {
                    label: 'Low',
                    data: lowData,
                    backgroundColor: '#10B981'
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    display: true,
                    position: 'top',
                    labels: {
                        color: '#c9d1d9',
                        usePointStyle: true,
                        padding: 12,
                        font: {
                            size: 12
                        },
                        boxWidth: 12,
                        boxHeight: 12
                    }
                },
                tooltip: {
                    enabled: true,
                    callbacks: {
                        label: function(context) {
                            return context.dataset.label + ': ' + context.parsed.y;
                        }
                    }
                }
            },
            scales: {
                x: {
                    stacked: true,
                    ticks: {
                        color: '#8b949e',
                        font: {
                            size: 12
                        },
                        padding: 3
                    },
                    grid: {
                        display: false
                    }
                },
                y: {
                    stacked: true,
                    beginAtZero: true,
                    suggestedMax: maxTotal + 5,
                    min: 0,
                    ticks: {
                        color: '#8b949e',
                        stepSize: Math.ceil(maxTotal / 5),
                        font: {
                            size: 12
                        },
                        padding: 3
                    },
                    grid: {
                        color: '#30363d'
                    }
                }
            },
            layout: {
                padding: {
                    bottom: 0,
                    top: 0,
                    left: 0,
                    right: 0
                }
            }
        }
    });
}

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

// Initialize all charts after the page loads
document.addEventListener('DOMContentLoaded', function() {
    // Fix: force-remove any overlay layers that could gray out the page
    const overlays = document.querySelectorAll('.modal-overlay, .modal-backdrop, .overlay, .modal');
    overlays.forEach(el => {
        el.style.display = 'none';
        // For full-screen overlays, removing may be safer, but hiding is usually enough
        if (getComputedStyle(el).position === 'fixed') {
            el.style.setProperty('display', 'none', 'important');
        }
    });

    // Fix: make sure the main content area can scroll and is not covered
    const mainContent = document.querySelector('.main-content');
    if (mainContent) {
        mainContent.style.overflowY = 'auto';
        // Ensure z-index is auto to avoid being constrained by a parent stacking context
        mainContent.style.zIndex = 'auto'; 
    }

    // Set Chart.js default colors
    Chart.defaults.color = '#ffffff';
    Chart.defaults.borderColor = '#2d2d2d';
    
    initHealthScoreChart();
    initProblemDistributionChart();
    initProjectStatisticsChart();
});
