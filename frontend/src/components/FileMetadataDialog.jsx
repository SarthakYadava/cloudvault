import {useState} from "react";
import {apiFetch} from "../api";
import {Icon} from "../icons";

const DEFAULT_FOLDERS = ["Unfiled", "Contracts", "Tax", "Identity", "Reports"];

export default function FileMetadataDialog({
    file,
    token,
    folders,
    onClose,
    onSaved,
    notify
}) {
    const [name, setName] = useState(file.originalName);
    const [folder, setFolder] = useState(file.folder || "Unfiled");
    const [customFolder, setCustomFolder] = useState("");
    const [tags, setTags] = useState((file.tags || []).join(", "));
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState("");
    const folderOptions = [...new Set([...DEFAULT_FOLDERS, ...folders])];
    const usingCustomFolder = folder === "__custom__";

    async function submit(event) {
        event.preventDefault();
        const resolvedFolder = usingCustomFolder ? customFolder.trim() : folder;
        if (!name.trim() || !resolvedFolder) {
            setMessage("Enter a document name and folder.");
            return;
        }
        setBusy(true);
        setMessage("");
        try {
            const updated = await apiFetch(token, `/api/files/${file.id}/metadata`, {
                method: "PATCH",
                body: JSON.stringify({
                    name: name.trim(),
                    folder: resolvedFolder,
                    tags: tags.split(",")
                        .map(tag => tag.trim())
                        .filter(Boolean)
                })
            });
            notify("Document organized", `${updated.originalName} was updated.`);
            onSaved(updated);
        } catch (error) {
            setMessage(error.message);
        } finally {
            setBusy(false);
        }
    }

    return (
        <div className="modal-backdrop" role="presentation" onMouseDown={event => {
            if (event.target === event.currentTarget) onClose();
        }}>
            <section className="share-sheet metadata-dialog" role="dialog" aria-modal="true" aria-labelledby="metadata-title">
                <div className="sheet-header">
                    <div>
                        <p className="eyebrow">Document details</p>
                        <h2 id="metadata-title">Organize file</h2>
                    </div>
                    <button className="icon-button" type="button" aria-label="Close" onClick={onClose}>
                        <Icon name="close"/>
                    </button>
                </div>
                <form onSubmit={submit}>
                    <label className="field">
                        <span className="label-row"><strong>Display name</strong></span>
                        <input value={name} maxLength="255" onChange={event => setName(event.target.value)} required/>
                    </label>
                    <label className="field">
                        <span className="label-row"><strong>Folder</strong></span>
                        <select value={folder} onChange={event => setFolder(event.target.value)}>
                            {folderOptions.map(option => <option value={option} key={option}>{option}</option>)}
                            <option value="__custom__">New folder...</option>
                        </select>
                    </label>
                    {usingCustomFolder && (
                        <label className="field">
                            <span className="label-row"><strong>New folder name</strong><small>Up to 80 characters</small></span>
                            <input value={customFolder} maxLength="80" onChange={event => setCustomFolder(event.target.value)} required/>
                        </label>
                    )}
                    <label className="field">
                        <span className="label-row"><strong>Tags</strong><small>Comma separated, up to 8</small></span>
                        <input value={tags} maxLength="250" placeholder="client, signed, 2026" onChange={event => setTags(event.target.value)}/>
                    </label>
                    {message && <div className="form-message" role="alert">{message}</div>}
                    <div className="dialog-actions">
                        <button className="secondary-button" type="button" onClick={onClose}>Cancel</button>
                        <button className="primary-button" type="submit" disabled={busy}>
                            {busy ? "Saving..." : "Save changes"}
                        </button>
                    </div>
                </form>
            </section>
        </div>
    );
}
