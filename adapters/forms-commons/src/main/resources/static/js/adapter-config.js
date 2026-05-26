/**
 * Shared front-end for /adapter-http and /adapter-rabbit config pages.
 * Both pages are list-of-nodes with expandable inline panels for engine
 * config + loaded adapters + loaded resource providers.
 *
 * The page bootstraps with:
 *   <script>AdapterConfig.init({svc: "prl.httpAdapterConfigViewService"});</script>
 * which sets the target ViewService bean for every RPC.
 */
window.AdapterConfig = (function () {
    var SVC = null;
    var expandedNodeId = null;
    var expandedRow = null;
    var openSeq = 0;

    // --- bootstrap ---------------------------------------------------

    function init(opts) {
        SVC = opts.svc;
        document.addEventListener("DOMContentLoaded", function () {
            var refresh = document.getElementById("btn-refresh");
            if (refresh) refresh.addEventListener("click", refreshNodes);
            loadNodes();
        });
    }

    function loadNodes() {
        document.getElementById("loading").style.display = "";
        document.getElementById("nodes-list").style.display = "none";
        document.getElementById("empty").classList.add("d-none");
        ViewEngine.call(SVC, "getNodes").then(function (nodes) {
            document.getElementById("loading").style.display = "none";
            renderNodes(nodes || []);
        }).catch(function (err) {
            document.getElementById("loading").style.display = "none";
            Prl.alert("#main-alert", "danger", "Failed to load: " + (err.message || err));
        });
    }

    function refreshNodes() {
        var btn = document.getElementById("btn-refresh");
        if (btn) btn.disabled = true;
        ViewEngine.call(SVC, "refreshAndPruneNodes").then(function (nodes) {
            renderNodes(nodes || []);
            Prl.alert("#main-alert", "success", "Node registry refreshed.");
        }).catch(function (err) {
            Prl.alert("#main-alert", "danger", "Refresh failed: " + (err.message || err));
        }).finally(function () {
            if (btn) btn.disabled = false;
        });
    }

    // --- list render -------------------------------------------------

    function renderNodes(nodes) {
        var container = document.getElementById("nodes-list");
        container.replaceChildren();
        expandedNodeId = null;
        expandedRow = null;
        if (nodes.length === 0) {
            document.getElementById("empty").classList.remove("d-none");
            return;
        }
        var wrap = document.createElement("div");
        wrap.className = "list-group";
        nodes.forEach(function (n) {
            wrap.appendChild(buildRow(n));
        });
        container.appendChild(wrap);
        container.style.display = "";
        Prl.reapplyListFilters();
    }

    function buildRow(node) {
        var item = document.createElement("div");
        item.className = "list-group-item list-group-item-action node-item";
        item.style.cursor = "pointer";
        item.setAttribute("data-node-id", node.nodeId || "");
        var transports = (node.transports || []).map(function (t) {
            var cfg = (t.config && (t.config.host || t.config.port))
                ? " " + (t.config.host || "?") + ":" + (t.config.port || "?")
                : "";
            return (t.type || "?") + cfg;
        }).join(", ");
        var haystack = [node.nodeId, transports].filter(Boolean).join(" ").toLowerCase();
        item.setAttribute("data-search", haystack);

        var head = document.createElement("div");
        head.className = "d-flex justify-content-between align-items-center";

        var info = document.createElement("div");
        info.appendChild(Prl.el("strong", {text: node.nodeId || "(no id)"}));
        if (transports) info.appendChild(document.createTextNode(" - " + transports));
        var providers = (node.resourceProviders || []).map(function (p) {
            return p.name;
        }).filter(Boolean).join(", ");
        var sub = document.createElement("small");
        sub.className = "text-muted d-block";
        sub.textContent =
            (node.registeredAt ? "registered " + Prl.formatDate(node.registeredAt) : "")
            + (providers ? " | providers: " + providers : "");
        info.appendChild(sub);

        var actions = document.createElement("div");
        actions.className = "btn-group btn-group-sm";
        var healthBtn = Prl.el("button", {
            className: "btn btn-outline-info", text: "Health check",
            onClick: function (e) {
                e.stopPropagation();
                doHealthCheck(node.nodeId);
            },
        });
        actions.appendChild(healthBtn);
        var removeBtn = Prl.el("button", {
            className: "btn btn-outline-danger", text: "Remove",
            onClick: function (e) {
                e.stopPropagation();
                Prl.confirmCall(
                    "Remove node '" + node.nodeId + "' from the registry?",
                    SVC, "removeNode", [node.nodeId], function () {
                        Prl.alert("#main-alert", "success", "Node removed.");
                        loadNodes();
                    },
                );
            },
        });
        actions.appendChild(removeBtn);

        head.appendChild(info);
        head.appendChild(actions);
        item.appendChild(head);
        item.addEventListener("click", function (e) {
            if (e.target.closest("button")) return;
            toggleExpand(node, item);
        });
        return item;
    }

    function doHealthCheck(nodeId) {
        ViewEngine.call(SVC, "healthCheck", [nodeId]).then(function (r) {
            Prl.alert(
                "#main-alert", r.healthy ? "success" : "warning",
                "Health check on '" + nodeId + "': " + (r.healthy ? "ALIVE" : "DEAD"),
            );
        }).catch(function (err) {
            Prl.alert("#main-alert", "danger", "Health check failed: " + (err.message || err));
        });
    }

    // --- expand panel ------------------------------------------------

    function toggleExpand(node, rowEl) {
        if (expandedNodeId === node.nodeId) {
            collapse();
            return;
        }
        collapse();
        expandedNodeId = node.nodeId;
        var token = ++openSeq;
        var panel = document.createElement("div");
        panel.className = "list-group-item bg-body-tertiary";
        panel.appendChild(spinner());
        rowEl.parentNode.insertBefore(panel, rowEl.nextSibling);
        expandedRow = panel;
        loadPanelData(node.nodeId).then(function (data) {
            if (token !== openSeq) return;
            panel.replaceChildren(buildPanel(node.nodeId, data));
        }).catch(function (err) {
            if (token !== openSeq) return;
            panel.replaceChildren(errorBox(err));
        });
    }

    function collapse() {
        expandedNodeId = null;
        ++openSeq;
        if (expandedRow) {
            expandedRow.remove();
            expandedRow = null;
        }
    }

    function loadPanelData(nodeId) {
        return Promise.all([
            ViewEngine.call(SVC, "queueStatus", [nodeId]),
            ViewEngine.call(SVC, "listAdapters", [nodeId]),
            ViewEngine.call(SVC, "listAvailableAdapters", [nodeId]),
            ViewEngine.call(SVC, "listResourceProviders", [nodeId]),
            ViewEngine.call(SVC, "listAvailableResourceProviders", [nodeId]),
        ]).then(function (results) {
            return {
                queueStatus: results[0],
                adapters: results[1] || [],
                availableAdapters: results[2] || [],
                providers: results[3] || [],
                availableProviders: results[4] || [],
            };
        });
    }

    function buildPanel(nodeId, data) {
        var box = document.createElement("div");
        box.appendChild(buildEngineConfigSection(nodeId, data.queueStatus));
        box.appendChild(buildAdaptersSection(nodeId, data.adapters, data.availableAdapters));
        box.appendChild(buildProvidersSection(nodeId, data.providers, data.availableProviders));
        return box;
    }

    function buildEngineConfigSection(nodeId, queueStatus) {
        var ec = (queueStatus && queueStatus.engineConfig) || {};
        var card = Prl.el("div", {className: "card mb-3"});
        var header = makeCardHeader("bi-gear", "Engine Config");
        card.appendChild(header);
        var body = Prl.el("div", {className: "card-body"});

        var fields = [
            ["Max correctness workers", "maxCorrectnessWorkers", "number"],
            ["Job retention (s)", "jobRetentionSeconds", "number"],
            ["Default memory limit (MB)", "defaultMemoryLimitMb", "number"],
            ["Default threads", "defaultThreads", "number"],
            ["Default wall time (s)", "defaultWallTimeSec", "number"],
            ["Default CPU time (s)", "defaultCpuTimeSec", "number"],
            ["Sandbox process multiplier", "sandboxProcessMultiplier", "number"],
        ];
        var row = Prl.el("div", {className: "row g-2"});
        fields.forEach(function (f) {
            var col = Prl.el("div", {className: "col-md-3"});
            col.appendChild(Prl.el("label", {className: "form-label small text-muted", text: f[0]}));
            var input = document.createElement("input");
            input.type = f[2];
            input.className = "form-control form-control-sm config-" + f[1];
            input.value = (ec[f[1]] == null ? "" : ec[f[1]]);
            col.appendChild(input);
            row.appendChild(col);
        });
        body.appendChild(row);

        var alertEl = Prl.el("div", {className: "alert mt-2 d-none config-alert"});
        body.appendChild(alertEl);

        var save = Prl.el("button", {className: "btn btn-primary btn-sm mt-2", text: "Save engine config"});
        save.addEventListener("click", function () {
            if (save.disabled) return;
            save.disabled = true;
            var payload = {nodeId: nodeId};
            fields.forEach(function (f) {
                var v = body.querySelector(".config-" + f[1]).value;
                payload[f[1]] = v === "" ? null : parseInt(v, 10);
            });
            ViewEngine.call(SVC, "updateConfig", [payload]).then(function () {
                Prl.alert(alertEl, "success", "Config saved.");
            }).catch(function (err) {
                Prl.alert(alertEl, "danger", err.message || err);
            }).finally(function () {
                save.disabled = false;
            });
        });
        body.appendChild(save);

        card.appendChild(body);
        return card;
    }

    function buildAdaptersSection(nodeId, loaded, available) {
        return buildPluginCard("Loaded adapters", "bi-plug", "adapter",
            loaded, available,
            function (name, configJson) {
                return ViewEngine.call(SVC, "loadAdapter",
                    [{nodeId: nodeId, adapterName: name, config: configJson}]);
            },
            function (name) {
                return ViewEngine.call(SVC, "unloadAdapter",
                    [{nodeId: nodeId, adapterName: name}]);
            },
        );
    }

    function buildProvidersSection(nodeId, loaded, available) {
        return buildPluginCard("Loaded resource providers", "bi-archive", "provider",
            loaded, available,
            function (name, configJson) {
                return ViewEngine.call(SVC, "loadResourceProvider",
                    [{nodeId: nodeId, providerName: name, config: configJson}]);
            },
            function (name) {
                return ViewEngine.call(SVC, "unloadResourceProvider",
                    [{nodeId: nodeId, providerName: name}]);
            },
        );
    }

    /**
     * Common card UI for adapters / providers - identical layout, only the
     * load/unload callbacks differ.
     */
    function buildPluginCard(title, icon, kind, loaded, available, loadFn, unloadFn) {
        var card = Prl.el("div", {className: "card mb-3"});
        var header = makeCardHeader(icon, title);
        card.appendChild(header);
        var body = Prl.el("div", {className: "card-body"});

        var loadedList = renderPluginList(loaded, function (name) {
            if (!confirm("Unload " + kind + " '" + name + "'?")) return;
            unloadFn(name).then(function () {
                refreshPanel();
            }).catch(function (err) {
                alert((err && err.message) || "Failed to unload " + kind);
            });
        });
        body.appendChild(loadedList);

        // Loader row: select available, optional config JSON, Load button.
        var loaderRow = Prl.el("div", {className: "row g-2 mt-2"});
        var selCol = Prl.el("div", {className: "col-md-4"});
        var select = document.createElement("select");
        select.className = "form-select form-select-sm";
        var placeholderOpt = document.createElement("option");
        placeholderOpt.value = "";
        placeholderOpt.textContent = "- pick available " + kind + " -";
        select.appendChild(placeholderOpt);
        available.forEach(function (p) {
            var opt = document.createElement("option");
            opt.value = p.name;
            opt.textContent = p.name + (p.status ? " (" + p.status + ")" : "");
            select.appendChild(opt);
        });
        selCol.appendChild(select);
        loaderRow.appendChild(selCol);

        var cfgCol = Prl.el("div", {className: "col-md-6"});
        var cfgInput = document.createElement("input");
        cfgInput.type = "text";
        cfgInput.className = "form-control form-control-sm font-monospace";
        cfgInput.placeholder = "Optional JSON config";
        cfgCol.appendChild(cfgInput);
        loaderRow.appendChild(cfgCol);

        var btnCol = Prl.el("div", {className: "col-md-2"});
        var loadBtn = Prl.el("button", {className: "btn btn-success btn-sm w-100", text: "Load"});
        loadBtn.addEventListener("click", function () {
            if (loadBtn.disabled) return;
            var name = select.value;
            if (!name) {
                alert("Pick a " + kind + " first.");
                return;
            }
            loadBtn.disabled = true;
            loadFn(name, cfgInput.value || null).then(function () {
                refreshPanel();
            }).catch(function (err) {
                alert((err && err.message) || "Failed to load " + kind);
            }).finally(function () {
                loadBtn.disabled = false;
            });
        });
        btnCol.appendChild(loadBtn);
        loaderRow.appendChild(btnCol);

        body.appendChild(loaderRow);
        card.appendChild(body);
        return card;
    }

    function renderPluginList(items, onUnload) {
        var ul = Prl.el("ul", {className: "list-group list-group-flush"});
        if (!items || items.length === 0) {
            ul.appendChild(Prl.el("li", {
                className: "list-group-item text-muted text-center small",
                text: "(none loaded)",
            }));
            return ul;
        }
        items.forEach(function (it) {
            var li = Prl.el("li", {className: "list-group-item d-flex justify-content-between align-items-center"});
            var info = Prl.el("span", {});
            info.appendChild(Prl.el("strong", {text: it.name || "?"}));
            if (it.status) {
                info.appendChild(document.createTextNode(" "));
                var b = Prl.el("span", {
                    className: "badge " + (String(it.status).toLowerCase() === "running" ? "bg-success" : "bg-secondary"),
                    text: String(it.status),
                });
                info.appendChild(b);
            }
            if (it.config) {
                var pre = Prl.el("pre", {className: "mb-0 mt-1 text-muted small font-monospace"});
                try {
                    pre.textContent = JSON.stringify(it.config, null, 2);
                } catch (_) {
                    pre.textContent = String(it.config);
                }
                var wrap = Prl.el("div", {});
                wrap.appendChild(info);
                wrap.appendChild(pre);
                li.appendChild(wrap);
            } else {
                li.appendChild(info);
            }
            var unloadBtn = Prl.el("button", {
                className: "btn btn-sm btn-outline-warning", text: "Unload",
                onClick: function () {
                    onUnload(it.name);
                },
            });
            li.appendChild(unloadBtn);
            ul.appendChild(li);
        });
        return ul;
    }

    function refreshPanel() {
        if (!expandedNodeId) return;
        var nodeId = expandedNodeId;
        var token = ++openSeq;
        if (expandedRow) expandedRow.replaceChildren(spinner());
        loadPanelData(nodeId).then(function (data) {
            if (token !== openSeq || !expandedRow) return;
            expandedRow.replaceChildren(buildPanel(nodeId, data));
        }).catch(function (err) {
            if (token !== openSeq || !expandedRow) return;
            expandedRow.replaceChildren(errorBox(err));
        });
    }

    function makeCardHeader(iconClass, title) {
        var header = Prl.el("div", {className: "card-header"});
        var icon = document.createElement("i");
        icon.className = "bi " + iconClass;
        header.appendChild(icon);
        header.appendChild(document.createTextNode(" " + title));
        return header;
    }

    function spinner() {
        var d = document.createElement("div");
        d.className = "text-center py-3";
        var spin = document.createElement("div");
        spin.className = "spinner-border spinner-border-sm";
        d.appendChild(spin);
        d.appendChild(document.createTextNode(" Loading..."));
        return d;
    }

    function errorBox(err) {
        var d = document.createElement("div");
        d.className = "alert alert-danger mb-0";
        d.textContent = (err && err.message) || "Failed to load node details";
        return d;
    }

    return {init: init};
})();
