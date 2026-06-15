import {useEffect, useState} from "react";
import {apiFetch} from "../api";
import {Icon} from "../icons";

export default function ShareDialog({file, token, onClose, onChanged, notify}) {
    const [links, setLinks] = useState([]);
    const [duration, setDuration] = useState("1440");
    const [newUrl, setNewUrl] = useState("");
    const [message, setMessage] = useState("");
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        loadLinks();
    }, [file.id]);

    async function loadLinks() {
        try {
            setLinks(await apiFetch(token, `/api/files/${file.id}/shares`));
        } catch (error) {
            setMessage(error.message);
        }
    }

    async function createLink(event) {
        event.preventDefault();
        setBusy(true);
        setMessage("");
        try {
            const link = await apiFetch(token, `/api/files/${file.id}/shares`, {
                method: "POST",
                body: JSON.stringify({expirationMinutes: Number(duration)})
            });
            setNewUrl(link.shareUrl);
            await loadLinks();
            onChanged();
            notify("Share link created", `Expires ${formatDateTime(link.expiresAt)}.`);
        } catch (error) {
            setMessage(error.message);
        } finally {
            setBusy(false);
        }
    }

    async function revoke(linkId) {
        try {
            await apiFetch(token, `/api/shares/${linkId}`, {method: "DELETE"});
            setNewUrl("");
            await loadLinks();
            onChanged();
            notify("Share link revoked", "The link can no longer be used.");
        } catch (error) {
            setMessage(error.message);
        }
    }

    async function copyLink() {
        await navigator.clipboard.writeText(newUrl);
        notify("Link copied", "The expiring URL is ready to share.");
    }

    return (
        <div className="modal-backdrop" role="presentation" onMouseDown={event => {
            if (event.target === event.currentTarget) onClose();
        }}>
            <section className="share-sheet" role="dialog" aria-modal="true" aria-labelledby="share-title">
                <header className="sheet-header">
                    <div>
                        <p className="eyebrow">Controlled sharing</p>
                        <h2 id="share-title">Share {file.originalName}</h2>
                        <p>Create a revocable link that expires automatically.</p>
                    </div>
                    <button className="icon-button" type="button" aria-label="Close" onClick={onClose}>
                        <Icon name="close"/>
                    </button>
                </header>

                <form className="share-form" onSubmit={createLink}>
                    <label>
                        <span>Link duration</span>
                        <select value={duration} onChange={event => setDuration(event.target.value)}>
                            <option value="60">1 hour</option>
                            <option value="1440">24 hours</option>
                            <option value="10080">7 days</option>
                        </select>
                    </label>
                    <button className="primary-button" type="submit" disabled={busy}>
                        {busy ? "Creating link..." : "Create secure link"}
                    </button>
                </form>

                {message && <div className="form-message" role="alert">{message}</div>}

                {newUrl && (
                    <div className="new-share-result">
                        <strong>Copy this link now</strong>
                        <p>CloudVault stores only its secure hash, so the complete URL is shown once.</p>
                        <div className="copy-row">
                            <input value={newUrl} readOnly aria-label="New share URL"/>
                            <button className="secondary-button compact" type="button" onClick={copyLink}>
                                Copy
                            </button>
                        </div>
                    </div>
                )}

                <div className="share-history">
                    <h3>Link history</h3>
                    {links.length === 0 ? (
                        <p className="empty-note">No share links have been created for this file.</p>
                    ) : links.map(link => (
                        <div className={`share-link-item${link.active ? "" : " inactive"}`} key={link.id}>
                            <div>
                                <strong>{link.active ? "Active link" : "Inactive link"}</strong>
                                <span>
                                    {link.active ? `Expires ${formatDateTime(link.expiresAt)}` : "Expired or revoked"}
                                    {" | "}Created {formatDateTime(link.createdAt)}
                                </span>
                            </div>
                            {link.active && (
                                <button
                                    className="secondary-button compact"
                                    type="button"
                                    onClick={() => revoke(link.id)}
                                >
                                    Revoke
                                </button>
                            )}
                        </div>
                    ))}
                </div>
            </section>
        </div>
    );
}

function formatDateTime(value) {
    return new Intl.DateTimeFormat(undefined, {
        month: "short",
        day: "numeric",
        hour: "numeric",
        minute: "2-digit"
    }).format(new Date(value));
}
