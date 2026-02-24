/**
 * Common utilities and page controller for adapter test pages (HTTP and RabbitMQ).
 */
var AdapterTestCommon = (function () {

    var currentSVC;
    var selectedNodeId = null;
    var transportType = "HTTP";
    var knownJobIds = [];

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
            if (transportType !== "AMQP") {
                html += "<td>" + escapeHtml(n.host || "") + "</td>";
                html += "<td>" + escapeHtml(String(n.port || "")) + "</td>";
            }
            html += "<td>" + formatCapabilities(n.capabilities) + "</td>";
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

    function collectTaskData() {
        var jobIdVal = document.getElementById("task-job-id").value.trim();
        var testIdVal = document.getElementById("task-test-id").value.trim();
        var threadsVal = document.getElementById("task-threads").value.trim();
        return {
            nodeId: getNodeId() || null,
            jobId: jobIdVal || null,
            testId: testIdVal || null,
            mode: document.getElementById("task-mode").value,
            threads: threadsVal ? parseInt(threadsVal) : null,
            solutionGitUrl: document.getElementById("task-solution-url").value || null,
            solutionDir: document.getElementById("task-solution-dir").value || null,
            testsGitUrl: document.getElementById("task-tests-url").value || null,
            testsDir: document.getElementById("task-tests-dir").value || null,
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
        showAlert("Node selected: " + nodeId, "info");
    }

    function requireNodeId() {
        var id = getNodeId();
        if (!id) showAlert("Select a node first", "warning");
        return id;
    }

    // ── Adapter List ─────────────────────────────────────

    function refreshAdapterList(nodeId) {
        ViewEngine.call(currentSVC, "listAvailableAdapters", [nodeId]).then(function (adapters) {
            var sel = document.getElementById("adapter-name");
            if (!sel) return;
            sel.innerHTML = "<option value=''>-- select adapter --</option>";
            adapters.forEach(function (a) {
                var opt = document.createElement("option");
                opt.value = a.name;
                opt.textContent = a.name + (a.status ? " (" + a.status + ")" : "");
                sel.appendChild(opt);
            });
        }).catch(function () {
            // silently ignore — node may not support adapter listing
        });
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
        }).catch(function (e) {
            log("Health Check ERROR", e.message);
        });
    }

    function doPollStatus() {
        var id = requireNodeId();
        if (!id) return;
        ViewEngine.call(currentSVC, "pollNodeStatus", [id]).then(function (r) {
            log("Node Status: " + id, r);
        }).catch(function (e) {
            log("Status ERROR", e.message);
        });
    }

    function doQueueStatus() {
        var id = requireNodeId();
        if (!id) return;
        ViewEngine.call(currentSVC, "queueStatus", [id]).then(function (r) {
            log("Queue Status: " + id, r);
        }).catch(function (e) {
            log("Queue Status ERROR", e.message);
        });
    }

    function doListAdapters() {
        var id = requireNodeId();
        if (!id) return;
        ViewEngine.call(currentSVC, "listAdapters", [id]).then(function (r) {
            log("Adapters: " + id, r);
        }).catch(function (e) {
            log("Adapters ERROR", e.message);
        });
    }

    function doListAvailable() {
        var id = requireNodeId();
        if (!id) return;
        ViewEngine.call(currentSVC, "listAvailableAdapters", [id]).then(function (r) {
            log("Available: " + id, r);
        }).catch(function (e) {
            log("Available ERROR", e.message);
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
                if (r && r.jobId) addJobId(r.jobId);
            }).catch(function (e) {
                log("Submit ERROR", e.message);
            });
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
            }).catch(function (e) {
                log("Cancel ERROR", e.message);
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
        });
    }

    return {
        log: log,
        showAlert: showAlert,
        clearLog: clearLog,
        renderNodes: renderNodes,
        initPage: initPage,
        getNodeId: getNodeId,
        selectNode: selectNode,
        refreshNodes: refreshNodes,
        doHealthCheck: doHealthCheck,
        doPollStatus: doPollStatus,
        doQueueStatus: doQueueStatus,
        doListAdapters: doListAdapters,
        doListAvailable: doListAvailable,
        doRemoveNode: doRemoveNode,
        removeNodeInline: removeNodeInline,
        doLoadAdapter: doLoadAdapter,
        doUnloadAdapter: doUnloadAdapter,
        doSubmitTask: doSubmitTask,
        doQueryJobStatus: doQueryJobStatus,
        doCancelJob: doCancelJob,
    };
})();
