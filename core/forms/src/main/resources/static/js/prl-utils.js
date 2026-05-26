/**
 * Prl - front-end framework for ViewEngine pages.
 *
 * Three pieces:
 *   1. `Prl`        - namespace of DOM/format/utility helpers and `bindFields`.
 *   2. `prlList`    - table loader (programmatic + declarative).
 *   3. `prlForm`    - button -> ViewService binder (programmatic + declarative).
 *   4. `prlOptions` - declarative `<select>` loader.
 *   5. `prlDetail`  - declarative single-record loader for detail pages.
 *
 * Auto-binding runs on `DOMContentLoaded` for any element carrying the matching
 * `data-prl-*` attributes - pages typically need only a few small helper functions
 * in their own `<script>` block.
 *
 * Depends on `view-engine.js` (provides `ViewEngine.call`).
 */

// ============================================================================
// Prl - namespace of small utilities
// ============================================================================

const Prl = {

    // --- DOM access ----------------------------------------------------

    /**
     * Resolves a CSS selector to an element. If a node is passed in directly,
     * returns it unchanged. Returns `null` when nothing matches.
     */
    $(selector) {
        return typeof selector === "string" ? document.querySelector(selector) : selector;
    },

    /**
     * Sets the text content of the element matched by `selector`. Empty/null
     * values render as the placeholder "-" so empty cells stay visually aligned.
     */
    setText(selector, value) {
        const el = Prl.$(selector);
        if (el) el.textContent = (value == null || value === "") ? "-" : String(value);
    },

    /** Bulk version of `setText`: pass `{selector: value, ...}`. */
    fillTexts(map) {
        Object.keys(map).forEach(k => Prl.setText(k, map[k]));
    },

    /**
     * Reads the chosen value from a `prlSearchSelect` input. The visible
     * `.value` holds the label; the picked value lives in `data-value`.
     * Returns "" when nothing is picked.
     */
    dataVal(selector) {
        const el = Prl.$(selector);
        return el ? (el.getAttribute("data-value") || "") : "";
    },

    /** Reads `.value` from an `<input>`/`<select>`. Returns "" when missing. */
    val(selector) {
        const el = Prl.$(selector);
        return el ? el.value : "";
    },

    /** Clears every `<input>` and `<textarea>` inside the given container. */
    clearInputs(containerSelector) {
        const root = Prl.$(containerSelector);
        if (root) root.querySelectorAll("input, textarea").forEach(el => {
            el.value = "";
        });
    },

    /**
     * Compact element factory. Supported options:
     *   {className, text, href, target, rel, onClick}
     */
    el(tag, opts) {
        opts = opts || {};
        const node = document.createElement(tag);
        if (opts.className) node.className = opts.className;
        if (opts.text != null) node.textContent = String(opts.text);
        if (opts.href) node.href = opts.href;
        if (opts.target) node.target = opts.target;
        if (opts.rel) node.rel = opts.rel;
        if (opts.onClick) node.addEventListener("click", opts.onClick);
        return node;
    },

    /** Convenience: `<td>{text}</td>`. */
    td(text) {
        return Prl.el("td", {text: text == null ? "" : String(text)});
    },

    // --- Alerts ---------------------------------------------------------

    /** Shows a Bootstrap alert: `Prl.alert("#alert", "danger", "Failed")`. */
    alert(selector, type, msg) {
        const el = Prl.$(selector);
        if (!el) return;
        el.className = "alert alert-" + type;
        el.textContent = msg;
        el.classList.remove("d-none");
    },

    hideAlert(selector) {
        const el = Prl.$(selector);
        if (el) {
            el.classList.add("d-none");
            el.textContent = "";
        }
    },

    /**
     * Shows alerts based on URL query parameters. Useful for redirect flows:
     *   Prl.urlAlerts("#alert", {error: "Bad creds", logout: "Bye"})
     * Keys `error` and `unauthorized` produce a "danger" alert; others "success".
     */
    urlAlerts(alertSelector, mapping) {
        const params = new URLSearchParams(window.location.search);
        Object.keys(mapping).forEach(key => {
            if (!params.has(key)) return;
            const type = (key === "error" || key === "unauthorized") ? "danger" : "success";
            Prl.alert(alertSelector, type, mapping[key]);
        });
    },

    // --- Formatting -----------------------------------------------------

    /** ISO-8601 string -> user's locale string. Returns the raw input on parse failure. */
    formatDate(iso) {
        if (!iso) return "";
        const d = new Date(iso);
        return isNaN(d.getTime()) ? String(iso) : d.toLocaleString();
    },

    /**
     * Returns true if the URL is safe to use as `<a href>`. Blocks `javascript:`,
     * `data:` and other potentially dangerous schemes. Allows `http(s)://` and
     * absolute/relative paths.
     */
    isSafeUrl(url) {
        if (typeof url !== "string") return false;
        const trimmed = url.trim();
        if (!trimmed) return false;
        if (trimmed.startsWith("/") || trimmed.startsWith("./") || trimmed.startsWith("../")) return true;
        return /^https?:\/\//i.test(trimmed);
    },

    /** Bootstrap badge class for a submission status. */
    statusClass(status) {
        switch (status) {
            case "COMPLETED":
                return "bg-success";
            case "FAILED":
            case "ERROR":
            case "REJECTED":
                return "bg-danger";
            case "TIMEOUT":
                return "bg-warning text-dark";
            case "DISPATCHED":
            case "PENDING":
                return "bg-info text-dark";
            default:
                return "bg-secondary";
        }
    },

    /** Renders a `<span class="badge ...">{status}</span>` element. */
    statusBadge(status) {
        return Prl.el("span", {className: "badge " + Prl.statusClass(status), text: status || ""});
    },

    /**
     * Builds a link to a GitLab MR - `<a href={mrUrl}>!{mrIid}</a>`. Falls back to
     * a plain text node when no URL is available. Used by `data-prl-fn-html="Prl.mrLink"`.
     */
    mrLink(item) {
        if (item.mrUrl && Prl.isSafeUrl(item.mrUrl)) {
            return Prl.el("a", {href: item.mrUrl, target: "_blank", rel: "noopener", text: "!" + (item.mrIid || "")});
        }
        return document.createTextNode(item.mrIid != null ? ("!" + item.mrIid) : "");
    },

    /**
     * Default `<tr>` for any list of submissions. Options:
     *   {includeStudent}        - adds a Student column after the id (admin views).
     *   {includeResultSummary}  - appends a muted result summary column.
     */
    submissionRow(s, opts) {
        opts = opts || {};
        const tr = document.createElement("tr");
        const idTd = document.createElement("td");
        idTd.appendChild(Prl.el("a", {href: "/submission?id=" + s.id, text: "#" + s.id}));
        tr.appendChild(idTd);

        if (opts.includeStudent) tr.appendChild(Prl.td(s.displayName || s.login || ""));

        tr.appendChild(Prl.td((s.assignmentCode || "") + (s.assignmentName ? " - " + s.assignmentName : "")));

        const mrTd = document.createElement("td");
        mrTd.appendChild(Prl.mrLink(s));
        tr.appendChild(mrTd);

        const statusTd = document.createElement("td");
        statusTd.appendChild(Prl.statusBadge(s.status));
        tr.appendChild(statusTd);

        tr.appendChild(Prl.td(Prl.formatDate(s.createdAt)));

        if (opts.includeResultSummary) {
            const sum = Prl.td(s.resultSummary || "");
            sum.className = "text-muted";
            tr.appendChild(sum);
        }
        return tr;
    },

    // --- Misc -----------------------------------------------------------

    /** Reads a query-string parameter as a Long. Returns NaN when absent or non-numeric. */
    queryId(name) {
        return parseInt(new URLSearchParams(window.location.search).get(name), 10);
    },

    /** "" -> null; otherwise `parseInt(value, 10)`. Use for `<select>` values backed by Long. */
    parseLong(value) {
        return value === "" ? null : parseInt(value, 10);
    },

    /**
     * Confirm-then-call: prompts the user, then invokes the service. Errors are
     * surfaced via `alert()`. On success runs `onDone(result)`.
     */
    confirmCall(question, svc, method, args, onDone) {
        if (!confirm(question)) return;
        ViewEngine.call(svc, method, args || [])
            .then(res => onDone && onDone(res))
            .catch(err => alert(err.message || "Operation failed"));
    },

    /**
     * Populates a `<select>` with one `<option>` per item, keeping any static
     * options that already exist. `value`/`label` may be field names ("id") or
     * extractor functions (`it => it.someField`).
     */
    fillSelect(selector, items, value, label) {
        const target = Prl.$(selector);
        const fn = x => typeof x === "string" ? (it => it[x]) : x;
        const v = fn(value), l = fn(label);
        items.forEach(it => {
            const opt = document.createElement("option");
            opt.value = v(it);
            opt.textContent = l(it);
            target.appendChild(opt);
        });
    },

    /**
     * Resolves a function reference. Plain names look up `window[name]`; names
     * starting with "Prl." resolve to `Prl[name.substring(4)]`. Returns null
     * when name is empty; throws when name is set but doesn't resolve to a function.
     */
    fn(name) {
        if (!name) return null;
        const f = name.startsWith("Prl.") ? Prl[name.substring(4)] : window[name];
        if (typeof f !== "function") throw new Error("Prl.fn: '" + name + "' is not a function");
        return f;
    },

    /** Reloads the first `prlList` table on the page (or the one matching the selector). */
    reloadList(selector) {
        const el = Prl.$(selector || "[data-prl-list]");
        if (el && el.prlList) el.prlList.reload();
    },

    /** Splits "svc.method" into [svc, method] - the format used in `data-prl-list/form/options`. */
    splitTarget(target) {
        const i = target.lastIndexOf(".");
        return [target.substring(0, i), target.substring(i + 1)];
    },

    // --- Declarative field binding --------------------------------------

    /**
     * Walks the DOM under `root` and applies each item from `data` to the
     * matching `data-prl-*` attribute. Recognised attributes:
     *
     *   data-prl-field="X"          - display data.X. Format inferred from the
     *                                  field name (see `_inferFormat`) unless
     *                                  overridden by `data-prl-format`.
     *   data-prl-format="raw|date|  - explicit format override.
     *                    url|status|
     *                    short|json|
     *                    text"
     *   data-prl-fn="fnName"        - calls fn(data) and sets the result as text.
     *   data-prl-fn-html="fnName"   - calls fn(data); result must be a Node or
     *                                  string; replaces the element's children.
     *   data-prl-input="X"          - sets `el.value` (used for `<input>`/`<select>`).
     *   data-prl-show-if="X"        - toggles `d-none` off when data.X is truthy.
     *   data-prl-hide-if="X"        - toggles `d-none` on when data.X is truthy.
     */
    bindFields(root, data) {
        const r = Prl.$(root);
        if (!r) return;

        const safeApply = (selector, action) => {
            r.querySelectorAll(selector).forEach(el => {
                try {
                    action(el);
                } catch (e) {
                    console.error("Prl.bindFields: '" + selector + "' on", el, "->", e);
                }
            });
        };

        safeApply("[data-prl-field]", el => {
            const key = el.dataset.prlField;
            const fmt = el.dataset.prlFormat || Prl._inferFormat(key);
            Prl._renderField(el, fmt, data[key]);
        });
        safeApply("[data-prl-fn]", el => Prl.setText(el, Prl.fn(el.dataset.prlFn)(data)));
        safeApply("[data-prl-fn-html]", el => {
            const node = Prl.fn(el.dataset.prlFnHtml)(data);
            el.replaceChildren();
            if (node != null) el.appendChild(typeof node === "string" ? document.createTextNode(node) : node);
        });
        safeApply("[data-prl-input]", el => {
            el.value = data[el.dataset.prlInput] ?? "";
        });
        safeApply("[data-prl-show-if]", el => el.classList.toggle("d-none", !data[el.dataset.prlShowIf]));
        safeApply("[data-prl-hide-if]", el => el.classList.toggle("d-none", !!data[el.dataset.prlHideIf]));
    },

    /** Convention-based format inference for `data-prl-field` (overridden by `data-prl-format`). */
    _inferFormat(fieldName) {
        if (/(At|Date)$/.test(fieldName)) return "date";
        if (/Url$/i.test(fieldName)) return "url";
        if (/(Sha|Hash)$/i.test(fieldName)) return "short";
        if (/^status$|Status$/.test(fieldName)) return "status";
        return "text";
    },

    /** Renders a single value into `el` using the resolved format. */
    _renderField(el, format, value) {
        el.replaceChildren();
        if (value == null || value === "") {
            el.textContent = "-";
            return;
        }
        switch (format) {
            case "date":
                el.textContent = Prl.formatDate(value);
                break;
            case "url":
                if (Prl.isSafeUrl(value)) {
                    el.appendChild(Prl.el("a", {href: value, target: "_blank", rel: "noopener", text: value}));
                } else {
                    el.textContent = String(value);
                }
                break;
            case "short":
                el.appendChild(Prl.el("code", {text: String(value).substring(0, 12)}));
                break;
            case "status":
                el.appendChild(Prl.statusBadge(value));
                break;
            case "json":
                try {
                    el.textContent = JSON.stringify(JSON.parse(String(value)), null, 2);
                } catch {
                    el.textContent = String(value);
                }
                break;
            case "raw":
            case "text":
            default:
                el.textContent = String(value);
        }
    },
};

