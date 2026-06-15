export async function apiFetch(token, url, options = {}) {
    const headers = new Headers(options.headers || {});
    if (token) {
        headers.set("Authorization", `Bearer ${token}`);
    }
    if (options.body && !(options.body instanceof FormData)) {
        headers.set("Content-Type", "application/json");
    }

    const response = await fetch(url, {...options, headers});
    if (response.status === 204) {
        return null;
    }

    const contentType = response.headers.get("content-type") || "";
    const data = contentType.includes("application/json")
        ? await response.json()
        : {message: await response.text()};

    if (!response.ok) {
        const error = new Error(data.message || "The request could not be completed.");
        error.status = response.status;
        throw error;
    }
    return data;
}

export function uploadWithProgress({url, method, headers, body, onProgress}) {
    return new Promise((resolve, reject) => {
        const request = new XMLHttpRequest();
        request.open(method, url);
        Object.entries(headers || {}).forEach(([name, value]) => {
            request.setRequestHeader(name, value);
        });
        request.upload.addEventListener("progress", event => {
            if (event.lengthComputable) {
                onProgress(event.loaded / event.total);
            }
        });
        request.addEventListener("load", () => {
            if (request.status >= 200 && request.status < 300) {
                resolve();
            } else {
                reject(new Error(`Upload failed with status ${request.status}.`));
            }
        });
        request.addEventListener("error", () => reject(new Error("The upload connection failed.")));
        request.send(body);
    });
}
