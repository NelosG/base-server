/**
 * Common utilities and page controller for adapter test pages (HTTP and RabbitMQ).
 */
var AdapterTestCommon = (function () {

    var currentSVC;
    var selectedNodeId = null;
    var transportType = "HTTP";
    var knownJobIds = [];
    var adapterConfigCache = {};   // {nodeId: {adapterName: configMap}}
    var providerConfigCache = {};  // {nodeId: {providerName: configMap}}

    // ── Toast Container (created once) ────────────────────────────

    function ensureToastContainer() {
        var c = document.getElementById("prl-toast-container");
        if (!c) {
            c = document.createElement("div");
            c.id = "prl-toast-container";
            c.className = "prl-toast-container";
            document.body.appendChild(c);
        }
        return c;
    }

    // ── Utilities ──────────────────────────────────────────

    function escapeHtml(str) {
        var div = document.createElement("div");
        div.textContent = str;
        return div.innerHTML;
    }

    function log(label, data) {
        var el = document.getElementById("response-log");
        var ts = new Date().toLocaleTimeString();
        var jsonStr = JSON.stringify(data, null, 2);
        var isError = label.indexOf("ERROR") !== -1;

        var entry = document.createElement("div");
        entry.className = "prl-log-entry" + (isError ? " prl-log-error" : " prl-log-success");
        entry.innerHTML =
            "<div>" +
                "<span class='prl-log-ts'>" + escapeHtml(ts) + "</span>" +
                "<span class='prl-log-label'>" + escapeHtml(label) + "</span>" +
            "</div>" +
            "<div class='prl-log-json'>" + escapeHtml(jsonStr) + "</div>";

        // Remove placeholder text if first real entry
        var placeholder = el.querySelector(".prl-log-placeholder");
        if (placeholder) placeholder.remove();

        el.appendChild(entry);
        el.scrollTop = el.scrollHeight;
    }

    function formatCapabilities(caps) {
        if (!caps) return "";
        var items = [];
        if (Array.isArray(caps)) {
            items = caps;
        } else {
            items = Object.keys(caps).map(function (k) {
                return k + "=" + caps[k];
            });
        }
        return items.map(function (c) {
            return "<span class='prl-badge'>" + escapeHtml(String(c)) + "</span>";
        }).join(" ");
    }

    function formatTransports(transports) {
        if (!transports || transports.length === 0) return "<span class='text-muted'>—</span>";
        return transports.map(function (t) {
            var statusClass = t.status === "running" ? "prl-badge-success" : "prl-badge-secondary";
            var label = escapeHtml((t.type || "?").toUpperCase());
            var status = t.status ? (" <small>(" + escapeHtml(t.status) + ")</small>") : "";
            var endpoint = "";
            if (t.config) {
                var h = t.config.host;
                var p = t.config.port;
                if (h && p) endpoint = " <small>" + escapeHtml(h + ":" + p) + "</small>";
            }
            return "<span class='prl-badge " + statusClass + "'>" + label + endpoint + status + "</span>";
        }).join(" ");
    }

    function showAlert(msg, type) {
        var container = ensureToastContainer();
        var toast = document.createElement("div");
        toast.className = "prl-toast prl-toast-" + type;
        toast.textContent = msg;
        container.appendChild(toast);

        setTimeout(function () {
            toast.classList.add("prl-toast-hiding");
            setTimeout(function () {
                if (toast.parentNode) toast.parentNode.removeChild(toast);
            }, 300);
        }, 3500);
    }

    // ── Result Popup Modal ─────────────────────────────────

    function showResultPopup(title, data) {
        var modal = document.getElementById("result-modal");
        if (!modal) return;
        var titleEl = modal.querySelector(".modal-title");
        var bodyEl = modal.querySelector(".modal-body pre");
        if (titleEl) titleEl.textContent = title;
        if (bodyEl) bodyEl.textContent = JSON.stringify(data, null, 2);

        var bsModal = bootstrap.Modal.getOrCreateInstance(modal);
        bsModal.show();
    }

    function clearLog() {
        var el = document.getElementById("response-log");
        el.innerHTML = "<div class='prl-log-placeholder prl-empty-state'>" +
            "<i class='bi bi-terminal'></i><span>Waiting for actions...</span></div>";
    }

    function renderNodes(nodes, options) {
        var hasActions = options && options.hasActions;
        var selectFn = options && options.onSelect;

        document.getElementById("nodes-loading").style.display = "none";
        var tbody = document.getElementById("nodes-tbody");
        tbody.innerHTML = "";

        if (!nodes || nodes.length === 0) {
            document.getElementById("nodes-table").style.display = "none";
            document.getElementById("nodes-empty").style.display = "";
            return;
        }

        document.getElementById("nodes-table").style.display = "";
        document.getElementById("nodes-empty").style.display = "none";

        nodes.forEach(function (n) {
            var tr = document.createElement("tr");
            if (selectedNodeId && n.nodeId === selectedNodeId) {
                tr.className = "prl-row-selected";
            }
            if (selectFn) {
                tr.style.cursor = "pointer";
                tr.onclick = function () {
                    selectFn(n.nodeId);
                };
            }

            var html = "<td><code>" + escapeHtml(n.nodeId) + "</code></td>";
            html += "<td>" + formatCapabilities(n.capabilities) + "</td>";
            html += "<td>" + formatTransports(n.transports) + "</td>";
            html += "<td><small>" + escapeHtml(n.registeredAt || "") + "</small></td>";

            if (hasActions) {
                html += "<td><button class='btn btn-sm btn-outline-danger' title='Remove from registry'" +
                    " onclick='event.stopPropagation(); AdapterTestCommon.removeNodeInline(\"" +
                    escapeHtml(n.nodeId) + "\");'><i class='bi bi-trash'></i></button></td>";
            }
            tr.innerHTML = html;
            tbody.appendChild(tr);
        });
    }

    // ── Source Type Toggling ──────────────────────────────

    function onSourceTypeChange(side) {
        var type = document.getElementById("task-" + side + "-source-type").value;
        document.querySelectorAll(".source-" + side + "-git").forEach(function (el) {
            el.style.display = type === "git" ? "" : "none";
        });
        document.querySelectorAll(".source-" + side + "-local").forEach(function (el) {
            el.style.display = type === "local" ? "" : "none";
        });
    }

    function collectTaskData() {
        var jobIdVal = document.getElementById("task-job-id").value.trim();
        var testIdVal = document.getElementById("task-test-id").value.trim();
        var threadsVal = document.getElementById("task-threads").value.trim();
        var memLimitVal = document.getElementById("task-memory-limit").value.trim();
        var solTypeEl = document.getElementById("task-solution-source-type");
        var testTypeEl = document.getElementById("task-test-source-type");
        return {
            nodeId: getNodeId() || null,
            jobId: jobIdVal || null,
            testId: testIdVal || null,
            mode: document.getElementById("task-mode").value,
            threads: threadsVal ? parseInt(threadsVal) : null,
            memoryLimitMb: memLimitVal ? parseInt(memLimitVal) : null,
            solutionSourceType: solTypeEl ? (solTypeEl.value || null) : null,
            solutionUrl: (document.getElementById("task-solution-url") || {}).value || null,
            solutionBranch: (document.getElementById("task-solution-branch") || {}).value || null,
            solutionToken: (document.getElementById("task-solution-token") || {}).value || null,
            solutionPath: (document.getElementById("task-solution-path") || {}).value || null,
            testSourceType: testTypeEl ? (testTypeEl.value || null) : null,
            testUrl: (document.getElementById("task-test-url") || {}).value || null,
            testBranch: (document.getElementById("task-test-branch") || {}).value || null,
            testToken: (document.getElementById("task-test-token") || {}).value || null,
            testPath: (document.getElementById("task-test-path") || {}).value || null,
        };
    }

    // ── Node Selection ────────────────────────────────────

    function getNodeId() {
        return document.getElementById("target-node-id").value.trim();
    }

    function selectNode(nodeId) {
        selectedNodeId = nodeId;
        document.getElementById("target-node-id").value = nodeId;

        // Highlight selected row
        var tbody = document.getElementById("nodes-tbody");
        if (tbody) {
            var rows = tbody.querySelectorAll("tr");
            rows.forEach(function (tr) {
                var code = tr.querySelector("code");
                if (code && code.textContent === nodeId) {
                    tr.className = "prl-row-selected";
                } else {
                    tr.classList.remove("prl-row-selected");
                }
            });
        }

        refreshAdapterList(nodeId);
        refreshProviderList(nodeId);
        showAlert("Node selected: " + nodeId, "info");
    }

    function requireNodeId() {
        var id = getNodeId();
        if (!id) showAlert("Select a node first", "warning");
        return id;
    }

    // ── Adapter List ─────────────────────────────────────

    function refreshAdapterList(nodeId) {
        var sel = document.getElementById("adapter-name");
        if (!sel) return;

        ViewEngine.call(currentSVC, "listAdapters", [nodeId]).then(function (adapters) {
            if (!Array.isArray(adapters)) adapters = [];

            if (!adapterConfigCache[nodeId]) adapterConfigCache[nodeId] = {};

            sel.options.length = 0;
            var defOpt = document.createElement("option");
            defOpt.value = "";
            defOpt.textContent = "-- select adapter --";
            sel.appendChild(defOpt);
            adapters.forEach(function (a) {
                var isRunning = a.status === "running";
                var opt = document.createElement("option");
                opt.value = a.name;
                opt.textContent = a.name + (isRunning ? " (running)" : " (available)");
                opt.setAttribute("data-adapter-state", isRunning ? "running" : "available");
                if (a.config != null) {
                    opt.setAttribute("data-adapter-config", JSON.stringify(a.config));
                    adapterConfigCache[nodeId][a.name] = a.config;
                }
                sel.appendChild(opt);
            });
            updateAdapterActions();
        }).catch(function () {
            // silently ignore — node may not support adapter listing
        });
    }

    function refreshAdapters() {
        var id = requireNodeId();
        if (!id) return;
        refreshAdapterList(id);
    }

    function updateAdapterActions() {
        var sel = document.getElementById("adapter-name");
        var loadSection = document.getElementById("adapter-load-section");
        var unloadSection = document.getElementById("adapter-unload-section");
        if (!sel || !loadSection || !unloadSection) return;

        var selected = sel.options[sel.selectedIndex];
        var state = selected ? selected.getAttribute("data-adapter-state") : null;
        var name = selected ? selected.value : null;

        loadSection.style.display = state === "available" ? "" : "none";
        unloadSection.style.display = state === "running" ? "" : "none";

        var configDisplay = document.getElementById("adapter-config-display");
        var runningConfigEl = document.getElementById("adapter-running-config");
        if (state === "running" && configDisplay && runningConfigEl) {
            var configAttr = selected ? selected.getAttribute("data-adapter-config") : null;
            if (configAttr) {
                runningConfigEl.textContent = JSON.stringify(JSON.parse(configAttr), null, 2);
                configDisplay.style.display = "";
            } else {
                configDisplay.style.display = "none";
            }
        } else if (configDisplay) {
            configDisplay.style.display = "none";
        }

        if (state === "available" && name && selectedNodeId) {
            var cached = adapterConfigCache[selectedNodeId] && adapterConfigCache[selectedNodeId][name];
            var configTextarea = document.getElementById("adapter-config");
            if (configTextarea && cached) {
                configTextarea.value = JSON.stringify(cached, null, 2);
            }
        }
    }

    // ── Provider List ────────────────────────────────────

    function refreshProviderList(nodeId) {
        var sel = document.getElementById("provider-name");
        if (!sel) return;

        ViewEngine.call(currentSVC, "listResourceProviders", [nodeId]).then(function (providers) {
            if (!Array.isArray(providers)) providers = [];

            if (!providerConfigCache[nodeId]) providerConfigCache[nodeId] = {};

            sel.options.length = 0;
            var defOpt = document.createElement("option");
            defOpt.value = "";
            defOpt.textContent = "-- select provider --";
            sel.appendChild(defOpt);
            providers.forEach(function (p) {
                var isRunning = p.status === "running";
                var opt = document.createElement("option");
                opt.value = p.name;
                opt.textContent = p.name + (isRunning ? " (running)" : " (available)");
                opt.setAttribute("data-provider-state", isRunning ? "running" : "available");
                if (p.config != null) {
                    opt.setAttribute("data-provider-config", JSON.stringify(p.config));
                    providerConfigCache[nodeId][p.name] = p.config;
                }
                sel.appendChild(opt);
            });
            updateProviderActions();
        }).catch(function () {
            // silently ignore — node may not support provider listing
        });
    }

    function refreshProviders() {
        var id = requireNodeId();
        if (!id) return;
        refreshProviderList(id);
    }

    function updateProviderActions() {
        var sel = document.getElementById("provider-name");
        var loadSection = document.getElementById("provider-load-section");
        var unloadSection = document.getElementById("provider-unload-section");
        if (!sel || !loadSection || !unloadSection) return;

        var selected = sel.options[sel.selectedIndex];
        var state = selected ? selected.getAttribute("data-provider-state") : null;
        var name = selected ? selected.value : null;

        loadSection.style.display = state === "available" ? "" : "none";
        unloadSection.style.display = state === "running" ? "" : "none";

        var configDisplay = document.getElementById("provider-config-display");
        var runningConfigEl = document.getElementById("provider-running-config");
        if (state === "running" && configDisplay && runningConfigEl) {
            var configAttr = selected ? selected.getAttribute("data-provider-config") : null;
            if (configAttr) {
                runningConfigEl.textContent = JSON.stringify(JSON.parse(configAttr), null, 2);
                configDisplay.style.display = "";
            } else {
                configDisplay.style.display = "none";
            }
        } else if (configDisplay) {
            configDisplay.style.display = "none";
        }

        if (state === "available" && name && selectedNodeId) {
            var cached = providerConfigCache[selectedNodeId] && providerConfigCache[selectedNodeId][name];
            var configTextarea = document.getElementById("provider-config");
            if (configTextarea && cached) {
                configTextarea.value = JSON.stringify(cached, null, 2);
            }
        }
    }

    // ── Job ID Tracking ──────────────────────────────────

    function addJobId(jobId) {
        if (!jobId || knownJobIds.indexOf(jobId) !== -1) return;
        knownJobIds.push(jobId);
        var datalist = document.getElementById("job-ids-datalist");
        if (datalist) {
            var opt = document.createElement("option");
            opt.value = jobId;
            datalist.appendChild(opt);
        }
    }

    // ── Page Actions ──────────────────────────────────────

    function refreshNodes() {
        document.getElementById("nodes-loading").style.display = "";
        document.getElementById("nodes-table").style.display = "none";
        document.getElementById("nodes-empty").style.display = "none";

        ViewEngine.call(currentSVC, "refreshAndPruneNodes").then(function (nodes) {
            renderNodes(nodes, {hasActions: true, onSelect: selectNode});
        }).catch(function (err) {
            document.getElementById("nodes-loading").style.display = "none";
            showAlert("Failed to load nodes: " + err.message, "danger");
        });
    }

    function doHealthCheck() {
        var id = requireNodeId();
        if (!id) return;
        ViewEngine.call(currentSVC, "healthCheck", [id]).then(function (r) {
            log("Health Check: " + id, r);
            showResultPopup("Health Check: " + id, r);
        }).catch(function (e) {
            log("Health Check ERROR", e.message);
        });
    }

    function doPollStatus() {
        var id = requireNodeId();
        if (!id) return;
        ViewEngine.call(currentSVC, "pollNodeStatus", [id]).then(function (r) {
            log("Node Status: " + id, r);
            showResultPopup("Node Status: " + id, r);
        }).catch(function (e) {
            log("Status ERROR", e.message);
        });
    }

    function doQueueStatus() {
        var id = requireNodeId();
        if (!id) return;
        ViewEngine.call(currentSVC, "queueStatus", [id]).then(function (r) {
            log("Queue Status: " + id, r);
            showResultPopup("Queue Status: " + id, r);
        }).catch(function (e) {
            log("Queue Status ERROR", e.message);
        });
    }

    function doListAdapters() {
        var id = requireNodeId();
        if (!id) return;
        ViewEngine.call(currentSVC, "listAdapters", [id]).then(function (r) {
            log("Adapters: " + id, r);
            showResultPopup("Adapters: " + id, r);
        }).catch(function (e) {
            log("Adapters ERROR", e.message);
        });
    }

    function doListAvailable() {
        var id = requireNodeId();
        if (!id) return;
        ViewEngine.call(currentSVC, "listAvailableAdapters", [id]).then(function (r) {
            log("Available: " + id, r);
            showResultPopup("Available: " + id, r);
        }).catch(function (e) {
            log("Available ERROR", e.message);
        });
    }

    function doListProviders() {
        var id = requireNodeId();
        if (!id) return;
        ViewEngine.call(currentSVC, "listResourceProviders", [id]).then(function (r) {
            log("Providers: " + id, r);
            showResultPopup("Providers: " + id, r);
        }).catch(function (e) {
            log("Providers ERROR", e.message);
        });
    }

    function doListAvailableProviders() {
        var id = requireNodeId();
        if (!id) return;
        ViewEngine.call(currentSVC, "listAvailableResourceProviders", [id]).then(function (r) {
            log("Available Providers: " + id, r);
            showResultPopup("Available Providers: " + id, r);
        }).catch(function (e) {
            log("Available Providers ERROR", e.message);
        });
    }

    function doLoadProvider() {
        var id = requireNodeId();
        if (!id) return;
        var name = document.getElementById("provider-name").value.trim();
        var config = document.getElementById("provider-config").value.trim() || "{}";
        if (!name) {
            showAlert("Select a provider", "warning");
            return;
        }
        ViewEngine.call(currentSVC, "loadResourceProvider", [{nodeId: id, providerName: name, config: config}])
            .then(function (r) {
                log("Load Provider '" + name + "' on " + id, r);
                showResultPopup("Load Provider: " + name, r);
                refreshProviderList(id);
            }).catch(function (e) {
                log("Load Provider ERROR", e.message);
            });
    }

    function doUnloadProvider() {
        var id = requireNodeId();
        if (!id) return;
        var name = document.getElementById("provider-name").value.trim();
        if (!name) {
            showAlert("Select a provider", "warning");
            return;
        }
        ViewEngine.call(currentSVC, "unloadResourceProvider", [{nodeId: id, providerName: name}])
            .then(function (r) {
                log("Unload Provider '" + name + "' on " + id, r);
                showResultPopup("Unload Provider: " + name, r);
                refreshProviderList(id);
            }).catch(function (e) {
                log("Unload Provider ERROR", e.message);
            });
    }

    function doRemoveNode() {
        var id = requireNodeId();
        if (!id) return;
        ViewEngine.call(currentSVC, "removeNode", [id]).then(function (r) {
            log("Remove Node: " + id, r);
            if (selectedNodeId === id) selectedNodeId = null;
            refreshNodes();
        }).catch(function (e) {
            log("Remove ERROR", e.message);
        });
    }

    function removeNodeInline(nodeId) {
        ViewEngine.call(currentSVC, "removeNode", [nodeId]).then(function (r) {
            log("Remove Node: " + nodeId, r);
            if (selectedNodeId === nodeId) selectedNodeId = null;
            refreshNodes();
        }).catch(function (e) {
            log("Remove ERROR", e.message);
        });
    }

    function doLoadAdapter() {
        var id = requireNodeId();
        if (!id) return;
        var name = document.getElementById("adapter-name").value.trim();
        var config = document.getElementById("adapter-config").value.trim() || "{}";
        if (!name) {
            showAlert("Select an adapter", "warning");
            return;
        }
        ViewEngine.call(currentSVC, "loadAdapter", [{nodeId: id, adapterName: name, config: config}])
            .then(function (r) {
                log("Load Adapter '" + name + "' on " + id, r);
                showResultPopup("Load Adapter: " + name, r);
                refreshAdapterList(id);
            }).catch(function (e) {
                log("Load ERROR", e.message);
            });
    }

    function doUnloadAdapter() {
        var id = requireNodeId();
        if (!id) return;
        var name = document.getElementById("adapter-name").value.trim();
        if (!name) {
            showAlert("Select an adapter", "warning");
            return;
        }
        ViewEngine.call(currentSVC, "unloadAdapter", [{nodeId: id, adapterName: name}])
            .then(function (r) {
                log("Unload Adapter '" + name + "' on " + id, r);
                showResultPopup("Unload Adapter: " + name, r);
                refreshAdapterList(id);
            }).catch(function (e) {
                log("Unload ERROR", e.message);
            });
    }

    function doSubmitTask() {
        var id = requireNodeId();
        if (!id) return;
        var data = collectTaskData();
        ViewEngine.call(currentSVC, "submitTask", [data])
            .then(function (r) {
                log("Submit Task on " + id, r);
                showResultPopup("Submit Task", r);
                if (r && r.jobId) {
                    addJobId(r.jobId);
                    startResultPolling(r.jobId);
                }
            }).catch(function (e) {
                log("Submit ERROR", e.message);
            });
    }

    function startResultPolling(jobId) {
        var attempts = 0;
        var maxAttempts = 120;
        var intervalMs = 5000;

        showAlert("Waiting for result of job " + jobId + "...", "info");

        var timer = setInterval(function () {
            attempts++;
            ViewEngine.call(currentSVC, "pollTaskResult", [jobId]).then(function (r) {
                if (r) {
                    clearInterval(timer);
                    log("Task Result: " + jobId, r);
                    showResultPopup("Task Result: " + jobId, r);
                    showAlert("Result received for job " + jobId, "success");
                } else if (attempts >= maxAttempts) {
                    clearInterval(timer);
                    showAlert("Polling timed out for job " + jobId, "warning");
                }
            }).catch(function () {
                clearInterval(timer);
            });
        }, intervalMs);
    }

    function doQueryJobStatus() {
        var nodeId = requireNodeId();
        if (!nodeId) return;
        var jobId = document.getElementById("job-status-id").value.trim();
        if (!jobId) {
            showAlert("Enter a Job ID", "warning");
            return;
        }
        ViewEngine.call(currentSVC, "queryJobStatus", [{nodeId: nodeId, jobId: jobId}])
            .then(function (r) {
                log("Job Status: " + jobId, r);
                showResultPopup("Job Status: " + jobId, r);
            }).catch(function (e) {
                log("Job Status ERROR", e.message);
            });
    }

    function doCancelJob() {
        var nodeId = requireNodeId();
        if (!nodeId) return;
        var jobId = document.getElementById("job-status-id").value.trim();
        if (!jobId) {
            showAlert("Enter a Job ID", "warning");
            return;
        }
        ViewEngine.call(currentSVC, "cancelJob", [{nodeId: nodeId, jobId: jobId}])
            .then(function (r) {
                log("Cancel Job: " + jobId, r);
                showResultPopup("Cancel Job: " + jobId, r);
            }).catch(function (e) {
                log("Cancel ERROR", e.message);
            });
    }

    function doUpdateConfig() {
        var id = requireNodeId();
        if (!id) return;
        var maxWorkers = document.getElementById("config-max-workers").value.trim();
        var retention = document.getElementById("config-retention").value.trim();
        var memLimit = document.getElementById("config-memory-limit").value.trim();
        var data = {
            nodeId: id,
            maxCorrectnessWorkers: maxWorkers ? parseInt(maxWorkers) : null,
            jobRetentionSeconds: retention ? parseInt(retention) : null,
            defaultMemoryLimitMb: memLimit ? parseInt(memLimit) : null,
        };
        ViewEngine.call(currentSVC, "updateConfig", [data])
            .then(function (r) {
                log("Update Config on " + id, r);
                showResultPopup("Update Config: " + id, r);
            }).catch(function (e) {
                log("Update Config ERROR", e.message);
            });
    }

    // ── Init ──────────────────────────────────────────────

    function initPage(serviceName, options) {
        currentSVC = serviceName;
        if (options && options.transportType) {
            transportType = options.transportType;
        }
        document.addEventListener("DOMContentLoaded", function () {
            refreshNodes();
            CodeEditor.attach("adapter-config", "json");
            CodeEditor.attach("provider-config", "json");
            var adapterSel = document.getElementById("adapter-name");
            if (adapterSel) adapterSel.addEventListener("change", updateAdapterActions);
            var providerSel = document.getElementById("provider-name");
            if (providerSel) providerSel.addEventListener("change", updateProviderActions);
        });
    }

    return {
        log: log,
        showAlert: showAlert,
        showResultPopup: showResultPopup,
        clearLog: clearLog,
        renderNodes: renderNodes,
        initPage: initPage,
        getNodeId: getNodeId,
        selectNode: selectNode,
        refreshNodes: refreshNodes,
        onSourceTypeChange: onSourceTypeChange,
        doHealthCheck: doHealthCheck,
        doPollStatus: doPollStatus,
        doQueueStatus: doQueueStatus,
        doListAdapters: doListAdapters,
        doListAvailable: doListAvailable,
        doListProviders: doListProviders,
        doListAvailableProviders: doListAvailableProviders,
        doRemoveNode: doRemoveNode,
        removeNodeInline: removeNodeInline,
        refreshAdapters: refreshAdapters,
        doLoadAdapter: doLoadAdapter,
        doUnloadAdapter: doUnloadAdapter,
        refreshProviders: refreshProviders,
        doLoadProvider: doLoadProvider,
        doUnloadProvider: doUnloadProvider,
        doSubmitTask: doSubmitTask,
        doQueryJobStatus: doQueryJobStatus,
        doCancelJob: doCancelJob,
        doUpdateConfig: doUpdateConfig,
    };
})();