// ============================================================================
// prlList - table loader (programmatic + declarative)
// ============================================================================

/**
 * Loads a list from a ViewService and renders rows into a `<tbody>`.
 *
 * Programmatic:
 *
 *     prlList({
 *         svc:     "prl.fooService",
 *         method:  "findAll",
 *         args:    () => [filter.value],   // or fixed array; defaults to []
 *         tbody:   "#tbody",
 *         empty:   "#empty",                // optional
 *         loading: "#loading",              // optional
 *         row:     item => buildRowFor(item),
 *         reloadOn: ["#filter:change"],     // optional triggers
 *     });
 *
 * Declarative:
 *
 *     <table data-prl-list="prl.fooService.findAll"
 *            data-prl-row="myRowFn"
 *            [data-prl-args="argsFn"]
 *            [data-prl-empty="#empty"]
 *            [data-prl-loading="#loading"]
 *            [data-prl-reload-on="#filter:change,#other:input"]>
 *       <tbody></tbody>
 *     </table>
 *
 * Returns `{reload}` for programmatic re-fetch. The same handle is attached to
 * the `<table>` element as `table.prlList` for declarative pages.
 */
function prlList(opts) {
    const tbody = Prl.$(opts.tbody);
    const empty = Prl.$(opts.empty);
    const loading = Prl.$(opts.loading);
    const argsOf = typeof opts.args === "function" ? opts.args : () => opts.args || [];
    const paged = !!opts.paged;
    const pageSize = opts.pageSize || 50;

    // Offset for the next page to fetch (paged mode only). Reset on every reload().
    let offset = 0;
    // Race guards: every reload() bumps `generation` so stale in-flight responses
    // (slower than a follow-up filter change / reload click) are discarded.
    // `inFlight` blocks concurrent Load-more clicks from sending duplicate
    // requests for the same offset.
    let generation = 0;
    let inFlight = false;

    // Backend may return either a raw List<T> (legacy mode) or a PagedView<T>
    // (`{items, hasMore, total?}`) when `paged` is set. Normalise here so the
    // rest of the function deals with one shape.
    const fetchPage = () => {
        const args = paged ? [...argsOf(), {offset, limit: pageSize}] : argsOf();
        return ViewEngine.call(opts.svc, opts.method, args).then(response => {
            if (paged && response && typeof response === "object" && Array.isArray(response.items)) {
                return {items: response.items, hasMore: !!response.hasMore};
            }
            return {items: response || [], hasMore: false};
        });
    };

    const removeLoadMoreRow = () => {
        const row = tbody.querySelector("tr.prl-load-more-row");
        if (row) row.remove();
    };

    const appendLoadMoreRow = () => {
        // Match the column count of the first data row so colspan looks right.
        const colCount = tbody.firstElementChild ? tbody.firstElementChild.children.length : 1;
        const tr = document.createElement("tr");
        tr.className = "prl-load-more-row";
        const td = document.createElement("td");
        td.colSpan = colCount;
        td.className = "text-center p-2";
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "btn btn-link btn-sm";
        btn.textContent = "Load more";
        btn.addEventListener("click", loadMore);
        td.appendChild(btn);
        tr.appendChild(td);
        tbody.appendChild(tr);
    };

    const render = (items, hasMore, append) => {
        removeLoadMoreRow();
        if (!append) tbody.replaceChildren();
        if (items.length === 0 && !append) {
            if (empty) empty.classList.remove("d-none");
            return;
        }
        if (empty) empty.classList.add("d-none");
        items.forEach(item => tbody.appendChild(opts.row(item)));
        if (hasMore) appendLoadMoreRow();
        // Re-apply any text filter on the page so a search that was in effect
        // before the reload keeps hiding the same set without user input.
        if (typeof Prl.reapplyListFilters === "function") Prl.reapplyListFilters();
    };

    const reload = () => {
        const myGen = ++generation;
        inFlight = true;
        if (loading) loading.classList.remove("d-none");
        offset = 0;
        if (empty) empty.classList.add("d-none");
        return fetchPage().then(r => {
            if (myGen !== generation) return [];   // superseded
            if (loading) loading.classList.add("d-none");
            render(r.items, r.hasMore, false);
            offset = r.items.length;
            return r.items;
        }).catch(err => {
            if (myGen !== generation) throw err;
            if (loading) loading.classList.add("d-none");
            console.error("prlList load failed: " + opts.svc + "." + opts.method, err);
            throw err;
        }).finally(() => {
            if (myGen === generation) inFlight = false;
        });
    };

    const loadMore = () => {
        if (!paged) return Promise.resolve([]);
        if (inFlight) return Promise.resolve([]);   // double-click guard
        const myGen = generation;
        inFlight = true;
        return fetchPage().then(r => {
            if (myGen !== generation) return [];   // a reload superseded us
            render(r.items, r.hasMore, true);
            offset += r.items.length;
            return r.items;
        }).finally(() => {
            if (myGen === generation) inFlight = false;
        });
    };

    (opts.reloadOn || []).forEach(spec => {
        const i = spec.lastIndexOf(":");
        const el = Prl.$(spec.substring(0, i));
        if (el) el.addEventListener(spec.substring(i + 1), reload);
    });

    reload();
    return {reload, loadMore};
}

