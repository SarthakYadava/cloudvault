import {afterEach, describe, expect, it, vi} from "vitest";
import {cleanup, render, screen} from "@testing-library/react";
import App from "./App";

afterEach(() => {
    cleanup();
    sessionStorage.clear();
    vi.restoreAllMocks();
});

describe("CloudVault React application", () => {
    it("renders the sign-in experience without a session", () => {
        render(<App/>);

        expect(screen.getByRole("heading", {name: "Welcome back"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Sign in securely"})).toBeInTheDocument();
        expect(screen.getByText("Important files, handled with care.")).toBeInTheDocument();
    });

    it("renders the document workspace from an existing browser session", async () => {
        sessionStorage.setItem("cloudvault.token", "test-token");
        sessionStorage.setItem("cloudvault.user", JSON.stringify({
            id: "test-user",
            name: "Sarthak Yadav",
            email: "sarthak@example.com",
            role: "USER"
        }));
        vi.stubGlobal("fetch", vi.fn(async url => {
            if (String(url).startsWith("/api/files")) {
                return jsonResponse({content: [], totalPages: 0, totalElements: 0});
            }
            if (String(url).startsWith("/api/activity")) {
                return jsonResponse({content: [], totalPages: 0, totalElements: 0});
            }
            if (String(url) === "/api/workspaces") {
                return jsonResponse([]);
            }
            throw new Error(`Unexpected request: ${url}`);
        }));

        render(<App/>);

        expect(await screen.findByRole("heading", {name: "My files"})).toBeInTheDocument();
        expect(await screen.findByRole("heading", {name: "Client workspaces"})).toBeInTheDocument();
        expect(screen.getByText("Sarthak Yadav")).toBeInTheDocument();
        expect(screen.getByText("Create your first client workspace")).toBeInTheDocument();
        expect(screen.getByText("Your vault is ready")).toBeInTheDocument();
    });
});

function jsonResponse(body) {
    return {
        ok: true,
        status: 200,
        headers: new Headers({"content-type": "application/json"}),
        json: async () => body
    };
}
