const state = {
    mode: "login",
    token: sessionStorage.getItem("cloudvault.token"),
    user: readSessionUser(),
    files: [],
    page: 0,
    totalPages: 0,
    pageSize: 20,
    uploadInProgress: false,
    query: "",
    sort: "uploadedAt",
    direction: "desc",
    searchTimer: null,
    activeShareFile: null
};

const elements = {
    authView: document.querySelector("#auth-view"),
    appView: document.querySelector("#app-view"),
    authForm: document.querySelector("#auth-form"),
    authTitle: document.querySelector("#auth-title"),
    authSubtitle: document.querySelector("#auth-subtitle"),
    authSubmit: document.querySelector("#auth-submit"),
    authMessage: document.querySelector("#auth-message"),
    loginTab: document.querySelector("#login-tab"),
    registerTab: document.querySelector("#register-tab"),
    nameField: document.querySelector("#name-field"),
    name: document.querySelector("#name"),
    email: document.querySelector("#email"),
    password: document.querySelector("#password"),
    passwordHint: document.querySelector("#password-hint"),
    togglePassword: document.querySelector("#toggle-password"),
    fileInput: document.querySelector("#file-input"),
    uploadZone: document.querySelector("#upload-zone"),
    headerUploadButton: document.querySelector("#header-upload-button"),
    emptyUploadButton: document.querySelector("#empty-upload-button"),
    uploadProgress: document.querySelector("#upload-progress"),
    progressFilename: document.querySelector("#progress-filename"),
    progressPercent: document.querySelector("#progress-percent"),
    progressBar: document.querySelector("#progress-bar"),
    progressStatus: document.querySelector("#progress-status"),
    fileList: document.querySelector("#file-list"),
    fileCount: document.querySelector("#file-count"),
    fileSearch: document.querySelector("#file-search"),
    fileSort: document.querySelector("#file-sort"),
    fileMessage: document.querySelector("#file-message"),
    emptyState: document.querySelector("#empty-state"),
    pagination: document.querySelector("#pagination"),
    previousPage: document.querySelector("#previous-page"),
    nextPage: document.querySelector("#next-page"),
    pageLabel: document.querySelector("#page-label"),
    userName: document.querySelector("#user-name"),
    userEmail: document.querySelector("#user-email"),
    userAvatar: document.querySelector("#user-avatar"),
    logoutButton: document.querySelector("#logout-button"),
    menuButton: document.querySelector("#menu-button"),
    sidebar: document.querySelector(".sidebar"),
    filesNav: document.querySelector("#files-nav"),
    activityNav: document.querySelector("#activity-nav"),
    activitySection: document.querySelector("#activity-section"),
    activityList: document.querySelector("#activity-list"),
    activityEmpty: document.querySelector("#activity-empty"),
    activityMessage: document.querySelector("#activity-message"),
    refreshActivity: document.querySelector("#refresh-activity"),
    shareDialog: document.querySelector("#share-dialog"),
    shareDialogTitle: document.querySelector("#share-dialog-title"),
    closeShareDialog: document.querySelector("#close-share-dialog"),
    shareForm: document.querySelector("#share-form"),
    shareDuration: document.querySelector("#share-duration"),
    createShareLink: document.querySelector("#create-share-link"),
    shareMessage: document.querySelector("#share-message"),
    newShareResult: document.querySelector("#new-share-result"),
    newShareUrl: document.querySelector("#new-share-url"),
    copyShareUrl: document.querySelector("#copy-share-url"),
    shareLinkList: document.querySelector("#share-link-list"),
    shareLinkEmpty: document.querySelector("#share-link-empty"),
    toastRegion: document.querySelector("#toast-region")
};

initialize();

function initialize() {
    bindEvents();
    if (state.token && state.user) {
        showWorkspace();
        Promise.all([loadFiles(), loadActivity()]);
    } else {
        clearSession();
        showAuth();
    }
}

