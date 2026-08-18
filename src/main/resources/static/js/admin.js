// Access Control page JavaScript

let currentPage = 0;
let pageSize = 10;
let totalPages = 1;
let totalElements = 0;
let allRoles = [];

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    // Load roles and users only on the User Management page
    const roleFilterEl = document.getElementById('roleFilter');
    if (roleFilterEl) {
        loadRoles();
        loadUsers();
    }
    
    // Load the Role Management page
    const rolesContent = document.getElementById('rolesContent');
    if (rolesContent) {
        loadRolesForManagement();
    }
    
    // Load the permission list page
    const permissionsContent = document.getElementById('permissionsContent');
    if (permissionsContent) {
        loadPermissionsForList();
    }
    
    // Log Out button (if not handled via onclick)
    const logoutBtn = document.querySelector('.logout-btn');
    if (logoutBtn && !logoutBtn.onclick) {
        logoutBtn.addEventListener('click', function() {
            logout();
        });
    }
});

// Log Out function
async function logout() {
    if (!confirm('Are you sure you want to log out?')) {
        return;
    }
    try {
        const resp = await fetch('/api/auth/logout', { method: 'POST' });
        // Return to the login page whether it succeeds or fails
        window.location.href = '/login';
    } catch (e) {
        window.location.href = '/login';
    }
}

function handleUnauthorized(response) {
    if (!response) return false;
    if (response.status === 401) {
        alert('Your session has expired. Redirecting to the login page.');
        window.location.href = '/login';
        return true;
    }
    if (response.status === 403) {
        alert('This account does not have access permission.');
        return true;
    }
    return false;
}

// Load role list
async function loadRoles() {
    try {
        const response = await fetch('/admin/roles/api');
        if (!response.ok) {
            console.error('Failed to load roles:', response.status, response.statusText);
            return;
        }
        
        const roles = await response.json();
        allRoles = roles;
        
        // Populate the role filter dropdown (if present)
        const roleFilter = document.getElementById('roleFilter');
        if (roleFilter) {
            // Clear existing options (except "All roles")
            while (roleFilter.children.length > 1) {
                roleFilter.removeChild(roleFilter.lastChild);
            }
            
            roles.forEach(role => {
                const option = document.createElement('option');
                option.value = role.code;
                option.textContent = role.name;
                roleFilter.appendChild(option);
            });
        }
    } catch (error) {
        console.error('Failed to load roles:', error);
    }
}

// Load user list
async function loadUsers() {
    try {
        const searchInput = document.getElementById('searchInput');
        const statusFilter = document.getElementById('statusFilter');
        const roleFilter = document.getElementById('roleFilter');
        
        // Check the elements exist (only present on the User Management page)
        if (!searchInput || !statusFilter || !roleFilter) {
            return;
        }
        
        const keyword = searchInput.value;
        const status = statusFilter.value;
        const roleCode = roleFilter.value;
        
        const params = new URLSearchParams({
            page: currentPage,
            size: pageSize
        });
        
        if (keyword) params.append('keyword', keyword);
        if (status !== '') params.append('status', status);
        if (roleCode) params.append('roleCode', roleCode);
        
        const response = await fetch(`/admin/users/api?${params}`);
        if (!response.ok) {
            if (handleUnauthorized(response)) return;
            let errorMessage = 'Failed to load users. Please try again later.';
            try {
                const error = await response.json();
                errorMessage = error.message || error.error || errorMessage;
            } catch (e) {
                console.error('Failed to parse error response:', e);
            }
            alert(errorMessage);
            return;
        }
        
        const result = await response.json();
        
        totalPages = result.totalPages;
        totalElements = result.totalElements;
        
        renderUserTable(result.content);
        updatePagination();
    } catch (error) {
        console.error('Failed to load users:', error);
        alert('Failed to load users. Please try again later.');
    }
}

