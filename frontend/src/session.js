const TOKEN_KEY = "cloudvault.token";
const USER_KEY = "cloudvault.user";

export function readSession() {
    try {
        const token = sessionStorage.getItem(TOKEN_KEY);
        const user = JSON.parse(sessionStorage.getItem(USER_KEY));
        return token && user ? {token, user} : null;
    } catch {
        return null;
    }
}

export function writeSession(session) {
    sessionStorage.setItem(TOKEN_KEY, session.token);
    sessionStorage.setItem(USER_KEY, JSON.stringify(session.user));
}

export function clearSession() {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
}