function bindEvents() {
    elements.loginTab.addEventListener("click", () => setAuthMode("login"));
    elements.registerTab.addEventListener("click", () => setAuthMode("register"));
    elements.authForm.addEventListener("submit", handleAuth);
    elements.togglePassword.addEventListener("click", togglePasswordVisibility);
    elements.logoutButton.addEventListener("click", logout);
    elements.menuButton.addEventListener("click", () => elements.sidebar.classList.toggle("open"));
    elements.fileSearch.addEventListener("input", handleFileSearch);
    elements.fileSort.addEventListener("change", handleFileSort);
    elements.previousPage.addEventListener("click", () => changePage(state.page - 1));
    elements.nextPage.addEventListener("click", () => changePage(state.page + 1));
    elements.filesNav.addEventListener("click", () => scrollToSection(document.querySelector(".files-section")));
    elements.activityNav.addEventListener("click", () => scrollToSection(elements.activitySection));
    elements.refreshActivity.addEventListener("click", loadActivity);
    elements.closeShareDialog.addEventListener("click", () => elements.shareDialog.close());
    elements.shareForm.addEventListener("submit", createShareLink);
    elements.copyShareUrl.addEventListener("click", copyNewShareUrl);
    elements.shareDialog.addEventListener("click", event => {
        if (event.target === elements.shareDialog) {
            elements.shareDialog.close();
        }
    });

    [elements.headerUploadButton, elements.emptyUploadButton].forEach(button => {
        button.addEventListener("click", () => elements.fileInput.click());
    });

    elements.uploadZone.addEventListener("click", () => elements.fileInput.click());
    elements.uploadZone.addEventListener("keydown", event => {
        if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            elements.fileInput.click();
        }
    });
    elements.fileInput.addEventListener("change", () => {
        const [file] = elements.fileInput.files;
        if (file) {
            uploadFile(file);
        }
        elements.fileInput.value = "";
    });

    ["dragenter", "dragover"].forEach(type => {
        elements.uploadZone.addEventListener(type, event => {
            event.preventDefault();
            elements.uploadZone.classList.add("dragging");
        });
    });
    ["dragleave", "drop"].forEach(type => {
        elements.uploadZone.addEventListener(type, event => {
            event.preventDefault();
            elements.uploadZone.classList.remove("dragging");
        });
    });
    elements.uploadZone.addEventListener("drop", event => {
        const [file] = event.dataTransfer.files;
        if (file) {
            uploadFile(file);
        }
    });
}

function setAuthMode(mode) {
    state.mode = mode;
    const registering = mode === "register";
    elements.loginTab.classList.toggle("active", !registering);
    elements.loginTab.setAttribute("aria-selected", String(!registering));
    elements.registerTab.classList.toggle("active", registering);
    elements.registerTab.setAttribute("aria-selected", String(registering));
    elements.nameField.classList.toggle("hidden", !registering);
    elements.name.required = registering;
    elements.password.autocomplete = registering ? "new-password" : "current-password";
    elements.authTitle.textContent = registering ? "Create your vault" : "Sign in to your vault";
    elements.authSubtitle.textContent = registering
        ? "Set up a private workspace in less than a minute."
        : "Use your CloudVault account to continue.";
    elements.authSubmit.querySelector("span").textContent = registering
        ? "Create secure account"
        : "Sign in securely";
    elements.passwordHint.textContent = registering ? "8 to 72 characters" : "Secure account access";
    hideMessage(elements.authMessage);
}

async function handleAuth(event) {
    event.preventDefault();
    hideMessage(elements.authMessage);

    const payload = {
        email: elements.email.value.trim(),
        password: elements.password.value
    };
    if (state.mode === "register") {
        payload.name = elements.name.value.trim();
    }

    if (!payload.email || !payload.password || (state.mode === "register" && !payload.name)) {
        showMessage(elements.authMessage, "Complete all required fields.");
        return;
    }

    setButtonBusy(elements.authSubmit, true);
    try {
        const response = await fetch(`/api/auth/${state.mode === "register" ? "register" : "login"}`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(payload)
        });
        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.message || "Authentication failed.");
        }

        state.token = data.accessToken;
        state.user = data.user;
        sessionStorage.setItem("cloudvault.token", state.token);
        sessionStorage.setItem("cloudvault.user", JSON.stringify(state.user));
        elements.authForm.reset();
        showWorkspace();
        await Promise.all([loadFiles(), loadActivity()]);
        toast(state.mode === "register" ? "Account created" : "Welcome back", `Signed in as ${state.user.email}.`);
    } catch (error) {
        showMessage(elements.authMessage, error.message);
    } finally {
        setButtonBusy(elements.authSubmit, false);
    }
}