// Render the user table
function renderUserTable(users) {
    const tbody = document.getElementById('userTableBody');
    if (!tbody) {
        return; // Not on the User Management page; return early
    }
    
    tbody.innerHTML = '';
    
    if (users.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: #a0a0a0;">No data</td></tr>';
        return;
    }
    
    users.forEach(user => {
        const tr = document.createElement('tr');
        
        // Username
        const usernameTd = document.createElement('td');
        usernameTd.textContent = user.username;
        tr.appendChild(usernameTd);

        // Email
        const emailTd = document.createElement('td');
        emailTd.textContent = user.email;
        tr.appendChild(emailTd);

        // Real name
        const realNameTd = document.createElement('td');
        realNameTd.textContent = user.realName || '-';
        tr.appendChild(realNameTd);

        // Role
        const roleTd = document.createElement('td');
        if (user.roles && user.roles.length > 0) {
            user.roles.forEach(roleCode => {
                const tag = document.createElement('span');
                tag.className = 'role-tag ' + roleCode.toLowerCase();
                tag.textContent = getRoleName(roleCode);
                roleTd.appendChild(tag);
            });
        } else {
            roleTd.textContent = '-';
        }
        tr.appendChild(roleTd);
        
        // Status
        const statusTd = document.createElement('td');
        const statusTag = document.createElement('span');
        if (user.status === 0) {
            statusTag.className = 'status-tag active';
            statusTag.textContent = 'Active';
        } else if (user.status === 1) {
            statusTag.className = 'status-tag inactive';
            statusTag.textContent = 'Inactive';
        } else {
            statusTag.className = 'status-tag locked';
            statusTag.textContent = 'Locked';
        }
        statusTd.appendChild(statusTag);
        tr.appendChild(statusTd);
        
        // Registration time
        const createdAtTd = document.createElement('td');
        if (user.createdAt) {
            createdAtTd.textContent = formatDateTime(user.createdAt);
        } else {
            createdAtTd.textContent = '-';
        }
        tr.appendChild(createdAtTd);
        
        // Actions
        const actionTd = document.createElement('td');
        const actionDiv = document.createElement('div');
        actionDiv.className = 'action-buttons';
        
        const editBtn = document.createElement('button');
        editBtn.className = 'action-btn edit-btn';
        editBtn.textContent = 'Edit';
        editBtn.onclick = () => editUser(user.id);
        actionDiv.appendChild(editBtn);
        
        const deleteBtn = document.createElement('button');
        deleteBtn.className = 'action-btn delete-btn';
        deleteBtn.textContent = 'Delete';
        deleteBtn.onclick = () => deleteUser(user.id, user.username);
        actionDiv.appendChild(deleteBtn);
        
        actionTd.appendChild(actionDiv);
        tr.appendChild(actionTd);
        
        tbody.appendChild(tr);
    });
}

// Get role name
function getRoleName(roleCode) {
    const role = allRoles.find(r => r.code === roleCode);
    return role ? role.name : roleCode;
}

// Format date/time
function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return '-';
    const date = new Date(dateTimeStr);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

// Update pagination info
function updatePagination() {
    const pageInfo = document.getElementById('pageInfo');
    if (!pageInfo) {
        return; // Not on the User Management page; return early
    }
    
    pageInfo.textContent = `Page ${currentPage + 1}/${totalPages} (${totalElements} total)`;
    
    const prevBtn = document.getElementById('prevBtn');
    const nextBtn = document.getElementById('nextBtn');
    if (prevBtn) prevBtn.disabled = currentPage === 0;
    if (nextBtn) nextBtn.disabled = currentPage >= totalPages - 1;
}

// Change page
function changePage(delta) {
    const newPage = currentPage + delta;
    if (newPage >= 0 && newPage < totalPages) {
        currentPage = newPage;
        loadUsers();
    }
}

// Handle search
function handleSearch(event) {
    const searchInput = document.getElementById('searchInput');
    if (!searchInput) {
        return; // Not on the User Management page; return early
    }
    
    if (event.key === 'Enter') {
        currentPage = 0;
        loadUsers();
    }
}

