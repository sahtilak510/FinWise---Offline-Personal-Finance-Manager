(function () {
  'use strict';

  var API_ASK = '/api/chatbot/ask';
  var API_HISTORY = '/api/chatbot/history';
  var API_DATA = '/api/chatbot/data';
  var MAX_LEN = 500;

  var fab, panel, backdrop, messagesEl, inputEl, sendBtn, charEl;
  var closeBtn, minBtn, clearBtn, chipsContainer, emptyEl, emptyChips, loadingBar;
  var badge;

  var isOpen = false;
  var isMinimized = false;
  var isProcessing = false;
  var lastUserMessage = '';

  function formatTimestamp(ts) {
    if (!ts) return getTimestamp();
    try {
      var d = new Date(ts);
      if (isNaN(d.getTime())) return getTimestamp();
      var h = d.getHours();
      var m = d.getMinutes();
      return (h < 10 ? '0' : '') + h + ':' + (m < 10 ? '0' : '') + m;
    } catch (e) {
      return getTimestamp();
    }

  }

  function getTimestamp() {
    var now = new Date();
    var h = now.getHours();
    var m = now.getMinutes();
    return (h < 10 ? '0' : '') + h + ':' + (m < 10 ? '0' : '') + m;
  }

  function escapeHtml(s) {
    var div = document.createElement('div');
    div.textContent = s;
    return div.innerHTML;
  }

  function getElements() {
    fab = document.getElementById('fwChatFab');
    panel = document.getElementById('fwChatPanel');
    backdrop = document.getElementById('fwChatBackdrop');
    messagesEl = document.getElementById('fwChatMessages');
    inputEl = document.getElementById('fwChatInput');
    sendBtn = document.getElementById('fwChatSend');
    charEl = document.getElementById('fwChatChar');
    closeBtn = document.getElementById('fwChatClose');
    minBtn = document.getElementById('fwChatMin');
    clearBtn = document.getElementById('fwChatClear');
    chipsContainer = document.getElementById('fwChatChips');
    emptyEl = document.getElementById('fwChatEmpty');
    emptyChips = document.getElementById('fwChatEmptyChips');
    loadingBar = document.getElementById('fwChatLoading');
    badge = document.getElementById('fwChatBadge');
  }

  function elementsReady() {
    return fab && panel && messagesEl && inputEl && sendBtn;
  }

  function setOpen(open) {
    isOpen = open;
    if (!open) isMinimized = false;
    fab.classList.toggle('open', open);
    fab.setAttribute('aria-expanded', open ? 'true' : 'false');
    panel.classList.toggle('open', open);
    panel.classList.toggle('minimized', isMinimized);
    panel.setAttribute('aria-hidden', open ? 'false' : 'true');
    if (backdrop) backdrop.classList.toggle('show', open);
    if (open) {
      setTimeout(function () {
        inputEl.focus();
        scrollToBottom();
      }, 180);
      if (badge) badge.style.display = 'none';
    } else {
      fab.focus();
    }

  }

  function t(key, fallback) {
    try {
      if (window.FinwiseLanguage && typeof window.FinwiseLanguage.t === 'function') {
        var v = window.FinwiseLanguage.t(key);
        if (v && v !== key) return v;
      }
    } catch (e) {}
    return fallback;
  }

  function toggleMinimized() {
    if (!panel || !isOpen) return;
    isMinimized = !isMinimized;
    panel.classList.toggle('minimized', isMinimized);
    if (minBtn) {
      minBtn.setAttribute('aria-label', isMinimized ? t('restore', 'Restore chat') : t('minimizeChat', 'Minimize chat'));
      minBtn.title = isMinimized ? t('restore', 'Restore') : t('minimize', 'Minimize');
    }
  }

  function scrollToBottom() {
    if (messagesEl) messagesEl.scrollTop = messagesEl.scrollHeight;
  }

  function hasMessages() {
    return messagesEl && messagesEl.querySelectorAll('.fw-chatbot-msg-row').length > 0;
  }

  function hideEmptyState() {
    if (emptyEl) emptyEl.style.display = 'none';
  }

  function showEmptyState() {
    if (emptyEl) emptyEl.style.display = '';
  }

  function getConfidenceColor(confidence) {
    if (typeof confidence !== 'number') return 'transparent';
    if (confidence > 0.7) return '#22c55e';
    if (confidence >= 0.4) return '#eab308';
    return '#ef4444';
  }

  function getConfidenceLabel(confidence) {
    if (typeof confidence !== 'number') return '';
    if (confidence > 0.7) return t('highConfidence', 'High confidence');
    if (confidence >= 0.4) return t('mediumConfidence', 'Medium confidence');
    return t('lowConfidence', 'Low confidence');
  }

  function createConfidenceIndicator(confidence) {
    if (typeof confidence !== 'number') return null;
    var dot = document.createElement('span');
    dot.className = 'fw-chatbot-confidence';
    dot.style.cssText = 'display:inline-block;width:8px;height:8px;border-radius:50%;margin-left:6px;vertical-align:middle;background:' + getConfidenceColor(confidence);
    dot.title = getConfidenceLabel(confidence) + ' (' + Math.round(confidence * 100) + '%)';
    return dot;
  }

  function addMessage(content, role, timestamp, confidence, suggestions) {
    hideEmptyState();
    var roleUpper = (role || '').toUpperCase();
    var isUser = roleUpper === 'USER';
    var isSystem = roleUpper === 'SYSTEM';
    var roleClass = isUser ? 'user' : isSystem ? 'system' : 'bot';

    var row = document.createElement('div');
    row.className = 'fw-chatbot-msg-row ' + roleClass;

    if (!isSystem) {
      var avatar = document.createElement('div');
      avatar.className = isUser ? 'fw-chatbot-avatar-user' : 'fw-chatbot-avatar-bot';
      avatar.textContent = isUser ? 'U' : 'AI';
      row.appendChild(avatar);
    }

    var bubbleWrap = document.createElement('div');
    bubbleWrap.className = 'fw-chatbot-msg-body';

    var bubble = document.createElement('div');
    bubble.className = 'fw-chatbot-msg-bubble';
    bubble.innerHTML = content;

    bubbleWrap.appendChild(bubble);

    var meta = document.createElement('div');
    meta.className = 'fw-chatbot-msg-meta';

    var time = document.createElement('span');
    time.className = 'fw-chatbot-msg-time';
    time.textContent = formatTimestamp(timestamp);
    meta.appendChild(time);

    if (!isUser && typeof confidence === 'number') {
      var confDot = createConfidenceIndicator(confidence);
      if (confDot) meta.appendChild(confDot);
    }

    if (!isUser && !isSystem) {
      var actions = document.createElement('span');
      actions.className = 'fw-chatbot-msg-actions';

      var copyBtn = document.createElement('button');
      copyBtn.className = 'fw-chatbot-msg-copy';
      copyBtn.title = 'Copy response';
      copyBtn.innerHTML = '<svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M9 9.5h8.5A1.5 1.5 0 0 1 19 11v7.5A1.5 1.5 0 0 1 17.5 20H9A1.5 1.5 0 0 1 7.5 18.5V11A1.5 1.5 0 0 1 9 9.5Z"/><path d="M14.5 4H6.5A1.5 1.5 0 0 0 5 5.5V15"/><path d="M10.5 9.5h5"/></svg>';
      copyBtn.setAttribute('aria-label', 'Copy response');
      copyBtn.addEventListener('click', function () {
        var text = bubble.textContent || bubble.innerText || '';
        navigator.clipboard.writeText(text).then(function () {
          copyBtn.innerHTML = '<svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.2 9.2 16.4 19 6.6"/></svg>';
          setTimeout(function () { copyBtn.innerHTML = '<svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M9 9.5h8.5A1.5 1.5 0 0 1 19 11v7.5A1.5 1.5 0 0 1 17.5 20H9A1.5 1.5 0 0 1 7.5 18.5V11A1.5 1.5 0 0 1 9 9.5Z"/><path d="M14.5 4H6.5A1.5 1.5 0 0 0 5 5.5V15"/><path d="M10.5 9.5h5"/></svg>'; }, 1200);
        }).catch(function () {});
      });
      actions.appendChild(copyBtn);

      var retryBtn = document.createElement('button');
      retryBtn.className = 'fw-chatbot-msg-retry';
      retryBtn.title = 'Regenerate';
      retryBtn.innerHTML = '<svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M3.5 12a8.5 8.5 0 0 1 14.5-6.1"/><path d="M18.5 6.5V3.5h3"/><path d="M20.5 12a8.5 8.5 0 0 1-14.5 6.1"/><path d="M5.5 17.5v3H2.5"/></svg>';
      retryBtn.setAttribute('aria-label', 'Regenerate response');
      retryBtn.addEventListener('click', function () {
        if (lastUserMessage && !isProcessing) {
          inputEl.value = lastUserMessage;
          sendMessage();
        }
      });
      actions.appendChild(retryBtn);

      meta.appendChild(actions);
    }

    bubbleWrap.appendChild(meta);
    row.appendChild(bubbleWrap);
    messagesEl.appendChild(row);

    if (!isUser && Array.isArray(suggestions) && suggestions.length > 0) {
      var chipsRow = document.createElement('div');
      chipsRow.className = 'fw-chatbot-msg-suggestions';
      suggestions.forEach(function (s) {
        if (typeof s !== 'string' || !s.trim()) return;
        var chip = document.createElement('button');
        chip.className = 'fw-chatbot-chip';
        chip.textContent = s;
        chip.addEventListener('click', function () {
          inputEl.value = s;
          sendMessage();
        });
        chipsRow.appendChild(chip);
      });
      if (chipsRow.childNodes.length > 0) {
        messagesEl.appendChild(chipsRow);
      }
    }

    scrollToBottom();
    return row;
  }

  function renderChart(data) {
    var container = document.getElementById('fwChatChartContainer');
    if (!container) return;
    container.style.display = 'block';
    var canvas = document.getElementById('fwChatCanvas');
    if (!canvas || typeof Chart === 'undefined') return;
    var ctx = canvas.getContext('2d');
    if (canvas._chartInstance) canvas._chartInstance.destroy();
    var labels = (data.labels || []).map(function(l){ return (l || '').toString(); });
    var values = (data.values || []).map(function(v){ return parseFloat(v) || 0; });
    // Theme-aware chart colors from the single global theme helper
    // (finwise-ui.js). Falls back to dark values when it is unavailable.
    var chC = (typeof window.fwChartThemeColors === 'function')
      ? window.fwChartThemeColors()
      : { tick: '#8ea0bb', grid: 'rgba(148,163,184,.14)', legend: '#CBD5E1',
          tooltipBg: '#151D2D', tooltipTitle: '#F8FAFC', tooltipBody: '#CBD5E1', tooltipBorder: '#263247' };
    canvas._chartInstance = new Chart(ctx, {
      type: 'bar',
      data: {
        labels: labels,
        datasets: [{
          label: data.title || 'Financial Data',
          data: values,
          backgroundColor: 'rgba(99,102,241,0.7)',
          borderColor: 'rgba(99,102,241,1)',
          borderWidth: 1,
          borderRadius: 4
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          title: { display: true, text: data.title || 'Data', color: chC.legend || chC.tick, font: { size: 13 } }
        },
        scales: {
          y: { beginAtZero: true, ticks: { color: chC.tick }, grid: { color: chC.grid } },
          x: { ticks: { color: chC.tick }, grid: { display: false } }
        }
      }
    });
  }

  function fetchChartData(query) {
    fetch(API_DATA, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body: JSON.stringify({ message: query })
    })
    .then(function(r){ return r.ok ? r.json() : null; })
    .then(function(data){
      if (data && data.chart) renderChart(data.chart);
    })
    .catch(function(){});
  }

  function addErrorMessage(text) {
    var row = addMessage(escapeHtml(text), 'BOT', getTimestamp(), undefined, undefined);
    var bubble = row.querySelector('.fw-chatbot-msg-bubble');
    if (bubble) bubble.classList.add('error');
  }

  function showTyping() {
    if (loadingBar) loadingBar.classList.add('active');
  }

  function hideTyping() {
    if (loadingBar) loadingBar.classList.remove('active');
  }

  function setLoading(on) {
    isProcessing = on;
    sendBtn.disabled = on;
    inputEl.disabled = on;
    if (on) {
      sendBtn.innerHTML = '<span class="fw-chatbot-send-spinner"></span>';
      sendBtn.setAttribute('aria-label', t('sendingMsg', 'Sending...'));
    } else {
      sendBtn.innerHTML = '&#x27A4;';
      sendBtn.setAttribute('aria-label', t('send', 'Send message'));
      inputEl.focus();
    }
  }

  function updateChar() {
    if (!charEl) return;
    var len = inputEl.value.length;
    charEl.textContent = len + '/' + MAX_LEN;
    charEl.classList.remove('warn', 'danger');
    if (len > MAX_LEN * 0.84 && len <= MAX_LEN * 0.96) {
      charEl.classList.add('warn');
    } else if (len > MAX_LEN * 0.96) {
      charEl.classList.add('danger');
    }

  }

  function translateWidget() {
    var language = window.FinwiseLanguage;
    if (!language) return;
    var tt = language.t;
    var title = document.getElementById('fwChatTitle');
    var welcome = document.querySelector('#fwChatEmpty h4');
    if (title && tt('chatTitle') !== 'chatTitle') title.textContent = tt('chatTitle');
    if (welcome && tt('chatWelcome') !== 'chatWelcome') welcome.textContent = tt('chatWelcome');
    if (inputEl && tt('chatPlaceholder') !== 'chatPlaceholder') inputEl.placeholder = tt('chatPlaceholder');
    if (clearBtn && tt('clearChat') !== 'clearChat') clearBtn.title = tt('clearChat');
    if (sendBtn && tt('send') !== 'send') sendBtn.setAttribute('aria-label', tt('send'));
    if (minBtn) {
      minBtn.title = isMinimized ? t('restore', 'Restore') : t('minimize', 'Minimize');
      minBtn.setAttribute('aria-label', isMinimized ? t('restore', 'Restore chat') : t('minimizeChat', 'Minimize chat'));
    }
  }

  function createSearchBar() {
    var searchWrapper = document.createElement('div');
    searchWrapper.className = 'fw-chatbot-search-wrapper';

    var searchInput = document.createElement('input');
    searchInput.type = 'text';
    searchInput.className = 'fw-chatbot-search-input';
    searchInput.placeholder = t('chatSearch', 'Search messages... (Ctrl+K)');
    searchInput.setAttribute('aria-label', t('chatSearch', 'Search messages'));
    searchInput.style.cssText = 'width:100%;padding:8px 12px;border:1px solid rgba(255,255,255,0.15);border-radius:8px;background:rgba(255,255,255,0.08);color:#e2e8f0;font-size:13px;outline:none;box-sizing:border-box;margin-bottom:8px;display:none;';

    searchWrapper.appendChild(searchInput);

    searchInput.addEventListener('input', function () {
      var query = searchInput.value.toLowerCase().trim();
      var rows = messagesEl.querySelectorAll('.fw-chatbot-msg-row, .fw-chatbot-msg-suggestions');
      rows.forEach(function (row) {
        if (!query) {
          row.style.display = '';
          return;
        }
        var text = (row.textContent || '').toLowerCase();
        row.style.display = text.indexOf(query) !== -1 ? '' : 'none';
      });
    });

    searchInput.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') {
        searchInput.value = '';
        searchInput.dispatchEvent(new Event('input'));
        searchInput.style.display = 'none';
        inputEl.focus();
      }
      e.stopPropagation();
    });

    return searchWrapper;
  }

  function loadHistory() {
    fetch(API_HISTORY, { headers: { 'Accept': 'application/json' } })
      .then(function (r) {
        if (!r.ok) throw new Error('history');
        return r.json();
      })
      .then(function (list) {
        if (!Array.isArray(list) || !list.length) return;
        hideEmptyState();
        list.forEach(function (m) {
          var role = (m.role || '').toUpperCase();
          addMessage(
            m.content || '',
            role === 'BOT' || role === 'ASSISTANT' ? 'BOT' : role,
            m.timestamp,
            m.confidence,
            m.suggestions
          );
        });
      })
      .catch(function () {});
  }

  function clearChat() {
    if (!confirm(t('chatClearConfirm', 'Clear your chat history? This will delete all messages.'))) return;
    fetch(API_HISTORY, { method: 'DELETE' })
      .then(function (r) {
        if (!r.ok) throw new Error('clear');
        messagesEl.querySelectorAll('.fw-chatbot-msg-row, .fw-chatbot-msg-suggestions').forEach(function (e) { e.remove(); });
        showEmptyState();
        addMessage(t('chatCleared', 'Chat cleared. How can I help with your finances?'), 'BOT', getTimestamp());
      })
      .catch(function () {
        addErrorMessage(t('chatClearFail', 'Could not clear history. Please try again.'));
      });
  }

  function sendMessage() {
    var text = inputEl.value.trim();
    if (!text || isProcessing) return;
    if (text.length > MAX_LEN) {
      addErrorMessage('Message too long (' + text.length + '/' + MAX_LEN + '). Please shorten it.');
      return;
    }

    lastUserMessage = text;
    addMessage(escapeHtml(text), 'USER', getTimestamp());
    inputEl.value = '';
    inputEl.style.height = 'auto';
    updateChar();
    setLoading(true);
    showTyping();

    fetch(API_ASK, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body: JSON.stringify({ message: text, language: window.FinwiseLanguage ? window.FinwiseLanguage.current : 'en' })
    })
      .then(function (res) {
        hideTyping();
        return res.text().then(function (body) {
          var contentType = (res.headers && res.headers.get && res.headers.get('content-type')) || '';
          if (!res.ok) {
            if (res.status === 401) throw new Error('unauthorized');
            if (res.status === 400) {
              try {
                var errorData = JSON.parse(body);
                throw new Error(errorData.message || 'Bad request');
              } catch (e) {
                throw new Error('Bad request');
              }
            }
            if (contentType.indexOf('application/json') === -1 && /<html|<!doctype/i.test(body)) {
              throw new Error('unauthorized');
            }
            throw new Error('server');
          }
          if (!body || !contentType || contentType.indexOf('application/json') === -1) {
            if (/<html|<!doctype/i.test(body || '')) {
              throw new Error('unauthorized');
            }
            throw new Error('server');
          }
          try {
            return JSON.parse(body);
          } catch (e) {
            throw new Error('invalid-json');
          }
        });
      })
      .then(function (data) {
        var reply = data && data.message ? data.message : 'I could not generate a response. Please try again.';
        addMessage(reply, 'BOT', data && data.timestamp ? data.timestamp : getTimestamp(), data && typeof data.confidence === 'number' ? data.confidence : undefined, data && Array.isArray(data.suggestions) ? data.suggestions : undefined);
        if (data && data.chart) renderChart(data.chart);
        else if (lastUserMessage) fetchChartData(lastUserMessage);
      })
      .catch(function (err) {
        hideTyping();
        if (err && err.message === 'unauthorized') {
          addErrorMessage('Please log in to use the chatbot. Your data is private per user.');
        } else if (err && err.message === 'invalid-json') {
          addErrorMessage('The server responded with an unexpected format. Please try again.');
        } else if (err && err.message && err.message !== 'server') {
          addErrorMessage(err.message);
        } else {
          addErrorMessage('Unable to reach the local Finwise server. Check that the app is running.');
        }
      })
      .finally(function () {
        setLoading(false);
      });
  }

  function bindChatLinkTriggers() {
    var triggers = document.querySelectorAll('a[href="/chatbot"], a[href="/chatbot/"], a[href$="/chatbot"], a[href$="/chatbot/"]');

    triggers.forEach(function (link) {
      if (link.dataset.fwChatBound === 'true') return;
      link.dataset.fwChatBound = 'true';
      link.addEventListener('click', function (event) {
        // Per spec, header AI Assistant must navigate to the standalone /chatbot page.
        // Only intercept if already on the chatbot page (avoid redundant navigation).
        var isChatbotPage = window.location.pathname === '/chatbot' || window.location.pathname === '/chatbot/';
        if (isChatbotPage) {
          event.preventDefault();
          if (!elementsReady()) {
            getElements();
          }
          if (!elementsReady()) return;
          setOpen(true);
        }
        // otherwise allow default navigation to /chatbot so the page opens correctly
      });
    });
  }

  function initWidget() {
    getElements();
    if (!elementsReady()) return;

    bindChatLinkTriggers();

    var searchWrapper = createSearchBar();
    var searchInput = searchWrapper.querySelector('.fw-chatbot-search-input');

    if (messagesEl && messagesEl.parentNode) {
      messagesEl.parentNode.insertBefore(searchWrapper, messagesEl);
    }

    fab.addEventListener('click', function () {
      setOpen(!isOpen);
    });

    if (closeBtn) {
      closeBtn.addEventListener('click', function () {
        setOpen(false);
      });
    }

    if (minBtn) {
      minBtn.addEventListener('click', toggleMinimized);
    }

    if (backdrop) {
      backdrop.addEventListener('click', function () {
        setOpen(false);
      });
    }

    if (clearBtn) {
      clearBtn.addEventListener('click', clearChat);
    }

    sendBtn.addEventListener('click', sendMessage);

    inputEl.addEventListener('input', function () {
      updateChar();
      inputEl.style.height = 'auto';
      inputEl.style.height = Math.min(inputEl.scrollHeight, 92) + 'px';
    });

    inputEl.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
      }
      e.stopPropagation();
    });

    if (chipsContainer) {
      chipsContainer.addEventListener('click', function (e) {
        var chip = e.target.closest('.fw-chatbot-chip');
        if (!chip) return;
        var q = chip.getAttribute('data-q') || chip.textContent.trim();
        if (q) {
          inputEl.value = q;
          sendMessage();
        }
      });
    }

    if (emptyChips) {
      emptyChips.addEventListener('click', function (e) {
        var chip = e.target.closest('.fw-chatbot-chip');
        if (!chip) return;
        var q = chip.getAttribute('data-q') || chip.textContent.trim();
        if (q) {
          inputEl.value = q;
          sendMessage();
        }
      });
    }

    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && isOpen) {
        if (searchInput && searchInput.style.display !== 'none') {
          searchInput.value = '';
          searchInput.dispatchEvent(new Event('input'));
          searchInput.style.display = 'none';
          inputEl.focus();
        } else {
          setOpen(false);
        }
        return;
      }

      if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault();
        if (isOpen) {
          searchInput.style.display = searchInput.style.display === 'none' ? '' : 'none';
          if (searchInput.style.display !== 'none') {
            searchInput.focus();
          } else {
            inputEl.focus();
          }
        }
        return;
      }

      if ((e.ctrlKey || e.metaKey) && e.key === 'l') {
        e.preventDefault();
        if (isOpen && !isProcessing) {
          clearChat();
        }
        return;
      }
    });

    updateChar();
    if (window.FinwiseLanguage && window.FinwiseLanguage.subscribe) {
      window.FinwiseLanguage.subscribe(translateWidget);
    }
    translateWidget();
    loadHistory();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initWidget);
  } else {
    initWidget();
  }

  window.FinwiseChat = {
    sendMessage: function () {
      if (!elementsReady()) {
        getElements();
        if (!elementsReady()) return;
      }
      sendMessage();
    },
    setOpen: function (open) {
      if (!elementsReady()) {
        getElements();
        if (!elementsReady()) return;
      }
      setOpen(open);
    }
  };
})();