function togglePasswordVisibility() {
    const visible = elements.password.type === "text";
    elements.password.type = visible ? "password" : "text";
    elements.togglePassword.setAttribute("aria-label", visible ? "Show password" : "Hide password");
    elements.togglePassword.querySelector(".eye-open").classList.toggle("hidden", !visible);
    elements.togglePassword.querySelector(".eye-closed").classList.toggle("hidden", visible);
}

function showAuth() {
    elements.appView.classList.add("hidden");
    elements.authView.classList.remove("hidden");
}

function showWorkspace() {
    elements.authView.classList.add("hidden");
    elements.appView.classList.remove("hidden");
    elements.userName.textContent = state.user.name;
    elements.userEmail.textContent = state.user.email;
    elements.userAvatar.textContent = initials(state.user.name);
}

function logout() {
    clearSession();
    state.files = [];
    renderFiles();
    elements.activityList.replaceChildren();
    showAuth();
    setAuthMode("login");
    toast("Signed out", "Your browser session has been cleared.");
}

async function loadFiles() {
    hideMessage(elements.fileMessage);
    elements.fileCount.textContent = "Loading your files...";
    try {
        const params = new URLSearchParams({
            page: state.page,
            size: state.pageSize,
            query: state.query,
            sort: state.sort,
            direction: state.direction
        });
        const page = await apiFetch(`/api/files?${params}`);
        state.files = page.content;
        state.totalPages = page.totalPages;
        const suffix = state.query ? ` matching "${state.query}"` : " in your vault";
        elements.fileCount.textContent = `${page.totalElements} ${page.totalElements === 1 ? "document" : "documents"}${suffix}`;
        renderFiles();
        renderPagination();
    } catch (error) {
        showMessage(elements.fileMessage, error.message);
        elements.fileCount.textContent = "Could not load files";
    }
}

function renderFiles() {
    elements.fileList.replaceChildren(...state.files.map(createFileRow));
    elements.emptyState.classList.toggle("hidden", state.files.length > 0);
    document.querySelector(".file-table").classList.toggle("hidden", state.files.length === 0);
}

function createFileRow(file) {
    const row = document.createElement("tr");
    const extension = fileExtension(file.originalName);
    row.innerHTML = `
        <td>
            <div class="file-name-cell">
                <span class="file-badge">${escapeHtml(extension)}</span>
                <strong title="${escapeHtml(file.originalName)}">${escapeHtml(file.originalName)}</strong>
            </div>
        </td>
        <td><span class="status-pill ${file.status === "PENDING" ? "pending" : ""}">${escapeHtml(file.status)}</span></td>
        <td>${formatBytes(file.sizeBytes)}</td>
        <td>${formatDate(file.uploadedAt)}</td>
        <td class="actions-cell">
            <button class="table-action share-action" type="button" aria-label="Share ${escapeHtml(file.originalName)}"
                    ${file.status !== "AVAILABLE" ? "disabled" : ""}>
                <svg viewBox="0 0 24 24"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="M8.6 10.5l6.8-4M8.6 13.5l6.8 4"/></svg>
            </button>
            <button class="table-action download-action" type="button" aria-label="Download ${escapeHtml(file.originalName)}"
                    ${file.status !== "AVAILABLE" ? "disabled" : ""}>
                <svg viewBox="0 0 24 24"><path d="M12 4v12M7 11l5 5 5-5M5 20h14"/></svg>
            </button>
            <button class="table-action danger delete-action" type="button" aria-label="Delete ${escapeHtml(file.originalName)}">
                <svg viewBox="0 0 24 24"><path d="M3 6h18M8 6V4h8v2M6 6l1 15h10l1-15M10 10v7M14 10v7"/></svg>
            </button>
        </td>
    `;
    row.querySelector(".share-action").addEventListener("click", () => openShareDialog(file));
    row.querySelector(".download-action").addEventListener("click", () => downloadFile(file));
    row.querySelector(".delete-action").addEventListener("click", () => deleteFile(file));
    return row;
}

function handleFileSearch() {
    window.clearTimeout(state.searchTimer);
    state.searchTimer = window.setTimeout(() => {
        state.query = elements.fileSearch.value.trim();
        state.page = 0;
        loadFiles();
    }, 280);
}

function handleFileSort() {
    [state.sort, state.direction] = elements.fileSort.value.split(",");
    state.page = 0;
    loadFiles();
}

