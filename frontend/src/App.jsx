import {useCallback, useEffect, useState} from "react";
import {apiFetch} from "./api";
import AuthScreen from "./components/AuthScreen";
import Dashboard from "./components/Dashboard";
import InvitationAcceptance from "./components/InvitationAcceptance";
import {clearSession, readSession, writeSession} from "./session";

export default function App() {
    const [session, setSession] = useState(readSession);
    const [inviteToken, setInviteToken] = useState(
        () => new URLSearchParams(window.location.search).get("invite")
    );
    const [invitation, setInvitation] = useState(null);
    const [invitationError, setInvitationError] = useState("");
    const [toasts, setToasts] = useState([]);
    const [authNotice, setAuthNotice] = useState("");

    useEffect(() => {
        if (!inviteToken) return;
        apiFetch(null, `/api/invitations/${inviteToken}`)
            .then(setInvitation)
            .catch(error => setInvitationError(error.message));
    }, [inviteToken]);

    const notify = useCallback((title, message, error = false) => {
        const id = crypto.randomUUID();
        setToasts(current => [...current, {id, title, message, error}]);
        window.setTimeout(() => {
            setToasts(current => current.filter(item => item.id !== id));
        }, 4200);
    }, []);

    function authenticate(nextSession) {
        const storedSession = {token: nextSession.token, user: nextSession.user};
        writeSession(storedSession);
        setSession(storedSession);
        setAuthNotice("");
        notify(
            nextSession.freshAccount ? "Account created" : "Welcome back",
            `Signed in as ${nextSession.user.email}.`
        );
    }

    const logout = useCallback(message => {
        clearSession();
        setSession(null);
        setAuthNotice(message || "");
        if (!message) notify("Signed out", "Your browser session has been cleared.");
    }, [notify]);

    function finishInvitation(workspaceName) {
        dismissInvitation();
        notify("Invitation accepted", `You now have access to ${workspaceName}.`);
    }

    function dismissInvitation() {
        window.history.replaceState({}, "", window.location.pathname);
        setInviteToken(null);
        setInvitation(null);
        setInvitationError("");
    }

    return (
        <>
            <div className="page-noise" aria-hidden="true"/>
            {session
                ? inviteToken
                    ? <InvitationAcceptance
                        token={session.token}
                        inviteToken={inviteToken}
                        invitation={invitation}
                        invitationError={invitationError}
                        onAccepted={finishInvitation}
                        onContinue={dismissInvitation}
                        onLogout={logout}
                    />
                    : <Dashboard session={session} onLogout={logout} notify={notify}/>
                : <>
                    <AuthScreen
                        onAuthenticated={authenticate}
                        invitation={invitation}
                        invitationError={invitationError}
                    />
                    {authNotice && <div className="session-notice" role="alert">{authNotice}</div>}
                </>}
            <div className="toast-region" aria-live="polite" aria-atomic="true">
                {toasts.map(item => (
                    <div className={`toast${item.error ? " error" : ""}`} key={item.id}>
                        <strong>{item.title}</strong>
                        <span>{item.message}</span>
                    </div>
                ))}
            </div>
        </>
    );
}