// Reset filters
function resetFilters() {
    const searchInput = document.getElementById('searchInput');
    const statusFilter = document.getElementById('statusFilter');
    const roleFilter = document.getElementById('roleFilter');
    
    if (!searchInput || !statusFilter || !roleFilter) {
        return; // Not on the User Management page; return early
    }
    
    searchInput.value = '';
    statusFilter.value = '';
    roleFilter.value = '';
    currentPage = 0;
    loadUsers();
}

// Show the Add User modal
function showAddUserModal() {
    document.getElementById('modalTitle').textContent = 'Add User';
    document.getElementById('userForm').reset();
    document.getElementById('userId').value = '';
    document.getElementById('passwordGroup').style.display = 'block';
    document.getElementById('password').required = true;
    
    renderRoleCheckboxes();
    document.getElementById('userModal').style.display = 'flex';
}

// Edit user
async function editUser(userId) {
    try {
        const response = await fetch(`/admin/users/api/${userId}`);
        if (!response.ok) {
            if (handleUnauthorized(response)) return;
            const error = await response.json();
            alert(error.message || error.error || 'Failed to load user info. Please try again later.');
            return;
        }
        
        const user = await response.json();
        
        document.getElementById('modalTitle').textContent = 'Edit User';
        document.getElementById('userId').value = user.id;
        document.getElementById('username').value = user.username;
        document.getElementById('username').disabled = true;
        document.getElementById('email').value = user.email;
        document.getElementById('realName').value = user.realName || '';
        document.getElementById('status').value = user.status;
        document.getElementById('passwordGroup').style.display = 'none';
        document.getElementById('password').required = false;
        
        renderRoleCheckboxes(user.roles || []);
        document.getElementById('userModal').style.display = 'flex';
    } catch (error) {
        console.error('Failed to load users:', error);
        alert('Failed to load user info. Please try again later.');
    }
}

// Render role checkboxes
function renderRoleCheckboxes(selectedRoles = []) {
    const container = document.getElementById('roleCheckboxes');
    container.innerHTML = '';
    
    allRoles.forEach(role => {
        const div = document.createElement('div');
        div.className = 'role-checkbox';
        
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.id = `role_${role.code}`;
        checkbox.value = role.code;
        checkbox.checked = selectedRoles.includes(role.code);
        
        const label = document.createElement('label');
        label.htmlFor = `role_${role.code}`;
        label.textContent = role.name;
        
        div.appendChild(checkbox);
        div.appendChild(label);
        container.appendChild(div);
    });
}

