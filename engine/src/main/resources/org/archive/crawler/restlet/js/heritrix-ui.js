(function () {
  "use strict";

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