function renderPagination() {
    elements.pagination.classList.toggle("hidden", state.totalPages <= 1);
    elements.previousPage.disabled = state.page === 0;
    elements.nextPage.disabled = state.page >= state.totalPages - 1;
    elements.pageLabel.textContent = `Page ${state.page + 1} of ${Math.max(state.totalPages, 1)}`;
}

async function changePage(page) {
    if (page < 0 || page >= state.totalPages || page === state.page) {
        return;
    }
    state.page = page;
    await loadFiles();
}

async function loadActivity() {
    hideMessage(elements.activityMessage);
    try {
        const page = await apiFetch("/api/activity?page=0&size=12");
        elements.activityList.replaceChildren(...page.content.map(createActivityItem));
        elements.activityList.classList.toggle("hidden", page.content.length === 0);
        elements.activityEmpty.classList.toggle("hidden", page.content.length > 0);
    } catch (error) {
        showMessage(elements.activityMessage, error.message);
    }
}

function createActivityItem(event) {
    const item = document.createElement("article");
    item.className = "activity-item";
    const details = activityDetails(event.action);
    item.innerHTML = `
        <span class="activity-icon">${details.code}</span>
        <div class="activity-copy">
            <strong>${escapeHtml(event.filename || "Deleted file")}</strong>
            <span>${details.label}</span>
        </div>
        <time datetime="${escapeHtml(event.occurredAt)}">${formatDateTime(event.occurredAt)}</time>
    `;
    return item;
}

function activityDetails(action) {
    const details = {
        FILE_UPLOADED: {code: "UP", label: "Uploaded to private storage"},
        DOWNLOAD_LINK_CREATED: {code: "DL", label: "Secure download requested"},
        FILE_DELETED: {code: "DEL", label: "Removed from the vault"},
        SHARE_LINK_CREATED: {code: "SH", label: "Expiring share link created"},
        SHARE_LINK_REVOKED: {code: "RV", label: "Share link revoked"},
        SHARED_FILE_ACCESSED: {code: "AC", label: "Shared link accessed"}
    };
    return details[action] || {code: "EV", label: action.replaceAll("_", " ").toLowerCase()};
}

async function openShareDialog(file) {
    state.activeShareFile = file;
    elements.shareDialogTitle.textContent = `Share ${file.originalName}`;
    elements.newShareResult.classList.add("hidden");
    elements.newShareUrl.value = "";
    hideMessage(elements.shareMessage);
    elements.shareDialog.showModal();
    await loadShareLinks(file.id);
}

async function loadShareLinks(fileId) {
    try {
        const links = await apiFetch(`/api/files/${fileId}/shares`);
        elements.shareLinkList.replaceChildren(...links.map(createShareLinkItem));
        elements.shareLinkEmpty.classList.toggle("hidden", links.length > 0);
    } catch (error) {
        showMessage(elements.shareMessage, error.message);
    }
}

function createShareLinkItem(link) {
    const item = document.createElement("div");
    item.className = `share-link-item${link.active ? "" : " inactive"}`;
    const status = link.active ? `Expires ${formatDateTime(link.expiresAt)}` : "Expired or revoked";
    item.innerHTML = `
        <div>
            <strong>${link.active ? "Active link" : "Inactive link"}</strong>
            <span>${escapeHtml(status)} | Created ${escapeHtml(formatDateTime(link.createdAt))}</span>
        </div>
        ${link.active ? `<button class="secondary-button compact revoke-share-link" type="button">Revoke</button>` : ""}
    `;
    const revokeButton = item.querySelector(".revoke-share-link");
    if (revokeButton) {
        revokeButton.addEventListener("click", () => revokeShareLink(link.id));
    }
    return item;
}

async function createShareLink(event) {
    event.preventDefault();
    if (!state.activeShareFile) {
        return;
    }
    hideMessage(elements.shareMessage);
    setShareButtonBusy(true);
    try {
        const link = await apiFetch(`/api/files/${state.activeShareFile.id}/shares`, {
            method: "POST",
            body: JSON.stringify({expirationMinutes: Number(elements.shareDuration.value)})
        });
        elements.newShareUrl.value = link.shareUrl;
        elements.newShareResult.classList.remove("hidden");
        await Promise.all([loadShareLinks(state.activeShareFile.id), loadActivity()]);
        toast("Share link created", `Expires ${formatDateTime(link.expiresAt)}.`);
    } catch (error) {
        showMessage(elements.shareMessage, error.message);
    } finally {
        setShareButtonBusy(false);
    }
}

