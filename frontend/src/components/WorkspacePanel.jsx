import {useCallback, useEffect, useMemo, useState} from "react";
import {apiFetch} from "../api";
import {Icon} from "../icons";

export default function WorkspacePanel({token, user, notify, handleError}) {
    const [workspaces, setWorkspaces] = useState([]);
    const [selectedId, setSelectedId] = useState(null);
    const [members, setMembers] = useState([]);
    const [invitations, setInvitations] = useState([]);
    const [requests, setRequests] = useState([]);
    const [message, setMessage] = useState("");
    const [creatingWorkspace, setCreatingWorkspace] = useState(false);
    const [workspaceName, setWorkspaceName] = useState("");
    const [inviteEmail, setInviteEmail] = useState("");
    const [inviteRole, setInviteRole] = useState("CLIENT");
    const [latestInviteUrl, setLatestInviteUrl] = useState("");
    const [requestForm, setRequestForm] = useState(emptyRequest());
    const [busy, setBusy] = useState(false);
    const [uploadingRequestId, setUploadingRequestId] = useState(null);

    const selected = useMemo(
        () => workspaces.find(workspace => workspace.id === selectedId) || null,
        [selectedId, workspaces]
    );
    const canManage = selected && selected.role !== "CLIENT";
    const isOwner = selected?.role === "OWNER";

    const loadWorkspaces = useCallback(async preferredId => {
        try {
            const response = await apiFetch(token, "/api/workspaces");
            setWorkspaces(response);
            setSelectedId(current => {
                if (preferredId && response.some(item => item.id === preferredId)) return preferredId;
                if (current && response.some(item => item.id === current)) return current;
                return response[0]?.id || null;
            });
        } catch (error) {
            if (!handleError(error)) setMessage(error.message);
        }
    }, [handleError, token]);

    const loadWorkspaceDetails = useCallback(async (workspaceId, role) => {
        if (!workspaceId) {
            setMembers([]);
            setInvitations([]);
            setRequests([]);
            return;
        }
        setMessage("");
        try {
            const [memberResponse, requestResponse, invitationResponse] = await Promise.all([
                apiFetch(token, `/api/workspaces/${workspaceId}/members`),
                apiFetch(token, `/api/workspaces/${workspaceId}/requests`),
                role === "OWNER"
                    ? apiFetch(token, `/api/workspaces/${workspaceId}/invitations`)
                    : Promise.resolve([])
            ]);
            setMembers(memberResponse);
            setRequests(requestResponse);
            setInvitations(invitationResponse);
        } catch (error) {
            if (!handleError(error)) setMessage(error.message);
        }
    }, [handleError, token]);

    useEffect(() => {
        loadWorkspaces();
    }, [loadWorkspaces]);

    useEffect(() => {
        setLatestInviteUrl("");
        loadWorkspaceDetails(selectedId, selected?.role);
    }, [loadWorkspaceDetails, selected?.role, selectedId]);

    async function createWorkspace(event) {
        event.preventDefault();
        if (!workspaceName.trim()) return;
        setBusy(true);
        try {
            const workspace = await apiFetch(token, "/api/workspaces", {
                method: "POST",
                body: JSON.stringify({name: workspaceName.trim()})
            });
            setWorkspaceName("");
            setCreatingWorkspace(false);
            await loadWorkspaces(workspace.id);
            notify("Workspace created", `${workspace.name} is ready for client access.`);
        } catch (error) {
            if (!handleError(error)) notify("Workspace not created", error.message, true);
        } finally {
            setBusy(false);
        }
    }

    async function createInvitation(event) {
        event.preventDefault();
        if (!selected || !inviteEmail.trim()) return;
        setBusy(true);
        try {
            const invitation = await apiFetch(token, `/api/workspaces/${selected.id}/invitations`, {
                method: "POST",
                body: JSON.stringify({
                    email: inviteEmail.trim(),
                    role: inviteRole
                })
            });
            const invitedEmail = inviteEmail.trim();
            setInviteEmail("");
            setLatestInviteUrl(invitation.acceptanceUrl || "");
            await Promise.all([
                loadWorkspaceDetails(selected.id, selected.role),
                loadWorkspaces(selected.id)
            ]);
            notify(
                "Invitation created",
                invitation.deliveryMode === "SMTP"
                    ? `An email was sent to ${invitedEmail}.`
                    : `The invitation for ${invitedEmail} is ready to copy.`
            );
        } catch (error) {
            if (!handleError(error)) notify("Invitation not created", error.message, true);
        } finally {
            setBusy(false);
        }
    }

    async function deleteWorkspace() {
        if (!selected) return;
        const confirmed = window.confirm(
            `Delete "${selected.name}"? Its members and document requests will be permanently removed. Personal files will not be deleted.`
        );
        if (!confirmed) return;

        setBusy(true);
        try {
            await apiFetch(token, `/api/workspaces/${selected.id}`, {
                method: "DELETE"
            });
            setMembers([]);
            setRequests([]);
            setSelectedId(null);
            await loadWorkspaces();
            notify("Workspace deleted", `${selected.name} was permanently removed.`);
        } catch (error) {
            if (!handleError(error)) notify("Workspace not deleted", error.message, true);
        } finally {
            setBusy(false);
        }
    }

    async function createRequest(event) {
        event.preventDefault();
        if (!selected || !requestForm.title.trim()) return;
        setBusy(true);
        try {
            await apiFetch(token, `/api/workspaces/${selected.id}/requests`, {
                method: "POST",
                body: JSON.stringify({
                    title: requestForm.title.trim(),
                    description: requestForm.description.trim() || null,
                    assigneeEmail: requestForm.assigneeEmail || null,
                    dueDate: requestForm.dueDate || null
                })
            });
            setRequestForm(emptyRequest());
            await Promise.all([
                loadWorkspaceDetails(selected.id, selected.role),
                loadWorkspaces(selected.id)
            ]);
            notify("Document requested", `${requestForm.title.trim()} was added to ${selected.name}.`);
        } catch (error) {
            if (!handleError(error)) notify("Request not created", error.message, true);
        } finally {
            setBusy(false);
        }
    }

    async function changeStatus(request, status) {
        setBusy(true);
        try {
            await apiFetch(
                token,
                `/api/workspaces/${selected.id}/requests/${request.id}`,
                {
                    method: "PATCH",
                    body: JSON.stringify({status})
                }
            );
            await Promise.all([
                loadWorkspaceDetails(selected.id, selected.role),
                loadWorkspaces(selected.id)
            ]);
            notify("Request updated", `${request.title} is now ${status.toLowerCase()}.`);
        } catch (error) {
            if (!handleError(error)) notify("Request not updated", error.message, true);
        } finally {
            setBusy(false);
        }
    }

    async function uploadSubmission(request, file) {
        if (!file) return;
        const validationError = validateSubmission(file);
        if (validationError) {
            notify("File not accepted", validationError, true);
            return;
        }

        setBusy(true);
        setUploadingRequestId(request.id);
        try {
            const body = new FormData();
            body.append("file", file);
            await apiFetch(
                token,
                `/api/workspaces/${selected.id}/requests/${request.id}/submission`,
                {method: "POST", body}
            );
            await Promise.all([
                loadWorkspaceDetails(selected.id, selected.role),
                loadWorkspaces(selected.id)
            ]);
            notify("Document submitted", `${file.name} is ready for review.`);
        } catch (error) {
            if (!handleError(error)) notify("Submission failed", error.message, true);
        } finally {
            setUploadingRequestId(null);
            setBusy(false);
        }
    }

    async function downloadSubmission(request) {
        try {
            const response = await apiFetch(
                token,
                `/api/workspaces/${selected.id}/requests/${request.id}/submission/download-url`
            );
            notify("Download started", request.submittedFileName);
            window.location.assign(response.downloadUrl);
        } catch (error) {
            if (!handleError(error)) notify("Download failed", error.message, true);
        }
    }

    return (
        <section id="workspaces" className="content-section workspace-panel">
            <div className="section-heading workspace-panel-heading">
                <div>
                    <p className="eyebrow">Client exchange</p>
                    <h2>Client workspaces</h2>
                    <span>Organize people, deadlines, and requested documents.</span>
                </div>
                <button className="secondary-button compact" type="button" onClick={() => setCreatingWorkspace(current => !current)}>
                    <Icon name="plus"/> New workspace
                </button>
            </div>

            {creatingWorkspace && (
                <form className="inline-workspace-form" onSubmit={createWorkspace}>
                    <label>
                        <span>Workspace name</span>
                        <input value={workspaceName} maxLength="120" placeholder="Acme Legal" onChange={event => setWorkspaceName(event.target.value)}/>
                    </label>
                    <button className="primary-button compact" type="submit" disabled={busy || !workspaceName.trim()}>Create</button>
                </form>
            )}

            {message && <div className="form-message" role="alert">{message}</div>}

            {workspaces.length === 0 ? (
                <div className="workspace-empty">
                    <span><Icon name="users"/></span>
                    <div>
                        <strong>Create your first client workspace</strong>
                        <p>Separate each client's requests and control who can access them.</p>
                    </div>
                </div>
            ) : (
                <>
                    <div className="workspace-tabs" role="tablist" aria-label="Client workspaces">
                        {workspaces.map(workspace => (
                            <button
                                className={workspace.id === selectedId ? "active" : ""}
                                type="button"
                                role="tab"
                                aria-selected={workspace.id === selectedId}
                                key={workspace.id}
                                onClick={() => setSelectedId(workspace.id)}
                            >
                                <span className="workspace-monogram">{initials(workspace.name)}</span>
                                <span><strong>{workspace.name}</strong><small>{workspace.memberCount} members / {workspace.pendingRequestCount} pending</small></span>
                                <em>{workspace.role}</em>
                            </button>
                        ))}
                    </div>

                    {selected && (
                        <div className="workspace-detail">
                            <div className="workspace-summary">
                                <div>
                                    <p className="eyebrow">Selected workspace</p>
                                    <h3>{selected.name}</h3>
                                </div>
                                <div className="workspace-summary-actions">
                                    <span className={`role-pill ${selected.role.toLowerCase()}`}>{selected.role}</span>
                                    {isOwner && (
                                        <button className="danger-button compact" type="button" disabled={busy} onClick={deleteWorkspace}>
                                            <Icon name="trash"/> Delete workspace
                                        </button>
                                    )}
                                </div>
                            </div>

                            <div className="workspace-columns">
                                <div className="workspace-column">
                                    <div className="subsection-heading">
                                        <div><strong>Members</strong><span>{members.length} people with access</span></div>
                                    </div>
                                    <div className="member-list">
                                        {members.map(member => (
                                            <div className="member-row" key={member.userId}>
                                                <span className="member-avatar">{initials(member.name)}</span>
                                                <div><strong>{member.name}</strong><small>{member.email}</small></div>
                                                <em>{member.role}</em>
                                            </div>
                                        ))}
                                    </div>

                                    {isOwner && (
                                        <form className="stacked-workspace-form" onSubmit={createInvitation}>
                                            <strong>Invite a client or staff member</strong>
                                            <input type="email" required value={inviteEmail} placeholder="client@company.com" onChange={event => setInviteEmail(event.target.value)}/>
                                            <div>
                                                <select value={inviteRole} onChange={event => setInviteRole(event.target.value)}>
                                                    <option value="CLIENT">Client</option>
                                                    <option value="STAFF">Staff</option>
                                                </select>
                                                <button className="secondary-button compact" type="submit" disabled={busy}>Send invite</button>
                                            </div>
                                            {latestInviteUrl && (
                                                <div className="invite-copy-row">
                                                    <input readOnly value={latestInviteUrl} aria-label="Latest invitation link"/>
                                                    <button className="secondary-button compact" type="button" onClick={() => {
                                                        navigator.clipboard.writeText(latestInviteUrl);
                                                        notify("Link copied", "The invitation link is ready to share.");
                                                    }}>Copy link</button>
                                                </div>
                                            )}
                                        </form>
                                    )}
                                    {isOwner && invitations.length > 0 && (
                                        <div className="invitation-list">
                                            <strong>Invitation history</strong>
                                            {invitations.map(invitation => (
                                                <div className="invitation-row" key={invitation.id}>
                                                    <span>
                                                        <strong>{invitation.email}</strong>
                                                        <small>{invitation.role} / Expires {formatDateTime(invitation.expiresAt)}</small>
                                                    </span>
                                                    <em className={invitation.status.toLowerCase()}>{invitation.status}</em>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </div>

                                <div className="workspace-column request-column">
                                    <div className="subsection-heading">
                                        <div><strong>Document requests</strong><span>{requests.length} tracked items</span></div>
                                    </div>

                                    {canManage && (
                                        <form className="request-form" onSubmit={createRequest}>
                                            <input required maxLength="160" value={requestForm.title} placeholder="Signed engagement letter" onChange={event => setRequestForm(current => ({...current, title: event.target.value}))}/>
                                            <textarea maxLength="1000" value={requestForm.description} placeholder="Add instructions for the client" onChange={event => setRequestForm(current => ({...current, description: event.target.value}))}/>
                                            <div>
                                                <select value={requestForm.assigneeEmail} onChange={event => setRequestForm(current => ({...current, assigneeEmail: event.target.value}))}>
                                                    <option value="">Any member</option>
                                                    {members.filter(member => member.role !== "OWNER").map(member => (
                                                        <option value={member.email} key={member.userId}>{member.name}</option>
                                                    ))}
                                                </select>
                                                <input type="date" min={today()} value={requestForm.dueDate} onChange={event => setRequestForm(current => ({...current, dueDate: event.target.value}))}/>
                                                <button className="primary-button compact" type="submit" disabled={busy}>Create request</button>
                                            </div>
                                        </form>
                                    )}

                                    {requests.length === 0 ? (
                                        <p className="workspace-request-empty">No document requests yet.</p>
                                    ) : (
                                        <div className="request-list">
                                            {requests.map(request => (
                                                <article className="request-card" key={request.id}>
                                                    <div className="request-card-top">
                                                        <div><strong>{request.title}</strong><span>{request.assigneeName || "Any workspace member"}</span></div>
                                                        <span className={`request-status ${request.status.toLowerCase()}`}>{request.status}</span>
                                                    </div>
                                                    {request.description && <p>{request.description}</p>}
                                                    {request.submittedFileId && (
                                                        <div className="submission-file">
                                                            <span className="file-badge">{fileExtension(request.submittedFileName)}</span>
                                                            <div>
                                                                <strong>{request.submittedFileName}</strong>
                                                                <small>
                                                                    {formatBytes(request.submittedFileSizeBytes)}
                                                                    {request.submittedByName ? ` / Submitted by ${request.submittedByName}` : ""}
                                                                </small>
                                                            </div>
                                                            <button className="table-action" type="button" aria-label={`Download ${request.submittedFileName}`} onClick={() => downloadSubmission(request)}>
                                                                <Icon name="download"/>
                                                            </button>
                                                        </div>
                                                    )}
                                                    <div className="request-meta">
                                                        <span>{request.dueDate ? `Due ${formatDate(request.dueDate)}` : "No due date"}</span>
                                                        <div className="request-actions">
                                                            {canUploadSubmission(request, selected.role, user.id) && (
                                                                <label className={`secondary-button compact submission-button${busy ? " disabled" : ""}`}>
                                                                    <Icon name="upload"/>
                                                                    {uploadingRequestId === request.id
                                                                        ? "Uploading..."
                                                                        : request.submittedFileId ? "Replace file" : "Upload file"}
                                                                    <input
                                                                        type="file"
                                                                        hidden
                                                                        disabled={busy}
                                                                        accept=".pdf,.png,.jpg,.jpeg,.txt,application/pdf,image/png,image/jpeg,text/plain"
                                                                        onChange={event => {
                                                                            const file = event.target.files?.[0];
                                                                            event.target.value = "";
                                                                            uploadSubmission(request, file);
                                                                        }}
                                                                    />
                                                                </label>
                                                            )}
                                                            {requestAction(request, selected.role, user.id) && (
                                                                <button className="secondary-button compact" type="button" disabled={busy} onClick={() => changeStatus(request, requestAction(request, selected.role, user.id).status)}>
                                                                    {requestAction(request, selected.role, user.id).label}
                                                                </button>
                                                            )}
                                                        </div>
                                                    </div>
                                                </article>
                                            ))}
                                        </div>
                                    )}
                                </div>
                            </div>
                        </div>
                    )}
                </>
            )}
        </section>
    );
}

function requestAction(request, role, userId) {
    const manager = role === "OWNER" || role === "STAFF";
    if (request.status === "PENDING" && request.submittedFileId
            && (manager || !request.assignedTo || request.assignedTo === userId)) {
        return {status: "SUBMITTED", label: "Resubmit existing"};
    }
    if (request.status === "SUBMITTED" && manager) {
        return {status: "APPROVED", label: "Approve"};
    }
    if (request.status === "APPROVED" && manager) {
        return {status: "PENDING", label: "Reopen"};
    }
    return null;
}

function canUploadSubmission(request, role, userId) {
    if (request.status === "APPROVED") return false;
    if (role === "OWNER" || role === "STAFF") return true;
    return !request.assignedTo || request.assignedTo === userId;
}

function validateSubmission(file) {
    const allowed = new Set(["application/pdf", "image/jpeg", "image/png", "text/plain"]);
    if (!allowed.has(file.type)) return "Choose a PDF, PNG, JPEG, or text file.";
    if (file.size === 0) return "The selected file is empty.";
    if (file.size > 10 * 1024 * 1024) return "The selected file is larger than 10 MB.";
    return null;
}

function emptyRequest() {
    return {title: "", description: "", assigneeEmail: "", dueDate: ""};
}

function initials(name) {
    return name.split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]).join("").toUpperCase();
}

function today() {
    return new Date().toISOString().slice(0, 10);
}

function formatDate(value) {
    return new Intl.DateTimeFormat(undefined, {month: "short", day: "numeric", year: "numeric"}).format(new Date(`${value}T00:00:00`));
}

function formatDateTime(value) {
    return new Intl.DateTimeFormat(undefined, {
        month: "short",
        day: "numeric",
        year: "numeric"
    }).format(new Date(value));
}

function formatBytes(bytes) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function fileExtension(filename) {
    return (filename.includes(".") ? filename.split(".").pop() : "FILE").slice(0, 4).toUpperCase();
}
