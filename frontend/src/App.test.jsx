import {afterEach, describe, expect, it, vi} from "vitest";
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
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

    it("allows an owner to delete a workspace after confirmation", async () => {
        sessionStorage.setItem("cloudvault.token", "test-token");
        sessionStorage.setItem("cloudvault.user", JSON.stringify({
            id: "owner-id",
            name: "Workspace Owner",
            email: "owner@example.com",
            role: "USER"
        }));
        vi.stubGlobal("confirm", vi.fn(() => true));
        let workspaceReads = 0;
        const fetchMock = vi.fn(async (url, options = {}) => {
            if (String(url).startsWith("/api/files")) {
                return jsonResponse({content: [], totalPages: 0, totalElements: 0});
            }
            if (String(url).startsWith("/api/activity")) {
                return jsonResponse({content: [], totalPages: 0, totalElements: 0});
            }
            if (String(url) === "/api/workspaces" && !options.method) {
                workspaceReads += 1;
                return jsonResponse(workspaceReads === 1 ? [{
                    id: "workspace-1",
                    name: "Acme Legal",
                    role: "OWNER",
                    memberCount: 1,
                    pendingRequestCount: 0
                }] : []);
            }
            if (String(url) === "/api/workspaces/workspace-1/members") {
                return jsonResponse([{
                    userId: "owner-id",
                    name: "Workspace Owner",
                    email: "owner@example.com",
                    role: "OWNER"
                }]);
            }
            if (String(url) === "/api/workspaces/workspace-1/requests") {
                return jsonResponse([]);
            }
            if (String(url) === "/api/workspaces/workspace-1"
                    && options.method === "DELETE") {
                return emptyResponse();
            }
            throw new Error(`Unexpected request: ${url}`);
        });
        vi.stubGlobal("fetch", fetchMock);

        render(<App/>);

        const deleteButton = await screen.findByRole("button", {name: "Delete workspace"});
        fireEvent.click(deleteButton);

        await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
            "/api/workspaces/workspace-1",
            expect.objectContaining({method: "DELETE"})
        ));
        expect(window.confirm).toHaveBeenCalled();
        expect(await screen.findByText("Create your first client workspace")).toBeInTheDocument();
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

function emptyResponse() {
    return {
        ok: true,
        status: 204,
        headers: new Headers()
    };
}