prlList.autoBind = function () {
    document.querySelectorAll("[data-prl-list]").forEach(table => {
        const tbody = table.querySelector("tbody");
        if (!tbody) return;
        const [svc, method] = Prl.splitTarget(table.dataset.prlList);
        const reloadOn = table.dataset.prlReloadOn;
        const pageSizeAttr = parseInt(table.dataset.prlPageSize, 10);
        table.prlList = prlList({
            svc, method, tbody,
            args: table.dataset.prlArgs ? Prl.fn(table.dataset.prlArgs) : undefined,
            empty: table.dataset.prlEmpty,
            loading: table.dataset.prlLoading,
            row: Prl.fn(table.dataset.prlRow),
            reloadOn: reloadOn ? reloadOn.split(",").map(s => s.trim()).filter(Boolean) : [],
            paged: table.dataset.prlPaged === "true",
            pageSize: Number.isFinite(pageSizeAttr) ? pageSizeAttr : undefined,
        });
    });
};

// ============================================================================
// prlForm - button -> ViewService binder
// ============================================================================

/**
 * Wires a button to a ViewService method. Validates input, shows alerts on
 * failure, optionally redirects on success.
 *
 * Programmatic:
 *
 *     prlForm({
 *         svc:            "prl.fooService",
 *         method:         "save",
 *         button:         "#btn-save",
 *         args:           () => [collectFormData()],
 *         alert:          "#alert",            // optional
 *         validate:       () => "error message" or null,   // optional
 *         onSuccess:      result => { ... },                // optional
 *         successMessage: "Saved.",                          // optional
 *         redirect:       "/somewhere",                      // optional
 *         redirectDelay:  800,                               // optional ms
 *     });
 *
 * Declarative:
 *
 *     <button data-prl-form="prl.fooService.save"
 *             data-prl-args="argsFn"
 *             [data-prl-validate="validateFn"]
 *             [data-prl-success="onSuccessFn"]
 *             [data-prl-success-msg="Saved."]
 *             [data-prl-redirect="/"]
 *             [data-prl-redirect-delay="800"]
 *             [data-prl-alert="#alert"]>Save</button>
 */
