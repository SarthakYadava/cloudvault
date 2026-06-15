import {useEffect, useState} from "react";
import {apiFetch} from "../api";
import {Brand, Icon} from "../icons";

export default function AuthScreen({onAuthenticated, invitation, invitationError}) {
    const [mode, setMode] = useState("login");
    const [showPassword, setShowPassword] = useState(false);
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState("");
    const [form, setForm] = useState({name: "", email: "", password: ""});
    const registering = mode === "register";

    useEffect(() => {
        if (!invitation?.email) return;
        setForm(current => current.email
            ? current
            : {...current, email: invitation.email}
        );
    }, [invitation]);

    function changeMode(nextMode) {
        setMode(nextMode);
        setMessage("");
    }

    function updateField(event) {
        setForm(current => ({...current, [event.target.name]: event.target.value}));
    }

    async function submit(event) {
        event.preventDefault();
        setMessage("");
        if (!form.email.trim() || !form.password || (registering && !form.name.trim())) {
            setMessage("Complete all required fields.");
            return;
        }

        setBusy(true);
        try {
            const payload = {
                email: form.email.trim(),
                password: form.password,
                ...(registering ? {name: form.name.trim()} : {})
            };
            const response = await apiFetch(null, `/api/auth/${registering ? "register" : "login"}`, {
                method: "POST",
                body: JSON.stringify(payload)
            });
            onAuthenticated({
                token: response.accessToken,
                user: response.user,
                freshAccount: registering
            });
        } catch (error) {
            setMessage(error.message);
        } finally {
            setBusy(false);
        }
    }

    return (
        <main className="auth-layout">
            <section className="auth-story">
                <Brand/>
                <div className="story-copy">
                    <p className="eyebrow">Private document workspace</p>
                    <h1>Important files, handled with care.</h1>
                    <p>
                        A focused workspace for exchanging contracts, reports, and client documents
                        without turning private storage into a public folder.
                    </p>
                </div>
                <div className="trust-list">
                    <Trust icon="shield" title="Private by default">
                        Public access remains blocked across your S3 storage.
                    </Trust>
                    <Trust icon="arrow" title="Controlled transfers">
                        Short-lived links move files without revealing cloud credentials.
                    </Trust>
                    <Trust icon="chart" title="Visible activity">
                        Important file and sharing actions remain traceable.
                    </Trust>
                </div>
            </section>

            <section className="auth-panel">
                <div className="auth-card">
                    {(invitation || invitationError) && (
                        <div className="invite-auth-banner">
                            <strong>
                                {invitationError
                                    ? "Invitation unavailable"
                                    : `Invitation to ${invitation.workspaceName}`}
                            </strong>
                            <span>
                                {invitationError
                                    ? invitationError
                                    : `Sign in or create an account with ${invitation.email}.`}
                            </span>
                        </div>
                    )}
                    <div className="auth-heading">
                        <p className="eyebrow">CloudVault</p>
                        <h2>{registering ? "Create your workspace" : "Welcome back"}</h2>
                        <p>
                            {registering
                                ? "Set up your private document workspace."
                                : "Sign in to manage your private documents."}
                        </p>
                    </div>

                    <div className="auth-tabs" role="tablist" aria-label="Account actions">
                        <button
                            className={mode === "login" ? "active" : ""}
                            type="button"
                            role="tab"
                            aria-selected={mode === "login"}
                            onClick={() => changeMode("login")}
                        >
                            Sign in
                        </button>
                        <button
                            className={registering ? "active" : ""}
                            type="button"
                            role="tab"
                            aria-selected={registering}
                            onClick={() => changeMode("register")}
                        >
                            Create account
                        </button>
                    </div>

                    <form onSubmit={submit}>
                        {registering && (
                            <Field label="Full name">
                                <input
                                    name="name"
                                    value={form.name}
                                    onChange={updateField}
                                    autoComplete="name"
                                    minLength="2"
                                    maxLength="100"
                                    placeholder="Sarthak Yadav"
                                    required
                                />
                            </Field>
                        )}
                        <Field label="Email address">
                            <input
                                name="email"
                                type="email"
                                value={form.email}
                                onChange={updateField}
                                autoComplete="email"
                                maxLength="254"
                                placeholder="you@example.com"
                                required
                            />
                        </Field>
                        <Field
                            label="Password"
                            hint={registering ? "8 to 72 characters" : "Secure account access"}
                        >
                            <div className="password-wrap">
                                <input
                                    name="password"
                                    type={showPassword ? "text" : "password"}
                                    value={form.password}
                                    onChange={updateField}
                                    autoComplete={registering ? "new-password" : "current-password"}
                                    maxLength="72"
                                    placeholder="Enter your password"
                                    required
                                />
                                <button
                                    className="icon-button password-toggle"
                                    type="button"
                                    aria-label={showPassword ? "Hide password" : "Show password"}
                                    onClick={() => setShowPassword(current => !current)}
                                >
                                    <Icon name={showPassword ? "eyeOff" : "eye"}/>
                                </button>
                            </div>
                        </Field>

                        {message && <div className="form-message" role="alert">{message}</div>}

                        <button className="primary-button full-width" type="submit" disabled={busy}>
                            {busy
                                ? (registering ? "Creating account..." : "Signing in...")
                                : (registering ? "Create secure account" : "Sign in securely")}
                        </button>
                    </form>

                    <p className="auth-footnote">
                        Protected by signed sessions and private cloud storage.
                    </p>
                </div>
            </section>
        </main>
    );
}

function Field({label, hint, children}) {
    return (
        <label className="field">
            <span className="label-row">
                <strong>{label}</strong>
                {hint && <small>{hint}</small>}
            </span>
            {children}
        </label>
    );
}

function Trust({icon, title, children}) {
    return (
        <div className="trust-item">
            <span className="trust-icon"><Icon name={icon}/></span>
            <span><strong>{title}</strong>{children}</span>
        </div>
    );
}
