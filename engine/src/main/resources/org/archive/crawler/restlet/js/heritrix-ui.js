(function () {
  "use strict";

  // Implement invoker commands for older browsers like Firefox ESR
  // This can likely be removed in April 2027 when ESR 115 ends
  if (!("command" in HTMLButtonElement.prototype &&
        "commandForElement" in HTMLButtonElement.prototype)) {
    // Delegate so buttons parsed after this script (including dialog close buttons) work.
    document.addEventListener("click", function (event) {
      var button = event.target.closest("button[command][commandfor]");
      if (!button || button.matches(":disabled") || event.defaultPrevented) {
        return;
      }

      var target = document.getElementById(button.getAttribute("commandfor"));
      if (!target || target.localName !== "dialog") {
        return;
      }

      var command = button.getAttribute("command").toLowerCase();
      if (command === "show-modal" && typeof target.showModal === "function") {
        event.preventDefault();
        target.showModal();
      } else if (command === "close" && typeof target.close === "function") {
        event.preventDefault();
        target.close();
      }
    });
  }

  document.addEventListener("click", function (event) {
    var topbarToggle = event.target.closest(".top-bar .toggle-topbar a");
    if (topbarToggle) {
      event.preventDefault();
      topbarToggle.closest(".top-bar").classList.toggle("expanded");
      return;
    }

    var dropdownToggle = event.target.closest(".top-bar .has-dropdown > a");
    if (dropdownToggle && window.matchMedia("(max-width: 58.75em)").matches) {
      event.preventDefault();
      dropdownToggle.parentElement.classList.toggle("open");
      return;
    }

    var alertClose = event.target.closest("[data-alert] .close");
    if (alertClose) {
      event.preventDefault();
      alertClose.closest("[data-alert]").remove();
    }
  });

}());
