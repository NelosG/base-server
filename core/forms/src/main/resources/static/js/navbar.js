document.addEventListener("DOMContentLoaded", function () {
    var path = window.location.pathname;

    var links = [
        {href: "/admin", label: "Admin", icon: "bi-shield-lock", role: "ADMIN"},
        {href: "/settings", label: "Settings", icon: "bi-gear"},
        {href: "/adapter-http-test", label: "HTTP Test", icon: "bi-globe"},
        {href: "/adapter-rabbit-test", label: "AMQP Test", icon: "bi-envelope"},
    ];

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
    backBtn.innerHTML = "<i class='bi bi-arrow-left'></i>";
    backBtn.onclick = function () {
        history.back();
    };
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

        if (link.role) {
            li.style.display = "none";
            li.setAttribute("data-navbar-role", link.role);
        }

        var a = document.createElement("a");
        a.className = "nav-link" + (path === link.href ? " active" : "");
        a.href = link.href;
        a.innerHTML = "<i class='bi " + link.icon + "'></i> " + link.label;

        li.appendChild(a);
        ul.appendChild(li);
    });

    leftGroup.appendChild(ul);
    container.appendChild(leftGroup);

    var form = document.createElement("form");
    form.method = "post";
    form.action = "/logout";

    var logoutBtn = document.createElement("button");
    logoutBtn.className = "btn btn-outline-light btn-sm";
    logoutBtn.type = "submit";
    logoutBtn.innerHTML = "<i class='bi bi-box-arrow-right'></i> Logout";

    form.appendChild(logoutBtn);
    container.appendChild(form);
    nav.appendChild(container);

    document.body.insertBefore(nav, document.body.firstChild);

    if (path === "/admin") {
        nav.querySelectorAll("[data-navbar-role]").forEach(function (el) {
            el.style.display = "";
        });
    }

    if (typeof ViewEngine !== "undefined") {
        ViewEngine.call("prl.userViewService", "getUserInfo").then(function (data) {
            if (data && data.roles) {
                nav.querySelectorAll("[data-navbar-role]").forEach(function (el) {
                    var role = el.getAttribute("data-navbar-role");
                    if (data.roles.indexOf(role) !== -1) {
                        el.style.display = "";
                    }
                });
            }
        });
    }
});
