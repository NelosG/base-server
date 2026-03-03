const ViewEngine = {
    call: function (service, method, args) {
        return fetch("/api/view/invoke", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({service: service, method: method, args: args || []})
        })
            .then(function (response) {
                return response.json().then(function (result) {
                    if (!result.success) {
                        throw new Error(result.error || "Unknown error");
                    }
                    return result.data;
                });
            });
    },

    _populateOptions: function (select, options) {
        select.innerHTML = "";
        options.forEach(function (opt) {
            var el = document.createElement("option");
            el.value = opt.value;
            el.textContent = opt.label;
            select.appendChild(el);
        });
    },

    _populateFields: function (container, data) {
        container.querySelectorAll("[name]").forEach(function (el) {
            var value = data[el.name];
            if (value === undefined) return;
            if (el.type === "checkbox") {
                el.checked = !!value;
            } else {
                el.value = value;
            }
        });
        container.querySelectorAll("[data-view-field]").forEach(function (el) {
            var value = data[el.getAttribute("data-view-field")];
            if (value !== undefined) {
                el.textContent = value;
            }
        });
    },

    _collectFields: function (container) {
        var data = {};
        var fields = container.querySelectorAll("[name]");
        fields.forEach(function (el) {
            if (el.type === "checkbox") {
                data[el.name] = el.checked;
            } else {
                data[el.name] = el.value;
            }
        });
        return data;
    },

    _showAlert: function (alertEl, message, type) {
        alertEl.className = "alert alert-" + type;
        alertEl.textContent = message;
        alertEl.classList.remove("d-none");
        alertEl.style.display = "";
        setTimeout(function () {
            alertEl.classList.add("d-none");
        }, 3000);
    },

    _processUrlAlerts: function (alertEl) {
        if (!alertEl) return;
        var urlAlertAttr = alertEl.getAttribute("data-url-alert");
        if (!urlAlertAttr) return;

        var params = new URLSearchParams(window.location.search);
        var alertMap = JSON.parse(urlAlertAttr);

        for (var paramName in alertMap) {
            if (params.has(paramName)) {
                var type = (paramName === "error" || paramName === "unauthorized") ? "danger" : "success";
                ViewEngine._showAlert(alertEl, alertMap[paramName], type);
                break;
            }
        }
    },

    _processUrlParams: function () {
        var params = new URLSearchParams(window.location.search);

        document.querySelectorAll("[data-url-param]").forEach(function (el) {
            var paramName = el.getAttribute("data-url-param");
            var value = params.get(paramName);
            if (value !== null) {
                el.textContent = value;
            }
        });

        document.querySelectorAll("[data-url-href]").forEach(function (el) {
            var paramName = el.getAttribute("data-url-href");
            var value = params.get(paramName);
            if (value !== null && value.startsWith("/") && !value.startsWith("//")) {
                el.href = value;
            }
        });
    },

    _processRoles: function (container, data) {
        if (!data || !data.roles) return;
        container.querySelectorAll("[data-view-role]").forEach(function (el) {
            var role = el.getAttribute("data-view-role");
            if (data.roles.indexOf(role) !== -1) {
                el.style.display = "";
            }
        });
    },

    init: function () {
        ViewEngine._processUrlParams();

        var containers = document.querySelectorAll("[data-view-service]");
        containers.forEach(function (container) {
            var service = container.getAttribute("data-view-service");
            var loadMethod = container.getAttribute("data-view-load");
            var saveMethod = container.getAttribute("data-view-save");
            var redirectUrl = container.getAttribute("data-view-redirect");
            var alertEl = container.querySelector("[data-view-alert]");
            var formEl = container.querySelector("[data-view-form]");
            var loadingEl = container.querySelector("[data-view-loading]");
            var saveBtn = container.querySelector("[data-view-save-btn]");

            ViewEngine._processUrlAlerts(alertEl);

            var optionSelects = container.querySelectorAll("[data-view-options]");
            var promises = [];

            optionSelects.forEach(function (select) {
                var optionsMethod = select.getAttribute("data-view-options");
                promises.push(
                    ViewEngine.call(service, optionsMethod).then(function (options) {
                        ViewEngine._populateOptions(select, options);
                    })
                );
            });

            if (loadMethod) {
                promises.push(
                    ViewEngine.call(service, loadMethod).then(function (data) {
                        return data;
                    })
                );
            }

            if (promises.length > 0) {
                Promise.all(promises).then(function (results) {
                    if (loadMethod) {
                        var data = results[results.length - 1];
                        ViewEngine._populateFields(container, data);
                        ViewEngine._processRoles(container, data);
                    }
                    if (loadingEl) loadingEl.style.display = "none";
                    if (formEl) formEl.style.display = "block";
                }).catch(function (err) {
                    if (loadingEl) loadingEl.style.display = "none";
                    if (alertEl) ViewEngine._showAlert(alertEl, "Failed to load: " + err.message, "danger");
                });
            }

            if (saveBtn && saveMethod) {
                saveBtn.addEventListener("click", function () {
                    var data = ViewEngine._collectFields(container);
                    ViewEngine.call(service, saveMethod, [data]).then(function () {
                        if (redirectUrl) {
                            window.location.href = redirectUrl;
                        } else if (alertEl) {
                            ViewEngine._showAlert(alertEl, "Saved successfully", "success");
                        }
                    }).catch(function (err) {
                        if (alertEl) ViewEngine._showAlert(alertEl, err.message, "danger");
                    });
                });
            }
        });
    }
};

document.addEventListener("DOMContentLoaded", function () {
    ViewEngine.init();

    if ("serviceWorker" in navigator) {
        navigator.serviceWorker.register("/sw.js");
    }
});