// Save user
async function saveUser(event) {
    event.preventDefault();
    
    const userId = document.getElementById('userId').value;
    const formData = {
        username: document.getElementById('username').value,
        email: document.getElementById('email').value,
        realName: document.getElementById('realName').value,
        status: parseInt(document.getElementById('status').value),
        roleCodes: getSelectedRoles()
    };
    
    // Password is required when adding a user
    if (!userId) {
        formData.password = document.getElementById('password').value;
    }
    
    try {
        let response;
        if (userId) {
            // Update user
            response = await fetch(`/admin/users/api/${userId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(formData)
            });
        } else {
            // Create user
            response = await fetch('/admin/users/api', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(formData)
            });
        }
        
        if (response.ok) {
            closeUserModal();
            loadUsers();
            alert(userId ? 'User updated successfully' : 'User created successfully');
        } else {
            let errorMessage = 'Operation failed. Please try again later.';
            try {
                const error = await response.json();
                if (error.errors) {
                    // Validation error: show all invalid fields
                    const errorFields = Object.entries(error.errors)
                        .map(([field, msg]) => `${field}: ${msg}`)
                        .join('\n');
                    errorMessage = `Validation failed:\n${errorFields}`;
                } else {
                    errorMessage = error.message || error.error || errorMessage;
                }
            } catch (e) {
                console.error('Failed to parse error response:', e);
            }
            alert(errorMessage);
        }
    } catch (error) {
        console.error('Failed to save user:', error);
        alert('Failed to save the user. Please try again later.');
    }
}

// Get selected roles
function getSelectedRoles() {
    const checkboxes = document.querySelectorAll('#roleCheckboxes input[type="checkbox"]:checked');
    return Array.from(checkboxes).map(cb => cb.value);
}

// Delete user
async function deleteUser(userId, username) {
    if (!confirm(`Are you sure you want to delete user "${username}"? This action cannot be undone.`)) {
        return;
    }
    
    try {
        const response = await fetch(`/admin/users/api/${userId}`, {
            method: 'DELETE'
        });
        
        if (response.ok) {
            loadUsers();
            alert('User deleted successfully');
        } else {
            if (handleUnauthorized(response)) return;
            let errorMessage = 'Delete failed. Please try again later.';
            try {
                const error = await response.json();
                errorMessage = error.message || error.error || errorMessage;
            } catch (e) {
                console.error('Failed to parse error response:', e);
            }
            alert(errorMessage);
        }
    } catch (error) {
        console.error('Failed to delete user:', error);
        alert('Failed to delete the user. Please try again later.');
    }
}

// Close the modal
function closeUserModal() {
    document.getElementById('userModal').style.display = 'none';
    document.getElementById('userForm').reset();
    document.getElementById('username').disabled = false;
}

// Removed the click-outside-to-close behavior
// The modal can now only be closed via the Close or Cancel buttons

// Load the Role Management page
async function loadRolesForManagement() {
    try {
        const response = await fetch('/admin/roles/api/with-permissions');
        if (!response.ok) {
            if (handleUnauthorized(response)) return;
            let errorMessage = 'Failed to load roles. Please try again later.';
            try {
                const error = await response.json();
                errorMessage = error.message || error.error || errorMessage;
            } catch (e) {
                console.error('Failed to parse error response:', e);
            }
            alert(errorMessage);
            return;
        }
        
        const roles = await response.json();
        renderRoleCards(roles);
    } catch (error) {
        console.error('Failed to load roles:', error);
        alert('Failed to load roles. Please try again later.');
    }
}

// Render role cards
function renderRoleCards(roles) {
    const rolesContent = document.getElementById('rolesContent');
    if (!rolesContent) return;
    
    rolesContent.innerHTML = roles.map(role => {
        const permissionTags = role.permissions.map(perm => 
            `<span class="permission-tag">${perm}</span>`
        ).join('');
        
        return `
            <div class="role-card">
                <div class="role-card-header">
                    <h3 class="role-name">${role.name}</h3>
                    <span class="role-code">${role.code}</span>
                </div>
                <p class="role-description">${role.description || ''}</p>
                <div class="role-permissions">
                    ${permissionTags}
                </div>
                <div class="role-card-actions">
                    <button class="role-edit-btn" onclick="editRole(${role.id})">
                        <i class="fas fa-edit"></i> Edit
                    </button>
                    <button class="role-assign-btn" onclick="assignPermissions(${role.id})">
                        <i class="fas fa-key"></i> Assign Permissions
                    </button>
                    <button class="role-delete-btn" onclick="deleteRole(${role.id}, '${role.name.replace(/'/g, "\\'")}')">
                        <i class="fas fa-trash"></i> Delete
                    </button>
                </div>
            </div>
        `;
    }).join('');
}

// Load the permission list page
async function loadPermissionsForList() {
    try {
        const response = await fetch('/admin/permissions/api/dto');
        if (!response.ok) {
            if (handleUnauthorized(response)) return;
            let errorMessage = 'Failed to load permissions. Please try again later.';
            try {
                const error = await response.json();
                errorMessage = error.message || error.error || errorMessage;
            } catch (e) {
                console.error('Failed to parse error response:', e);
            }
            alert(errorMessage);
            return;
        }
        
        const permissions = await response.json();
        renderPermissionCards(permissions);
    } catch (error) {
        console.error('Failed to load permissions:', error);
        alert('Failed to load permissions. Please try again later.');
    }
}

// Render permission cards
function renderPermissionCards(permissions) {
    const permissionsContent = document.getElementById('permissionsContent');
    if (!permissionsContent) return;
    
    permissionsContent.innerHTML = permissions.map(permission => {
        return `
            <div class="permission-card">
                <h3 class="permission-name">${permission.name}</h3>
                <div class="permission-code">${permission.code}</div>
                <div class="permission-info">
                    <div class="permission-resource">Resource: ${permission.resource}</div>
                    <div class="permission-action">Action: ${permission.action}</div>
                </div>
                <p class="permission-description">${permission.description || ''}</p>
            </div>
        `;
    }).join('');
}

// Show the Add Role modal
function showAddRoleModal() {
    document.getElementById('roleModalTitle').textContent = 'Add Role';
    document.getElementById('roleId').value = '';
    document.getElementById('roleCode').value = '';
    document.getElementById('roleCode').disabled = false;
    document.getElementById('roleName').value = '';
    document.getElementById('roleDescription').value = '';
    document.getElementById('roleStatus').value = '0';
    document.getElementById('roleModal').style.display = 'flex';
}

// Edit role
async function editRole(roleId) {
    try {
        const response = await fetch(`/admin/roles/api/${roleId}`);
        if (!response.ok) {
            if (handleUnauthorized(response)) return;
            let errorMessage = 'Failed to load role info. Please try again later.';
            try {
                const error = await response.json();
                errorMessage = error.message || error.error || errorMessage;
            } catch (e) {
                console.error('Failed to parse error response:', e);
            }
            alert(errorMessage);
            return;
        }
        
        const role = await response.json();
        
        document.getElementById('roleModalTitle').textContent = 'Edit Role';
        document.getElementById('roleId').value = role.id;
        document.getElementById('roleCode').value = role.code;
        document.getElementById('roleCode').disabled = true;
        document.getElementById('roleName').value = role.name;
        document.getElementById('roleDescription').value = role.description || '';
        document.getElementById('roleStatus').value = role.status;
        document.getElementById('roleModal').style.display = 'flex';
    } catch (error) {
        console.error('Failed to load roles:', error);
        alert('Failed to load role info. Please try again later.');
    }
}

// Close the Role modal
function closeRoleModal() {
    document.getElementById('roleModal').style.display = 'none';
    document.getElementById('roleForm').reset();
    document.getElementById('roleCode').disabled = false;
}

// Save role
async function saveRole(event) {
    event.preventDefault();
    
    const roleId = document.getElementById('roleId').value;
    const formData = {
        code: document.getElementById('roleCode').value,
        name: document.getElementById('roleName').value,
        description: document.getElementById('roleDescription').value,
        status: parseInt(document.getElementById('roleStatus').value)
    };
    
    try {
        let response;
        if (roleId) {
            // Update role (code is not updated when editing)
            const updateData = {
                name: formData.name,
                description: formData.description,
                status: formData.status
            };
            response = await fetch(`/admin/roles/api/${roleId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(updateData)
            });
        } else {
            // Create role
            response = await fetch('/admin/roles/api', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(formData)
            });
        }
        
        if (response.ok) {
            closeRoleModal();
            loadRolesForManagement();
            alert(roleId ? 'Role updated successfully' : 'Role created successfully');
        } else {
            if (handleUnauthorized(response)) return;
            let errorMessage = 'Operation failed. Please try again later.';
            try {
                const error = await response.json();
                if (error.errors) {
                    const errorFields = Object.entries(error.errors)
                        .map(([field, msg]) => `${field}: ${msg}`)
                        .join('\n');
                    errorMessage = `Validation failed:\n${errorFields}`;
                } else {
                    errorMessage = error.message || error.error || errorMessage;
                }
            } catch (e) {
                console.error('Failed to parse error response:', e);
            }
            alert(errorMessage);
        }
    } catch (error) {
        console.error('Failed to save role:', error);
        alert('Failed to save the role. Please try again later.');
    }
}

