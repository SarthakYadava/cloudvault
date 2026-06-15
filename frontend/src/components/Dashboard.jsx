import {useCallback, useEffect, useRef, useState} from "react";
import {apiFetch, uploadWithProgress} from "../api";
import {Brand, Icon} from "../icons";
import ShareDialog from "./ShareDialog";

const PAGE_SIZE = 20;
const ALLOWED_TYPES = new Set(["application/pdf", "image/jpeg", "image/png", "text/plain"]);

export default function Dashboard({session, onLogout, notify}) {
    const {token, user} = session;
    const fileInput = useRef(null);
    const [files, setFiles] = useState([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [searchInput, setSearchInput] = useState("");
    const [query, setQuery] = useState("");
    const [sort, setSort] = useState("uploadedAt,desc");
    const [fileMessage, setFileMessage] = useState("");
    const [activity, setActivity] = useState([]);
    const [activityMessage, setActivityMessage] = useState("");
    const [upload, setUpload] = useState(null);
    const [dragging, setDragging] = useState(false);
    const [shareFile, setShareFile] = useState(null);
    const [menuOpen, setMenuOpen] = useState(false);

    const handleError = useCallback(error => {
        if (error.status === 401) {
            onLogout("Your session expired. Sign in again.");
            return true;
        }
        return false;
    }, [onLogout]);

    const loadFiles = useCallback(async () => {
        setFileMessage("");
        const [sortBy, direction] = sort.split(",");
        const params = new URLSearchParams({
            page,
            size: PAGE_SIZE,
            query,
            sort: sortBy,
            direction
        });
        try {
            const response = await apiFetch(token, `/api/files?${params}`);
            setFiles(response.content);
            setTotalPages(response.totalPages);
            setTotalElements(response.totalElements);
        } catch (error) {
            if (!handleError(error)) setFileMessage(error.message);
        }
    }, [handleError, page, query, sort, token]);

    const loadActivity = useCallback(async () => {
        setActivityMessage("");
        try {
            const response = await apiFetch(token, "/api/activity?page=0&size=12");
            setActivity(response.content);
        } catch (error) {
            if (!handleError(error)) setActivityMessage(error.message);
        }
    }, [handleError, token]);

    useEffect(() => {
        const timeout = window.setTimeout(() => {
            setPage(0);
            setQuery(searchInput.trim());
        }, 280);
        return () => window.clearTimeout(timeout);
    }, [searchInput]);

    useEffect(() => {
        loadFiles();
    }, [loadFiles]);

    useEffect(() => {
        loadActivity();
    }, [loadActivity]);

    function chooseFile() {
        fileInput.current?.click();
    }

    async function uploadFile(file) {
        const validationError = validateFile(file);
        if (validationError) {
            notify("File not accepted", validationError, true);
            return;
        }
        if (upload) {
            notify("Upload in progress", "Wait for the current upload to finish.", true);
            return;
        }

        setUpload({filename: file.name, percent: 0, status: "Preparing secure upload..."});
        try {
            updateUpload(8, "Preparing encrypted cloud storage...");
            await uploadThroughApi(file);
            updateUpload(100, "Upload complete");
            notify("Upload complete", `${file.name} is now available.`);
        } catch (error) {
            if (!handleError(error)) {
                notify("Upload failed", error.message, true);
            }
        } finally {
            setPage(0);
            await Promise.all([loadFiles(), loadActivity()]);
            window.setTimeout(() => setUpload(null), 700);
        }
    }

    function uploadThroughApi(file) {
        return uploadWithProgress({
            url: "/api/files",
            method: "POST",
            headers: {Authorization: `Bearer ${token}`},
            body: createMultipartBody(file),
            onProgress: ratio => updateUpload(
                Math.max(8, Math.round(ratio * 95)),
                "Uploading securely to private S3..."
            )
        });
    }

    function updateUpload(percent, status) {
        setUpload(current => current ? {...current, percent, status} : current);
    }

    async function downloadFile(file) {
        try {
            const response = await apiFetch(token, `/api/files/${file.id}/download-url`);
            notify("Download started", file.originalName);
            window.location.assign(response.downloadUrl);
            window.setTimeout(loadActivity, 500);
        } catch (error) {
            if (!handleError(error)) notify("Download failed", error.message, true);
        }
    }

    async function deleteFile(file) {
        if (!window.confirm(`Delete "${file.originalName}"? This cannot be undone.`)) return;
        try {
            await apiFetch(token, `/api/files/${file.id}`, {method: "DELETE"});
            notify("File deleted", file.originalName);
            if (files.length === 1 && page > 0) setPage(current => current - 1);
            else await loadFiles();
            await loadActivity();
        } catch (error) {
            if (!handleError(error)) notify("Delete failed", error.message, true);
        }
    }

    function scrollTo(id) {
        document.getElementById(id)?.scrollIntoView({behavior: "smooth", block: "start"});
        setMenuOpen(false);
    }

    const countLabel = `${totalElements} ${totalElements === 1 ? "document" : "documents"}${query ? ` matching "${query}"` : " in your vault"}`;

    return (
        <main className="app-shell">
            {menuOpen && <button className="mobile-overlay" aria-label="Close navigation" onClick={() => setMenuOpen(false)}/>}
            <aside className={`sidebar${menuOpen ? " open" : ""}`}>
                <Brand/>
                <nav className="side-nav" aria-label="Workspace navigation">
                    <button className="active" type="button" onClick={() => scrollTo("documents")}>
                        <Icon name="home"/> My files
                    </button>
                    <button type="button" onClick={() => scrollTo("activity")}>
                        <Icon name="clock"/> Activity
                    </button>
                    <button className="disabled" type="button" disabled>
                        <Icon name="trash"/> Trash <span>Soon</span>
                    </button>
                </nav>
                <div className="sidebar-security">
                    <Icon name="shield"/>
                    <div><strong>Private storage</strong><span>Public access blocked</span></div>
                </div>
                <div className="account-menu">
                    <div className="avatar">{initials(user.name)}</div>
                    <div>
                        <strong>{user.name}</strong>
                        <span>{user.email}</span>
                    </div>
                    <button className="icon-button" type="button" aria-label="Sign out" onClick={() => onLogout()}>
                        <Icon name="logout"/>
                    </button>
                </div>
            </aside>

            <section className="workspace">
                <header className="workspace-header">
                    <button className="icon-button menu-button" type="button" aria-label="Open navigation" onClick={() => setMenuOpen(true)}>
                        <Icon name="menu"/>
                    </button>
                    <div>
                        <p className="eyebrow">Document workspace</p>
                        <h1>My files</h1>
                    </div>
                    <button className="primary-button" type="button" onClick={chooseFile}>
                        <Icon name="upload"/> <span>Upload file</span>
                    </button>
                </header>

                <div className="workspace-content">
                    <input
                        ref={fileInput}
                        type="file"
                        hidden
                        accept=".pdf,.png,.jpg,.jpeg,.txt,application/pdf,image/png,image/jpeg,text/plain"
                        onChange={event => {
                            const file = event.target.files?.[0];
                            if (file) uploadFile(file);
                            event.target.value = "";
                        }}
                    />

                    <button
                        className={`upload-zone${dragging ? " dragging" : ""}`}
                        type="button"
                        onClick={chooseFile}
                        onDragEnter={event => { event.preventDefault(); setDragging(true); }}
                        onDragOver={event => event.preventDefault()}
                        onDragLeave={event => { event.preventDefault(); setDragging(false); }}
                        onDrop={event => {
                            event.preventDefault();
                            setDragging(false);
                            const file = event.dataTransfer.files?.[0];
                            if (file) uploadFile(file);
                        }}
                    >
                        <span className="upload-icon"><Icon name="upload"/></span>
                        <strong>Drop a file here</strong>
                        <span>or browse your computer</span>
                        <small>PDF, PNG, JPEG or TXT. Maximum 10 MB.</small>
                    </button>

                    {upload && (
                        <div className="upload-progress" aria-live="polite">
                            <span className="file-badge">UP</span>
                            <div>
                                <p><strong>{upload.filename}</strong><span>{upload.percent}%</span></p>
                                <div className="progress-track"><span style={{width: `${upload.percent}%`}}/></div>
                                <small>{upload.status}</small>
                            </div>
                        </div>
                    )}

                    <section id="documents" className="content-section">
                        <div className="section-heading files-heading">
                            <div>
                                <p className="eyebrow">Library</p>
                                <h2>Documents</h2>
                                <span>{countLabel}</span>
                            </div>
                            <div className="file-controls">
                                <label className="search-box">
                                    <Icon name="search"/>
                                    <input
                                        type="search"
                                        value={searchInput}
                                        onChange={event => setSearchInput(event.target.value)}
                                        placeholder="Search all files"
                                    />
                                </label>
                                <select value={sort} aria-label="Sort documents" onChange={event => {
                                    setSort(event.target.value);
                                    setPage(0);
                                }}>
                                    <option value="uploadedAt,desc">Newest first</option>
                                    <option value="uploadedAt,asc">Oldest first</option>
                                    <option value="name,asc">Name A-Z</option>
                                    <option value="name,desc">Name Z-A</option>
                                    <option value="size,desc">Largest first</option>
                                    <option value="size,asc">Smallest first</option>
                                </select>
                            </div>
                        </div>

                        {fileMessage && <div className="form-message" role="alert">{fileMessage}</div>}

                        {files.length === 0 ? (
                            <div className="empty-state">
                                <span className="empty-icon"><Icon name="upload"/></span>
                                <h3>{query ? "No documents found" : "Your vault is ready"}</h3>
                                <p>{query ? "Try a different filename." : "Upload your first document to see it here."}</p>
                                {!query && <button className="secondary-button" type="button" onClick={chooseFile}>Choose a file</button>}
                            </div>
                        ) : (
                            <div className="file-table-wrap">
                                <table className="file-table">
                                    <thead><tr><th>Name</th><th>Status</th><th>Size</th><th>Uploaded</th><th><span className="sr-only">Actions</span></th></tr></thead>
                                    <tbody>
                                    {files.map(file => (
                                        <tr key={file.id}>
                                            <td><div className="file-name"><span className="file-badge">{fileExtension(file.originalName)}</span><strong title={file.originalName}>{file.originalName}</strong></div></td>
                                            <td><span className={`status-pill ${file.status.toLowerCase()}`}>{file.status}</span></td>
                                            <td>{formatBytes(file.sizeBytes)}</td>
                                            <td>{formatDate(file.uploadedAt)}</td>
                                            <td className="actions-cell">
                                                <button className="table-action" type="button" aria-label={`Share ${file.originalName}`} disabled={file.status !== "AVAILABLE"} onClick={() => setShareFile(file)}><Icon name="share"/></button>
                                                <button className="table-action" type="button" aria-label={`Download ${file.originalName}`} disabled={file.status !== "AVAILABLE"} onClick={() => downloadFile(file)}><Icon name="download"/></button>
                                                <button className="table-action danger" type="button" aria-label={`Delete ${file.originalName}`} onClick={() => deleteFile(file)}><Icon name="trash"/></button>
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            </div>
                        )}

                        {totalPages > 1 && (
                            <div className="pagination">
                                <button className="secondary-button compact" type="button" disabled={page === 0} onClick={() => setPage(current => current - 1)}>Previous</button>
                                <span>Page {page + 1} of {totalPages}</span>
                                <button className="secondary-button compact" type="button" disabled={page >= totalPages - 1} onClick={() => setPage(current => current + 1)}>Next</button>
                            </div>
                        )}
                    </section>

                    <section id="activity" className="content-section activity-section">
                        <div className="section-heading">
                            <div>
                                <p className="eyebrow">Security history</p>
                                <h2>Recent activity</h2>
                                <span>A private record of important file actions.</span>
                            </div>
                            <button className="secondary-button compact" type="button" onClick={loadActivity}>Refresh</button>
                        </div>
                        {activityMessage && <div className="form-message" role="alert">{activityMessage}</div>}
                        {activity.length === 0 ? (
                            <p className="empty-note">Activity appears after you upload, download, share, or delete a file.</p>
                        ) : (
                            <div className="activity-list">
                                {activity.map(event => {
                                    const details = activityDetails(event.action);
                                    return (
                                        <article className="activity-item" key={event.id}>
                                            <span className="activity-icon">{details.code}</span>
                                            <div><strong>{event.filename || "Deleted file"}</strong><span>{details.label}</span></div>
                                            <time dateTime={event.occurredAt}>{formatDateTime(event.occurredAt)}</time>
                                        </article>
                                    );
                                })}
                            </div>
                        )}
                    </section>
                </div>
            </section>

            {shareFile && (
                <ShareDialog
                    file={shareFile}
                    token={token}
                    onClose={() => setShareFile(null)}
                    onChanged={loadActivity}
                    notify={notify}
                />
            )}
        </main>
    );
}

function createMultipartBody(file) {
    const body = new FormData();
    body.append("file", file);
    return body;
}

function validateFile(file) {
    if (!ALLOWED_TYPES.has(file.type)) return "Choose a PDF, PNG, JPEG, or text file.";
    if (file.size === 0) return "The selected file is empty.";
    if (file.size > 10 * 1024 * 1024) return "The selected file is larger than 10 MB.";
    return null;
}

function initials(name) {
    return name.split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]).join("").toUpperCase();
}

function fileExtension(filename) {
    return (filename.includes(".") ? filename.split(".").pop() : "FILE").slice(0, 4).toUpperCase();
}

function formatBytes(bytes) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDate(value) {
    return new Intl.DateTimeFormat(undefined, {month: "short", day: "numeric", year: "numeric"}).format(new Date(value));
}

function formatDateTime(value) {
    return new Intl.DateTimeFormat(undefined, {month: "short", day: "numeric", hour: "numeric", minute: "2-digit"}).format(new Date(value));
}

function activityDetails(action) {
    return {
        FILE_UPLOADED: {code: "UP", label: "Uploaded to private storage"},
        DOWNLOAD_LINK_CREATED: {code: "DL", label: "Secure download requested"},
        FILE_DELETED: {code: "DEL", label: "Removed from the vault"},
        SHARE_LINK_CREATED: {code: "SH", label: "Expiring share link created"},
        SHARE_LINK_REVOKED: {code: "RV", label: "Share link revoked"},
        SHARED_FILE_ACCESSED: {code: "AC", label: "Shared link accessed"}
    }[action] || {code: "EV", label: action.replaceAll("_", " ").toLowerCase()};
}