function prlForm(opts) {
    const btn = Prl.$(opts.button);
    btn.addEventListener("click", () => {
        // Disable for the duration of the request so a double-click can't
        // submit the same payload twice (creating duplicate students /
        // assignments / etc on the backend before the first save settles).
        if (btn.disabled) return;
        Prl.hideAlert(opts.alert);
        const err = opts.validate && opts.validate();
        if (err) {
            Prl.alert(opts.alert, "danger", err);
            return;
        }
        const args = opts.args ? opts.args() : [];
        btn.disabled = true;
        ViewEngine.call(opts.svc, opts.method, args).then(result => {
            if (opts.successMessage) Prl.alert(opts.alert, "success", opts.successMessage);
            if (opts.onSuccess) opts.onSuccess(result);
            if (opts.redirect) setTimeout(() => {
                window.location.href = opts.redirect;
            }, opts.redirectDelay || 0);
        }).catch(err => {
            Prl.alert(opts.alert, "danger", err.message || "Failed");
        }).finally(() => {
            btn.disabled = false;
        });
    });
}

prlForm.autoBind = function () {
    document.querySelectorAll("[data-prl-form]").forEach(btn => {
        const [svc, method] = Prl.splitTarget(btn.dataset.prlForm);
        prlForm({
            svc, method, button: btn,
            args: btn.dataset.prlArgs ? Prl.fn(btn.dataset.prlArgs) : null,
            alert: btn.dataset.prlAlert,
            validate: btn.dataset.prlValidate ? Prl.fn(btn.dataset.prlValidate) : null,
            onSuccess: btn.dataset.prlSuccess ? Prl.fn(btn.dataset.prlSuccess) : null,
            successMessage: btn.dataset.prlSuccessMsg,
            redirect: btn.dataset.prlRedirect,
            redirectDelay: btn.dataset.prlRedirectDelay ? parseInt(btn.dataset.prlRedirectDelay, 10) : 0,
        });
    });
};