// Assign permissions
let currentRoleIdForPermission = null;

async function assignPermissions(roleId) {
    currentRoleIdForPermission = roleId;
    
    try {
        // Load all permissions
        const permissionsResponse = await fetch('/admin/permissions/api');
        if (!permissionsResponse.ok) {
            alert('Failed to load the permission list. Please try again later.');
            return;
        }
        const allPermissions = await permissionsResponse.json();
        
        // Load permissions for the current role
        const roleResponse = await fetch(`/admin/roles/api/${roleId}`);
        if (!roleResponse.ok) {
            if (handleUnauthorized(roleResponse)) return;
            alert('Failed to load role info. Please try again later.');
            return;
        }
        const role = await roleResponse.json();
        const selectedPermissionCodes = role.permissions || [];
        
        // Render permission checkboxes
        const container = document.getElementById('permissionCheckboxes');
        container.innerHTML = '';
        
        allPermissions.forEach(permission => {
            const div = document.createElement('div');
            div.className = 'form-group';
            
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.id = `perm_${permission.id}`;
            checkbox.value = permission.code;
            checkbox.checked = selectedPermissionCodes.includes(permission.code);
            
            const label = document.createElement('label');
            label.htmlFor = `perm_${permission.id}`;
            label.textContent = `${permission.name} (${permission.code})`;
            
            div.appendChild(checkbox);
            div.appendChild(label);
            container.appendChild(div);
        });
        
        document.getElementById('permissionModalTitle').textContent = `Assign permissions to role "${role.name}"`;
        document.getElementById('permissionModal').style.display = 'flex';
    } catch (error) {
        console.error('Failed to load permissions:', error);
        alert('Failed to load the permission list. Please try again later.');
    }
}

