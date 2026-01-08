/**
 * Code editor with syntax highlighting powered by highlight.js.
 * Uses transparent textarea over a highlighted overlay.
 *
 * Usage:
 *   CodeEditor.attach("my-textarea-id", "json");
 *   var value = CodeEditor.getValue("my-textarea-id");
 */
var CodeEditor = (function () {

    var editors = {};

    function syncHighlight(id) {
        var ed = editors[id];
        if (!ed) return;
        var text = ed.textarea.value;
        if (!text) {
            ed.highlight.innerHTML = "\n";
            return;
        }
        try {
            ed.highlight.innerHTML = hljs.highlight(text, {language: ed.language}).value + "\n";
        } catch (e) {
            ed.highlight.textContent = text + "\n";
        }
        ed.highlight.scrollTop = ed.textarea.scrollTop;
        ed.highlight.scrollLeft = ed.textarea.scrollLeft;
    }

    function autoResize(id) {
        var ed = editors[id];
        if (!ed) return;
        var ta = ed.textarea;
        ta.style.height = "auto";
        var h = Math.max(ta.scrollHeight, 38);
        ta.style.height = h + "px";
        ed.highlight.style.height = h + "px";
    }

    function attach(id, language) {
        var textarea = document.getElementById(id);
        if (!textarea || editors[id]) return;

        var wrapper = document.createElement("div");
        wrapper.className = "prl-code-editor";
        textarea.parentNode.insertBefore(wrapper, textarea);

        var highlight = document.createElement("pre");
        highlight.className = "prl-code-highlight hljs";
        wrapper.appendChild(highlight);

        wrapper.appendChild(textarea);
        textarea.classList.add("prl-code-input");

        editors[id] = {textarea: textarea, highlight: highlight, language: language || "json"};

        textarea.addEventListener("input", function () {
            syncHighlight(id);
            autoResize(id);
        });
        textarea.addEventListener("scroll", function () {
            highlight.scrollTop = textarea.scrollTop;
            highlight.scrollLeft = textarea.scrollLeft;
        });

        syncHighlight(id);
        autoResize(id);
    }

    function getValue(id) {
        var ed = editors[id];
        return ed ? ed.textarea.value : "";
    }

    function setValue(id, val) {
        var ed = editors[id];
        if (!ed) return;
        ed.textarea.value = val;
        syncHighlight(id);
        autoResize(id);
    }

    return {
        attach: attach,
        getValue: getValue,
        setValue: setValue
    };
})();