// ============================================================================
// prlOptions - declarative <select> loader
// ============================================================================

/**
 * Populates `<select>` elements that carry the `data-prl-options` attribute.
 *
 *     <select data-prl-options="prl.fooService.findAll"
 *             [data-prl-args="[null]"]       JSON array literal, default []
 *             [data-prl-value="id"]           field name, default "id"
 *             [data-prl-label="name"]>        field name, default "name"
 *       <option value="">- None -</option>      static options are kept
 *     </select>
 *
 * For computed labels use `data-prl-label-fn="myLabelFn"` instead of `-label`.
 */
const prlOptions = {
    autoBind() {
        document.querySelectorAll("[data-prl-options]").forEach(sel => {
            const [svc, method] = Prl.splitTarget(sel.dataset.prlOptions);
            let args;
            try {
                args = sel.dataset.prlArgs ? JSON.parse(sel.dataset.prlArgs) : [];
            } catch (e) {
                console.error("prlOptions: invalid JSON in data-prl-args on", sel, "->", e);
                return;
            }
            const labelFn = sel.dataset.prlLabelFn;
            ViewEngine.call(svc, method, args).then(items => {
                Prl.fillSelect(sel, items || [],
                    sel.dataset.prlValue || "id",
                    labelFn ? Prl.fn(labelFn) : (sel.dataset.prlLabel || "name"));
            }).catch(err => {
                console.error("prlOptions load failed: " + svc + "." + method, err);
            });
        });
    },
};