async function revokeShareLink(linkId) {
    try {
        await apiFetch(`/api/shares/${linkId}`, {method: "DELETE"});
        elements.newShareResult.classList.add("hidden");
        await Promise.all([loadShareLinks(state.activeShareFile.id), loadActivity()]);
        toast("Share link revoked", "The link can no longer be used.");
    } catch (error) {
        showMessage(elements.shareMessage, error.message);
    }
}

async function copyNewShareUrl() {
    try {
        await navigator.clipboard.writeText(elements.newShareUrl.value);
        toast("Link copied", "The expiring URL is ready to share.");
    } catch {
        elements.newShareUrl.select();
        document.execCommand("copy");
        toast("Link copied", "The expiring URL is ready to share.");
    }
}

function setShareButtonBusy(busy) {
    elements.createShareLink.disabled = busy;
    elements.createShareLink.querySelector("span").textContent = busy ? "Creating link..." : "Create secure link";
}

function scrollToSection(section) {
    section.scrollIntoView({behavior: "smooth", block: "start"});
    elements.sidebar.classList.remove("open");
}

async function uploadFile(file) {
    if (state.uploadInProgress) {
        toast("Upload in progress", "Wait for the current upload to finish.", true);
        return;
    }
    const validationError = validateFile(file);
    if (validationError) {
        toast("File not accepted", validationError, true);
        return;
    }

    state.uploadInProgress = true;
    showProgress(file.name, 0, "Preparing secure upload...");
    let reservationId = null;

    try {
        const reservation = await apiFetch("/api/files/upload-requests", {
            method: "POST",
            body: JSON.stringify({
                filename: file.name,
                contentType: file.type,
                sizeBytes: file.size
            })
        });
        reservationId = reservation.file.id;
        await uploadToS3(reservation, file);
        updateProgress(96, "Verifying upload with S3...");
        await apiFetch(`/api/files/${reservationId}/complete`, {method: "POST"});
        updateProgress(100, "Upload complete");
        toast("Upload complete", `${file.name} is now available.`);
    } catch (directError) {
        if (reservationId) {
            await apiFetch(`/api/files/${reservationId}`, {method: "DELETE"}).catch(() => {});
        }
        try {
            updateProgress(12, "Using secure server upload...");
            await uploadThroughApi(file);
            updateProgress(100, "Upload complete");
            toast("Upload complete", `${file.name} is now available.`);
        } catch (fallbackError) {
            toast("Upload failed", fallbackError.message || directError.message, true);
        }
    } finally {
        window.setTimeout(() => elements.uploadProgress.classList.add("hidden"), 850);
        state.uploadInProgress = false;
        state.page = 0;
        await Promise.all([loadFiles(), loadActivity()]);
    }
}

function uploadToS3(reservation, file) {
    return new Promise((resolve, reject) => {
        const request = new XMLHttpRequest();
        request.open(reservation.method, reservation.uploadUrl);
        Object.entries(reservation.requiredHeaders || {}).forEach(([name, value]) => {
            request.setRequestHeader(name, value);
        });
        request.upload.addEventListener("progress", event => {
            if (event.lengthComputable) {
                updateProgress(Math.min(90, Math.round((event.loaded / event.total) * 90)), "Uploading directly to private S3...");
            }
        });
        request.addEventListener("load", () => {
            if (request.status >= 200 && request.status < 300) {
                resolve();
            } else {
                reject(new Error(`Direct upload failed with status ${request.status}.`));
            }
        });
        request.addEventListener("error", () => reject(new Error("The browser could not reach the S3 upload URL.")));
        request.send(file);
    });
}

function uploadThroughApi(file) {
    return new Promise((resolve, reject) => {
        const request = new XMLHttpRequest();
        request.open("POST", "/api/files");
        request.setRequestHeader("Authorization", `Bearer ${state.token}`);
        request.upload.addEventListener("progress", event => {
            if (event.lengthComputable) {
                updateProgress(Math.round((event.loaded / event.total) * 92), "Uploading through CloudVault...");
            }
        });
        request.addEventListener("load", () => {
            if (request.status >= 200 && request.status < 300) {
                resolve();
                return;
            }
            try {
                reject(new Error(JSON.parse(request.responseText).message));
            } catch {
                reject(new Error("The upload could not be completed."));
            }
        });
        request.addEventListener("error", () => reject(new Error("The upload connection failed.")));
        const body = new FormData();
        body.append("file", file);
        request.send(body);
    });
}

