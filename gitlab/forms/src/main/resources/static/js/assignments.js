var SERVICE = "prl.assignmentViewService";
var activeFormElement = null;
var activeDetailElement = null;
var editingAssignmentId = null;
var branchCheckInterval = null;
var SEARCH_DEBOUNCE_MS = 300;

// HTML-escape user-controlled strings before injecting into innerHTML templates.
// All assignment fields (code, name, description, paths, URLs) flow from the
// admin form back into here on edit - without escaping, an admin saving
// "</textarea><script>...</script>" would later self-XSS on open.
function esc(s) {
    var d = document.createElement("div");
    d.textContent = s == null ? "" : String(s);
    return d.innerHTML;
}

// Search wiring is declarative now (data-prl-list-filter on the input);
// see Prl.attachListFilter in prl-utils.js. We just need to kick off the
// initial data load.
document.addEventListener("DOMContentLoaded", function () {
    loadAssignments();
});

// --- Load & Render ---------------------------------------------------

function loadAssignments() {
    Prl.$("#loading").style.display = "";
    Prl.$("#assignment-list").style.display = "none";
    ViewEngine.call(SERVICE, "getAssignments").then(function (data) {
        Prl.$("#loading").style.display = "none";
        renderAssignmentList(data);
        Prl.$("#assignment-list").style.display = "";
    }).catch(function (err) {
        Prl.$("#loading").style.display = "none";
        Prl.alert("#main-alert", "danger", "Failed to load: " + err);
    });
}