// ============================================================================
// prlSearchSelect - searchable single-select combobox built on a text <input>
// ============================================================================

/**
 * Wires an `<input>` carrying `data-prl-search-select="svc.method"` as a
 * searchable single-select. The list is fetched once on bind; typing filters
 * the dropdown client-side; picking writes the value into `data-value` and
 * dispatches a synthetic `change` event so existing `data-prl-reload-on`
 * wiring keeps working unchanged.
 *
 *     <input type="text" id="group-filter"
 *            data-prl-search-select="prl.studentGroupViewService.getGroups"
 *            data-prl-args="[]"
 *            data-prl-value="id"
 *            data-prl-label="name"
 *            data-prl-all-label="All groups"
 *            data-prl-extra='[{"value":-1,"label":"- Without group -"}]'>
 *
 * Page code reads the picked id with `Prl.dataVal("#group-filter")`. The
 * visible input text is the label; `data-value` holds the chosen value.
 * Empty input (cleared) means "no selection" - `data-value` is "".
 */
const prlSearchSelect = {
    autoBind() {
        document.querySelectorAll("[data-prl-search-select]").forEach(input => {
            const [svc, method] = Prl.splitTarget(input.dataset.prlSearchSelect);
            let args;
            try {
                args = input.dataset.prlArgs ? JSON.parse(input.dataset.prlArgs) : [];
            } catch (e) {
                console.error("prlSearchSelect: invalid JSON in data-prl-args on", input, e);
                return;
            }
            const valueKey = input.dataset.prlValue || "id";
            const labelKey = input.dataset.prlLabel || "name";
            // Lazy resolution: page-defined formatter functions may not be on
            // window at autoBind time depending on script order. Re-check on
            // every item so the moment the function is defined we pick it up.
            const resolveFn = key => {
                if (typeof window[key] === "function") return window[key];
                if (Prl[key] && typeof Prl[key] === "function") return Prl[key];
                return null;
            };
            const valueFn = it => {
                const f = resolveFn(valueKey);
                return f ? f(it) : it[valueKey];
            };
            const labelFn = it => {
                const f = resolveFn(labelKey);
                return f ? f(it) : it[labelKey];
            };
            const allLabel = input.dataset.prlAllLabel || "";
            let extra = [];
            if (input.dataset.prlExtra) {
                try {
                    extra = JSON.parse(input.dataset.prlExtra) || [];
                } catch (e) {
                    console.error("prlSearchSelect: invalid JSON in data-prl-extra on", input, e);
                }
            }

            ViewEngine.call(svc, method, args).then(items => {
                prlSearchSelect._attach(input, items || [], valueFn, labelFn, allLabel, extra);
            }).catch(err => {
                console.error("prlSearchSelect load failed: " + svc + "." + method, err);
            });
        });
    },

    _attach(input, items, valueFn, labelFn, allLabel, extra) {
        // Normalise to a flat [{value, label}] list with the "all/none" option first.
        const options = [];
        if (allLabel) options.push({value: "", label: allLabel});
        extra.forEach(o => options.push({value: o.value, label: o.label}));
        items.forEach(it => options.push({value: valueFn(it), label: String(labelFn(it) ?? "")}));

        input.setAttribute("autocomplete", "off");
        if (!input.getAttribute("data-value")) input.setAttribute("data-value", "");
        if (input.value === "" && allLabel) input.value = allLabel;

        const dropdown = document.createElement("div");
        dropdown.className = "list-group position-absolute w-100 shadow";
        dropdown.style.display = "none";
        // 1050 = above Bootstrap navbar/sticky elements. Anything lower can
        // be hidden by sibling cards once the dropdown overflows its own
        // card.
        dropdown.style.zIndex = "1050";
        dropdown.style.maxHeight = "240px";
        dropdown.style.overflowY = "auto";

        // Float the dropdown over the input - wrap if the parent isn't
        // positioned, and lift the surrounding card above its siblings so
        // overflowing dropdown items don't slip behind the next card.
        const parent = input.parentElement;
        if (parent && getComputedStyle(parent).position === "static") {
            parent.style.position = "relative";
        }
        const card = input.closest(".card");
        if (card) {
            card.style.position = "relative";
            // Keep above later cards in document flow; 1040 < 1050 so the
            // dropdown still beats the card's own header/etc.
            if (!card.style.zIndex || parseInt(card.style.zIndex, 10) < 1040) {
                card.style.zIndex = "1040";
            }
        }
        parent.appendChild(dropdown);

        function pick(opt) {
            input.value = opt.label;
            input.setAttribute("data-value", opt.value == null ? "" : String(opt.value));
            close();
            input.dispatchEvent(new Event("change", {bubbles: true}));
        }

        function close() {
            dropdown.style.display = "none";
        }

        function render(filter) {
            dropdown.replaceChildren();
            const q = (filter || "").trim().toLowerCase();
            const matches = (!q ? options : options.filter(o => (o.label || "").toLowerCase().indexOf(q) !== -1));
            if (matches.length === 0) {
                dropdown.appendChild(Prl.el("div", {
                    className: "list-group-item text-muted small",
                    text: "No matches",
                }));
            } else {
                matches.slice(0, 100).forEach(opt => {
                    const btn = Prl.el("button", {
                        className: "list-group-item list-group-item-action text-truncate",
                        text: opt.label,
                    });
                    btn.type = "button";
                    btn.addEventListener("mousedown", e => {
                        e.preventDefault();
                        pick(opt);
                    });
                    dropdown.appendChild(btn);
                });
            }
            dropdown.style.display = "block";
        }

        input.addEventListener("input", () => {
            // Typing only filters the dropdown - the chosen value (data-value)
            // changes only on an explicit pick. Otherwise blurring after a
            // half-typed query would silently clear the filter without
            // reloading the dependent list.
            render(input.value);
        });
        input.addEventListener("focus", () => {
            // Clear the visible label on focus so the user can start typing
            // immediately. Without this they'd be appending after the current
            // pick's label (e.g. "All studentsXYZ") and get zero matches.
            // The picked value is kept in data-value and restored on blur if
            // no new option is chosen.
            input.value = "";
            render("");
            input.select();
        });
        // After a pick, focus stays on the input but the dropdown was closed.
        // Clicking the input again wouldn't fire `focus` (still focused), so
        // listen for clicks to re-open. Re-rendering with the current value
        // is safe since "input" / "focus" do the same.
        input.addEventListener("click", () => {
            if (dropdown.style.display === "none") {
                if (document.activeElement === input) input.value = "";
                render("");
            }
        });
        input.addEventListener("blur", () => setTimeout(() => {
            close();
            // Snap the visible label back to whatever the current pick says,
            // so a half-typed query doesn't linger in the input.
            const cur = input.getAttribute("data-value") || "";
            const found = options.find(o => String(o.value) === String(cur));
            input.value = found ? found.label : (allLabel || "");
        }, 150));
        input.addEventListener("keydown", e => {
            if (e.key === "Escape") {
                close();
                input.blur();
            }
        });
    },
};

