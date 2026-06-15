import {useState} from "react";
import {apiFetch} from "../api";
import {Brand, Icon} from "../icons";

export default function InvitationAcceptance({
    token,
    inviteToken,
    invitation,
    invitationError,
    onAccepted,
    onContinue,
    onLogout
}) {
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState("");
    const unavailable = invitationError
        || invitation?.status === "EXPIRED"
        || invitation?.status === "ACCEPTED";

    async function accept() {
        setBusy(true);
        setMessage("");
        try {
            const response = await apiFetch(
                token,
                `/api/invitations/${inviteToken}/accept`,
                {method: "POST"}
            );
            onAccepted(response.workspaceName);
        } catch (error) {
            if (error.status === 401) {
                onLogout("Your session expired. Sign in again.");
                return;
            }
            setMessage(error.message);
        } finally {
            setBusy(false);
        }
    }

    return (
        <main className="invitation-layout">
            <section className="invitation-card">
                <Brand/>
                <span className="invitation-icon"><Icon name="users"/></span>
                <p className="eyebrow">Workspace invitation</p>
                <h1>{invitation?.workspaceName || "CloudVault workspace"}</h1>
                {invitationError ? (
                    <p className="form-message" role="alert">{invitationError}</p>
                ) : invitation ? (
                    <>
                        <p>
                            You were invited as <strong>{invitation.role.toLowerCase()}</strong> using{" "}
                            <strong>{invitation.email}</strong>.
                        </p>
                        <span className={`invitation-status ${invitation.status.toLowerCase()}`}>
                            {invitation.status}
                        </span>
                    </>
                ) : (
                    <p>Checking this secure invitation...</p>
                )}
                {message && <p className="form-message" role="alert">{message}</p>}
                <div className="invitation-actions">
                    {!unavailable && invitation && (
                        <button className="primary-button" type="button" disabled={busy} onClick={accept}>
                            {busy ? "Accepting..." : "Accept invitation"}
                        </button>
                    )}
                    {unavailable && (
                        <button className="secondary-button" type="button" onClick={onContinue}>
                            Continue to CloudVault
                        </button>
                    )}
                    <button className="text-button" type="button" onClick={() => onLogout()}>
                        Sign in with another account
                    </button>
                </div>
            </section>
        </main>
    );
}
