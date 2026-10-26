(function () {
  var button = document.getElementById("installFinWise");
  var status = document.getElementById("installFinWiseStatus");
  var installPrompt = null;

  if (!button || !status) {
    return;
  }

  function isInstalled() {
    return window.matchMedia("(display-mode: standalone)").matches || window.navigator.standalone === true;
  }

  if (isInstalled()) {
    button.hidden = true;
  }

  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.register("/service-worker.js").catch(function (error) {
      console.error("FinWise could not register its app worker.", error);
    });
  }

  window.addEventListener("beforeinstallprompt", function (event) {
    event.preventDefault();
    installPrompt = event;
  });

  window.addEventListener("appinstalled", function () {
    installPrompt = null;
    button.hidden = true;
    status.textContent = "FinWise is installed on this computer.";
  });

  button.addEventListener("click", async function () {
    if (!window.isSecureContext || !("serviceWorker" in navigator)) {
      status.textContent = "App installation requires a secure browser connection. Open FinWise at http://localhost:8080.";
      return;
    }

    if (!installPrompt) {
      status.textContent = "In Chrome or Edge, open the browser menu and choose Install FinWise or Install this site as an app.";
      return;
    }

    installPrompt.prompt();
    var choice = await installPrompt.userChoice;
    installPrompt = null;
    status.textContent = choice.outcome === "accepted"
      ? "FinWise is being installed on this computer."
      : "Installation was cancelled.";
  });
})();