// ============================================================================
// prlListFilter - simple text-substring filter over a static node list
// ============================================================================

/**
 * Hide/show DOM nodes matching a CSS selector based on the substring of an
 * `<input>`. Each candidate node must carry its own `data-search` attribute
 * (lower-cased haystack of searchable fields); the filter is case-insensitive
 * and matches anywhere in that string. Using a pre-built attribute beats
 * scanning `textContent` because the latter also picks up badge labels
 * ("Active") and column values you didn't want to be searchable.
 *
 * Programmatic:
 *
 *     const apply = Prl.attachListFilter("#search-input", ".my-item");
 *     // ...later, after re-rendering rows:
 *     apply();
 *
 * Declarative - add the attribute to the input and forget about wiring:
 *
 *     <input id="search" data-prl-list-filter=".my-item">
 *
 * Returns the apply() function so callers that re-render the target nodes
 * (e.g. inside a `data-prl-list` reload) can re-apply the current filter.
 */
Prl.attachListFilter = function (input, itemsSelector) {
    const el = typeof input === "string" ? document.querySelector(input) : input;
    if (!el) return () => {};
    const apply = () => {
        const q = (el.value || "").trim().toLowerCase();
        document.querySelectorAll(itemsSelector).forEach(node => {
            const hay = node.getAttribute("data-search") || "";
            const visible = !q || hay.indexOf(q) !== -1;
            // setProperty with `important` beats Bootstrap's `d-flex` / `d-block`
            // classes (they're `display: ... !important` and a plain inline
            // style.display would lose the specificity match).
            if (visible) node.style.removeProperty("display");
            else node.style.setProperty("display", "none", "important");
        });
    };
    el.addEventListener("input", apply);
    el.prlListFilterApply = apply;
    return apply;
};

