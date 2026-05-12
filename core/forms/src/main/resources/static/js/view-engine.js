/**
 * Thin client for the ViewEngine RPC endpoint at POST /api/view/invoke.
 * Returns a Promise that resolves with `result.data` or rejects with an Error.
 */
const ViewEngine = {
    call(service, method, args) {
        return fetch("/api/view/invoke", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({service, method, args: args || []}),
        }).then(response => response.json().then(result => {
            if (!result.success) throw new Error(result.error || "Unknown error");
            return result.data;
        }));
    },
};