function renderAssignmentList(assignments) {
    var container = document.getElementById("assignment-list");
    container.textContent = "";
    if (!assignments || assignments.length === 0) {
        container.textContent = "No assignments yet.";
        return;
    }
    var list = document.createElement("div");
    list.className = "list-group";
    assignments.forEach(function (a) {
        var item = document.createElement("div");
        item.className = "list-group-item list-group-item-action d-flex justify-content-between align-items-center assignment-item";
        item.style.cursor = "pointer";
        // Pre-built search haystack: all fields the user might type, lower-cased,
        // joined by space. Avoids relying on textContent (which also picks up
        // badge labels like "Active") and survives DOM reflow during filter.
        var testName = (a.testRepoUrl || "").replace(/.*\//, "").replace(/\.git$/, "");
        var haystack = [
            a.code, a.name, a.gitlabProjectPath, testName, a.testRepoBranch,
        ].filter(Boolean).join(" ").toLowerCase();
        item.setAttribute("data-search", haystack);

        var infoDiv = document.createElement("div");
        var strong = document.createElement("strong");
        strong.textContent = a.code || "";
        infoDiv.appendChild(strong);
        if (a.name && a.name !== a.code) {
            infoDiv.appendChild(document.createTextNode(" - " + a.name));
        }
        if (a.active !== false) {
            infoDiv.appendChild(Prl.el("small", {className: "badge bg-success ms-2", text: " Active"}));
        } else {
            infoDiv.appendChild(Prl.el("small", {className: "badge bg-secondary ms-2", text: " Inactive"}));
        }
        var br = document.createElement("br");
        infoDiv.appendChild(br);
        var small = document.createElement("small");
        small.className = "text-muted";
        small.textContent = (a.gitlabProjectPath || "") + (testName ? " | tests: " + testName : "") + " @ " + (a.testRepoBranch || "main");
        infoDiv.appendChild(small);

        // Click on the row toggles the settings (edit) form. Forks live on
        // their own button - keeps the most common action (review/edit the
        // assignment) on the whole-row hit target.
        item.addEventListener("click", function (e) {
            if (e.target.closest("button")) return;
            if (editingAssignmentId === a.id) hideForm();
            else editAssignment(a.id, item);
        });

        // Action buttons
        var btnGroup = document.createElement("div");
        btnGroup.className = "btn-group btn-group-sm";

        var forksBtn = document.createElement("button");
        forksBtn.className = "btn btn-outline-primary";
        forksBtn.title = "Manage forks";
        forksBtn.appendChild(Prl.el("i", {className: "bi bi-diagram-3"}));
        forksBtn.addEventListener("click", function (e) {
            e.stopPropagation();
            toggleDetail(a.id, item);
        });

        var toggleBtn = document.createElement("button");
        toggleBtn.className = a.active !== false ? "btn btn-outline-warning" : "btn btn-outline-success";
        toggleBtn.title = a.active !== false ? "Deactivate" : "Activate";
        var toggleIcon = document.createElement("i");
        toggleIcon.className = a.active !== false ? "bi bi-pause-circle" : "bi bi-play-circle";
        toggleBtn.appendChild(toggleIcon);
        toggleBtn.addEventListener("click", function (e) {
            e.stopPropagation();
            // Dedicated partial endpoint so flipping Active doesn't have to
            // re-send the whole assignment - the full saveAssignment is a PUT
            // and would null any field absent from the payload.
            ViewEngine.call(SERVICE, "setAssignmentActive", [a.id, a.active === false]).then(function () {
                loadAssignments();
            });
        });

        var deleteBtn = document.createElement("button");
        deleteBtn.className = "btn btn-outline-danger";
        deleteBtn.title = "Delete";
        deleteBtn.addEventListener("click", function (e) {
            e.stopPropagation();
            deleteAssignment(a.id);
        });
        var deleteIcon = document.createElement("i");
        deleteIcon.className = "bi bi-trash";
        deleteBtn.appendChild(deleteIcon);

        btnGroup.appendChild(forksBtn);
        btnGroup.appendChild(toggleBtn);
        btnGroup.appendChild(deleteBtn);

        item.appendChild(infoDiv);
        item.appendChild(btnGroup);
        list.appendChild(item);
    });
    container.appendChild(list);
    // Re-apply any active text filter to freshly-rendered rows so a search
    // typed before the fetch settled keeps hiding the right set.
    Prl.reapplyListFilters();
}

// --- Project Typeahead Search ----------------------------------------

/**
 * Attach a debounced typeahead to an input. Calls onSelect({pathWithNamespace, gitHttpUrl, name})
 * when the user picks an option from the result list. Closes on outside click or Escape.
 *
 * Returns `{ detach }`. Call detach() when the input element is being removed
 * from the DOM (e.g. inside hideForm()) so the document-level mousedown handler
 * that closes the dropdown gets cleaned up - otherwise it leaks per-form and
 * keeps detached DOM nodes alive in memory.
 */
function attachProjectSearch(inputEl, resultsEl, onSelect) {
    var timer = null;
    var currentResults = [];
    var selectedIdx = -1;

    function close() {
        resultsEl.style.display = "none";
        resultsEl.textContent = "";
        selectedIdx = -1;
    }

    function highlight() {
        Array.prototype.forEach.call(resultsEl.children, function (el, i) {
            if (i === selectedIdx) el.classList.add("active");
            else el.classList.remove("active");
        });
    }

    function pick(p) {
        inputEl.value = p.pathWithNamespace || "";
        inputEl.setAttribute("data-selected-path", p.pathWithNamespace || "");
        inputEl.setAttribute("data-selected-url", p.gitHttpUrl || "");
        inputEl.setAttribute("data-selected-name", p.name || "");
        close();
        if (typeof onSelect === "function") onSelect(p);
    }

    function render(projects) {
        resultsEl.textContent = "";
        currentResults = projects || [];
        if (currentResults.length === 0) {
            var empty = document.createElement("div");
            empty.className = "dropdown-item-text text-muted small";
            empty.textContent = "No matching projects";
            resultsEl.appendChild(empty);
            resultsEl.style.display = "block";
            return;
        }
        currentResults.forEach(function (p, i) {
            var item = document.createElement("button");
            item.type = "button";
            item.className = "dropdown-item text-truncate";
            item.textContent = p.pathWithNamespace || "";
            item.title = p.pathWithNamespace || "";
            item.addEventListener("mousedown", function (e) {
                // mousedown beats blur - keeps input focus from closing dropdown first
                e.preventDefault();
                pick(p);
            });
            item.addEventListener("mouseenter", function () {
                selectedIdx = i;
                highlight();
            });
            resultsEl.appendChild(item);
        });
        selectedIdx = -1;
        resultsEl.style.display = "block";
    }

    function fetchSearch() {
        var q = inputEl.value.trim();
        // Clear stale selection if the user is editing the picked text
        if (q !== inputEl.getAttribute("data-selected-path")) {
            inputEl.setAttribute("data-selected-path", "");
            inputEl.setAttribute("data-selected-url", "");
            inputEl.setAttribute("data-selected-name", "");
        }
        ViewEngine.call(SERVICE, "getGitLabProjects", [q || null]).then(function (projects) {
            render(projects);
        }).catch(function (err) {
            resultsEl.textContent = "";
            var e = document.createElement("div");
            e.className = "dropdown-item-text text-danger small";
            e.textContent = "Error: " + err;
            resultsEl.appendChild(e);
            resultsEl.style.display = "block";
        });
    }

    inputEl.addEventListener("input", function () {
        if (timer) clearTimeout(timer);
        timer = setTimeout(fetchSearch, SEARCH_DEBOUNCE_MS);
    });
    inputEl.addEventListener("focus", function () {
        // Don't re-fetch when the value still matches the last pick: the user
        // is just tabbing through the form, not asking for a new search.
        if (inputEl.value === inputEl.getAttribute("data-selected-path")) return;
        if (timer) clearTimeout(timer);
        timer = setTimeout(fetchSearch, SEARCH_DEBOUNCE_MS / 2);
    });
    // After a pick the input keeps focus but the dropdown was closed.
    // A second click on the already-focused input wouldn't fire `focus`,
    // so handle `click` directly to re-open with a fresh fetch.
    inputEl.addEventListener("click", function () {
        if (resultsEl.style.display !== "none") return;
        if (timer) clearTimeout(timer);
        timer = setTimeout(fetchSearch, 0);
    });
    inputEl.addEventListener("keydown", function (e) {
        if (resultsEl.style.display === "none" || currentResults.length === 0) return;
        if (e.key === "ArrowDown") {
            e.preventDefault();
            selectedIdx = Math.min(selectedIdx + 1, currentResults.length - 1);
            highlight();
        } else if (e.key === "ArrowUp") {
            e.preventDefault();
            selectedIdx = Math.max(selectedIdx - 1, 0);
            highlight();
        } else if (e.key === "Enter") {
            if (selectedIdx >= 0) {
                e.preventDefault();
                pick(currentResults[selectedIdx]);
            }
        } else if (e.key === "Escape") {
            close();
        }
    });
    var outsideHandler = function (e) {
        if (!inputEl.contains(e.target) && !resultsEl.contains(e.target)) close();
    };
    document.addEventListener("mousedown", outsideHandler);

    return {
        detach: function () {
            document.removeEventListener("mousedown", outsideHandler);
            if (timer) clearTimeout(timer);
        }
    };
}

function loadBranches(formEl, projectPath) {
    var select = formEl.querySelector(".branch-select");
    select.textContent = "";
    if (!projectPath) {
        var ph = document.createElement("option");
        ph.textContent = "Select test project first";
        select.appendChild(ph);
        return Promise.resolve();
    }
    var loading = document.createElement("option");
    loading.textContent = "Loading...";
    loading.disabled = true;
    select.appendChild(loading);
    return ViewEngine.call(SERVICE, "getProjectBranches", [projectPath]).then(function (branches) {
        select.textContent = "";
        if (!branches || branches.length === 0) {
            var d = document.createElement("option");
            d.value = "main";
            d.textContent = "main (default)";
            select.appendChild(d);
            return;
        }
        branches.forEach(function (b) {
            var opt = document.createElement("option");
            opt.value = b.name;
            opt.textContent = b.name + (b.default_ ? " (default)" : "");
            if (b.default_) opt.selected = true;
            select.appendChild(opt);
        });
    }).catch(function () {
        select.textContent = "";
        var e = document.createElement("option");
        e.value = "main";
        e.textContent = "main";
        select.appendChild(e);
    });
}

// --- Form Builder ----------------------------------------------------

function buildAssignmentForm(title, a) {
    a = a || {};
    var card = document.createElement("div");
    card.className = "card mt-1 mb-1";

    // IMPORTANT: build the WHOLE inner HTML in a single assignment, then wire
    // listeners afterwards. Earlier `card.appendChild(headerWithListener)` +
    // `card.innerHTML += '<body>...'` serialised/reparsed the card and silently
    // dropped the close-button's click listener - the x was dead on every form.
    var idVal = esc(a.id);
    var pathVal = esc(a.gitlabProjectPath);
    var testVal = esc(a.testRepoUrl);
    var codeVal = esc(a.code);
    var nameVal = esc(a.name);
    var descVal = esc(a.description);
    var memVal = esc(a.memoryLimitMb);
    var threadVal = esc(a.threads);
    var wallVal = esc(a.wallTimeSec);
    var cpuVal = esc(a.cpuTimeSec);
    var procVal = esc(a.maxProcesses);
    var warmupVal = esc(a.warmupIterations);

    card.innerHTML =
        '<div class="card-header d-flex justify-content-between">' +
        '<span class="form-title"></span>' +
        '<button class="btn-close close-form-btn" type="button"></button>' +
        '</div>' +
        '<div class="card-body">' +
        '<input type="hidden" class="form-id" value="' + idVal + '">' +
        '<input type="hidden" class="form-gitlabProjectPath" value="' + pathVal + '">' +
        '<input type="hidden" class="form-testRepoUrl" value="' + testVal + '">' +
        '<input type="hidden" class="form-testProjectPath" value="">' +
        '<input type="hidden" class="form-code" value="' + codeVal + '">' +
        '<input type="hidden" class="form-name" value="' + nameVal + '">' +
        '<div class="row">' +
        '<div class="col-md-6 mb-3 position-relative"><label class="form-label required">Solution Project</label>' +
        '<input class="form-control project-search" type="text" placeholder="Type to search projects..." autocomplete="off" value="' + pathVal + '">' +
        '<div class="dropdown-menu w-100 project-results" style="max-height:280px;overflow-y:auto;"></div>' +
        '</div>' +
        '<div class="col-md-6 mb-3 position-relative"><label class="form-label required">Test Project</label>' +
        '<input class="form-control test-project-search" type="text" placeholder="Type to search projects..." autocomplete="off">' +
        '<div class="dropdown-menu w-100 test-project-results" style="max-height:280px;overflow-y:auto;"></div>' +
        '</div>' +
        '</div>' +
        '<div class="mb-3"><label class="form-label">Description</label>' +
        '<textarea class="form-control form-description" rows="2">' + descVal + '</textarea></div>' +
        '<div class="row">' +
        '<div class="col-md-6 mb-3"><label class="form-label required">Test Branch</label>' +
        '<select class="form-select branch-select"><option>Select test project</option></select></div>' +
        '<div class="col-md-3 mb-3"><label class="form-label">Memory (MB)</label>' +
        '<input class="form-control form-memoryLimitMb" type="number" placeholder="1024" value="' + memVal + '"></div>' +
        '<div class="col-md-3 mb-3"><label class="form-label">Threads</label>' +
        '<input class="form-control form-threads" type="number" placeholder="4" value="' + threadVal + '"></div>' +
        '</div>' +
        '<div class="row">' +
        '<div class="col-md-3 mb-3"><label class="form-label">Wall (s)</label>' +
        '<input class="form-control form-wallTimeSec" type="number" placeholder="60" value="' + wallVal + '"></div>' +
        '<div class="col-md-3 mb-3"><label class="form-label">CPU (s)</label>' +
        '<input class="form-control form-cpuTimeSec" type="number" placeholder="30" value="' + cpuVal + '"></div>' +
        '<div class="col-md-3 mb-3"><label class="form-label">Max Processes</label>' +
        '<input class="form-control form-maxProcesses" type="number" placeholder="threads x multiplier" value="' + procVal + '"></div>' +
        '<div class="col-md-3 mb-3"><label class="form-label">Warmup Iterations</label>' +
        '<input class="form-control form-warmupIterations" type="number" min="0" placeholder="0" value="' + warmupVal + '"></div>' +
        '</div>' +
        '<div class="mb-3 form-check">' +
        '<input class="form-check-input form-active" type="checkbox"' + (a.active !== false ? ' checked' : '') + '>' +
        '<label class="form-check-label">Active</label></div>' +
        // Verdict-script section: instructor attaches an optional .kts or
        // .py script that overrides the default test-count verdict. UI
        // collapses when "None" is picked so the form stays compact for
        // assignments that don't need custom logic.
        '<div class="card mb-3 script-section">' +
        '<div class="card-header py-2 d-flex align-items-center flex-wrap gap-2">' +
        '<i class="bi bi-code-slash me-1"></i>' +
        '<span class="me-2">Verdict Script</span>' +
        '<select class="form-select form-select-sm w-auto script-type">' +
        '<option value="NONE">None (use default test-count verdict)</option>' +
        '<option value="KTS">Kotlin (.kts)</option>' +
        '<option value="PYTHON">Python (.py)</option>' +
        '</select>' +
        '<div class="form-check form-switch ms-2 script-enabled-wrap" style="display:none;">' +
        '<input class="form-check-input script-enabled" type="checkbox" checked>' +
        '<label class="form-check-label small">Enabled</label>' +
        '</div>' +
        '<div class="ms-2 script-timeout-wrap d-flex align-items-center" style="display:none;">' +
        '<label class="form-label small text-muted mb-0 me-1">Timeout (ms)</label>' +
        '<input class="form-control form-control-sm script-timeout" type="number" min="100" step="100" value="5000" style="width:7em;">' +
        '</div>' +
        '</div>' +
        '<div class="card-body script-body" style="display:none;">' +
        '<div class="row g-3">' +
        '<div class="col-md-8">' +
        '<label class="form-label small text-muted">Script source - runs against TaskResult after each submission</label>' +
        '<div class="script-editor border" style="height:340px;"></div>' +
        '</div>' +
        '<div class="col-md-4">' +
        '<label class="form-label small text-muted">JudgeContext schema (read-only reference)</label>' +
        '<pre class="bg-body-tertiary border rounded p-2 small mb-0" style="height:340px;overflow:auto;">' + judgeContextDocs() + '</pre>' +
        '</div>' +
        '</div>' +
        '</div>' +
        '</div>' +
        '<button class="btn btn-primary save-btn">Save</button>' +
        '</div>';

    // Title via textContent (no further escape needed)
    card.querySelector(".form-title").textContent = title;

    // Close + Save listeners - wire AFTER the innerHTML is settled.
    card.querySelector(".close-form-btn").addEventListener("click", function () {
        hideForm();
    });

    var body = card.querySelector(".card-body");

    // Solution project search - keep the detach handle so hideForm can clean up.
    var solutionSearch = attachProjectSearch(
        body.querySelector(".project-search"),
        body.querySelector(".project-results"),
        function (p) {
            body.querySelector(".form-gitlabProjectPath").value = p.pathWithNamespace || "";
            body.querySelector(".form-code").value = p.name || "";
            body.querySelector(".form-name").value = p.pathWithNamespace || "";
        }
    );

    var testSearch = attachProjectSearch(
        body.querySelector(".test-project-search"),
        body.querySelector(".test-project-results"),
        function (p) {
            body.querySelector(".form-testRepoUrl").value = p.gitHttpUrl || "";
            body.querySelector(".form-testProjectPath").value = p.pathWithNamespace || "";
            loadBranches(card, p.pathWithNamespace);
        }
    );

    card._typeaheadDetachers = [solutionSearch.detach, testSearch.detach];

    body.querySelector(".save-btn").addEventListener("click", function () {
        saveAssignment(card);
    });

    initScriptEditor(card, a && a.evaluatorScript);

    // Pre-fill solution project marker: in an edit form the input already
    // shows the saved value. Without data-selected-path, focusing the input
    // would fire a search for that exact path and the dropdown would say
    // "No matching projects" before the user touched anything.
    if (a.gitlabProjectPath) {
        var solutionInput = body.querySelector(".project-search");
        solutionInput.setAttribute("data-selected-path", a.gitlabProjectPath);
    }

    // Pre-fill test project text + branches for existing assignment
    if (a.testRepoUrl) {
        var testInput = body.querySelector(".test-project-search");
        // Derive path from gitHttpUrl as a best-effort fallback display value.
        // The user can confirm/replace it; saving uses the hidden form-testRepoUrl as-is.
        var derivedPath = String(a.testRepoUrl).replace(/^https?:\/\/[^/]+\//, "").replace(/\.git$/, "");
        testInput.value = derivedPath;
        testInput.setAttribute("data-selected-path", derivedPath);
        testInput.setAttribute("data-selected-url", a.testRepoUrl);
        body.querySelector(".form-testProjectPath").value = derivedPath;
        loadBranches(card, derivedPath).then(function () {
            var bs = card.querySelector(".branch-select");
            if (bs) bs.value = a.testRepoBranch || "main";
        });
    }

    return card;
}

// --- Create / Edit ---------------------------------------------------

function showCreateForm() {
    hideForm();
    hideDetail();
    var form = buildAssignmentForm("New Assignment");
    var container = document.getElementById("new-assignment-form");
    container.textContent = "";
    container.appendChild(form);
    container.style.display = "";
    activeFormElement = container;
}

function editAssignment(id, afterElement) {
    hideForm();
    hideDetail();
    editingAssignmentId = id;
    ViewEngine.call(SERVICE, "getAssignment", [id]).then(function (a) {
        if (editingAssignmentId !== id) return;
        var form = buildAssignmentForm("Edit: " + (a.code || "").toUpperCase(), a);
        if (afterElement && afterElement.parentNode) {
            afterElement.parentNode.insertBefore(form, afterElement.nextSibling);
        } else {
            document.getElementById("assignment-list").appendChild(form);
        }
        activeFormElement = form;
    }).catch(function (err) {
        Prl.alert("#main-alert", "danger", "Failed to load: " + err);
    });
}

function hideForm() {
    editingAssignmentId = null;
    if (branchCheckInterval) {
        clearInterval(branchCheckInterval);
        branchCheckInterval = null;
    }
    if (activeFormElement) {
        // Detach typeahead document-level mousedown handlers so they don't leak
        // and keep removed DOM nodes alive in memory.
        runDetachers(activeFormElement);
        if (activeFormElement.id === "new-assignment-form") {
            // Container hosts a single card - detach its handlers before clearing.
            var card = activeFormElement.firstElementChild;
            if (card) runDetachers(card);
            activeFormElement.style.display = "none";
            activeFormElement.textContent = "";
        } else {
            activeFormElement.remove();
        }
        activeFormElement = null;
    }
    var naf = document.getElementById("new-assignment-form");
    if (naf) {
        var leftoverCard = naf.firstElementChild;
        if (leftoverCard) runDetachers(leftoverCard);
        naf.style.display = "none";
        naf.textContent = "";
    }
}

function runDetachers(el) {
    var detachers = el._typeaheadDetachers;
    if (!detachers) return;
    detachers.forEach(function (fn) {
        try {
            fn();
        } catch (_) {
        }
    });
    el._typeaheadDetachers = null;
}

function saveAssignment(formEl) {
    var body = formEl.querySelector(".card-body");
    var idVal = body.querySelector(".form-id").value;
    var memVal = body.querySelector(".form-memoryLimitMb").value;
    var thrVal = body.querySelector(".form-threads").value;
    var wallVal = body.querySelector(".form-wallTimeSec").value;
    var cpuVal = body.querySelector(".form-cpuTimeSec").value;
    var procVal = body.querySelector(".form-maxProcesses").value;
    var warmupVal = body.querySelector(".form-warmupIterations").value;
    var gitlabProjectPath = body.querySelector(".form-gitlabProjectPath").value;
    var testRepoUrl = body.querySelector(".form-testRepoUrl").value;
    var branchSelect = body.querySelector(".branch-select");
    var testRepoBranch = branchSelect.value;
    var code = body.querySelector(".form-code").value;
    var name = body.querySelector(".form-name").value;
    // Required-field validation. The server is strict (PUT semantics, non-
    // null types in the request), but UI feedback is friendlier than a 500.
    if (!gitlabProjectPath) {
        Prl.alert("#main-alert", "warning", "Select a solution project.");
        return;
    }
    if (!testRepoUrl) {
        Prl.alert("#main-alert", "warning", "Select a test project.");
        return;
    }
    if (!testRepoBranch || branchSelect.querySelector("option[value='" + testRepoBranch + "']") === null) {
        Prl.alert("#main-alert", "warning", "Select a test branch.");
        return;
    }
    var data = {
        id: idVal ? parseInt(idVal) : null,
        code: code,
        name: name,
        description: body.querySelector(".form-description").value || null,
        gitlabProjectPath: gitlabProjectPath,
        testRepoUrl: testRepoUrl,
        testRepoBranch: testRepoBranch,
        memoryLimitMb: memVal ? parseInt(memVal) : null,
        threads: thrVal ? parseInt(thrVal) : null,
        wallTimeSec: wallVal ? parseInt(wallVal) : null,
        cpuTimeSec: cpuVal ? parseInt(cpuVal) : null,
        maxProcesses: procVal ? parseInt(procVal) : null,
        warmupIterations: warmupVal ? parseInt(warmupVal) : null,
        active: body.querySelector(".form-active").checked,
        evaluatorScript: collectScriptPayload(formEl)
    };
    ViewEngine.call(SERVICE, "saveAssignment", [data]).then(function () {
        hideForm();
        Prl.alert("#main-alert", "success", "Assignment saved.");
        loadAssignments();
    }).catch(function (err) {
        Prl.alert("#main-alert", "danger", "Save failed: " + err);
    });
}

function deleteAssignment(id) {
    if (!confirm("Delete this assignment?")) return;
    ViewEngine.call(SERVICE, "deleteAssignment", [id]).then(function () {
        hideForm();
        hideDetail();
        Prl.alert("#main-alert", "success", "Assignment deleted.");
        loadAssignments();
    }).catch(function (err) {
        Prl.alert("#main-alert", "danger", "Delete failed: " + err);
    });
}

// --- Detail (Forks) -------------------------------------------------

var currentAssignmentId = null;

function toggleDetail(id, afterElement) {
    if (currentAssignmentId === id) {
        hideDetail();
        return;
    }
    hideDetail();
    hideForm();
    currentAssignmentId = id;

    var card = document.createElement("div");
    card.className = "card mt-1 mb-1";
    card.innerHTML = '<div class="card-header d-flex justify-content-between">' +
        '<span>Forks</span><button class="btn-close detail-close-btn"></button></div>' +
        '<div class="card-body">' +
        '<h6>Create Forks</h6><div class="fork-groups-container mb-2"></div>' +
        '<button class="btn btn-primary btn-sm create-forks-btn"><i class="bi bi-git"></i> Create Selected Forks</button>' +
        '<div class="fork-results mt-3"></div><hr>' +
        '<h6>Existing Forks</h6><div class="existing-forks">Loading...</div>' +
        '</div>';

    card.querySelector(".detail-close-btn").addEventListener("click", function () {
        hideDetail();
    });
    card.querySelector(".create-forks-btn").addEventListener("click", function () {
        createForks(card);
    });

    if (afterElement && afterElement.parentNode) {
        afterElement.parentNode.insertBefore(card, afterElement.nextSibling);
    }
    activeDetailElement = card;

    // Load data
    var groupsContainer = card.querySelector(".fork-groups-container");
    groupsContainer.textContent = "Loading...";
    ViewEngine.call(SERVICE, "getGroupsWithForkStatus", [id]).then(function (groups) {
        renderForkGroups(groups || [], groupsContainer);
    }).catch(function (err) {
        groupsContainer.textContent = "Error: " + err;
    });

    loadExistingForksInto(card.querySelector(".existing-forks"), id);
}

function hideDetail() {
    currentAssignmentId = null;
    if (activeDetailElement) {
        activeDetailElement.remove();
        activeDetailElement = null;
    }
}

function createForks(card) {
    var checked = card.querySelectorAll(".fork-member-cb:checked:not(:disabled)");
    var usernames = Array.from(checked).map(function (cb) {
        return cb.value;
    });
    if (usernames.length === 0) {
        Prl.alert("#main-alert", "warning", "Select at least one student.");
        return;
    }

    var btn = card.querySelector(".create-forks-btn");

    function restoreBtn() {
        if (!btn) return;
        btn.disabled = false;
        btn.textContent = "";
        btn.appendChild(Prl.el("i", {className: "bi bi-git"}));
        btn.appendChild(document.createTextNode(" Create Selected Forks"));
    }

    if (btn) {
        btn.disabled = true;
        btn.textContent = "Creating...";
    }
    var resultsDiv = card.querySelector(".fork-results");
    resultsDiv.textContent = "Creating " + usernames.length + " fork(s)...";

    var payload = {assignmentId: currentAssignmentId, usernames: usernames};
    ViewEngine.call(SERVICE, "createForks", [payload])
        .then(function (result) {
            restoreBtn();
            resultsDiv.textContent = "";
            var ul = document.createElement("ul");
            ul.className = "list-unstyled mb-0";
            (result.results || []).forEach(function (r) {
                var li = document.createElement("li");
                if (r.success) {
                    li.className = "text-success";
                    li.appendChild(Prl.el("i", {className: "bi bi-check-circle"}));
                    li.appendChild(document.createTextNode(" " + r.username));
                } else {
                    li.className = "text-danger";
                    li.appendChild(Prl.el("i", {className: "bi bi-x-circle"}));
                    li.appendChild(document.createTextNode(" " + r.username + ": " + (r.error || "unknown")));
                }
                ul.appendChild(li);
            });
            resultsDiv.appendChild(ul);
            // Refresh
            var gc = card.querySelector(".fork-groups-container");
            ViewEngine.call(SERVICE, "getGroupsWithForkStatus", [currentAssignmentId]).then(function (g) {
                var searchVal = gc.querySelector("input[type='text']");
                var sv = searchVal ? searchVal.value : "";
                renderForkGroups(g || [], gc);
                if (sv) {
                    var ns = gc.querySelector("input[type='text']");
                    if (ns) {
                        ns.value = sv;
                        filterForkGroupsIn(gc, sv.toLowerCase());
                    }
                }
            });
            loadExistingForksInto(card.querySelector(".existing-forks"), currentAssignmentId);
        }).catch(function (err) {
        restoreBtn();
        resultsDiv.textContent = "Failed: " + err;
    });
}

// --- Fork Groups -----------------------------------------------------

function renderForkGroups(groups, container) {
    container.textContent = "";
    if (groups.length === 0) {
        container.textContent = "No student groups.";
        return;
    }

    var search = document.createElement("input");
    search.type = "text";
    search.className = "form-control form-control-sm mb-2";
    search.placeholder = "Search groups or students...";
    search.addEventListener("input", function () {
        filterForkGroupsIn(container, this.value.toLowerCase());
    });
    container.appendChild(search);

    groups.forEach(function (g) {
        var hasCreatable = g.missingForkCount > 0;
        var card = document.createElement("div");
        card.className = "border rounded p-2 mb-2 fork-group-card";
        card.setAttribute("data-group-name", (g.name || "").toLowerCase());

        var header = document.createElement("div");
        header.className = "d-flex justify-content-between align-items-center";
        header.style.cursor = "pointer";
        var left = document.createElement("div");
        left.className = "d-flex align-items-center";

        if (hasCreatable) {
            var gcb = document.createElement("input");
            gcb.type = "checkbox";
            gcb.className = "form-check-input me-2";
            gcb.setAttribute("data-group-cb", "1");
            left.appendChild(gcb);
        }

        var label = document.createElement("strong");
        label.textContent = g.name || "";
        left.appendChild(label);
        left.appendChild(Prl.el("small", {
            className: "text-muted ms-2",
            text: " " + (g.memberCount || 0) + " members"
        }));

        if (g.missingForkCount > 0) left.appendChild(Prl.el("small", {
            className: "badge bg-warning ms-2",
            text: " " + g.missingForkCount + " not created"
        }));
        if ((g.unavailableCount || 0) > 0) left.appendChild(Prl.el("small", {
            className: "badge bg-danger ms-2",
            text: " " + g.unavailableCount + " unavailable"
        }));
        if (g.missingForkCount === 0 && (g.unavailableCount || 0) === 0) left.appendChild(Prl.el("small", {
            className: "badge bg-success ms-2",
            text: " all forked"
        }));

        header.appendChild(left);
        var chevron = document.createElement("i");
        chevron.className = "bi bi-chevron-down";
        header.appendChild(chevron);
        card.appendChild(header);

        var memberList = document.createElement("div");
        memberList.style.display = "none";
        memberList.className = "mt-2";
        memberList.setAttribute("data-member-list", "true");

        (g.members || []).forEach(function (m) {
            var row = document.createElement("div");
            row.className = "form-check ms-4 fork-member-row";
            row.setAttribute("data-username", (m.username || "").toLowerCase());
            row.setAttribute("data-displayname", (m.displayName || "").toLowerCase());

            var cb = document.createElement("input");
            cb.type = "checkbox";
            cb.className = "form-check-input fork-member-cb";
            cb.value = m.username;
            cb.checked = !m.hasFork && m.gitlabExists !== false;
            cb.disabled = m.hasFork || m.gitlabExists === false;

            var lbl = document.createElement("label");
            lbl.className = "form-check-label";
            if (m.hasFork) {
                lbl.appendChild(Prl.el("i", {className: "bi bi-check-circle text-success"}));
                lbl.appendChild(document.createTextNode(" " + (m.username || "")));
                if (m.displayName) lbl.appendChild(Prl.el("small", {
                    className: "text-muted",
                    text: " (" + m.displayName + ")"
                }));
                lbl.appendChild(Prl.el("small", {className: "text-success", text: " forked"}));
            } else if (m.gitlabExists === false) {
                lbl.appendChild(Prl.el("i", {className: "bi bi-exclamation-triangle text-danger"}));
                lbl.appendChild(document.createTextNode(" " + (m.username || "")));
                if (m.displayName) lbl.appendChild(Prl.el("small", {
                    className: "text-muted",
                    text: " (" + m.displayName + ")"
                }));
                lbl.appendChild(Prl.el("small", {className: "text-danger", text: " unavailable"}));
            } else {
                lbl.textContent = (m.username || "") + (m.displayName ? " (" + m.displayName + ")" : "");
            }

            row.appendChild(cb);
            row.appendChild(lbl);
            memberList.appendChild(row);
        });
        card.appendChild(memberList);

        header.addEventListener("click", function (e) {
            if (e.target.type === "checkbox") return;
            var vis = memberList.style.display !== "none";
            memberList.style.display = vis ? "none" : "";
            chevron.className = vis ? "bi bi-chevron-down" : "bi bi-chevron-up";
        });

        if (hasCreatable) {
            var gcbEl = card.querySelector("[data-group-cb]");
            if (gcbEl) gcbEl.addEventListener("change", function () {
                memberList.querySelectorAll(".fork-member-cb:not(:disabled)").forEach(function (c) {
                    c.checked = gcbEl.checked;
                });
            });
        }

        container.appendChild(card);
    });
}

function filterForkGroupsIn(container, query) {
    container.querySelectorAll(".fork-group-card").forEach(function (card) {
        if (!query) {
            card.style.display = "";
            card.querySelectorAll(".fork-member-row").forEach(function (r) {
                r.style.display = "";
            });
            return;
        }
        var groupMatch = (card.getAttribute("data-group-name") || "").indexOf(query) !== -1;
        var anyMember = false;
        card.querySelectorAll(".fork-member-row").forEach(function (row) {
            var match = (row.getAttribute("data-username") || "").indexOf(query) !== -1 || (row.getAttribute("data-displayname") || "").indexOf(query) !== -1;
            row.style.display = (groupMatch || match) ? "" : "none";
            if (match) anyMember = true;
        });
        card.style.display = (groupMatch || anyMember) ? "" : "none";
        if (anyMember && !groupMatch) {
            var ml = card.querySelector("[data-member-list]");
            if (ml) ml.style.display = "";
        }
    });
}

function loadExistingForksInto(container, assignmentId) {
    container.textContent = "Loading...";
    ViewEngine.call(SERVICE, "getExistingForks", [assignmentId]).then(function (forks) {
        container.textContent = "";
        if (!forks || forks.length === 0) {
            container.textContent = "No forks yet";
            return;
        }
        var ul = document.createElement("ul");
        ul.className = "list-unstyled mb-0";
        forks.forEach(function (f) {
            var li = document.createElement("li");
            li.appendChild(Prl.el("i", {className: "bi bi-git"}));
            li.appendChild(document.createTextNode(" "));
            var link = document.createElement("a");
            link.href = f.webUrl;
            link.target = "_blank";
            link.textContent = f.pathWithNamespace;
            li.appendChild(link);
            ul.appendChild(li);
        });
        container.appendChild(ul);
    }).catch(function (err) {
        container.textContent = "Error: " + err;
    });
}

// --- Verdict Script editor (Monaco) ---------------------------------

// Monaco is loaded via the AMD loader from CDN once per page; each form gets
// its own editor instance attached to the .script-editor element.
var monacoReady = null;

function ensureMonaco() {
    if (monacoReady) return monacoReady;
    monacoReady = new Promise(function (resolve, reject) {
        if (typeof require === "undefined" || !require.config) {
            reject(new Error("Monaco loader not present"));
            return;
        }
        require.config({paths: {vs: "https://cdn.jsdelivr.net/npm/monaco-editor@0.52.2/min/vs"}});
        require(["vs/editor/editor.main"], function () {
            resolve(window.monaco);
        }, reject);
    });
    return monacoReady;
}

var SCRIPT_TEMPLATES = {
    KTS: [
        "// ctx (JudgeContext) is provided automatically. Helpers pass(),",
        "// fail(), ctx.followBaseline() and the engine VOs (TaskResult,",
        "// TestSummary, ...) are auto-imported. The script's LAST expression",
        "// is the verdict.",
        "",
        "val perf = ctx.result.summary?.performance",
        "val passed = perf?.passed",
        "val total = perf?.totalTests",
        "when {",
        "    passed == null || total == null || total == 0 ->",
        "        ctx.followBaseline(\"No performance data\")",
        "    passed.toDouble() / total >= 0.7 ->",
        "        pass(summary = \"perf rate $passed/$total\")",
        "    else ->",
        "        fail(reason = \"Need 70% perf pass rate, got $passed/$total\")",
        "}",
        ""
    ].join("\n"),
    PYTHON: [
        "# Stdin = JudgeContext JSON (see schema on the right).",
        "# Print a JudgeResult JSON to stdout: {pass, summary?, reason?}.",
        "import json, sys",
        "ctx = json.load(sys.stdin)",
        "perf = ctx['result'].get('summary', {}).get('performance')",
        "if perf is None:",
        "    out = {'pass': ctx['baseline']['submissionStatus'] == 'COMPLETED',",
        "           'reason': 'No performance data'}",
        "elif perf['passed'] / perf['totalTests'] >= 0.7:",
        "    out = {'pass': True, 'summary': f\"perf rate {perf['passed']}/{perf['totalTests']}\"}",
        "else:",
        "    out = {'pass': False, 'reason': f\"Need 70% perf pass rate, got {perf['passed']}/{perf['totalTests']}\"}",
        "print(json.dumps(out))",
        ""
    ].join("\n")
};

// Read-only sidebar - instructor sees the field structure JudgeContext exposes
// to their script. Tracks the Kotlin VO definitions in pipeline/commons; if
// those change, update here too. Plain text on purpose (no live introspection).
function judgeContextDocs() {
    return [
        "JudgeContext",
        "+-- result: TaskResult",
        "|   +-- status: String          // \"completed\" | \"failed\" | \"cancelled\"",
        "|   +-- durationMs: Double?",
        "|   +-- error: String?",
        "|   +-- failedStep: String?",
        "|   +-- correctness: ScenarioResult[]?",
        "|   +-- performance: ScenarioResult[]?",
        "|   +-- performanceSkipped: Boolean?",
        "|   +-- assignmentConfig: { framework, mode, ... }?",
        "|   +-- environment: { platform, hardwareThreads }?",
        "|   +-- effectiveParams: { threads, memoryLimitMb, ... }?",
        "|   \-- summary: ResultSummary?",
        "|       +-- correctness: TestSummary?",
        "|       \-- performance: TestSummary?",
        "|           +-- totalTests: Int?",
        "|           +-- passed: Int?",
        "|           +-- failed: Int?",
        "|           +-- failedByTimeout: Int?",
        "|           +-- failedByOom: Int?",
        "|           +-- failedByCrash: Int?",
        "|           +-- failedByCorrectness: Int?",
        "|           \-- scalability: ScalabilityPoint[]?",
        "|",
        "\-- baseline: SubmissionVerdict",
        "    +-- submissionStatus: COMPLETED | FAILED | ERROR | REJECTED",
        "    +-- jobStatus: SUCCESS | FAILED | INTERRUPTED | ERROR",
        "    +-- summary: String",
        "    \-- reason: String?",
        "",
        "Return / print JudgeResult:",
        "    { pass: Boolean,",
        "      summary: String?,   // short one-liner for UI / CI",
        "      reason: String? }   // long explanation"
    ].join("\n");
}

// Wires the script section: language dropdown toggles editor visibility +
// switches Monaco language mode; existing script is loaded into the editor.
function initScriptEditor(formCard, existingScript) {
    var section = formCard.querySelector(".script-section");
    if (!section) return;
    var typeSelect = section.querySelector(".script-type");
    var body = section.querySelector(".script-body");
    var editorHost = section.querySelector(".script-editor");
    var enabledWrap = section.querySelector(".script-enabled-wrap");
    var enabledInput = section.querySelector(".script-enabled");
    var timeoutWrap = section.querySelector(".script-timeout-wrap");
    var timeoutInput = section.querySelector(".script-timeout");

    var currentType = (existingScript && existingScript.type) ? existingScript.type : "NONE";
    var sourceCache = {KTS: SCRIPT_TEMPLATES.KTS, PYTHON: SCRIPT_TEMPLATES.PYTHON};
    if (existingScript && existingScript.source) {
        sourceCache[existingScript.type] = existingScript.source;
    }
    typeSelect.value = currentType;
    if (existingScript) {
        if (existingScript.enabled === false) enabledInput.checked = false;
        if (existingScript.timeoutMs != null) timeoutInput.value = existingScript.timeoutMs;
    }

    var editor = null;

    function syncVisibility() {
        var t = typeSelect.value;
        if (t === "NONE") {
            body.style.display = "none";
            enabledWrap.style.display = "none";
            timeoutWrap.style.display = "none";
            return;
        }
        body.style.display = "";
        enabledWrap.style.display = "";
        timeoutWrap.style.display = "";
        ensureMonaco().then(function (monaco) {
            if (!editor) {
                editor = monaco.editor.create(editorHost, {
                    value: sourceCache[t] || SCRIPT_TEMPLATES[t] || "",
                    language: t === "KTS" ? "kotlin" : "python",
                    theme: "vs-dark",
                    automaticLayout: true,
                    minimap: {enabled: false},
                    fontSize: 13,
                    scrollBeyondLastLine: false,
                });
                formCard._scriptEditor = editor;
            } else {
                // Cache current text under the OLD type before switching, so a
                // user toggling KTS <-> PYTHON doesn't lose their work.
                if (editor._lastType) sourceCache[editor._lastType] = editor.getValue();
                monaco.editor.setModelLanguage(editor.getModel(), t === "KTS" ? "kotlin" : "python");
                editor.setValue(sourceCache[t] || SCRIPT_TEMPLATES[t] || "");
            }
            editor._lastType = t;
        }).catch(function (err) {
            editorHost.textContent = "Monaco failed to load: " + (err && err.message ? err.message : err);
        });
    }

    typeSelect.addEventListener("change", syncVisibility);
    syncVisibility();
}

// Pulls the script payload out of the form for save. Returns the
// EvaluatorScript JSON object when the instructor wants a script attached,
// or null when they switched the dropdown to NONE / left the body empty -
// under PUT semantics, null on the wire clears any existing script.
function collectScriptPayload(formCard) {
    var section = formCard.querySelector(".script-section");
    if (!section) return null;
    var type = section.querySelector(".script-type").value;
    if (type === "NONE") return null;
    var editor = formCard._scriptEditor;
    var source = editor ? editor.getValue() : (SCRIPT_TEMPLATES[type] || "");
    if (!source.trim()) return null;
    var enabledEl = section.querySelector(".script-enabled");
    var timeoutEl = section.querySelector(".script-timeout");
    var timeoutMs = parseInt(timeoutEl && timeoutEl.value, 10);
    if (!timeoutMs || timeoutMs <= 0) timeoutMs = 5000;
    return {
        type: type,
        source: source,
        enabled: !enabledEl || enabledEl.checked,
        timeoutMs: timeoutMs
    };
}
