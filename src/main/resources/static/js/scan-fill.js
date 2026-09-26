/* Scan & Fill page logic: upload -> POST /scan/extract -> auto-fill editable form.
 * Works with the current `fw*` IDs in scan-fill.html and legacy `scan*` IDs.
 * Safe to load on any page: no-ops when the scan form is absent. */
(function () {
    "use strict";

    function pick() {
        for (var i = 0; i < arguments.length; i++) {
            var el = document.getElementById(arguments[i]);
            if (el) return el;
        }
        return null;
    }

    function init() {
        var fileInput = pick("fwFile", "scanFile");
        var scanBtn = pick("fwScanBtn", "scanBtn");
        var loading = pick("fwScanbar", "scanLoading");
        var msgBox = pick("fwScanMsg", "scanError");
        var warnBox = pick("fwWarn", "scanNotice");
        var form = pick("fwSaveForm", "scanForm");
        var editBtn = pick("fwEditBtn", "editBtn");
        var clearBtn = pick("fwClearBtn", "clearBtn");
         var drop = document.getElementById("fwDrop");
         var browse = document.getElementById("fwBrowse");
         var fileName = document.getElementById("fwFileName");
         var transactionBox = document.getElementById("fwTransactions");
         var transactionRows = document.getElementById("fwTransactionRows");
         var transactionCount = document.getElementById("fwTransactionCount");

        // Not on the scan page — do nothing so other pages never break.
        if (!fileInput || !scanBtn || !form) return;

        var ALLOWED = [".pdf", ".jpg", ".jpeg", ".png"];
        var MAX_SIZE = 10 * 1024 * 1024; // 10 MB, mirrors server-side validation

        function toast(title, message, kind) {
            try {
                if (typeof window.fwToast === "function") window.fwToast(title, message, kind);
            } catch (e) { /* toast is best-effort */ }
        }

        function showMsg(html, kind) {
            if (!msgBox) return;
            msgBox.innerHTML = html
                ? '<div class="fw-alert ' + kind + '">' + html + "</div>"
                : "";
        }

        function showWarn(html) {
            if (!warnBox) return;
            warnBox.innerHTML = html
                ? '<div class="fw-alert warn">⚠ ' + html + "</div>"
                : "";
        }

        function setField(idA, idB, value) {
            var el = pick(idA, idB);
            if (el && value !== null && value !== undefined) el.value = value;
        }

        function setSelect(idA, idB, value, fallback) {
            var el = pick(idA, idB);
            if (!el || !el.options) return;
            var target = String(value === null || value === undefined ? "" : value).toLowerCase();
            var matched = null;
            Array.prototype.forEach.call(el.options, function (opt) {
                if (String(opt.value).toLowerCase() === target) matched = opt.value;
            });
            el.value = matched !== null ? matched : (fallback || (el.options[0] && el.options[0].value));
        }

        function refreshPreview() {
            var pv = document.getElementById("fwPreview");
            if (pv) pv.style.display = "grid";
            var merchantEl = pick("fMerchant", "merchant");
            var amountEl = pick("fAmount", "amount");
            var dateEl = pick("fDate", "date");
            var payEl = pick("fPay", "paymentMethod");
            var catEl = pick("fCat", "category");
            var typeEl = pick("fType", "transactionType");
            var setText = function (id, text) {
                var node = document.getElementById(id);
                if (node) node.textContent = text;
            };
            setText("pvMerchant", (merchantEl && merchantEl.value) || "—");
            setText("pvAmount", (amountEl && amountEl.value) ? "₹" + amountEl.value : "—");
            setText("pvDate", (dateEl && dateEl.value) || "—");
            setText("pvPay", (payEl && payEl.value) || "—");
            setText("pvCat", (catEl && catEl.value) || "—");
            setText("pvType", (typeEl && typeEl.value) || "—");
        }

        function setTransactionForm(transaction) {
            if (!transaction) return;
            setField("fMerchant", "merchant", transaction.merchant || "");
            setField("fAmount", "amount", transaction.amount !== null && transaction.amount !== undefined ? transaction.amount : "");
            setField("fDate", "date", transaction.date || "");
            setSelect("fPay", "paymentMethod", transaction.paymentMethod, "Other");
            setSelect("fCat", "category", transaction.category, "Other");
            setSelect("fType", "transactionType", transaction.transactionType, "EXPENSE");
            var dup = document.getElementById("fwDupBadge");
            if (dup) dup.style.display = transaction.duplicate ? "" : "none";
            refreshPreview();
        }

        function renderTransactions(transactions) {
            if (!transactionBox || !transactionRows) return;
            transactionRows.replaceChildren();
            if (!Array.isArray(transactions) || transactions.length === 0) {
                transactionBox.style.display = "none";
                return;
            }
            transactions.forEach(function (transaction) {
                var row = document.createElement("tr");
                var description = document.createElement("td");
                var type = document.createElement("td");
                var amount = document.createElement("td");
                var date = document.createElement("td");
                var actions = document.createElement("td");
                description.textContent = transaction.merchant || "—";
                type.textContent = transaction.transactionType === "INCOME" ? "Income" : "Expense";
                amount.textContent = transaction.amount !== null && transaction.amount !== undefined ? "₹" + transaction.amount : "—";
                date.textContent = transaction.date || "—";
                var review = document.createElement("button");
                review.type = "button";
                review.className = "fw-btn fw-btn-sec";
                review.textContent = "Review";
                review.addEventListener("click", function () {
                    setTransactionForm(transaction);
                    toast("Review transaction", "Review the fields before saving.", "");
                });
                actions.appendChild(review);
                row.appendChild(description);
                row.appendChild(type);
                row.appendChild(amount);
                row.appendChild(date);
                row.appendChild(actions);
                transactionRows.appendChild(row);
            });
            if (transactionCount) transactionCount.textContent = transactions.length;
            transactionBox.style.display = "block";
        }

        function refreshState() {
            var f = fileInput.files && fileInput.files[0];
            if (fileName) {
                fileName.textContent = f
                    ? "Selected: " + f.name + " (" + Math.round(f.size / 1024) + " KB)"
                    : "";
            }
            scanBtn.disabled = !f;
        }

        function validateFile(f) {
            if (!f) return "No file selected. Please choose a PDF or image file.";
            var lower = (f.name || "").toLowerCase();
            var ok = ALLOWED.some(function (ext) { return lower.endsWith(ext); });
            if (!ok) return "Unsupported file type. Please upload a PDF, JPG/JPEG or PNG file.";
            if (f.size > MAX_SIZE) return "File is too large. Maximum allowed size is 10 MB.";
            return null;
        }

        if (browse) {
            browse.addEventListener("click", function (e) {
                e.stopPropagation();
                fileInput.click();
            });
        }
        if (drop) {
            drop.addEventListener("click", function () { fileInput.click(); });
            drop.addEventListener("keydown", function (e) {
                if (e.key === "Enter" || e.key === " ") { e.preventDefault(); fileInput.click(); }
            });
            ["dragover", "dragenter"].forEach(function (ev) {
                drop.addEventListener(ev, function (e) { e.preventDefault(); drop.classList.add("over"); });
            });
            ["dragleave", "drop"].forEach(function (ev) {
                drop.addEventListener(ev, function (e) { e.preventDefault(); drop.classList.remove("over"); });
            });
            drop.addEventListener("drop", function (e) {
                try {
                    if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length) {
                        fileInput.files = e.dataTransfer.files;
                        refreshState();
                    }
                } catch (err) { /* assignment may be read-only in some browsers */ }
            });
        }

        fileInput.addEventListener("change", refreshState);

        if (clearBtn) {
            clearBtn.addEventListener("click", function () {
                try { form.reset(); } catch (e) {}
                try { fileInput.value = ""; } catch (e) {}
                refreshState();
                showMsg("", "");
                showWarn("");
                var pv = document.getElementById("fwPreview");
                if (pv) pv.style.display = "none";
                 var dup = document.getElementById("fwDupBadge");
                 if (dup) dup.style.display = "none";
                 if (transactionBox) transactionBox.style.display = "none";
                 if (transactionRows) transactionRows.replaceChildren();
                 if (transactionCount) transactionCount.textContent = "0";
            });
        }

        if (editBtn) {
            editBtn.addEventListener("click", function () {
                var m = pick("fMerchant", "merchant");
                if (m) m.focus();
                toast("Review mode", "All fields are editable — correct anything, then save.", "");
            });
        }

        scanBtn.addEventListener("click", function () {
            showMsg("", "");
            showWarn("");

            var file = fileInput.files && fileInput.files[0];
            var err = validateFile(file);
            if (err) {
                showMsg("⚠ " + err, "bad");
                toast("Check file", err, "warn");
                return;
            }

            var formData = new FormData();
            formData.append("file", file);

            if (loading) loading.classList.add("on");
            else if (loading !== null && loading.hidden !== undefined) loading.hidden = false;
            scanBtn.disabled = true;

            fetch("/scan/extract", { method: "POST", body: formData })
                .then(function (res) {
                    var contentType = "";
                    try { contentType = res.headers.get("content-type") || ""; } catch (e) {}
                    if (!res.ok || contentType.indexOf("application/json") === -1) {
                        throw new Error("HTTP " + res.status);
                    }
                    return res.json();
                })
                .then(function (payload) {
                    if (loading) loading.classList.remove("on");
                    if (loading !== null && loading.hidden !== undefined) loading.hidden = true;
                    scanBtn.disabled = false;
                    if (!payload || !payload.success) {
                        var m = (payload && payload.message) ||
                            "Unable to extract transaction details. Please enter the details manually.";
                        showMsg("⚠ " + m, "bad");
                        toast("Extraction failed", "Enter the details manually and save.", "bad");
                        return;
                    }
                     var d = payload.data || {};
                     var extracted = Array.isArray(payload.transactions) ? payload.transactions : [];
                     if (extracted.length === 0) extracted = [d];
                     renderTransactions(extracted);
                     setTransactionForm(extracted[0] || d);

                     var warnings = Array.isArray(d.warnings) ? d.warnings.slice() : [];
                     if (payload.message && warnings.indexOf(payload.message) === -1) warnings.push(payload.message);
                     showWarn(warnings.length ? warnings.join("<br>") : "");

                     var count = extracted.length;
                     showMsg("✓ Extraction complete — " + count + " transaction" + (count === 1 ? "" : "s") + " found. Review and save.", "ok");
                     toast("Transactions extracted", count + " transaction" + (count === 1 ? "" : "s") + " ready for review.", "ok");
                })
                .catch(function () {
                    if (loading) loading.classList.remove("on");
                    if (loading !== null && loading.hidden !== undefined) loading.hidden = true;
                    scanBtn.disabled = false;
                    showMsg("⚠ Unable to process document — please enter details manually.", "bad");
                });
        });

        form.addEventListener("submit", function (e) {
            var merchantEl = pick("fMerchant", "merchant");
            var amountEl = pick("fAmount", "amount");
            var dateEl = pick("fDate", "date");
            var ok = merchantEl && merchantEl.value.trim() && amountEl && amountEl.value && dateEl && dateEl.value;
            if (!ok) {
                toast("Check fields", "Merchant, amount and date are required.", "warn");
                if (!ok) { e.preventDefault(); return false; }
            }
        });

        refreshState();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
