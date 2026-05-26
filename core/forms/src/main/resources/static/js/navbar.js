document.addEventListener("DOMContentLoaded", function () {
    var path = window.location.pathname;

    // Each link can require:
    //   - role:    one of the user's ROLE_* roles (ADMIN / STUDENT / USER)
    //   - feature: a feature-flag token returned by UserViewService.getUserInfo,
    //              present only when the backing module is on the classpath.
    //              The HTTP / AMQP adapter pages live in optional jars, so we
    //              hide them entirely when the jar isn't there.
    var links = [
        {href: "/admin", label: "Admin", icon: "bi-shield-lock", role: "ADMIN"},
        {href: "/api-keys", label: "API Keys", icon: "bi-key", role: "ADMIN"},
        {href: "/students", label: "Students", icon: "bi-mortarboard", role: "ADMIN"},
        {href: "/student-groups", label: "Groups", icon: "bi-people", role: "ADMIN"},
        {href: "/assignments", label: "Assignments", icon: "bi-card-checklist", role: "ADMIN"},
        {href: "/submissions", label: "Submissions", icon: "bi-cloud-upload", role: "ADMIN"},
        {href: "/runners", label: "Runners", icon: "bi-cpu", role: "ADMIN"},
        {href: "/adapter-http", label: "HTTP Config", icon: "bi-globe", role: "ADMIN", feature: "adapter-http-forms"},
        {href: "/adapter-rabbit", label: "AMQP Config", icon: "bi-envelope", role: "ADMIN", feature: "adapter-rabbit-forms"},
        {href: "/my-submissions", label: "My submissions", icon: "bi-cloud-upload", role: "STUDENT"},
    ];

    function makeIconLabel(parent, iconClass, labelText) {
        var icon = document.createElement("i");
        icon.className = "bi " + iconClass;
        parent.appendChild(icon);
        parent.appendChild(document.createTextNode(" " + labelText));
    }

    var nav = document.createElement("nav");
    nav.className = "navbar prl-navbar";
    nav.setAttribute("data-bs-theme", "dark");

    var container = document.createElement("div");
    container.className = "container-fluid";

    var leftGroup = document.createElement("div");
    leftGroup.className = "d-flex align-items-center gap-3";

    var backBtn = document.createElement("button");
    backBtn.className = "btn btn-outline-light btn-sm";
    backBtn.title = "Back";
    var backIcon = document.createElement("i");
    backIcon.className = "bi bi-arrow-left";
    backBtn.appendChild(backIcon);
    backBtn.addEventListener("click", function () {
        history.back();
    });
    leftGroup.appendChild(backBtn);

    var brand = document.createElement("a");
    brand.className = "navbar-brand mb-0";
    brand.href = "/";
    brand.textContent = "Parallel";
    leftGroup.appendChild(brand);

    var ul = document.createElement("ul");
    ul.className = "navbar-nav flex-row gap-2";

    links.forEach(function (link) {
        var li = document.createElement("li");
        li.className = "nav-item";
        if (link.role || link.feature) {
            li.style.display = "none";
            if (link.role) li.setAttribute("data-navbar-role", link.role);
            if (link.feature) li.setAttribute("data-navbar-feature", link.feature);
        }

        var a = document.createElement("a");
        a.className = "nav-link" + (path === link.href ? " active" : "");
        a.href = link.href;
        makeIconLabel(a, link.icon, link.label);

        li.appendChild(a);
        ul.appendChild(li);
    });

    leftGroup.appendChild(ul);
    container.appendChild(leftGroup);

    var rightGroup = document.createElement("div");
    rightGroup.className = "d-flex align-items-center gap-2";

    var profileLink = document.createElement("a");
    profileLink.className = "btn btn-outline-light btn-sm";
    profileLink.href = "/profile";
    var profileIcon = document.createElement("i");
    profileIcon.className = "bi bi-person-circle";
    profileLink.appendChild(profileIcon);
    profileLink.appendChild(document.createTextNode(" "));
    var nameSpan = document.createElement("span");
    nameSpan.setAttribute("data-navbar-display-name", "");
    nameSpan.textContent = "Profile";
    profileLink.appendChild(nameSpan);
    rightGroup.appendChild(profileLink);

    var form = document.createElement("form");
    form.method = "post";
    form.action = "/logout";

    var logoutBtn = document.createElement("button");
    logoutBtn.className = "btn btn-outline-light btn-sm";
    logoutBtn.type = "submit";
    makeIconLabel(logoutBtn, "bi-box-arrow-right", "Logout");
    form.appendChild(logoutBtn);
    rightGroup.appendChild(form);
    container.appendChild(rightGroup);
    nav.appendChild(container);

    document.body.insertBefore(nav, document.body.firstChild);

    if (typeof ViewEngine !== "undefined") {
        ViewEngine.call("prl.userViewService", "getUserInfo").then(function (data) {
            if (!data) return;
            var roles = data.roles || [];
            var features = data.features || [];
            // A link is visible when ALL of its declared constraints pass:
            // the user has the role (if any) AND the feature is enabled
            // (if any). Both default to "no constraint".
            nav.querySelectorAll("[data-navbar-role], [data-navbar-feature]").forEach(function (el) {
                var reqRole = el.getAttribute("data-navbar-role");
                var reqFeature = el.getAttribute("data-navbar-feature");
                var roleOk = !reqRole || roles.indexOf(reqRole) !== -1;
                var featureOk = !reqFeature || features.indexOf(reqFeature) !== -1;
                if (roleOk && featureOk) el.style.display = "";
            });
            var nameEl = nav.querySelector("[data-navbar-display-name]");
            if (nameEl) nameEl.textContent = data.displayName || data.login || "Profile";
        }).catch(function () {
            // Session expired between page load and getUserInfo call - the
            // page itself is still in the user's browser but the cookie
            // doesn't auth anymore. Send them through /login so they can
            // pick up a fresh session.
            window.location.href = "/login?unauthorized=true";
        });
    }
});
