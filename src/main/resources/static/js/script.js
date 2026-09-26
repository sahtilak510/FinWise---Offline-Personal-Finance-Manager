// Finwise — Offline-First Finance Manager JavaScript
// No external dependencies — everything runs locally

(function() {
    'use strict';

    // ============================================
    // OFFLINE DETECTION & STORAGE
    // ============================================
    const STORAGE_KEYS = {
        THEME: 'finwise_theme',
        LANGUAGE: 'finwise_locale',
        LANGUAGE_COMPAT: 'finwise-language',
        SIDEBAR_STATE: 'finwise_sidebar',
        SIDEBAR_COLLAPSED: 'finwise_sidebar_collapsed',
        LAST_SYNC: 'finwise_last_sync',
        OFFLINE_QUEUE: 'finwise_offline_queue'
    };

    let isOnline = navigator.onLine;
    const offlineQueue = [];

    /* Active-dictionary lookup with English fallback (never leaks keys). */
    function tr(key, fallback) {
        try {
            if (typeof activeDictionary !== 'undefined' && activeDictionary && activeDictionary[key]) {
                return activeDictionary[key];
            }
        } catch (e) {}
        return fallback;
    }

    function checkOnlineStatus() {
        const wasOffline = !isOnline;
        isOnline = navigator.onLine;
        
        if (wasOffline && isOnline) {
            processOfflineQueue();
            showToast('Connection restored — syncing data', 'success');
        } else if (!isOnline) {
            showToast('You are offline — changes saved locally', 'warning');
        }
    }

    function processOfflineQueue() {
        const queue = JSON.parse(localStorage.getItem(STORAGE_KEYS.OFFLINE_QUEUE) || '[]');
        if (queue.length === 0) return;
        
        queue.forEach(item => {
            fetch(item.url, item.options)
                .then(res => res.json())
                .catch(() => {});
        });
        
        localStorage.removeItem(STORAGE_KEYS.OFFLINE_QUEUE);
    }

    function queueOfflineRequest(url, options) {
        const queue = JSON.parse(localStorage.getItem(STORAGE_KEYS.OFFLINE_QUEUE) || '[]');
        queue.push({ url, options, timestamp: Date.now() });
        localStorage.setItem(STORAGE_KEYS.OFFLINE_QUEUE, JSON.stringify(queue));
    }

    // ============================================
    // TOAST NOTIFICATIONS
    // ============================================
    function getStatusIcon(type) {
        const icons = {
            success: '<svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.5 9.2 16.7 19 6.8"/></svg>',
            error: '<svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8"/><path d="M9.3 9.3 14.7 14.7"/><path d="M14.7 9.3 9.3 14.7"/></svg>',
            warning: '<svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 4.5 20 17.5a1.5 1.5 0 0 1-1.3 2.2H5.3A1.5 1.5 0 0 1 4 17.5L12 4.5Z"/><path d="M12 9v4.5"/><path d="M12 17h.01"/></svg>',
            info: '<svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8"/><path d="M12 10.5V16"/><path d="M12 7.5h.01"/></svg>'
        };
        return icons[type] || icons.info;
    }

    function getCloseIcon() {
        return '<svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12"/><path d="M18 6 6 18"/></svg>';
    }

    function showToast(message, type = 'info', duration = 4000) {
        const container = getOrCreateToastContainer();
        const toast = document.createElement('div');
        
        const styles = {
            success: { bg: 'var(--success-subtle)', border: 'var(--success-border)', color: 'var(--success)' },
            error: { bg: 'var(--danger-subtle)', border: 'var(--danger-border)', color: 'var(--danger)' },
            warning: { bg: 'var(--warning-subtle)', border: 'var(--warning-border)', color: 'var(--warning)' },
            info: { bg: 'var(--info-subtle)', border: 'var(--info-border)', color: 'var(--info)' }
        };
        
        const style = styles[type] || styles.info;
        
        toast.style.cssText = `
            background: ${style.bg};
            border: 1px solid ${style.border};
            border-radius: var(--radius-md);
            padding: var(--space-3) var(--space-4);
            margin-bottom: var(--space-2);
            display: flex; align-items: center; gap: var(--space-3);
            color: ${style.color};
            font-size: 0.85rem;
            box-shadow: var(--shadow-lg);
            animation: slideIn 0.3s var(--spring);
            min-width: 280px; max-width: 400px;
        `;
        
        toast.innerHTML = `
            <span style="font-weight: 700; display: inline-flex; align-items: center;">${getStatusIcon(type)}</span>
            <span style="flex: 1;">${message}</span>
            <button onclick="this.parentElement.remove()" style="background:none;border:none;color:inherit;cursor:pointer;padding:0;font-size:1.2rem;line-height:1;display:inline-flex;align-items:center;">${getCloseIcon()}</button>
        `;
        
        container.appendChild(toast);
        
        setTimeout(() => {
            toast.style.animation = 'slideOut 0.3s var(--ease) forwards';
            setTimeout(() => toast.remove(), 300);
        }, duration);
    }

    function getOrCreateToastContainer() {
        let container = document.getElementById('toast-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'toast-container';
            container.style.cssText = `
                position: fixed; bottom: var(--space-5); right: var(--space-5); z-index: 10000;
                pointer-events: none;
            `;
            document.body.appendChild(container);
        }
        return container;
    }

    // ============================================
    // FORM VALIDATION
    // ============================================
    function setupFormValidation() {
        const forms = document.querySelectorAll('form[data-validate]');
        
        forms.forEach(form => {
            const inputs = form.querySelectorAll('input[required], select[required], textarea[required]');
            
            inputs.forEach(input => {
                input.addEventListener('blur', () => validateField(input));
                input.addEventListener('input', () => {
                    if (input.classList.contains('error')) validateField(input);
                });
            });
            
            form.addEventListener('submit', (e) => {
                let valid = true;
                inputs.forEach(input => {
                    if (!validateField(input)) valid = false;
                });
                
                if (!valid) {
                    e.preventDefault();
                    showToast(tr('pleaseFixErrors', 'Please fix the errors before submitting'), 'error');
                } else {
                    showLoadingState(form);
                }
            });
        });
    }

    function validateField(input) {
        const value = input.value.trim();
        let isValid = true;
        let message = '';

        if (input.required && !value) {
            isValid = false;
            message = tr('thisFieldIsRequired', 'This field is required');
        } else if (input.type === 'number' && value) {
            const num = parseFloat(value);
            if (isNaN(num)) { isValid = false; message = tr('pleaseEnterValidNumber', 'Please enter a valid number'); }
            else if (input.hasAttribute('min') && num < parseFloat(input.min)) { isValid = false; message = `${tr('minimumValueIs', 'Minimum value is')} ${input.min}`; }
        } else if (input.type === 'email' && value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
            isValid = false; message = tr('pleaseEnterValidEmail', 'Please enter a valid email');
        }
        
        input.classList.toggle('error', !isValid);
        input.classList.toggle('valid', isValid && value);
        
        let errorEl = input.parentElement.querySelector('.field-error');
        if (!isValid) {
            if (!errorEl) {
                errorEl = document.createElement('div');
                errorEl.className = 'field-error';
                errorEl.style.cssText = 'color: var(--danger); font-size: 0.75rem; margin-top: var(--space-1);';
                input.parentElement.appendChild(errorEl);
            }
            errorEl.textContent = message;
        } else if (errorEl) {
            errorEl.remove();
        }
        
        return isValid;
    }

    function showLoadingState(form) {
        const submitBtn = form.querySelector('button[type="submit"]');
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.dataset.originalText = submitBtn.innerHTML;
            submitBtn.innerHTML = '<span class="spinner"></span> Processing...';
            submitBtn.style.pointerEvents = 'none';
        }
    }

    function hideLoadingState(form) {
        const submitBtn = form.querySelector('button[type="submit"]');
        if (submitBtn && submitBtn.dataset.originalText) {
            submitBtn.disabled = false;
            submitBtn.innerHTML = submitBtn.dataset.originalText;
            submitBtn.style.pointerEvents = 'auto';
        }
    }

    // ============================================
    // SIDEBAR TOGGLE
    // ============================================
    window.toggleSidebar = function() {
        const sidebar = document.getElementById('sidebar');
        const backdrop = document.querySelector('.fw-sidebar-backdrop');
        
        if (sidebar) {
            if (window.innerWidth > 1024) {
                const isCollapsed = document.body.classList.toggle('fw-sidebar-collapsed');
                localStorage.setItem(STORAGE_KEYS.SIDEBAR_COLLAPSED, isCollapsed ? 'true' : 'false');
                const collapseButton = sidebar.querySelector('.fw-sidebar-collapse');
                if (collapseButton) {
                    collapseButton.setAttribute('aria-expanded', String(!isCollapsed));
                    collapseButton.setAttribute('aria-label', isCollapsed ? 'Expand sidebar' : 'Collapse sidebar');
                }
                return;
            }

            const isOpen = sidebar.classList.toggle('open');
            localStorage.setItem(STORAGE_KEYS.SIDEBAR_STATE, isOpen ? 'open' : 'closed');
            
            if (backdrop) {
                backdrop.style.display = isOpen ? 'block' : 'none';
            }
        }
    };

    function initSidebar() {
        const sidebar = document.getElementById('sidebar');
        const backdrop = document.querySelector('.fw-sidebar-backdrop');
        if (!sidebar) return;

        // The sidebar is an overlay and always starts closed; it never reserves
        // space in the page layout.
        document.body.classList.remove('fw-sidebar-collapsed');
        sidebar.classList.remove('open', 'fw-sidebar--open');
        if (backdrop) backdrop.style.display = 'none';

        let closeTimer;
        const openSidebar = () => {
            clearTimeout(closeTimer);
            sidebar.classList.add('open', 'fw-sidebar--open');
            if (backdrop && window.innerWidth <= 1024) backdrop.style.display = 'block';
        };
        const scheduleClose = () => {
            clearTimeout(closeTimer);
            closeTimer = setTimeout(() => {
                sidebar.classList.remove('open', 'fw-sidebar--open');
                if (backdrop) backdrop.style.display = 'none';
            }, 400);
        };

        sidebar.addEventListener('mouseenter', () => clearTimeout(closeTimer));
        sidebar.addEventListener('mouseleave', scheduleClose);

        if (!document.getElementById('fw-sidebar-edge-trigger')) {
            const edgeTrigger = document.createElement('div');
            edgeTrigger.id = 'fw-sidebar-edge-trigger';
            edgeTrigger.setAttribute('aria-hidden', 'true');
            document.body.appendChild(edgeTrigger);
            edgeTrigger.addEventListener('mouseenter', openSidebar);
            edgeTrigger.addEventListener('mouseleave', scheduleClose);
            edgeTrigger.addEventListener('touchstart', openSidebar, { passive: true });
        }

        if (backdrop) {
            backdrop.addEventListener('click', scheduleClose);
        }
    }

    // ============================================
    // DATE INPUT DEFAULTS
    // ============================================
    function setDefaultDates() {
        const today = new Date().toISOString().split('T')[0];
        
        document.querySelectorAll('input[type="date"]').forEach(input => {
            if (!input.value && !input.hasAttribute('data-no-default')) {
                if (input.id === 'expenseDate' || input.id === 'incomeDate' || input.id === 'targetDate' || input.id === 'fDate') {
                    input.value = today;
                }
            }
        });
        
        document.querySelectorAll('input[type="month"]').forEach(input => {
            if (!input.value) {
                input.value = new Date().toISOString().slice(0, 7);
            }
        });
    }

    // ============================================
    // AUTO-SAVE FORM DRAFTS
    // ============================================
    function setupAutoSave() {
        const forms = document.querySelectorAll('form[data-autosave]');
        
        forms.forEach(form => {
            const formId = form.id || form.action;
            const savedData = JSON.parse(localStorage.getItem(`draft_${formId}`) || '{}');
            
            Object.keys(savedData).forEach(name => {
                const input = form.querySelector(`[name="${name}"]`);
                if (input && !input.value) {
                    input.value = savedData[name];
                    input.dispatchEvent(new Event('input'));
                }
            });
            
            form.addEventListener('input', debounce(() => {
                const data = {};
                new FormData(form).forEach((value, key) => { data[key] = value; });
                localStorage.setItem(`draft_${formId}`, JSON.stringify(data));
            }, 1000));
            
            form.addEventListener('submit', () => {
                localStorage.removeItem(`draft_${formId}`);
            });
        });
    }

    function debounce(fn, wait) {
        let timeout;
        return (...args) => {
            clearTimeout(timeout);
            timeout = setTimeout(() => fn(...args), wait);
        };
    }

    // ============================================
    // NUMBER FORMATTING
    // ============================================
    window.formatCurrency = function(value) {
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(value);
    };

    window.formatNumber = function(value, decimals = 2) {
        return new Intl.NumberFormat('en-US', {
            minimumFractionDigits: decimals,
            maximumFractionDigits: decimals
        }).format(value);
    };

    // ============================================
    // CONFIRM DIALOGS
    // ============================================
    window.confirmDelete = function(itemName) {
        return confirm(`Are you sure you want to delete this ${itemName}? This action cannot be undone.`);
    };

    window.confirmAction = function(message) {
        return confirm(message);
    };

    // ============================================
    // KEYBOARD SHORTCUTS
    // ============================================
    function setupKeyboardShortcuts() {
        document.addEventListener('keydown', (e) => {
            // Ctrl/Cmd + K for search (future)
            if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
                e.preventDefault();
                const searchInput = document.querySelector('input[type="search"], input[name="search"]');
                if (searchInput) searchInput.focus();
            }
            
            // Escape to close modals/sidebar
            if (e.key === 'Escape') {
                const sidebar = document.getElementById('sidebar');
                if (sidebar && sidebar.classList.contains('open')) {
                    toggleSidebar();
                }
            }
        });
    }

    // ============================================
    // LOADING STATES FOR LINKS
    // ============================================
    function setupLinkLoading() {
        // Never hijack quick-action cards or in-page anchors: they must navigate
        // even if JS is slow. Only dim same-origin links, never replace content
        // (replacing innerHTML permanently breaks the link on Back/forward cache).
        document.querySelectorAll('a:not([href^="#"]):not([href^="javascript"]):not([target="_blank"])').forEach(link => {
            if (link.dataset.fwLoadingBound === 'true') return;
            link.dataset.fwLoadingBound = 'true';
            link.addEventListener('click', (e) => {
                if (e.defaultPrevented || e.button !== 0 || e.ctrlKey || e.metaKey || e.shiftKey || e.altKey) return;
                if (link.classList.contains('fw-action-card')) return;
                if (link.closest('.fw-qa')) return;
                if (link.href && link.href.startsWith(window.location.origin)) {
                    link.style.opacity = '0.7';
                }
            });
        });
        // Restore dimmed links when returning via Back/forward cache.
        window.addEventListener('pageshow', () => {
            document.querySelectorAll('a[data-fw-loading-bound="true"]').forEach(link => {
                link.style.opacity = '';
                link.style.pointerEvents = '';
            });
        });
    }

    function getEffectiveTheme() {
        // Unified with finwise-ui.js: Light or Dark only (no Auto).
        if (typeof window.fwGetTheme === 'function') return window.fwGetTheme().effective;
        const saved = localStorage.getItem('finwise_theme');
        if (saved === 'light' || saved === 'dark') return saved;
        const current = document.documentElement.getAttribute('data-theme');
        if (current === 'light' || current === 'dark') return current;
        return 'light';
    }

    /* ONE global language system — exactly 4 supported languages.
       English (default) + Hindi + Tamil + Nepali. No other options. */
    const SUPPORTED_LOCALES = {
        en: { label: 'English', rtl: false },
        hi: { label: 'हिन्दी', rtl: false },
        ta: { label: 'தமிழ்', rtl: false },
        ne: { label: 'नेपाली', rtl: false }
    };

    function normalizeLocale(code) {
        if (typeof code !== 'string') return null;
        var c = code.trim().toLowerCase();
        return SUPPORTED_LOCALES[c] ? c : null;
    }

    function readServerLocale() {
        if (typeof window.__FW_SERVER_LANG === 'string') {
            var s = normalizeLocale(window.__FW_SERVER_LANG);
            if (s) return s;
        }
        return null;
    }

    function getCurrentLocale() {
        // Source of truth: saved local preference. Default is ALWAYS English.
        var saved = normalizeLocale(localStorage.getItem(STORAGE_KEYS.LANGUAGE))
            || normalizeLocale(localStorage.getItem(STORAGE_KEYS.LANGUAGE_COMPAT));
        if (saved) return saved;
        return readServerLocale() || 'en';
    }

    let activeDictionary = {};
    const localeDictionaries = {};
    const languageListeners = [];

     function getLocaleDictionary(localeCode) {
         const fallback = {
             appName: 'Finwise',
              dashboard: 'Dashboard',
              income: 'Income',
             expenses: 'Expenses',
             budgets: 'Budgets',
              goals: 'Goals',
              analytics: 'Analytics',
              account: 'Account',
             profile: 'Profile',
             settings: 'Settings',
             language: 'Language',
             logout: 'Logout',
             chat: 'AI Assistant',
             darkMode: 'Dark Mode',
             lightMode: 'Light Mode',
             theme: 'Theme',
             searchLanguage: 'Search language',
             totalIncome: 'Total Income',
             totalExpenses: 'Total Expenses',
             netBalance: 'Net Balance',
             welcome: 'Welcome back!',
             financialSnapshot: "Here's your financial snapshot",
             trackedThisMonth: 'Tracked this month',
             noIncomeYet: 'No income yet',
             spendingThisMonth: 'Spending tracked this month',
             noExpensesYet: 'No expenses yet',
             profileInformation: 'Profile Information',
             accountSettings: 'Account Settings',
             notifications: 'Notifications',
             appearance: 'Appearance',
             configure: 'Configure',
             customize: 'Customize',
             uploadPhoto: 'Upload photo',
             removePhoto: 'Remove photo',
             savePhoto: 'Save photo',
             languageChanged: 'Language changed to',
             themeChanged: 'Theme changed to',
             close: 'Close',
             save: 'Save',
             cancel: 'Cancel',
             edit: 'Edit',
             delete: 'Delete',
             incomeVsExpenses: 'Income vs Expenses',
             monthlyComparison: 'Monthly comparison',
             viewAll: 'View All →',
             budgetProgress: 'Budget Progress',
             trackSpendingLimits: 'Track spending limits',
             manage: 'Manage →',
             noBudgetsYet: 'No budgets yet',
             financialGoals: 'Financial Goals',
             trackSavingsTargets: 'Track your savings targets',
              noGoalsYet: 'No goals yet',
              createGoal: 'Create a goal to start tracking',
              saved: 'Saved:',
             of: 'of',
             chatTitle: 'Offline Finance AI',
             chatWelcome: "Hi, I'm your Finwise assistant",
             chatPlaceholder: 'Ask about your finances…',
             clearChat: 'Clear chat',
             send: 'Send message',
             addIncome: 'Add Income',
             addExpense: 'Add Expense',
             addBudget: 'Add Budget',
             addGoal: 'Add Goal',
             amount: 'Amount',
             category: 'Category',
             date: 'Date',
             description: 'Description',
             budgetAmount: 'Budget Amount',
             spent: 'Spent',
             remaining: 'Remaining',
             progress: 'Progress',
             goalName: 'Goal Name',
             targetAmount: 'Target Amount',
             currentAmount: 'Current Amount',
             deadline: 'Deadline',
             saveChanges: 'Save Changes',
             areYouSureDelete: 'Are you sure you want to delete this',
             pleaseFixErrors: 'Please fix the errors before submitting',
             thisFieldIsRequired: 'This field is required',
             pleaseEnterValidNumber: 'Please enter a valid number',
             pleaseEnterValidEmail: 'Please enter a valid email',
             minimumValueIs: 'Minimum value is',
             profileImage: 'Profile image',
             changePassword: 'Change Password',
             deleteAccount: 'Delete Account',
             typeDeleteToConfirm: 'Type DELETE to confirm',
             accountDeleted: 'Account deleted successfully',
             errorOccurred: 'Error',
             exportData: 'Export your data',
             dataExported: 'Data exported successfully',
             notificationSettings: 'Notification Settings',
             budgetAlerts: 'Budget Alerts',
             dailySummary: 'Daily Summary',
             goalUpdates: 'Goal Updates',
             savePreferences: 'Save Preferences',
             passwordChanged: 'Password changed successfully',
             newPasswordMinLength: 'New password must be at least 6 characters',
             passwordsDoNotMatch: 'Passwords do not match',
             aiAssistant: 'AI Assistant',
             analyzeExpenses: 'Analyze Expenses',
              showBalance: 'Show Balance',
              budgetSummary: 'Budget Summary',
              thisMonthSpend: 'This Month Spend',
             monthlyIncome: 'Monthly Income',
             analyzingFinances: 'Analyzing your finances',
             askAboutFinances: 'Ask about your finances',
             noDataAvailable: 'No data available',
             loading: 'Loading…',
             errorLoading: 'Error loading data',
             tryAgain: 'Try again',
             success: 'Success',
             warning: 'Warning',
             info: 'Information',
             export: 'Export',
             change: 'Change',
             back: 'Back',
             saveToDraft: 'Save to draft',
             processing: 'Processing…',
             noResults: 'No results found',
              search: 'Search',
              sortBy: 'Sort by',
              filter: 'Filter',
              all: 'All',
              analyticsSubtitle: 'Financial insights and spending trends',
              balance: 'Balance',
              previousMonth: 'Previous Month',
              categorySpending: 'Category Spending',
              spendingTrends: 'Spending Trends',
              budgetUsage: 'Budget Usage',
              highestSpending: 'Highest Spending',
              highestMerchant: 'Highest Merchant',
              noDataAvailable: 'No financial data available yet.',
              noDataHint: 'Add income or expenses to see your analytics here.',
              thisMonth: 'This Month',
              lastMonth: 'Last Month',
              last3Months: 'Last 3 Months',
              last6Months: 'Last 6 Months',
              thisYear: 'This Year',
              customRange: 'Custom Range',
              apply: 'Apply',
              dateRange: 'Date Range'
              , managePreferences: 'Manage your preferences and app configuration'
              , dark: 'Dark'
              , light: 'Light'
              , auto: 'Auto'
              , regional: 'Regional'
              , currency: 'Currency'
              , dateFormat: 'Date Format'
              , enableNotifications: 'Enable Notifications'
              , dataManagement: 'Data Management'
              , createBackup: 'Create Backup'
              , resetDefaults: 'Reset to Defaults'
              , saveSettings: 'Save Settings'
              , fullName: 'Full Name'
              , username: 'Username'
              , email: 'Email'
              , budgetAlertsDescription: 'Budget alerts and spending updates'
              , appearanceDescription: 'Theme and display preferences'
              , downloadFinancialData: 'Download your financial data'
              , passwordSecurityDescription: 'Update your password for security'
              , permanentDeleteDescription: 'Permanently delete your account and all data'
              , fullNamePh: 'Your full name'
              , min6Ph: 'Min. 6 characters'
              , repeatPh: 'Repeat password'
              , createAccountTitle: 'Create your account'
              , joinFinwise: 'Join FinWise'
              , signInTitle: 'Sign in'
              , getStarted: 'Get started'
              , learnMore: 'Learn more'
              , chatSubtitle: 'Offline · Local SQL · No internet'
              , chatEmptyNote: '100% offline. I read only your local database — no internet, no cloud, no LLMs.'
              , chatWelcomeSys: 'Welcome! Ask about your finances — all answers from your local DB.'
              , askFinances: 'Ask about your finances'
              , typeQuestion: 'Type your question'
              , messageLabel: 'Message'
              , openAssistant: 'Open Finwise Assistant'
              , closeChat: 'Close chat'
              , clearChatTitle: 'Clear chat'
              , quickActions: 'Quick actions'
              , fabTitle: 'Finwise Assistant — offline'
              , minimizeChat: 'Minimize chat'
              , clearHistoryTitle: 'Clear history'
              , localDbStatus: 'Local database · No internet'
              , safeQueries: 'All queries are safe predefined selects scoped to your user — never arbitrary SQL. Works with Wi-Fi disabled.'
              , savingsTargets: 'Savings targets'
              , latestActivity: 'Latest activity'
              , startTracking: 'Start tracking your finances by adding your first transaction.'
              , computeSavings: 'Add income to compute savings rate'
              , minimize: 'Minimize'
              , restore: 'Restore'
              , clearHistory: 'Clear history'
              , typeToSearch: 'Type to search…'
              , accentColor: 'Accent color'
              , accentDescription: 'FinWise indigo is the default. More accents are on the way.'
              , animationsDescription: 'Subtle transitions across the app. Honors your OS reduced-motion setting.'
              , managedRegional: 'Managed in Regional & Localization above.'
              , incomeExpenseCategories: 'Income & expense categories'
              , budgetCycleDescription: 'Monthly, weekly or custom budget periods.'
              , defaultAccountType: 'Default account type'
              , defaultAccountDescription: 'Preselect an account type when adding accounts.'
              , defaultTxDate: 'Default transaction date'
              , defaultTxDateDescription: 'New transactions default to today.'
              , todayBadge: 'Today'
              , decimalsDescription: 'Choose 0 or 2 decimals for amounts.'
              , negativeDescription: 'How negative amounts look.'
              , dashDescription: 'Choose what appears on your dashboard.'
              , dashboardCards: 'Dashboard cards'
              , defaultPeriod: 'Default dashboard period'
              , defaultPeriodDescription: 'This week, this month, last 3 months or this year.'
              , chartPrefs: 'Chart preferences'
              , chartDescription: 'Show charts, percentages and comparison indicators.'
              , textSizeDescription: 'Default, Large or Extra large interface text.'
              , contrastDescription: 'Extra contrast for text and controls.'
              , reduceDescription: 'Minimize motion and transitions everywhere.'
              , clearCachedData: 'Clear cached UI data'
              , storageTitle: 'Storage'
              , resetPreferencesBtn: 'Reset Preferences'
              , deleteAllDataBtn: 'Delete All Data'
              , changePasswordBtn: 'Change password'
              , openAppearanceBtn: 'Open Appearance'
              , exportDataBtn: 'Export Data'
              , createBackupDescription: 'Save a timestamped copy of your data. Files are stored in /data/backups/.'
              , passcodeDescription: 'Require a passcode to open FinWise.'
              , biometricDescription: 'Unlock with fingerprint or face recognition.'
              , autoLockDescription: 'Sign out automatically after a period of inactivity.'
              , regionalDescription: 'Language, currency, dates and week start.'
              , dateFormatDescription: 'How dates are displayed.'
              , notificationsDescription: 'Everything stays on this device. Each preference is independent.'
              , enableNotificationsDescription: 'Master switch for in-app notifications.'
              , budgetAlertsDescription2: 'Warn when spending approaches a budget limit.'
              , dailySummaryDescription: 'A short recap of each day\'s activity.'
              , goalMilestonesDescription: 'Celebrate progress on your financial goals.'
              , billRemindersDescription: 'Get reminded before recurring payments are due.'
              , largeExpenseDescription: 'Flag unusually large transactions automatically.'
              , weeklyMonthlySummary: 'Weekly & monthly summaries'
              , weeklyMonthlyDescription: 'Longer-range spending digests.'
              , saveSettingsDescription: 'Theme and language preview instantly; saving persists them to your account.'
              , backupDescription: 'Everything is stored on this device.'
              , storageStatus: 'Storage status'
              , storageStatusDescription: 'Local SQLite database on this device.'
              , localStorageBadge: 'Local storage'
              , exportDataDescription: 'Download your financial data (CSV/Excel).'
              , importDataDescription: 'Bring transactions in from PDF, Excel or CSV files.'
              , backupNote: 'Backup history is not tracked in the app — backup files live in /data/backups/.'
              , privacyDescription: 'Your data never leaves this device.'
              , localOnlyDescription: 'Your financial data is stored locally on this device. No cloud storage is used.'
              , enabledBadge: 'Enabled'
              , changePasswordDescription: 'Update the password for this local account.'
              , financeDescription: 'Defaults that shape how FinWise handles your money.'
              , openRegional: 'Open Regional'
              , manageCategories: 'Manage Categories'
              , categoriesDescription: 'Create and organize your own categories.'
              , dashboardCardsDescription: 'Balance, income, expenses, budgets, goals, recent transactions, trends and insights.'
              , aiDescription: 'Financial insights are generated from your locally stored data. No external AI APIs are used.'
              , accessibilityDescription: 'FinWise is fully keyboard navigable with visible focus states.'
              , openAppearance: 'Open Appearance'
              , advancedDescription: 'Technical details and maintenance.'
              , connectedBadge: 'Connected'
              , storageDescription: 'Local SQLite database on this device. Preferences are stored per account.'
              , clearCacheDescription: 'Removes offline queues and drafts kept in this browser. Your accounts and settings stay.'
              , dangerDescription: 'Destructive actions are always confirmed first.'
              , resetDescription: 'Restores default preferences only. Your financial data is never touched.'
              , deleteDataDescription: 'Permanently remove your account and everything stored on this device.'
              , aboutDescription: 'Private. Local. In control.'
              , dataHandlingDescription: 'Offline-first. Accounts, transactions and preferences live in a local database.'
              , privacySecurityDescription: 'No tracking, no external cloud storage, no analytics calls.'
              , themeDescription: 'Light or Dark — applies instantly across the whole app.'
              , languageDescription: 'One language for the entire app — navigation, pages, forms and assistant.'
              , weekStartPreview: 'Week preview'
              , prefSummary: 'Live summary of your current preferences.'
              , totalBalance: 'Total Balance'
              , netSavings: 'Net Savings'
               , financialHealth: 'Financial Health'
               , incomeMinusExpenses: 'Income minus expenses · this month'
               , createBudget: 'Create Budget'
              , logSpending: 'Log spending'
              , logEarnings: 'Log earnings'
              , setLimit: 'Set a limit'
              , saveForMore: 'Save for more'
              , scanReceipt: 'Scan Receipt'
              , autoFill: 'Auto-fill'
              , whereMoneyGoes: 'Where your money goes'
              , limitsVsSpending: 'Limits vs spending'
              , noSpendingYet: 'No spending yet'
              , addFirstExpenseDesc: 'Add your first expense to see the category breakdown.'
              , monthlyComparisonThisMonth: 'Monthly comparison · this month'
              , basedOnReal: 'Based on your real savings, budgets & goals'
              , yourMoneyGlance: 'Your money at a glance'
              , assistantTitle: 'FinWise Assistant'
              , assistantDesc: 'Your personal financial companion — 100% offline, answers from your local records only.'
              , suggestedPrompts: 'Suggested prompts — tap to ask'
              , qSpendMonth: 'How much did I spend this month?'
              , qMostSpending: 'Where am I spending the most?'
              , qRecentExpenses: 'Show my recent expenses'
              , qHowSave: 'How much can I save?'
              , qMonthlyIncome: 'What is my monthly income?'
              , offlineAiWelcome: 'Hi, I\'m your Offline Finance AI'
              , tryOneOf: 'Try one of these — all answers come strictly from your authenticated local records:'
              , askNatural: 'Ask in natural language — 100% offline, powered only by your local SQL database. Zero external APIs.'
              , yourMoney: 'Your money.'
              , yourPrivacy: 'Your privacy.'
              , yourControl: 'Your control.'
              , privateWorkspace: 'Private finance workspace'
              , takeControl: 'Take control of your finances with a private, offline-first financial workspace.'
              , offlineFirst: 'OFFLINE-FIRST'
              , dataStaysDevice: 'Your financial data stays on your device.'
              , privateByDesign: 'PRIVATE BY DESIGN'
              , noTracking: 'No tracking. No external cloud storage.'
              , smartFinance: 'SMART FINANCE'
              , budgetsGoalsInsights: 'Budgets, goals, insights and expense tracking.'
              , privateOffline: 'PRIVATE & OFFLINE'
              , dataRemains: 'Your financial data remains on this device.'
              , footCopy: '© 2026 FinWise • Data never leaves this device'
              , designedOffline: 'FinWise is designed offline-first with privacy in mind.'
              , credsRequired: 'Please enter your username and password.'
              , addExpenseTitle: 'Add Expense'
              , addIncomeTitle: 'Add Income'
              , overview: 'Overview'
              , money: 'Money'
              , planning: 'Planning'
              , insights: 'Insights'
              , tools: 'Tools'
              , system: 'System'
              , accounts: 'Accounts'
              , importAction: 'Import'
              , reports: 'Reports'
              , forecasts: 'Forecasts'
              , habits: 'Habits'
              , scanFill: 'Scan & Fill'
              , newBadge: 'New'
              , assistant: 'Assistant'
              , collapseSidebar: 'Collapse sidebar'
              , openMenu: 'Open menu'
              , switchToDark: 'Switch to dark mode'
              , switchToLight: 'Switch to light mode'
              , searchPage: 'Search this page…'
              , footerOffline: '© 2026 FinWise • 100% offline — your data stays on this device'
              , footerLocal: 'Local SQLite • No tracking'
              , somethingWentWrong: 'Something went wrong'
              , noSettingsMatch: 'No settings match your search.'
              , password: 'Password'
              , signIn: 'Sign In'
              , createAccount: 'Create Account'
              , welcomeBack: 'Welcome back'
              , signInContinue: 'Sign in to continue to your FinWise account.'
              , noAccount: 'Don\'t have an account?'
              , haveAccount: 'Already have an account?'
              , invalidCredentials: 'Invalid username or password. Please try again.'
              , loggedOut: 'You have been logged out successfully.'
              , enterUsername: 'Enter your username'
              , enterPassword: 'Enter your password'
              , signUpContinue: 'Create your FinWise account to get started.'
              , chooseUsername: 'Choose a username'
              , choosePassword: 'Choose a password (min 6 characters)'
              , goodMorning: 'Good morning'
              , goodAfternoon: 'Good afternoon'
              , goodEvening: 'Good evening'
              , controlCenter: 'FinWise Control Center'
              , controlSubtitle: 'Manage your FinWise experience, privacy, finance preferences and data.'
              , searchSettings: 'Search settings...'
              , languageRegion: 'Language & Region'
              , privacySecurity: 'Privacy & Security'
              , dataBackup: 'Data & Backup'
              , financePrefs: 'Finance Preferences'
              , dashboardPrefs: 'Dashboard Preferences'
              , aiInsights: 'AI & Insights'
              , accessibility: 'Accessibility'
              , advanced: 'Advanced'
              , aboutFinwise: 'About FinWise'
              , themeLight: 'Light'
              , themeDark: 'Dark'
              , changesSaved: 'Changes saved'
              , unsavedChanges: 'Unsaved changes'
              , comingSoon: 'Coming soon'
              , builtIn: 'Built-in'
              , resetPreferences: 'Reset preferences'
              , deleteAllData: 'Delete all local data'
              , confirmTitle: 'Are you sure?'
              , confirm: 'Confirm'
              , firstDayWeek: 'First day of week'
              , sunday: 'Sunday'
              , monday: 'Monday'
              , saturday: 'Saturday'
              , summaryTitle: 'Summary'
              , thisWeek: 'This week'
              , textSize: 'Text size'
              , textDefault: 'Default'
              , textLarge: 'Large'
              , textExtraLarge: 'Extra large'
              , highContrast: 'High contrast'
              , reduceAnimations: 'Reduce animations'
              , animations: 'Animations'
              , clearCache: 'Clear Cache'
              , storageUsed: 'Storage used'
              , appVersion: 'Application version'
              , dangerZone: 'Danger Zone'
              , selectLanguage: 'Select language'
              , localOnlyData: 'Local-only data'
              , passcodeLock: 'Passcode lock'
              , biometricUnlock: 'Biometric unlock'
              , autoLock: 'Auto-lock'
              , never: 'Never'
              , min5: '5 minutes'
              , min15: '15 minutes'
              , min30: '30 minutes'
              , hour1: '1 hour'
              , restoreBackup: 'Restore Backup'
              , importData: 'Import Data'
              , noBackup: 'No backup created yet.'
              , defaultCurrency: 'Default currency'
              , budgetCycle: 'Budget cycle'
              , monthly: 'Monthly'
              , weekly: 'Weekly'
              , yearly: 'Yearly'
              , decimalPlaces: 'Decimal places'
              , negativeDisplay: 'Negative amount display'
              , upcomingBills: 'Upcoming bills'
              , smartCategorization: 'Smart categorization'
              , spendingInsights: 'Spending insights'
              , budgetRecommendations: 'Budget recommendations'
              , goalInsights: 'Goal insights'
              , forecasting: 'Forecasting'
              , duplicateDetection: 'Duplicate transaction detection'
              , ocrImports: 'OCR-assisted imports'
              , version: 'Version'
              , dataHandling: 'Data handling'
              , privacy: 'Privacy'
              , security: 'Security'
              , activeGoals: 'Active Goals'
              , goalsTracking: 'goals tracking'
              , healthy: 'Healthy'
              , deficit: 'Deficit'
              , neutral: 'Neutral'
              , newBudget: 'New Budget'
              , newGoal: 'New Goal'
              , myAccount: 'My Account'
              , smartCategories: 'Smart Categories'
              , trackEveryCategory: 'Track every category in one place'
              , noDataYet: 'No data yet'
              , setSpendingLimits: 'Set spending limits to stay on track'
              , addFirstExpense: 'Add your first expense to get started'
              , financialInsights: 'Financial Insights'
              , trackExpensesInsights: 'Track your expenses to get insights'
              , goalsCreated: 'Goals Created'
              , setMeaningfulTargets: 'Set meaningful financial targets'
              , chatSearch: 'Search messages...'
              , chatCleared: 'Chat cleared. How can I help with your finances?'
              , chatClearConfirm: 'Clear your chat history? This will delete all messages.'
              , chatClearFail: 'Could not clear history. Please try again.'
              , highConfidence: 'High confidence'
              , mediumConfidence: 'Medium confidence'
              , lowConfidence: 'Low confidence'
              , sendingMsg: 'Sending...'
              , offlineBadge: 'Offline'
              , localDbOnly: 'Local DB only'
              , billReminders: 'Bill reminders'
              , goalMilestones: 'Goal milestones'
              , largeExpenseAlerts: 'Large expense alerts'
              , weeklySummary: 'Weekly summary'
              , monthlySummary: 'Monthly summary'
          };
         const english = fallback;
         // English is ALWAYS the fallback: any missing key keeps its English
         // text so the UI never shows raw keys, undefined, or "missing".
         var code = normalizeLocale(localeCode) || 'en';
         const requested = code !== 'en'
             ? (localeDictionaries[code] || fetchedLocale(code))
             : {};
         const merged = { ...english, ...requested };
         return merged;
     }

    function fetchedLocale(code) {
        try {
            const data = JSON.parse(localStorage.getItem('finwise_locale_cache_' + code) || 'null');
            return data || {};
        } catch (err) {
            return {};
        }
    }

      const staticPageTitles = {
          dashboard: 'Dashboard',
          income: 'Income',
          expenses: 'Expenses',
          budgets: 'Budgets',
          goals: 'Goals',
          analytics: 'Analytics',
          account: 'Account',
           profile: 'Profile',
           chatbot: 'Chatbot',
           reports: 'Reports',
          forecasts: 'Forecasts',
          habits: 'Habits',
          categories: 'Categories',
          importAction: 'Import',
          notifications: 'Notifications',
          settings: 'Settings',
          scanFill: 'Scan & Fill',
          signIn: 'Sign In',
          createAccount: 'Create Account'
      };

      const staticTextMap = {
           Dashboard: 'dashboard',
           Income: 'income',
          Expenses: 'expenses',
          Budgets: 'budgets',
          Goals: 'goals',
          Analytics: 'analytics',
          'Analytics →': 'analytics',
          Account: 'account',
         Profile: 'profile',
         Settings: 'settings',
         Language: 'language',
         Logout: 'logout',
         Overview: 'overview',
         Money: 'money',
         Planning: 'planning',
         Insights: 'insights',
         Tools: 'tools',
         System: 'system',
         Accounts: 'accounts',
         Import: 'importAction',
         Reports: 'reports',
         Forecasts: 'forecasts',
         Habits: 'habits',
         'Scan & Fill': 'scanFill',
         Assistant: 'assistant',
         'AI Assistant': 'chat',
         'Dark Mode': 'darkMode',
         'Light Mode': 'lightMode',
         'Theme': 'theme',
         'Search language': 'searchLanguage',
         'Total Income': 'totalIncome',
         'Total Expenses': 'totalExpenses',
         'Total Balance': 'totalBalance',
         'Net Balance': 'netBalance',
         'Net Savings': 'netSavings',
         'Financial Health': 'financialHealth',
         'Welcome back!': 'welcome',
         'Welcome back': 'welcomeBack',
         "Here's your financial snapshot": 'financialSnapshot',
         'Tracked this month': 'trackedThisMonth',
         'No income yet': 'noIncomeYet',
         'Spending tracked this month': 'spendingThisMonth',
         'No expenses yet': 'noExpensesYet',
         'No spending yet': 'noSpendingYet',
         'Profile Information': 'profileInformation',
         'Account Settings': 'accountSettings',
         'Notifications': 'notifications',
         'Appearance': 'appearance',
         'Configure': 'configure',
         'Customize': 'customize',
         'Upload photo': 'uploadPhoto',
         'Remove photo': 'removePhoto',
         'Save photo': 'savePhoto',
         'Edit': 'edit',
         'Close': 'close',
         'Save': 'save',
         'Cancel': 'cancel',
         'Confirm': 'confirm',
         'Delete': 'delete',
         'Change': 'change',
         'Open Regional': 'openRegional',
         'Manage Categories': 'manageCategories',
         'Accent color': 'accentColor',
         'FinWise indigo is the default. More accents are on the way.': 'accentDescription',
         'Subtle transitions across the app. Honors your OS reduced-motion setting.': 'animationsDescription',
         'Light or Dark — applies instantly across the whole app.': 'themeDescription',
         'One language for the entire app — navigation, pages, forms and assistant.': 'languageDescription',
         'How dates are displayed.': 'dateFormatDescription',
         'Minimize': 'minimize',
         'Restore': 'restore',
         'Clear history': 'clearHistory',
         'Export': 'export',
         'Search': 'search',
         'Back': 'back',
         'Apply': 'apply',
         'Income vs Expenses': 'incomeVsExpenses',
         'Monthly comparison': 'monthlyComparison',
         'Monthly comparison · this month': 'monthlyComparisonThisMonth',
         'View All →': 'viewAll',
         'Budget Progress': 'budgetProgress',
         'Track spending limits': 'trackSpendingLimits',
         'Manage →': 'manage',
         'No budgets yet': 'noBudgetsYet',
         'Financial Goals': 'financialGoals',
         'Savings targets': 'savingsTargets',
         'Latest activity': 'latestActivity',
         'Start tracking your finances by adding your first transaction.': 'startTracking',
         'Add income to compute savings rate': 'computeSavings',
         'Create a goal to start tracking your savings.': 'createGoal',
         'Track your savings targets': 'trackSavingsTargets',
         'No goals yet': 'noGoalsYet',
          'Create a goal to start tracking': 'createGoal',
          'Saved:': 'saved',
         'of': 'of',
         'Budget': 'budgets',
         'Goal': 'goals',
         'Income minus expenses · this month': 'incomeMinusExpenses',
         'Add Expense': 'addExpense',
         'Add Income': 'addIncome',
          'Create Budget': 'createBudget',
          'Add Goal': 'addGoal',
          'Log spending': 'logSpending',
         'Log earnings': 'logEarnings',
         'Set a limit': 'setLimit',
         'Save for more': 'saveForMore',
         'Scan Receipt': 'scanReceipt',
         'Auto-fill': 'autoFill',
         'Spending by Category': 'categorySpending',
         'Where your money goes': 'whereMoneyGoes',
         'Budget Usage': 'budgetUsage',
         'Limits vs spending': 'limitsVsSpending',
         'Add your first expense to see the category breakdown.': 'addFirstExpenseDesc',
         'Based on your real savings, budgets & goals': 'basedOnReal',
         'Your money at a glance': 'yourMoneyGlance',
         'Good morning': 'goodMorning',
         'Good afternoon': 'goodAfternoon',
         'Good evening': 'goodEvening',
         'Sign In': 'signIn',
         'Create Account': 'createAccount',
         'Create account': 'createAccount',
         'Password': 'password',
         'Username': 'username',
         'Email': 'email',
         'Full Name': 'fullName',
         'Your money.': 'yourMoney',
         'Your privacy.': 'yourPrivacy',
         'Your control.': 'yourControl',
         'Private finance workspace': 'privateWorkspace',
         'Take control of your finances with a private, offline-first financial workspace.': 'takeControl',
         'OFFLINE-FIRST': 'offlineFirst',
         'Your financial data stays on your device.': 'dataStaysDevice',
         'PRIVATE BY DESIGN': 'privateByDesign',
         'No tracking. No external cloud storage.': 'noTracking',
         'SMART FINANCE': 'smartFinance',
         'Budgets, goals, insights and expense tracking.': 'budgetsGoalsInsights',
         'PRIVATE & OFFLINE': 'privateOffline',
         'Your financial data remains on this device.': 'dataRemains',
         'FinWise is designed offline-first with privacy in mind.': 'designedOffline',
         'Please enter your username and password.': 'credsRequired',
         'Invalid username or password. Please try again.': 'invalidCredentials',
         'You have been logged out successfully.': 'loggedOut',
         'Sign in to continue to your FinWise account.': 'signInContinue',
         "Don't have an account?": 'noAccount',
         'Already have an account?': 'haveAccount',
         'FinWise Assistant': 'assistantTitle',
         'Suggested prompts — tap to ask': 'suggestedPrompts',
         'How much did I spend this month?': 'qSpendMonth',
         'Where am I spending the most?': 'qMostSpending',
         'Show my recent expenses': 'qRecentExpenses',
         'How much can I save?': 'qHowSave',
         'What is my monthly income?': 'qMonthlyIncome',
         'Analyze Expenses': 'analyzeExpenses',
         'Show Balance': 'showBalance',
          'Budget Summary': 'budgetSummary',
          'This Month Spend': 'thisMonthSpend',
         'Monthly Income': 'monthlyIncome',
         "Hi, I'm your Offline Finance AI": 'offlineAiWelcome',
         'Try one of these — all answers come strictly from your authenticated local records:': 'tryOneOf',
         'FinWise Control Center': 'controlCenter',
         'Manage your FinWise experience, privacy, finance preferences and data.': 'controlSubtitle',
         'Search settings...': 'searchSettings',
         'Language & Region': 'languageRegion',
         'Privacy & Security': 'privacySecurity',
         'Data & Backup': 'dataBackup',
         'Finance Preferences': 'financePrefs',
         'Dashboard Preferences': 'dashboardPrefs',
         'AI & Insights': 'aiInsights',
         'Accessibility': 'accessibility',
         'Advanced': 'advanced',
         'About FinWise': 'aboutFinwise',
         'About': 'aboutFinwise',
         'Dark': 'themeDark',
         'Light': 'themeLight',
         'Regional': 'regional',
         'Currency': 'currency',
         'Date Format': 'dateFormat',
         'First day of week': 'firstDayWeek',
         'Sunday': 'sunday',
         'Monday': 'monday',
         'Saturday': 'saturday',
         'Enable Notifications': 'enableNotifications',
         'Data Management': 'dataManagement',
         'Create Backup': 'createBackup',
         'Export Data': 'exportDataBtn',
         'Restore Backup': 'restoreBackup',
         'Import Data': 'importData',
         'No backup created yet.': 'noBackup',
         'Reset to Defaults': 'resetDefaults',
         'Reset preferences': 'resetPreferences',
         'Delete all local data': 'deleteAllData',
         'Danger Zone': 'dangerZone',
         'Are you sure?': 'confirmTitle',
         'Save Settings': 'saveSettings',
         'Unsaved changes': 'unsavedChanges',
         'Changes saved': 'changesSaved',
         'Coming soon': 'comingSoon',
         'Built-in': 'builtIn',
         'No settings match your search.': 'noSettingsMatch',
         'Summary': 'summaryTitle',
         'Text size': 'textSize',
         'High contrast': 'highContrast',
         'Reduce animations': 'reduceAnimations',
         'Animations': 'animations',
         'Clear Cache': 'clearCache',
         'Storage used': 'storageUsed',
         'Application version': 'appVersion',
         'Version': 'version',
         'Local-only data': 'localOnlyData',
         'Passcode lock': 'passcodeLock',
         'Biometric unlock': 'biometricUnlock',
         'Auto-lock': 'autoLock',
         'Change Password': 'changePassword',
         'Default currency': 'defaultCurrency',
         'Budget cycle': 'budgetCycle',
         'Monthly': 'monthly',
         'Weekly': 'weekly',
         'Yearly': 'yearly',
         'Decimal places': 'decimalPlaces',
         'Negative amount display': 'negativeDisplay',
         'Upcoming bills': 'upcomingBills',
         'Smart categorization': 'smartCategorization',
         'Spending insights': 'spendingInsights',
         'Budget recommendations': 'budgetRecommendations',
         'Goal insights': 'goalInsights',
         'Forecasting': 'forecasting',
         'Duplicate transaction detection': 'duplicateDetection',
         'OCR-assisted imports': 'ocrImports',
         'Data handling': 'dataHandling',
         'Privacy': 'privacy',
         'Security': 'security',
         'Bill reminders': 'billReminders',
         'Goal milestones': 'goalMilestones',
         'Large expense alerts': 'largeExpenseAlerts',
         'Weekly summary': 'weeklySummary',
         'Monthly summary': 'monthlySummary',
         'Budget alerts and spending updates': 'budgetAlertsDescription',
         'Theme and display preferences': 'appearanceDescription',
         'Download your financial data': 'downloadFinancialData',
         'Permanently delete your account and all data': 'permanentDeleteDescription',
         'Manage your preferences and app configuration': 'managePreferences',
         'Active Goals': 'activeGoals',
         'goals tracking': 'goalsTracking',
         'Healthy': 'healthy',
         'Deficit': 'deficit',
         'Neutral': 'neutral',
         'New Budget': 'newBudget',
         'New Goal': 'newGoal',
         'My Account': 'myAccount',
         'Smart Categories': 'smartCategories',
         'Track every category in one place': 'trackEveryCategory',
         'No data yet': 'noDataYet',
         'Set spending limits to stay on track': 'setSpendingLimits'
     };

      /* Known English input placeholders -> translation keys (for inputs
         without an explicit data-i18n-placeholder attribute). */
      const placeholderTextMap = {
          'Enter your username': 'enterUsername',
          'Enter your password': 'enterPassword',
          'Choose a username': 'chooseUsername',
          'Choose a password (min 6 characters)': 'choosePassword',
          'Your full name': 'fullNamePh',
          'Min. 6 characters': 'min6Ph',
          'Repeat password': 'repeatPh',
          'Search this page…  ( / )': 'searchPage',
          'Search this page…': 'searchPage',
          'Search language': 'searchLanguage',
          'Search settings...': 'searchSettings',
          'Search messages... (Ctrl+K)': 'chatSearch',
          'Ask about your finances…': 'chatPlaceholder',
          'Type DELETE to confirm': 'typeDeleteToConfirm'
      };

      function applyTranslations(localeCode) {
          const code = normalizeLocale(localeCode) || 'en';
          const dictionary = getLocaleDictionary(code);
          const meta = SUPPORTED_LOCALES[code];
          activeDictionary = dictionary;
          document.documentElement.lang = code;
          document.documentElement.dir = meta.rtl ? 'rtl' : 'ltr';
          document.body.style.direction = meta.rtl ? 'rtl' : 'ltr';
          document.body.classList.toggle('fw-rtl', meta.rtl);
         const pageTitle = document.documentElement.dataset.finwiseTitleSource || document.title;
         document.documentElement.dataset.finwiseTitleSource = pageTitle;
         const titleMatch = pageTitle.match(/^(.+?)\s*[-—]\s*Finwise/i);
         if (titleMatch) {
             const titleKey = Object.keys(staticPageTitles).find((key) => staticPageTitles[key].toLowerCase() === titleMatch[1].trim().toLowerCase());
             if (titleKey && dictionary[titleKey]) document.title = dictionary[titleKey] + ' - ' + dictionary.appName;
         }

         injectDataI18nAttributes(dictionary);

         document.querySelectorAll('[data-i18n]').forEach((el) => {
             const key = el.dataset.i18n;
             if (dictionary[key]) {
                 el.textContent = dictionary[key];
             }
         });

         document.querySelectorAll('[data-i18n-placeholder]').forEach((el) => {
             const key = el.dataset.i18nPlaceholder;
             if (dictionary[key]) el.setAttribute('placeholder', dictionary[key]);
         });

         document.querySelectorAll('[data-i18n-title]').forEach((el) => {
             const key = el.dataset.i18nTitle;
             if (key && dictionary[key]) el.title = dictionary[key];
         });

          document.querySelectorAll('[data-i18n-value]').forEach((el) => {
              const key = el.dataset.i18nValue;
              if (dictionary[key]) el.value = dictionary[key];
          });

          document.querySelectorAll('[data-i18n-aria-label]').forEach((el) => {
              const key = el.dataset.i18nAriaLabel;
              if (key && dictionary[key]) el.setAttribute('aria-label', dictionary[key]);
          });

          /* Translate known English placeholders even without explicit attrs. */
          document.querySelectorAll('input[placeholder], textarea[placeholder]').forEach((el) => {
              if (el.dataset.i18nPlaceholder) return;
              const key = placeholderTextMap[(el.getAttribute('placeholder') || '').trim()];
              if (key && dictionary[key]) el.setAttribute('placeholder', dictionary[key]);
          });

          /* Topbar page title + subtitle: keep English source, render translated. */
          document.querySelectorAll('[data-fw-page-title]').forEach((el) => {
              const src = (el.getAttribute('data-fw-page-title') || '').trim();
              if (!src) return;
              const lower = src.toLowerCase();
              let key = Object.keys(staticPageTitles).find((k) => staticPageTitles[k].toLowerCase() === lower);
              if (!key) key = staticTextMap[src];
              if (key && dictionary[key]) el.textContent = dictionary[key];
          });
          document.querySelectorAll('[data-fw-page-sub]').forEach((el) => {
              if (el.dataset.i18n) return;
              const src = el.textContent.trim();
              const key = staticTextMap[src];
              if (key && dictionary[key]) { el.dataset.i18n = key; el.textContent = dictionary[key]; }
          });

          /* Day-part greeting follows the global language. */
          document.querySelectorAll('[data-fw-greeting]').forEach((el) => {
              el.textContent = window.fwGreeting();
          });

          document.querySelectorAll('.fw-account-theme-toggle').forEach((button) => updateAccountThemeToggle(button));
         document.querySelectorAll('.fw-account-menu-item[data-account-action="language"]').forEach((button) => {
             const lang = button.getAttribute('data-language');
             if (SUPPORTED_LOCALES[lang]) button.textContent = SUPPORTED_LOCALES[lang].label;
         });

         const headerThemeBtn = document.querySelector('.fw-header-theme-toggle');
         if (headerThemeBtn) updateHeaderThemeToggle(headerThemeBtn);

         languageListeners.forEach((listener) => listener(localeCode, dictionary));
     }

     const i18nInjectedKeys = new Set();

     function injectDataI18nAttributes(dictionary) {
         const reverseMap = {};
         for (const [key, value] of Object.entries(staticTextMap)) {
             reverseMap[value] = key;
         }

         const allTextNodes = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
         const processed = new Set();
         while (allTextNodes.nextNode()) {
             const node = allTextNodes.currentNode;
             const parent = node.parentElement;
             if (!parent || parent.dataset.i18n || parent.closest('[data-i18n]') || parent.closest('script') || parent.closest('style')) continue;
             const text = node.textContent.trim();
             if (!text || text.length > 100 || processed.has(node)) continue;
             const key = staticTextMap[text];
             if (key && dictionary[key]) {
                 parent.dataset.i18n = key;
                 processed.add(node);
             }
         }

         document.querySelectorAll('.fw-nav__link, .fw-sidebar__item').forEach((el) => {
             const text = el.textContent.trim().split('\n')[0].trim();
             const key = staticTextMap[text];
             if (key && dictionary[key] && !el.dataset.i18n) {
                 el.dataset.i18n = key;
                 el.textContent = dictionary[key];
             } else if (key && dictionary[key] && el.dataset.i18n && !processed.has(el.firstChild)) {
                 el.textContent = dictionary[key];
             }
         });

         document.querySelectorAll('h1, h2, h3, h4, h5, h6').forEach((el) => {
             const text = el.textContent.trim();
             if (!text || text.length > 100) return;
             const key = staticTextMap[text];
             if (key && dictionary[key] && !el.dataset.i18n) {
                 el.dataset.i18n = key;
                 el.textContent = dictionary[key];
             }
         });

         document.querySelectorAll('p, span, div, label, a, button, li, td, th, option, h1, h2, h3, h4, h5, h6').forEach((el) => {
             const text = el.textContent.trim();
             if (!text || text.length > 100 || el.dataset.i18n || el.querySelector('[data-i18n]')) return;
             const key = staticTextMap[text];
             if (key && dictionary[key]) {
                 el.dataset.i18n = key;
                 el.textContent = dictionary[key];
             }
         });

         document.querySelectorAll('title').forEach((el) => {
             const text = el.textContent.trim();
             const titleMatch = text.match(/^(.+?)\s*[-—]\s*Finwise/i);
             if (titleMatch) {
                 const titleKey = Object.keys(staticPageTitles).find((k) => staticPageTitles[k].toLowerCase() === titleMatch[1].trim().toLowerCase());
                 if (titleKey && dictionary[titleKey]) {
                     el.dataset.i18n = titleKey;
                     el.textContent = dictionary[titleKey] + ' - ' + dictionary.appName;
                 }
             }
         });
     }

       function loadLocale(localeCode) {
           const code = normalizeLocale(localeCode) || 'en';
           localStorage.setItem(STORAGE_KEYS.LANGUAGE, code);
           localStorage.setItem(STORAGE_KEYS.LANGUAGE_COMPAT, code);
           // Persist server-side (best-effort, offline-safe, no reload) so the
           // choice also survives on other devices/sessions for this account.
           try {
               fetch('/account/update-language', {
                   method: 'POST',
                   headers: { 'Content-Type': 'application/json' },
                   body: JSON.stringify({ language: code })
               }).catch(function () {});
           } catch (e) {}
          // Do NOT force a theme here — finwise-ui.js owns data-theme.
          // Only ensure an effective theme is painted if none exists yet.
          try {
              const dt = document.documentElement.getAttribute('data-theme');
              if (dt !== 'light' && dt !== 'dark') {
                  const pref = localStorage.getItem(STORAGE_KEYS.THEME);
                  // Light or Dark only: legacy 'auto'/unknown values migrate to Light.
                  const eff = (pref === 'dark' || pref === 'light') ? pref : 'light';
                  if (pref !== eff) { try { localStorage.setItem(STORAGE_KEYS.THEME, eff); } catch (e2) {} }
                  document.documentElement.setAttribute('data-theme', eff);
              }
              updateAllThemeToggles(getEffectiveTheme());
          } catch (e) {}
          applyTranslations(code);
      }

     function preloadLocaleDictionaries() {
         const localeLoads = Object.keys(SUPPORTED_LOCALES)
             .filter((code) => code !== 'en')
             .map((code) => fetch('/locales/' + code + '.json')
                 .then((response) => response.ok ? response.json() : {})
                 .then((data) => {
                     localeDictionaries[code] = data || {};
                     localStorage.setItem('finwise_locale_cache_' + code, JSON.stringify(localeDictionaries[code]));
                 })
                 .catch(() => {
                     localeDictionaries[code] = fetchedLocale(code);
                 }));
         return Promise.all(localeLoads);
     }

    window.FinwiseLanguage = {
        get current() { return getCurrentLocale(); },
        t: function (key) { return activeDictionary[key] || key; },
        set: loadLocale,
        subscribe: function (listener) {
            if (typeof listener === 'function') languageListeners.push(listener);
            return function () {
                const index = languageListeners.indexOf(listener);
                if (index >= 0) languageListeners.splice(index, 1);
            };
        }
    };

    function updateAccountThemeToggle(button) {
        if (!button) return;
        const isLight = getEffectiveTheme() === 'light';
        const icon = button.querySelector('.fw-account-theme-icon');
        const label = button.querySelector('.fw-account-theme-label');
        const strong = button.querySelector('.fw-account-theme-mode');
        if (icon) {
            // Icon-only: Moon in LIGHT, Sun in DARK (single helper in finwise-ui.js).
            if (typeof window.__fwThemeIcon === 'function') { try { icon.innerHTML = window.__fwThemeIcon(isLight ? 'light' : 'dark'); } catch (e) {} }
            else icon.textContent = isLight ? '☾' : '☀';
        }
        if (label) {
            const dictionary = getLocaleDictionary(getCurrentLocale());
            label.textContent = isLight ? (dictionary.lightMode || 'Light Mode') : (dictionary.darkMode || 'Dark Mode');
        }
        if (strong) {
            const dictionary = getLocaleDictionary(getCurrentLocale());
            strong.textContent = isLight ? (dictionary.themeLight || 'Light') : (dictionary.themeDark || 'Dark');
        }
    }

    function buildLanguageMenuItems() {
        const entries = Object.entries(SUPPORTED_LOCALES).map(([code, meta]) => `
            <button type="button" class="fw-account-menu-item" data-account-action="language" data-language="${code}">${meta.label}</button>
        `).join('');
        return `
            <div class="fw-account-menu-item fw-account-menu-item--sub" tabindex="0">
                <span class="fw-account-menu-link"><span data-i18n="language">Language</span> <span aria-hidden="true">›</span></span>
                <div class="fw-account-submenu-panel" role="menu">
                    <input class="fw-language-search" type="search" data-i18n-placeholder="searchLanguage"
                           placeholder="Search language" aria-label="Search language">
                    <div class="fw-language-options">${entries}</div>
                </div>
            </div>
        `;
    }

    function getUserInitials(name) {
        if (!name || !name.trim()) return 'A';
        const parts = name.trim().split(/\s+/).filter(Boolean);
        if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
        return (parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
    }

    function updateNavUserAvatar(imageUrl, fullName) {
        const avatarEls = document.querySelectorAll('.fw-nav-avatar, .fw-nav__avatar, .fw-nav-name');
        avatarEls.forEach((el) => {
            if (el.classList.contains('fw-nav-name')) {
                el.textContent = getUserInitials(fullName);
                el.style.background = 'linear-gradient(135deg, rgba(99,102,241,.25), rgba(16,185,129,.2))';
                return;
            }
            if (imageUrl) {
                el.innerHTML = '<img src="' + imageUrl + '" alt="Profile avatar" style="width:100%;height:100%;object-fit:cover;border-radius:50%;display:block;" />';
            } else {
                el.textContent = getUserInitials(fullName);
            }
        });
    }

    function setupProfileImageUpload() {
        const input = document.getElementById('profile-image-input');
        const preview = document.getElementById('profile-image-preview');
        const fallback = document.getElementById('profile-image-fallback');
        const removeBtn = document.getElementById('remove-profile-image');
        if (!input) return;

        input.addEventListener('change', function () {
            const file = input.files && input.files[0];
            if (!file) return;
            const formData = new FormData();
            formData.append('file', file);

            fetch('/api/profile/image', {
                method: 'POST',
                body: formData
            })
            .then((res) => res.json())
            .then((data) => {
                if (data && data.avatarUrl) {
                    if (preview) {
                        preview.src = data.avatarUrl;
                        preview.style.display = 'block';
                    }
                    if (fallback) fallback.style.display = 'none';
                    updateNavUserAvatar(data.avatarUrl, document.querySelector('h1') ? document.querySelector('h1').textContent : 'A');
                    if (removeBtn) removeBtn.style.display = 'inline-flex';
                    showToast('Profile image updated successfully', 'success');
                } else {
                    showToast(data && data.message ? data.message : 'Unable to update profile image', 'error');
                }
            })
            .catch(() => showToast('Unable to save the profile image while offline', 'error'));
        });

        if (removeBtn) {
            removeBtn.addEventListener('click', function () {
                fetch('/api/profile/image', { method: 'DELETE' })
                    .then((res) => res.json())
                    .then((data) => {
                        if (preview) {
                            preview.src = '';
                            preview.style.display = 'none';
                        }
                        if (fallback) fallback.style.display = 'block';
                        updateNavUserAvatar('', document.querySelector('h1') ? document.querySelector('h1').textContent : 'A');
                        showToast(data && data.message ? data.message : 'Profile image removed', 'success');
                    })
                    .catch(() => showToast('Unable to remove the profile image', 'error'));
            });
        }
    }

    function syncCurrentProfile() {
        fetch('/api/profile/me')
            .then((res) => res.ok ? res.json() : null)
            .then((profile) => {
                if (!profile) return;
                const fullName = profile.fullName || 'A';
                const avatarUrl = profile.avatarUrl || '';
                const preview = document.getElementById('profile-image-preview');
                const fallback = document.getElementById('profile-image-fallback');
                if (preview) {
                    preview.src = avatarUrl || '';
                    preview.style.display = avatarUrl ? 'block' : 'none';
                }
                if (fallback) {
                    fallback.style.display = avatarUrl ? 'none' : 'block';
                    fallback.textContent = getUserInitials(fullName);
                }
                updateNavUserAvatar(avatarUrl, fullName);
            })
            .catch(() => {});
    }

    window.exportData = function() {
        fetch('/account/export-data', { credentials: 'same-origin' })
            .then(async (response) => {
                if (!response.ok) {
                    const message = await response.text();
                    throw new Error(message || 'Unable to export your data.');
                }

                const blob = await response.blob();
                const header = response.headers.get('content-disposition') || 'attachment; filename="finwise-export.csv"';
                const match = /filename\s*=\s*(?:"([^"]+)"|([^;]+))/i.exec(header);
                const filename = match ? (match[1] || match[2] || 'finwise-export.csv') : 'finwise-export.csv';
                const url = URL.createObjectURL(blob);
                const link = document.createElement('a');
                link.href = url;
                link.download = filename;
                document.body.appendChild(link);
                link.click();
                link.remove();
                URL.revokeObjectURL(url);
                showToast('Your financial data export was downloaded successfully.', 'success');
            })
            .catch((error) => {
                showToast(error && error.message ? error.message : 'Unable to export your data right now.', 'error');
            });
    };

    function initAccountDropdown() {
        const containers = document.querySelectorAll('.fw-nav-user, .fw-nav__actions');
        if (!containers.length) return;

        containers.forEach((container) => {
            if (container.dataset.accountDropdownReady === 'true') return;
            container.dataset.accountDropdownReady = 'true';
            const accountMenu = document.createElement('div');
            accountMenu.className = 'fw-account-menu';
            accountMenu.innerHTML = `
                <button type="button" class="fw-account-trigger" aria-expanded="false" aria-haspopup="true">
                    <span data-i18n="account">Account</span><span class="fw-account-trigger-chevron">▾</span>
                </button>
                <div class="fw-account-menu-panel">
                    <button type="button" class="fw-account-menu-item" data-account-action="profile"><span data-i18n="profile">Profile</span></button>
                    <button type="button" class="fw-account-menu-item" data-account-action="settings"><span data-i18n="settings">Settings</span></button>
                    <div class="fw-account-menu-item fw-account-menu-item--sub" tabindex="0">
                        <span class="fw-account-menu-link"><span data-i18n="language">Language</span><span aria-hidden="true">›</span></span>
                        <div class="fw-account-submenu-panel">
                            ${Object.entries(SUPPORTED_LOCALES).map(([code, meta]) =>
                                `<button type="button" class="fw-account-menu-item" data-account-action="language" data-language="${code}">${meta.label}</button>`
                            ).join('')}
                        </div>
                    </div>
                    <div class="fw-account-menu-divider"></div>
                    <button type="button" class="fw-account-theme-toggle" data-account-action="theme">
                        <span class="fw-account-theme-label"><span class="fw-account-theme-icon">☾</span><span class="fw-account-theme-mode">Dark Mode</span></span>
                    </button>
                    <div class="fw-account-menu-divider"></div>
                    <form method="post" action="/logout">
                        <input type="hidden" name="_csrf" value="${window.FW_CSRF_TOKEN || ''}">
                        <button type="submit" class="fw-account-logout">
                            <span data-i18n="logout">Logout</span>
                        </button>
                    </form>
                </div>
            `;
            container.replaceChildren(accountMenu);

            const trigger = accountMenu.querySelector('.fw-account-trigger');
            const panel = accountMenu.querySelector('.fw-account-menu-panel');
            trigger.addEventListener('click', (event) => {
                event.stopPropagation();
                const open = accountMenu.classList.toggle('is-open');
                trigger.setAttribute('aria-expanded', String(open));
            });
            panel.querySelectorAll('[data-account-action]').forEach((item) => {
                item.addEventListener('click', (event) => {
                    event.stopPropagation();
                    const action = item.dataset.accountAction;
                    if (action === 'profile' || action === 'settings') window.location.href = '/account';
                    if (action === 'language') loadLocale(item.dataset.language || 'en');
                    if (action === 'theme') window.changeTheme(getEffectiveTheme() === 'light' ? 'dark' : 'light');
                    if (action !== 'language') {
                        accountMenu.classList.remove('is-open');
                        trigger.setAttribute('aria-expanded', 'false');
                    }
                });
            });
            const languageItem = panel.querySelector('.fw-account-menu-item--sub');
            languageItem.addEventListener('click', (event) => {
                event.stopPropagation();
                languageItem.classList.toggle('is-open');
            });
            document.addEventListener('click', () => {
                accountMenu.classList.remove('is-open');
                trigger.setAttribute('aria-expanded', 'false');
            });
            updateAccountThemeToggle(panel.querySelector('.fw-account-theme-toggle'));
        });
    }

    function updateHeaderThemeToggle(button) {
        if (!button) return;
        const isLight = getEffectiveTheme() === 'light';
        const ic = button.querySelector('.fw-header-theme-icon');
        if (ic) {
            if (typeof window.__fwThemeIcon === 'function') { try { ic.innerHTML = window.__fwThemeIcon(isLight ? 'light' : 'dark'); } catch (e) {} }
            else ic.textContent = isLight ? '☾' : '☀';
        }
        const tx = button.querySelector('.fw-header-theme-text');
        if (tx) tx.remove();
        button.setAttribute('aria-label', isLight ? tr('switchToDark', 'Switch to dark mode') : tr('switchToLight', 'Switch to light mode'));
    }

    // ============================================
    // LOCAL UI PREFERENCES (offline-first, no backend changes)
    // text size / contrast / motion / dashboard cards / auto-lock
    // ============================================
    function fwLocalPref(key, fallback) {
        try {
            var v = localStorage.getItem(key);
            return v == null ? fallback : v;
        } catch (e) { return fallback; }
    }

    function fwApplyLocalPrefs() {
        try {
            document.documentElement.setAttribute('data-fw-textsize', fwLocalPref('finwise_textsize', 'default'));
            document.documentElement.classList.toggle('fw-high-contrast', fwLocalPref('finwise_contrast', 'off') === 'on');
            document.documentElement.classList.toggle('fw-no-motion', fwLocalPref('finwise_motion', 'on') === 'off');
        } catch (e) {}
        var cards = null;
        try { cards = JSON.parse(localStorage.getItem('finwise_dash_cards') || 'null'); } catch (e) {}
        if (cards) {
            document.querySelectorAll('[data-dash-card]').forEach(function (el) {
                var k = el.getAttribute('data-dash-card');
                if (k && cards[k] === false) el.style.display = 'none';
            });
        }
    }

    function fwInitAutoLock() {
        var mins = parseInt(fwLocalPref('finwise_autolock', 'never'), 10);
        if (isNaN(mins) || mins <= 0) return;
        var timer = null;
        function signOut() {
            try {
                var f = document.createElement('form');
                f.method = 'post';
                f.action = fwLocalPref('finwise_passcode_enabled', 'false') === 'true' ? '/app-lock/lock' : '/logout';
                var token = document.createElement('input');
                token.type = 'hidden';
                token.name = '_csrf';
                token.value = window.FW_CSRF_TOKEN || '';
                f.appendChild(token);
                f.style.display = 'none';
                document.body.appendChild(f);
                f.submit();
            } catch (e) { try { window.location.href = '/login?logout'; } catch (e2) {} }
        }
        function reset() {
            if (timer) clearTimeout(timer);
            timer = setTimeout(signOut, mins * 60 * 1000);
        }
        ['click', 'keydown', 'mousemove', 'touchstart', 'scroll'].forEach(function (ev) {
            document.addEventListener(ev, reset, { passive: true });
        });
        reset();
    }

    // ============================================
    // INITIALIZATION
    // ============================================
 document.addEventListener('DOMContentLoaded', function() {
         // Local UI prefs (text size, contrast, motion, dashboard cards) apply
         // on every page before anything else, so prefs feel global.
         try { fwApplyLocalPrefs(); } catch (e) {}
         try { fwInitAutoLock(); } catch (e) {}
          // Single global theme state lives in finwise-ui.js (localStorage
          // 'finwise_theme' light|dark -> html[data-theme] + .dark/.light).
         // Reuse it here: resolve the EFFECTIVE theme (saved > server > OS)
         // instead of forcing a default, so toggle/refresh/navigate agree.
         const theme = getEffectiveTheme();
         document.documentElement.setAttribute('data-theme', theme);
         if (document.documentElement.classList) {
             document.documentElement.classList.toggle('dark', theme === 'dark');
             document.documentElement.classList.toggle('light', theme === 'light');
         }
         
         window.addEventListener('online', checkOnlineStatus);
         window.addEventListener('offline', checkOnlineStatus);
         checkOnlineStatus();
         
         initSidebar();
         initAccountDropdown();
         
         const locale = getCurrentLocale();
         loadLocale(locale);
         preloadLocaleDictionaries().then(() => {
             const activeLocale = getCurrentLocale();
             applyTranslations(activeLocale);
             updateAllThemeToggles(getEffectiveTheme());
         });
         updateAllThemeToggles(theme);
         
         setupProfileImageUpload();
         syncCurrentProfile();
         setDefaultDates();
         setupFormValidation();
         setupAutoSave();
         setupKeyboardShortcuts();
         setupLinkLoading();
         
         document.querySelectorAll('.fw-alert, .alert').forEach(alert => {
             setTimeout(() => {
                 alert.style.opacity = '0';
                 alert.style.transition = 'opacity 0.3s ease';
                 setTimeout(() => alert.remove(), 300);
             }, 5000);
         });
         
         document.querySelectorAll('.fw-stat-card, .fw-section, .fw-goal-card, .fw-form-card, .fw-quick-action').forEach(card => {
             card.style.transformStyle = 'preserve-3d';
             card.style.transition = 'transform 0.1s ease-out, box-shadow 0.3s var(--ease)';
             
             card.addEventListener('mousemove', (e) => {
                 if (window.innerWidth <= 768) return;
                 const rect = card.getBoundingClientRect();
                 const x = e.clientX - rect.left;
                 const y = e.clientY - rect.top;
                 const centerX = rect.width / 2;
                 const centerY = rect.height / 2;
                 const rotateX = ((y - centerY) / centerY) * -5;
                 const rotateY = ((x - centerX) / centerX) * 5;
                 card.style.transform = `perspective(800px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) translateZ(6px)`;
             });
             
             card.addEventListener('mouseleave', () => {
                 card.style.transform = 'perspective(800px) rotateX(0) rotateY(0) translateZ(0)';
             });
         });
         
         document.addEventListener('mousemove', (e) => {
             if (window.innerWidth <= 768) return;
             const x = (e.clientX / window.innerWidth - 0.5) * 15;
             const y = (e.clientY / window.innerHeight - 0.5) * 15;
             document.body.style.backgroundPosition = `${50 + x}% ${50 + y}%`;
         });
         
         document.addEventListener('keydown', (e) => {
             if (e.key === 'Tab') document.body.classList.add('keyboard-nav');
         });
         
         document.addEventListener('mousedown', () => {
             document.body.classList.remove('keyboard-nav');
         });
         
         setupAccountButtons();
     });

    // Expose utilities globally
    window.Finwise = {
        showToast,
        formatCurrency: window.formatCurrency,
        formatNumber: window.formatNumber,
        confirmDelete: window.confirmDelete,
        confirmAction: window.confirmAction,
        isOnline: () => isOnline
    };

    // Add toast animation styles
    const style = document.createElement('style');
    style.textContent = `
        @keyframes slideIn {
            from { opacity: 0; transform: translateX(100%); }
            to { opacity: 1; transform: translateX(0); }
        }
        @keyframes slideOut {
            from { opacity: 1; transform: translateX(0); }
            to { opacity: 0; transform: translateX(100%); }
        }
        .spinner {
            display: inline-block; width: 16px; height: 16px;
            border: 2px solid transparent; border-top-color: currentColor;
            border-radius: 50%; animation: spin 0.8s linear infinite;
            margin-right: var(--space-2); vertical-align: middle;
        }
        @keyframes spin { to { transform: rotate(360deg); } }
        .keyboard-nav *:focus { outline: 2px solid var(--primary) !important; outline-offset: 2px !important; }
    `;
    // ============================================
    // ACCOUNT SETTINGS MODALS
    // ============================================
    window.openEditModal = function(fieldName, currentValue, fieldKey) {
        const t = (key, fallback) => window.FinwiseLanguage ? window.FinwiseLanguage.t(key) : fallback;
        fieldKey = fieldKey || ({'Full Name': 'fullName', Username: 'username', Email: 'email'}[fieldName] || fieldName.toLowerCase());
        const translatedField = fieldName === 'Full Name' ? t('fullName', fieldName) : fieldName === 'Username' ? t('username', fieldName) : fieldName === 'Email' ? t('email', fieldName) : fieldName;
        const modal = document.createElement('div');
        modal.id = 'edit-modal';
        modal.style.cssText = `
            position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 1000;
            display: flex; align-items: center; justify-content: center;
            backdrop-filter: blur(4px);
        `;
        
        const modalContent = document.createElement('div');
        modalContent.style.cssText = `
            background: var(--glass-bg);
            border: 1px solid var(--glass-border);
            border-radius: var(--radius-xl);
            padding: var(--sp-8);
            max-width: 450px; width: 90%;
            box-shadow: var(--shadow-xl);
        `;
        
        let inputType = 'text';
        let validationError = '';
        
        if (fieldName === 'Email') {
            inputType = 'email';
        }
        
        modalContent.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--sp-6);">
                <h2 style="font-size: 1.5rem; font-weight: 700;">${t('edit', 'Edit')} ${translatedField}</h2>
                <button onclick="document.getElementById('edit-modal').remove()" style="background:none; border:none; cursor:pointer; font-size:1.5rem; color:var(--surface-500);">✕</button>
            </div>
            <div style="margin-bottom: var(--sp-6);">
                <label style="display: block; font-size: .85rem; color: var(--surface-400); margin-bottom: var(--sp-2); font-weight: 600;">${translatedField}</label>
                <input type="${inputType}" id="edit-input" value="${currentValue}" placeholder="Enter new ${fieldName.toLowerCase()}" 
                    style="width: 100%; padding: var(--sp-3); border: 1px solid var(--glass-border); border-radius: var(--radius-lg); background: rgba(0,0,0,0.2); color: inherit; font-size: .95rem;"
                    required>
                <div id="edit-error" style="color: var(--danger-400); font-size: 0.8rem; margin-top: var(--sp-2); display: none;"></div>
            </div>
            <div style="display: flex; gap: var(--sp-3);">
                <button onclick="document.getElementById('edit-modal').remove()" class="fw-btn fw-btn-secondary" style="flex:1;">${t('cancel', 'Cancel')}</button>
                <button onclick="submitEditField('${fieldName}', '${fieldKey}')" class="fw-btn fw-btn-primary" style="flex:1;">${t('saveChanges', 'Save Changes')}</button>
            </div>
        `;
        
        modal.appendChild(modalContent);
        document.body.appendChild(modal);
        document.getElementById('edit-input').focus();
        
        document.getElementById('edit-input').addEventListener('keydown', (e) => {
            if (e.key === 'Enter') submitEditField(fieldName);
            if (e.key === 'Escape') modal.remove();
        });
    };
    
    window.submitEditField = function(fieldName, fieldKey) {
        const input = document.getElementById('edit-input');
        const errorDiv = document.getElementById('edit-error');
        const value = input.value.trim();
        const fieldMap = {
            'Full Name': 'fullName',
            'Username': 'username',
            'Email': 'email'
        };
        fieldKey = fieldKey || fieldMap[fieldName] || fieldName.toLowerCase();
        
        if (!value) {
            errorDiv.textContent = `${fieldName} is required`;
            errorDiv.style.display = 'block';
            return;
        }
        
        const emailParts = value.split('@');
        const hasValidEmailFormat = emailParts.length === 2
            && emailParts[0].length > 0
            && emailParts[1].includes('.')
            && !emailParts[1].startsWith('.')
            && !emailParts[1].endsWith('.');
        if (fieldKey === 'email' && !hasValidEmailFormat) {
            errorDiv.textContent = 'Please enter a valid email address';
            errorDiv.style.display = 'block';
            return;
        }
        
        const formData = new FormData();
        formData.append(fieldKey, value);
        
        fetch('/account/update-profile', {
            method: 'POST',
            body: formData,
            credentials: 'same-origin',
            headers: { 'Accept': 'application/json' }
        })
        .then(async res => {
            const contentType = res.headers.get('content-type') || '';
            const payload = contentType.includes('application/json') ? await res.json() : {};
            if (!res.ok) {
                throw new Error(payload.message || (res.status === 401 || res.redirected ? 'Your session has expired. Please sign in again.' : `Save failed (${res.status})`));
            }
            return payload;
        })
        .then(data => {
            if (data.success) {
                showToast(`${fieldName} updated successfully`, 'success');
                document.getElementById('edit-modal').remove();
                location.reload();
            } else {
                errorDiv.textContent = data.message || 'Error updating ' + fieldName.toLowerCase();
                errorDiv.style.display = 'block';
            }
        })
        .catch(err => {
            errorDiv.textContent = err.message || 'Error saving changes';
            errorDiv.style.display = 'block';
            console.error('Error:', err);
        });
    };
    
    window.openNotificationsModal = function() {
        const modal = document.createElement('div');
        modal.id = 'notifications-modal';
        modal.style.cssText = `
            position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 1000;
            display: flex; align-items: center; justify-content: center;
            backdrop-filter: blur(4px);
        `;
        
        const modalContent = document.createElement('div');
        modalContent.style.cssText = `
            background: var(--glass-bg);
            border: 1px solid var(--glass-border);
            border-radius: var(--radius-xl);
            padding: var(--sp-8);
            max-width: 500px; width: 90%;
            box-shadow: var(--shadow-xl);
            max-height: 80vh; overflow-y: auto;
        `;
        
        modalContent.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--sp-6);">
                <h2 style="font-size: 1.5rem; font-weight: 700;">Notification Settings</h2>
                <button onclick="document.getElementById('notifications-modal').remove()" style="background:none; border:none; cursor:pointer; font-size:1.5rem; color:var(--surface-500);">✕</button>
            </div>
            <div style="display: flex; flex-direction: column; gap: var(--sp-4); margin-bottom: var(--sp-6);">
                <label style="display: flex; align-items: center; gap: var(--sp-3); cursor: pointer;">
                    <input type="checkbox" class="notification-pref" data-type="budget-alerts" checked style="width: 18px; height: 18px; cursor: pointer;">
                    <span>Budget Alerts</span>
                    <span style="font-size: 0.8rem; color: var(--surface-500);">Get notified when spending approaches budget limits</span>
                </label>
                <label style="display: flex; align-items: center; gap: var(--sp-3); cursor: pointer;">
                    <input type="checkbox" class="notification-pref" data-type="daily-summary" checked style="width: 18px; height: 18px; cursor: pointer;">
                    <span>Daily Summary</span>
                    <span style="font-size: 0.8rem; color: var(--surface-500);">Receive a daily summary of your spending</span>
                </label>
                <label style="display: flex; align-items: center; gap: var(--sp-3); cursor: pointer;">
                    <input type="checkbox" class="notification-pref" data-type="goal-updates" checked style="width: 18px; height: 18px; cursor: pointer;">
                    <span>Goal Updates</span>
                    <span style="font-size: 0.8rem; color: var(--surface-500);">Track progress toward your financial goals</span>
                </label>
            </div>
            <div style="display: flex; gap: var(--sp-3);">
                <button onclick="document.getElementById('notifications-modal').remove()" class="fw-btn fw-btn-secondary" style="flex:1;">Close</button>
                <button onclick="saveNotificationPrefs()" class="fw-btn fw-btn-primary" style="flex:1;">Save Preferences</button>
            </div>
        `;
        
        modal.appendChild(modalContent);
        document.body.appendChild(modal);
    };
    
    window.saveNotificationPrefs = function() {
        const prefs = {
            notificationsEnabled: true,
            budgetAlerts: false,
            dailySummary: false,
            goalUpdates: false
        };
        document.querySelectorAll('.notification-pref').forEach(checkbox => {
            const type = checkbox.dataset.type;
            if (type === 'budget-alerts') prefs.budgetAlerts = checkbox.checked;
            if (type === 'daily-summary') prefs.dailySummary = checkbox.checked;
            if (type === 'goal-updates') prefs.goalUpdates = checkbox.checked;
        });
        
        fetch('/account/update-notifications', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(prefs)
        })
        .then(res => res.json())
        .then(data => {
            if (data.success) {
                showToast('Notification preferences updated', 'success');
                document.getElementById('notifications-modal').remove();
            } else {
                showToast(data.message || 'Error updating preferences', 'error');
            }
        })
        .catch(err => {
            showToast('Error saving preferences', 'error');
            console.error('Error:', err);
        });
    };
    
    window.openAppearanceModal = function() {
        const modal = document.createElement('div');
        modal.id = 'appearance-modal';
        modal.style.cssText = `
            position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 1000;
            display: flex; align-items: center; justify-content: center;
            backdrop-filter: blur(4px);
        `;
        
        const currentTheme = (typeof window.fwGetTheme === 'function')
            ? window.fwGetTheme().pref
            : (localStorage.getItem('finwise_theme') || 'light');
        
        const modalContent = document.createElement('div');
        modalContent.style.cssText = `
            background: var(--glass-bg);
            border: 1px solid var(--glass-border);
            border-radius: var(--radius-xl);
            padding: var(--sp-8);
            max-width: 500px; width: 90%;
            box-shadow: var(--shadow-xl);
        `;
        
        modalContent.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--sp-6);">
                <h2 style="font-size: 1.5rem; font-weight: 700;">Appearance</h2>
                <button onclick="document.getElementById('appearance-modal').remove()" style="background:none; border:none; cursor:pointer; font-size:1.5rem; color:var(--surface-500);">✕</button>
            </div>
            <div style="margin-bottom: var(--sp-6);">
                <label style="display: block; font-size: 0.9rem; font-weight: 600; margin-bottom: var(--sp-4);">Theme</label>
                <div style="display: flex; gap: var(--sp-4); flex-wrap: wrap;">
                    <label style="display: flex; align-items: center; gap: var(--sp-2); cursor: pointer; flex:1; min-width: 120px; padding: var(--sp-3); background: var(--fw-surface-2, rgba(0,0,0,0.2)); border-radius: var(--radius-lg); border: 2px solid ${currentTheme === 'light' ? 'var(--primary-500)' : 'transparent'};">
                        <input type="radio" name="theme" value="light" ${currentTheme === 'light' ? 'checked' : ''} onchange="changeTheme('light')" style="cursor: pointer;">
                        <span>☀ Light</span>
                    </label>
                    <label style="display: flex; align-items: center; gap: var(--sp-2); cursor: pointer; flex:1; min-width: 120px; padding: var(--sp-3); background: var(--fw-surface-2, rgba(0,0,0,0.2)); border-radius: var(--radius-lg); border: 2px solid ${currentTheme === 'dark' ? 'var(--primary-500)' : 'transparent'};">
                        <input type="radio" name="theme" value="dark" ${currentTheme === 'dark' ? 'checked' : ''} onchange="changeTheme('dark')" style="cursor: pointer;">
                        <span>☾ Dark</span>
                    </label>
                </div>
                <p style="font-size: .78rem; color: var(--fw-text-3, var(--surface-500)); margin: var(--sp-3) 0 0;">Light or Dark — applies instantly across the whole app. Text stays high-contrast in every mode.</p>
            </div>
            <div style="display: flex; gap: var(--sp-3);">
                <button onclick="document.getElementById('appearance-modal').remove()" class="fw-btn fw-btn-secondary" style="flex:1;">Close</button>
            </div>
        `;
        
        modal.appendChild(modalContent);
        document.body.appendChild(modal);
    };
    
  window.changeTheme = function(theme) {
          if (theme !== 'light' && theme !== 'dark') return;
          // Delegate to the unified controller when available (updates
          // data-theme, buttons, charts and persists server-side).
          if (typeof window.fwSetTheme === 'function') {
              window.fwSetTheme(theme);
              try { updateAllThemeToggles(getEffectiveTheme()); } catch (e) {}
              showToast(tr('themeChanged', 'Theme changed to') + ': ' + theme, 'success');
              return;
          }
          localStorage.setItem('finwise_theme', theme);
          document.documentElement.setAttribute('data-theme', theme);
          fetch('/account/update-theme', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ theme: theme })
          }).catch(() => {});
          updateAllThemeToggles(theme);
          showToast(tr('themeChanged', 'Theme changed to') + ': ' + theme, 'success');
      };

     function updateAllThemeToggles(theme) {
         const isLight = theme === 'light';
         // Keep the global class API (.dark/.light on <html>) in sync with
         // [data-theme] so every route/component inherits the same theme.
         try {
             document.documentElement.setAttribute('data-theme', theme);
             if (document.documentElement.classList) {
                 document.documentElement.classList.toggle('dark', !isLight);
                 document.documentElement.classList.toggle('light', isLight);
             }
         } catch (e) {}
         document.querySelectorAll('.fw-account-theme-toggle').forEach(function(btn) {
             updateAccountThemeToggle(btn);
         });
         document.querySelectorAll('.fw-header-theme-toggle').forEach(function(btn) {
             updateHeaderThemeToggle(btn);
         });
           document.querySelectorAll('.fw-theme-toggle').forEach(function(btn) {
               const icon = btn.querySelector('.fw-theme-icon');
               const label = btn.querySelector('.fw-theme-label');
               if (icon) {
                   if (typeof window.__fwThemeIcon === 'function') { try { icon.innerHTML = window.__fwThemeIcon(isLight ? 'light' : 'dark'); } catch (e) {} }
                   else icon.textContent = isLight ? '☾' : '☀';
               }
               if (label) label.remove();
           });
         const themeRadios = document.querySelectorAll('input[name="theme"]');
         themeRadios.forEach(function(radio) {
             const parent = radio.closest('label');
             if (parent) {
                 parent.style.borderColor = radio.value === theme ? 'var(--primary-500)' : 'transparent';
             }
         });
     }
    
    window.exportData = function(format) {
        const selectedFormat = format === 'xlsx' ? 'xlsx' : 'csv';
        fetch('/account/export-data?format=' + selectedFormat, { credentials: 'same-origin' })
            .then(res => {
                if (!res.ok) throw new Error('Unable to export data');
                return res.blob();
            })
            .then(blob => {
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `finwise-data-${new Date().toISOString().split('T')[0]}.${selectedFormat}`;
                document.body.appendChild(a);
                a.click();
                window.URL.revokeObjectURL(url);
                a.remove();
                showToast('Data exported successfully', 'success');
            })
            .catch(err => {
                showToast('Error exporting data', 'error');
                console.error('Error:', err);
            });
    };
    
    window.openChangePasswordModal = function() {
        const modal = document.createElement('div');
        modal.id = 'password-modal';
        modal.style.cssText = `
            position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 1000;
            display: flex; align-items: center; justify-content: center;
            backdrop-filter: blur(4px);
        `;
        
        const modalContent = document.createElement('div');
        modalContent.style.cssText = `
            background: var(--glass-bg);
            border: 1px solid var(--glass-border);
            border-radius: var(--radius-xl);
            padding: var(--sp-8);
            max-width: 450px; width: 90%;
            box-shadow: var(--shadow-xl);
        `;
        
        modalContent.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--sp-6);">
                <h2 style="font-size: 1.5rem; font-weight: 700;">Change Password</h2>
                <button onclick="document.getElementById('password-modal').remove()" style="background:none; border:none; cursor:pointer; font-size:1.5rem; color:var(--surface-500);">✕</button>
            </div>
            <div id="password-error" style="color: var(--danger-400); margin-bottom: var(--sp-4); display: none; padding: var(--sp-3); background: rgba(239,68,68,0.1); border-radius: var(--radius-lg); font-size: 0.85rem;"></div>
            <form onsubmit="submitPasswordChange(event)" style="display: flex; flex-direction: column; gap: var(--sp-4);">
                <div>
                    <label style="display: block; font-size: 0.85rem; color: var(--surface-400); margin-bottom: var(--sp-2); font-weight: 600;">Current Password</label>
                    <input type="password" id="current-password" placeholder="Enter current password" required
                        style="width: 100%; padding: var(--sp-3); border: 1px solid var(--glass-border); border-radius: var(--radius-lg); background: rgba(0,0,0,0.2); color: inherit; font-size: .95rem;">
                </div>
                <div>
                    <label style="display: block; font-size: 0.85rem; color: var(--surface-400); margin-bottom: var(--sp-2); font-weight: 600;">New Password</label>
                    <input type="password" id="new-password" placeholder="Enter new password" required
                        style="width: 100%; padding: var(--sp-3); border: 1px solid var(--glass-border); border-radius: var(--radius-lg); background: rgba(0,0,0,0.2); color: inherit; font-size: .95rem;">
                </div>
                <div>
                    <label style="display: block; font-size: 0.85rem; color: var(--surface-400); margin-bottom: var(--sp-2); font-weight: 600;">Confirm New Password</label>
                    <input type="password" id="confirm-password" placeholder="Confirm new password" required
                        style="width: 100%; padding: var(--sp-3); border: 1px solid var(--glass-border); border-radius: var(--radius-lg); background: rgba(0,0,0,0.2); color: inherit; font-size: .95rem;">
                </div>
                <div style="display: flex; gap: var(--sp-3); margin-top: var(--sp-2);">
                    <button type="button" onclick="document.getElementById('password-modal').remove()" class="fw-btn fw-btn-secondary" style="flex:1;">Cancel</button>
                    <button type="submit" class="fw-btn fw-btn-primary" style="flex:1;">Change Password</button>
                </div>
            </form>
        `;
        
        modal.appendChild(modalContent);
        document.body.appendChild(modal);
    };
    
    window.submitPasswordChange = function(e) {
        e.preventDefault();
        const errorDiv = document.getElementById('password-error');
        errorDiv.style.display = 'none';
        
        const current = document.getElementById('current-password').value;
        const newPass = document.getElementById('new-password').value;
        const confirm = document.getElementById('confirm-password').value;
        
        if (newPass.length < 6) {
            errorDiv.textContent = 'New password must be at least 6 characters';
            errorDiv.style.display = 'block';
            return;
        }
        
        if (newPass !== confirm) {
            errorDiv.textContent = 'Passwords do not match';
            errorDiv.style.display = 'block';
            return;
        }
        
        fetch('/account/change-password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ currentPassword: current, newPassword: newPass })
        })
        .then(res => res.json())
        .then(data => {
            if (data.success) {
                showToast('Password changed successfully', 'success');
                document.getElementById('password-modal').remove();
            } else {
                errorDiv.textContent = data.message || 'Error changing password';
                errorDiv.style.display = 'block';
            }
        })
        .catch(err => {
            errorDiv.textContent = 'Error changing password';
            errorDiv.style.display = 'block';
            console.error('Error:', err);
        });
    };
    
    let clearBrowserDataOnDelete = false;

    function clearFinwiseBrowserData() {
        [localStorage, sessionStorage].forEach(storage => {
            Object.keys(storage).forEach(key => {
                if (key.startsWith('finwise_') || key.startsWith('draft_')) {
                    storage.removeItem(key);
                }
            });
        });
    }

    window.openDeleteAccountModal = function(clearBrowserData) {
        clearBrowserDataOnDelete = clearBrowserData === true;
        const modal = document.createElement('div');
        modal.id = 'delete-modal';
        modal.style.cssText = `
            position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 1000;
            display: flex; align-items: center; justify-content: center;
            backdrop-filter: blur(4px);
        `;
        
        const modalContent = document.createElement('div');
        modalContent.style.cssText = `
            background: var(--glass-bg);
            border: 1px solid var(--glass-border);
            border-radius: var(--radius-xl);
            padding: var(--sp-8);
            max-width: 450px; width: 90%;
            box-shadow: var(--shadow-xl);
        `;
        
        modalContent.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--sp-6);">
                <h2 style="font-size: 1.5rem; font-weight: 700; color: var(--danger-400);">Delete Account</h2>
                <button onclick="document.getElementById('delete-modal').remove()" style="background:none; border:none; cursor:pointer; font-size:1.5rem; color:var(--surface-500);">✕</button>
            </div>
            <div style="background: rgba(239,68,68,0.1); border: 1px solid rgba(239,68,68,0.2); padding: var(--sp-4); border-radius: var(--radius-lg); margin-bottom: var(--sp-6);">
                <p style="margin: 0; font-size: 0.9rem; color: var(--surface-200);">⚠️ This action is permanent and cannot be undone. All your financial data will be permanently deleted.</p>
            </div>
            <div style="margin-bottom: var(--sp-6);">
                <label style="display: block; font-size: 0.85rem; color: var(--surface-400); margin-bottom: var(--sp-2); font-weight: 600;">Type "DELETE" to confirm</label>
                <input type="text" id="delete-confirm" placeholder="Type DELETE to confirm" 
                    style="width: 100%; padding: var(--sp-3); border: 1px solid var(--glass-border); border-radius: var(--radius-lg); background: rgba(0,0,0,0.2); color: inherit; font-size: .95rem;">
            </div>
            <div style="display: flex; gap: var(--sp-3);">
                <button onclick="document.getElementById('delete-modal').remove()" class="fw-btn fw-btn-secondary" style="flex:1;">Cancel</button>
                <button onclick="confirmDeleteAccount()" class="fw-btn fw-btn-danger" style="flex:1;">Delete Account</button>
            </div>
        `;
        
        modal.appendChild(modalContent);
        document.body.appendChild(modal);
        document.getElementById('delete-confirm').focus();
    };
    
    window.confirmDeleteAccount = function() {
        const confirmText = document.getElementById('delete-confirm').value.trim();
        
        if (confirmText !== 'DELETE') {
            showToast('Please type DELETE to confirm account deletion', 'error');
            return;
        }
        
        fetch('/account/delete-account', { method: 'POST' })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    if (clearBrowserDataOnDelete) {
                        try {
                            clearFinwiseBrowserData();
                        } catch (err) {
                            console.error('Error:', err);
                        }
                    }
                    showToast('Account deleted successfully. Redirecting...', 'success');
                    setTimeout(() => window.location.href = '/login', 2000);
                } else {
                    showToast(data.message || 'Error deleting account', 'error');
                }
            })
            .catch(err => {
                showToast('Error deleting account', 'error');
                console.error('Error:', err);
            });
    };
    
    // Setup account buttons on page load
    function setupAccountButtons() {
        // Profile edit buttons
        const profileButtons = document.querySelectorAll('.fw-main .fw-btn');
        profileButtons.forEach((btn, index) => {
            if (btn.dataset.profileField) {
                const rows = btn.closest('.fw-main').querySelectorAll('[style*="display: flex"]');
                const row = btn.parentElement;
                const fieldLabels = { fullName: 'Full Name', username: 'Username', email: 'Email' };
                const label = fieldLabels[btn.dataset.profileField];
                const value = row.querySelector('div:first-child > div:last-child')?.textContent;
                
                btn.onclick = () => {
                    if (label && value) {
                        openEditModal(label.trim(), value.trim(), btn.dataset.profileField);
                    }
                };
            }
        });
        
        // Account settings buttons
        document.querySelectorAll('.fw-btn').forEach(btn => {
            if (btn.textContent === 'Configure') {
                btn.onclick = () => openNotificationsModal();
            } else if (btn.textContent === 'Customize') {
                btn.onclick = () => openAppearanceModal();
            } else if (btn.textContent === 'Export') {
                btn.onclick = () => exportData();
            } else if (btn.textContent === 'Change') {
                btn.onclick = () => openChangePasswordModal();
            } else if (btn.textContent === 'Delete') {
                btn.onclick = () => openDeleteAccountModal();
            }
        });
    }
    
    // Setup on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', setupAccountButtons);
    } else {
        setupAccountButtons();
    }
    
    document.head.appendChild(style);
})();