// Close the permission modal
function closePermissionModal() {
    document.getElementById('permissionModal').style.display = 'none';
    currentRoleIdForPermission = null;
}

// Save permission assignment
async function savePermissions() {
    if (!currentRoleIdForPermission) {
        alert('Role ID does not exist');
        return;
    }
    
    const checkboxes = document.querySelectorAll('#permissionCheckboxes input[type="checkbox"]:checked');
    const permissionCodes = Array.from(checkboxes).map(cb => cb.value);
    
    try {
        const response = await fetch(`/admin/roles/api/${currentRoleIdForPermission}/permissions`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(permissionCodes)
        });
        
        if (response.ok) {
            closePermissionModal();
            loadRolesForManagement();
            alert('Permissions assigned successfully');
        } else {
            if (handleUnauthorized(response)) return;
            let errorMessage = 'Failed to assign permissions. Please try again later.';
            try {
                const error = await response.json();
                errorMessage = error.message || error.error || errorMessage;
            } catch (e) {
                console.error('Failed to parse error response:', e);
            }
            alert(errorMessage);
        }
    } catch (error) {
        console.error('Failed to save permissions:', error);
        alert('Failed to save permissions. Please try again later.');
    }
}

// Delete role
async function deleteRole(roleId, roleName) {
    if (!confirm(`Are you sure you want to delete role "${roleName}"? This action cannot be undone.`)) {
        return;
    }
    
    try {
        const response = await fetch(`/admin/roles/api/${roleId}`, {
            method: 'DELETE'
        });
        
        if (response.ok) {
            loadRolesForManagement();
            alert('Role deleted successfully');
        } else {
            if (handleUnauthorized(response)) return;
            let errorMessage = 'Delete failed. Please try again later.';
            try {
                const error = await response.json();
                errorMessage = error.message || error.error || errorMessage;
            } catch (e) {
                console.error('Failed to parse error response:', e);
            }
            alert(errorMessage);
        }
    } catch (error) {
        console.error('Failed to delete role:', error);
        alert('Failed to delete the role. Please try again later.');
    }
}
