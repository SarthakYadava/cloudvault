import {useCallback, useEffect, useMemo, useState} from "react";
import {apiFetch} from "../api";
import {Icon} from "../icons";

export default function WorkspacePanel({token, user, notify, handleError}) {
    const [workspaces, setWorkspaces] = useState([]);
    const [selectedId, setSelectedId] = useState(null);
    const [members, setMembers] = useState([]);
    const [requests, setRequests] = useState([]);
    const [message, setMessage] = useState("");
    const [creatingWorkspace, setCreatingWorkspace] = useState(false);
    const [workspaceName, setWorkspaceName] = useState("");
    const [memberEmail, setMemberEmail] = useState("");
    const [memberRole, setMemberRole] = useState("CLIENT");
    const [requestForm, setRequestForm] = useState(emptyRequest());
    const [busy, setBusy] = useState(false);

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

    const loadWorkspaceDetails = useCallback(async workspaceId => {
        if (!workspaceId) {
            setMembers([]);
            setRequests([]);
            return;
        }
        setMessage("");
        try {
            const [memberResponse, requestResponse] = await Promise.all([
                apiFetch(token, `/api/workspaces/${workspaceId}/members`),
                apiFetch(token, `/api/workspaces/${workspaceId}/requests`)
            ]);
            setMembers(memberResponse);
            setRequests(requestResponse);
        } catch (error) {
            if (!handleError(error)) setMessage(error.message);
        }
    }, [handleError, token]);

    useEffect(() => {
        loadWorkspaces();
    }, [loadWorkspaces]);

    useEffect(() => {
        loadWorkspaceDetails(selectedId);
    }, [loadWorkspaceDetails, selectedId]);

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

    async function addMember(event) {
        event.preventDefault();
        if (!selected || !memberEmail.trim()) return;
        setBusy(true);
        try {
            await apiFetch(token, `/api/workspaces/${selected.id}/members`, {
                method: "POST",
                body: JSON.stringify({
                    email: memberEmail.trim(),
                    role: memberRole
                })
            });
            setMemberEmail("");
            await Promise.all([
                loadWorkspaceDetails(selected.id),
                loadWorkspaces(selected.id)
            ]);
            notify("Member added", `${memberEmail.trim()} can now access ${selected.name}.`);
        } catch (error) {
            if (!handleError(error)) notify("Member not added", error.message, true);
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
                loadWorkspaceDetails(selected.id),
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
                loadWorkspaceDetails(selected.id),
                loadWorkspaces(selected.id)
            ]);
            notify("Request updated", `${request.title} is now ${status.toLowerCase()}.`);
        } catch (error) {
            if (!handleError(error)) notify("Request not updated", error.message, true);
        } finally {
            setBusy(false);
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
                        <p>Separate each client’s requests and control who can access them.</p>
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
                                <span><strong>{workspace.name}</strong><small>{workspace.memberCount} members · {workspace.pendingRequestCount} pending</small></span>
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
                                        <form className="stacked-workspace-form" onSubmit={addMember}>
                                            <strong>Add a registered user</strong>
                                            <input type="email" required value={memberEmail} placeholder="client@company.com" onChange={event => setMemberEmail(event.target.value)}/>
                                            <div>
                                                <select value={memberRole} onChange={event => setMemberRole(event.target.value)}>
                                                    <option value="CLIENT">Client</option>
                                                    <option value="STAFF">Staff</option>
                                                </select>
                                                <button className="secondary-button compact" type="submit" disabled={busy}>Add member</button>
                                            </div>
                                        </form>
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
                                                    <div className="request-meta">
                                                        <span>{request.dueDate ? `Due ${formatDate(request.dueDate)}` : "No due date"}</span>
                                                        {requestAction(request, selected.role, user.id) && (
                                                            <button className="secondary-button compact" type="button" disabled={busy} onClick={() => changeStatus(request, requestAction(request, selected.role, user.id).status)}>
                                                                {requestAction(request, selected.role, user.id).label}
                                                            </button>
                                                        )}
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
    if (request.status === "PENDING") {
        if (manager || !request.assignedTo || request.assignedTo === userId) {
            return {status: "SUBMITTED", label: "Mark submitted"};
        }
    }
    if (request.status === "SUBMITTED" && manager) {
        return {status: "APPROVED", label: "Approve"};
    }
    if (request.status === "APPROVED" && manager) {
        return {status: "PENDING", label: "Reopen"};
    }
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