/**
 * Re-applies every `data-prl-list-filter` on the page. Call after a
 * `data-prl-list` table reloads its rows so the active search keeps hiding
 * the same set without the user touching the input.
 */
Prl.reapplyListFilters = function () {
    document.querySelectorAll("[data-prl-list-filter]").forEach(input => {
        if (typeof input.prlListFilterApply === "function") input.prlListFilterApply();
    });
};

const prlListFilter = {
    autoBind() {
        document.querySelectorAll("[data-prl-list-filter]").forEach(input => {
            Prl.attachListFilter(input, input.dataset.prlListFilter);
        });
    },
};

// Catch-all input delegation - handles the corner case where the input
// element gets re-created after autoBind() ran (e.g. dynamically-rendered
// forms) or autoBind missed the element because it appeared later. The
// listener fires unconditionally on every input event in the document; it
// only does work when the source carries data-prl-list-filter.
document.addEventListener("input", function (e) {
    const t = e.target;
    if (t && t.getAttribute && t.getAttribute("data-prl-list-filter")) {
        const q = (t.value || "").trim().toLowerCase();
        document.querySelectorAll(t.getAttribute("data-prl-list-filter")).forEach(node => {
            const hay = node.getAttribute("data-search") || "";
            const visible = !q || hay.indexOf(q) !== -1;
            if (visible) node.style.removeProperty("display");
            else node.style.setProperty("display", "none", "important");
        });
    }
});

// ============================================================================
// prlDetail - declarative one-shot detail loader
// ============================================================================

/**
 * Loads a single record on page load and renders it via `Prl.bindFields`.
 *
 *     <div data-prl-detail="prl.fooService.getById"
 *          [data-prl-fallback="prl.barService.getMyById"]   tried after a 403/404
 *          [data-prl-arg="id"]                              URL query param -> first arg
 *          [data-prl-not-found="#not-found"]                shown when both calls fail
 *          [data-prl-success="onLoadedFn"]                  extra callback after bind
 *          class="d-none">
 *       <span data-prl-field="status"></span>
 *       <td data-prl-fn="myFormatter"></td>
 *       ...
 *     </div>
 *
 * On success the framework removes `d-none` from the container and runs
 * `Prl.bindFields(root, data)` so all `data-prl-field`/`-fn`/`-input`/`-show-if`
 * children get populated.
 */
const prlDetail = {
    autoBind() {
        document.querySelectorAll("[data-prl-detail]").forEach(root => {
            const [svc, method] = Prl.splitTarget(root.dataset.prlDetail);
            const argName = root.dataset.prlArg;
            const args = argName ? [Prl.queryId(argName)] : [];
            if (argName && isNaN(args[0])) {
                prlDetail._notFound(root);
                return;
            }

            const onSuccess = data => {
                root.classList.remove("d-none");
                Prl.bindFields(root, data);
                if (root.dataset.prlSuccess) Prl.fn(root.dataset.prlSuccess)(data);
            };

            ViewEngine.call(svc, method, args).then(onSuccess).catch(() => {
                const fallback = root.dataset.prlFallback;
                if (fallback) {
                    const [fbSvc, fbMethod] = Prl.splitTarget(fallback);
                    ViewEngine.call(fbSvc, fbMethod, args).then(onSuccess).catch(() => prlDetail._notFound(root));
                } else {
                    prlDetail._notFound(root);
                }
            });
        });
    },

    _notFound(root) {
        const target = root.dataset.prlNotFound ? Prl.$(root.dataset.prlNotFound) : null;
        if (target) target.classList.remove("d-none");
    },
};

// ============================================================================
// Auto-binding bootstrap. Pages declare wiring through HTML attributes; this
// hook applies them once the DOM is ready, so most pages need no inline JS at all.
// ============================================================================

document.addEventListener("DOMContentLoaded", () => {
    prlOptions.autoBind();
    prlSearchSelect.autoBind();
    prlListFilter.autoBind();
    prlList.autoBind();
    prlForm.autoBind();
    prlDetail.autoBind();
});