async function downloadFile(file) {
    try {
        const response = await apiFetch(`/api/files/${file.id}/download-url`);
        window.location.assign(response.downloadUrl);
        toast("Download started", file.originalName);
        await loadActivity();
    } catch (error) {
        toast("Download failed", error.message, true);
    }
}

async function deleteFile(file) {
    if (!window.confirm(`Delete "${file.originalName}"? This cannot be undone.`)) {
        return;
    }
    try {
        await apiFetch(`/api/files/${file.id}`, {method: "DELETE"});
        toast("File deleted", file.originalName);
        await Promise.all([loadFiles(), loadActivity()]);
    } catch (error) {
        toast("Delete failed", error.message, true);
    }
}

async function apiFetch(url, options = {}) {
    const headers = new Headers(options.headers || {});
    headers.set("Authorization", `Bearer ${state.token}`);
    if (options.body && !(options.body instanceof FormData)) {
        headers.set("Content-Type", "application/json");
    }
    const response = await fetch(url, {...options, headers});
    if (response.status === 401) {
        clearSession();
        showAuth();
        throw new Error("Your session expired. Sign in again.");
    }
    if (response.status === 204) {
        return null;
    }
    const data = await response.json();
    if (!response.ok) {
        throw new Error(data.message || "The request could not be completed.");
    }
    return data;
}

function validateFile(file) {
    const allowed = new Set(["application/pdf", "image/jpeg", "image/png", "text/plain"]);
    if (!allowed.has(file.type)) {
        return "Choose a PDF, PNG, JPEG, or text file.";
    }
    if (file.size === 0) {
        return "The selected file is empty.";
    }
    if (file.size > 10 * 1024 * 1024) {
        return "The selected file is larger than 10 MB.";
    }
    return null;
}

function showProgress(filename, percent, status) {
    elements.uploadProgress.classList.remove("hidden");
    elements.progressFilename.textContent = filename;
    updateProgress(percent, status);
}

function updateProgress(percent, status) {
    elements.progressPercent.textContent = `${percent}%`;
    elements.progressBar.style.width = `${percent}%`;
    elements.progressStatus.textContent = status;
}

function toast(title, message, error = false) {
    const item = document.createElement("div");
    item.className = `toast${error ? " error" : ""}`;
    item.innerHTML = `<div><strong>${escapeHtml(title)}</strong><span>${escapeHtml(message)}</span></div>`;
    elements.toastRegion.append(item);
    window.setTimeout(() => item.remove(), 4200);
}

function showMessage(element, message, success = false) {
    element.textContent = message;
    element.classList.toggle("success", success);
    element.classList.remove("hidden");
}

function hideMessage(element) {
    element.classList.add("hidden");
    element.classList.remove("success");
    element.textContent = "";
}

function setButtonBusy(button, busy) {
    button.disabled = busy;
    button.querySelector("span").textContent = busy
        ? (state.mode === "register" ? "Creating account..." : "Signing in...")
        : (state.mode === "register" ? "Create secure account" : "Sign in securely");
}

function readSessionUser() {
    try {
        return JSON.parse(sessionStorage.getItem("cloudvault.user"));
    } catch {
        return null;
    }
}

function clearSession() {
    state.token = null;
    state.user = null;
    sessionStorage.removeItem("cloudvault.token");
    sessionStorage.removeItem("cloudvault.user");
}

function initials(name) {
    return name.split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]).join("").toUpperCase();
}

function fileExtension(filename) {
    const extension = filename.includes(".") ? filename.split(".").pop() : "FILE";
    return extension.slice(0, 4).toUpperCase();
}

function formatBytes(bytes) {
    if (bytes < 1024) {
        return `${bytes} B`;
    }
    if (bytes < 1024 * 1024) {
        return `${(bytes / 1024).toFixed(1)} KB`;
    }
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDate(value) {
    return new Intl.DateTimeFormat(undefined, {
        month: "short",
        day: "numeric",
        year: "numeric"
    }).format(new Date(value));
}

function formatDateTime(value) {
    return new Intl.DateTimeFormat(undefined, {
        month: "short",
        day: "numeric",
        hour: "numeric",
        minute: "2-digit"
    }).format(new Date(value));
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}
