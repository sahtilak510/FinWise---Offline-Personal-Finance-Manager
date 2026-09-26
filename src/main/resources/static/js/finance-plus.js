(function () {
  'use strict';

  var hash = window.location.hash.replace('#', '');
  var target = hash ? document.getElementById(hash) : document.getElementById('overview');
  if (target) {
    var button = document.querySelector('[data-bs-target="#' + target.id + '"]');
    if (button && window.bootstrap) {
      var tab = new bootstrap.Tab(button);
      tab.show();
    }
  }

  function showHash() {
    var id = window.location.hash.replace('#', '');
    var target = id && document.getElementById(id);
    if (!target || !target.classList.contains('tab-pane')) return;
    var button = document.querySelector('[data-bs-target="#' + target.id + '"]');
    if (button && window.bootstrap) new bootstrap.Tab(button).show();
  }

  document.querySelectorAll('[data-bs-toggle="pill"]').forEach(function (button) {
    button.addEventListener('shown.bs.tab', function () {
      var id = button.getAttribute('data-bs-target').replace('#', '');
      history.replaceState(null, '', '#' + id);
    });
  });
  window.addEventListener('hashchange', showHash);
  showHash();

  document.querySelectorAll('.fp-setting input[type="checkbox"]').forEach(function (checkbox) {
    checkbox.addEventListener('change', function () {
      var hidden = checkbox.parentElement.querySelector('input[type="hidden"]');
      if (hidden) hidden.value = checkbox.checked ? 'true' : 'false';
    });
  });

  document.querySelectorAll('form[data-confirm]').forEach(function (form) {
    form.addEventListener('submit', function (event) {
      if (!window.confirm(form.getAttribute('data-confirm'))) event.preventDefault();
    });
  });

  document.querySelectorAll('form[method="post"]').forEach(function (form) {
    if (form.querySelector('input[name="_csrf"]')) return;
    var input = document.createElement('input');
    input.type = 'hidden';
    input.name = '_csrf';
    input.value = window.FW_CSRF_TOKEN || '';
    form.appendChild(input);
  });

  var alerts = document.querySelectorAll('.fp-alert');
  window.setTimeout(function () {
    alerts.forEach(function (alert) {
      alert.style.transition = 'opacity .3s ease';
      alert.style.opacity = '0';
      window.setTimeout(function () { alert.remove(); }, 300);
    });
  }, 5000);
})();
