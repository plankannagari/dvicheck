// dvicheck admin dashboard (Day 38). Plain JS, same-origin only, no external requests.
// Rules: server text goes into the DOM via textContent only (never innerHTML); the token
// lives in sessionStorage; nothing — token or phone — is ever logged to the console.
'use strict';

(function () {
  const TOKEN_KEY = 'dvi_admin_token';
  const GUARD_MESSAGE = 'Not found'; // AdminGuard's 404 message (non-admin)

  // ---------- small helpers ----------

  const $ = (id) => document.getElementById(id);

  function el(tag, text, className) {
    const node = document.createElement(tag);
    if (text !== undefined && text !== null) node.textContent = String(text);
    if (className) node.className = className;
    return node;
  }

  function clear(node) {
    while (node.firstChild) node.removeChild(node.firstChild);
  }

  function getToken() {
    try { return sessionStorage.getItem(TOKEN_KEY); } catch (e) { return null; }
  }
  function setToken(t) {
    try { sessionStorage.setItem(TOKEN_KEY, t); } catch (e) { /* private mode: session-only in memory */ }
  }
  function dropToken() {
    try { sessionStorage.removeItem(TOKEN_KEY); } catch (e) { /* ignore */ }
  }

  function fmtDateTime(iso) {
    if (!iso) return '—';
    const d = new Date(iso);
    return isNaN(d) ? String(iso) : d.toLocaleString();
  }
  function fmtDate(iso) {
    if (!iso) return '—';
    // Dates from the server are UTC calendar days (yyyy-mm-dd); show them as-is
    return String(iso);
  }

  function setMsg(node, text, kind) {
    node.textContent = text || '';
    node.className = 'msg' + (kind ? ' ' + kind : '');
  }

  function showBanner(text) {
    const b = $('banner');
    b.textContent = text || '';
    b.hidden = !text;
  }

  // ---------- API ----------

  class ApiError extends Error {
    constructor(status, message) { super(message); this.status = status; }
  }

  async function readJson(res) {
    try { return await res.json(); } catch (e) { return null; }
  }

  // Calls an authenticated endpoint. 401 -> session expired; guard 404 -> not-an-admin view;
  // anything else -> throws ApiError with the server's message for the caller to show.
  async function api(path, options) {
    const opts = options || {};
    const headers = { 'Accept': 'application/json' };
    const token = getToken();
    if (token) headers['Authorization'] = 'Bearer ' + token;
    if (opts.body !== undefined) headers['Content-Type'] = 'application/json';

    let res;
    try {
      res = await fetch(path, {
        method: opts.method || 'GET',
        headers,
        body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
        credentials: 'same-origin',
        cache: 'no-store',
      });
    } catch (e) {
      throw new ApiError(0, "Can't reach the server.");
    }
    const json = await readJson(res);

    if (res.status === 401) {
      signOut('Session expired - please sign in again.');
      throw new ApiError(401, 'Session expired');
    }
    if (res.status === 404 && json && json.message === GUARD_MESSAGE) {
      showView('denied');
      throw new ApiError(404, 'Not available');
    }
    if (!res.ok || !json || json.success === false) {
      const message = (json && json.message) || ('Request failed (' + res.status + ')');
      throw new ApiError(res.status, message);
    }
    return json.data; // ApiResponse { success, message, data }
  }

  // ---------- views ----------

  const VIEWS = ['login', 'denied', 'overview', 'users', 'audit'];

  function showView(name) {
    VIEWS.forEach((v) => { $('view-' + v).hidden = v !== name; });
    const signedIn = !!getToken();
    const inApp = name === 'overview' || name === 'users' || name === 'audit';
    $('tabs').hidden = !inApp;
    $('signout').hidden = !signedIn;
    document.querySelectorAll('.tab').forEach((t) => {
      t.classList.toggle('active', t.dataset.tab === name);
    });
  }

  function openTab(name) {
    showBanner('');
    showView(name);
    if (name === 'overview') loadOverview();
    if (name === 'audit') loadAudit();
  }

  function signOut(message) {
    dropToken();
    resetLogin();
    showView('login');
    showBanner(message || '');
  }

  // ---------- login ----------

  let loginPhone = '';

  function resetLogin() {
    loginPhone = '';
    $('form-phone').hidden = false;
    $('form-code').hidden = true;
    $('login-code').value = '';
    setMsg($('login-msg'), '');
  }

  async function publicPost(path, body) {
    let res;
    try {
      res = await fetch(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
        body: JSON.stringify(body),
        credentials: 'same-origin',
        cache: 'no-store',
      });
    } catch (e) {
      throw new ApiError(0, "Can't reach the server.");
    }
    const json = await readJson(res);
    if (!res.ok || !json || json.success === false) {
      throw new ApiError(res.status, (json && json.message) || ('Request failed (' + res.status + ')'));
    }
    return json;
  }

  async function onSendCode(e) {
    e.preventDefault();
    const phone = $('login-phone').value.trim();
    if (!phone) return;
    setMsg($('login-msg'), 'Sending code...');
    try {
      await publicPost('/api/auth/send-otp', { phone });
      loginPhone = phone;
      $('form-phone').hidden = true;
      $('form-code').hidden = false;
      $('login-code').focus();
      setMsg($('login-msg'), 'Code sent.', 'ok');
    } catch (err) {
      setMsg($('login-msg'), err.message, 'error');
    }
  }

  async function onSignIn(e) {
    e.preventDefault();
    const otp = $('login-code').value.trim();
    if (!otp || !loginPhone) return;
    setMsg($('login-msg'), 'Signing in...');
    try {
      const json = await publicPost('/api/auth/verify-otp', { phone: loginPhone, otp });
      const token = json.data && json.data.accessToken;
      if (!token) throw new ApiError(0, 'No token in the response.');
      setToken(token);
      $('login-phone').value = '';
      resetLogin();
      openTab('overview');
    } catch (err) {
      setMsg($('login-msg'), err.message, 'error');
    }
  }

  // ---------- overview ----------

  function card(label, value, sub) {
    const c = el('div', null, 'stat');
    c.appendChild(el('div', label, 'stat-label'));
    c.appendChild(el('div', value, 'stat-value'));
    if (sub) c.appendChild(el('div', sub, 'stat-sub'));
    return c;
  }

  function barChart(container, days) {
    clear(container);
    const list = Array.isArray(days) ? days : [];
    const max = Math.max(1, ...list.map((d) => Number(d.count) || 0));
    list.forEach((d) => {
      const count = Number(d.count) || 0;
      const col = el('div', null, 'bar-col');
      col.title = d.date + ': ' + count;
      const bar = el('div', null, 'bar' + (count === 0 ? ' zero' : ''));
      // CSSOM style assignment is allowed under style-src 'self' (only style="" attributes aren't)
      bar.style.height = Math.max(2, Math.round((count / max) * 100)) + '%';
      col.appendChild(el('div', count ? String(count) : '', 'bar-value'));
      col.appendChild(bar);
      col.appendChild(el('div', String(d.date).slice(5), 'bar-label')); // mm-dd
      container.appendChild(col);
    });
  }

  async function loadOverview() {
    $('generated-at').textContent = 'Loading...';
    let o;
    try {
      o = await api('/api/admin/overview');
    } catch (err) {
      if (err.status !== 401 && err.status !== 404) {
        $('generated-at').textContent = '';
        showBanner(err.message);
      }
      return;
    }
    const cards = $('cards');
    clear(cards);
    cards.appendChild(card('Total users', o.users.total,
      o.users.onboardingCompletedPercent + '% onboarded'));
    cards.appendChild(card('New users', o.users.new7d + ' / ' + o.users.new30d, 'last 7 / 30 days'));
    cards.appendChild(card('Active scanners', o.activity.activeScanners7d + ' / ' + o.activity.activeScanners30d,
      'last 7 / 30 days'));
    cards.appendChild(card('Scans this month', o.activity.scansThisMonth, 'UTC calendar month'));
    cards.appendChild(card('Pro', o.pro.total, o.pro.paid + ' paid · ' + o.pro.bonusOnly + ' bonus only'));
    cards.appendChild(card('Referrals', o.referrals.pending + ' / ' + o.referrals.qualified, 'pending / qualified'));
    cards.appendChild(card('Push reachable', o.users.withPushToken,
      o.users.notificationsEnabled + ' with notifications on'));
    const alertTotal = (o.alerts.sentLast7d || []).reduce((s, a) => s + (Number(a.count) || 0), 0);
    cards.appendChild(card('Budget alerts', alertTotal, 'logged in the last 7 days'));

    barChart($('chart-signups'), o.activity.signupsPerDay);
    barChart($('chart-scans'), o.activity.scansPerDay);

    const rows = $('coach-rows');
    clear(rows);
    (o.coach.weeks || []).forEach((w) => {
      const ai = Number(w.aiCount) || 0;
      const fb = Number(w.fallbackCount) || 0;
      const total = ai + fb;
      const pct = total === 0 ? null : Math.round((fb / total) * 1000) / 10;
      const tr = el('tr');
      tr.appendChild(el('td', fmtDate(w.weekStart)));
      tr.appendChild(el('td', ai));
      tr.appendChild(el('td', fb));
      tr.appendChild(el('td', pct === null ? '—' : pct + '%', pct !== null && pct > 30 ? 'warn' : ''));
      rows.appendChild(tr);
    });
    $('coach-latest').textContent = 'Latest coach week: ' + fmtDate(o.coach.latestWeekStart);
    $('generated-at').textContent = 'Generated at ' + fmtDateTime(o.generatedAt) + ' (cached up to 60 s)';
  }

  // ---------- users ----------

  let currentUser = null;     // last lookup result
  let currentPhone = '';      // what was typed, kept in memory only for re-lookup

  function field(dl, label, value) {
    dl.appendChild(el('dt', label));
    dl.appendChild(el('dd', value === null || value === undefined || value === '' ? '—' : value));
  }

  function renderUser(u) {
    const dl = $('user-fields');
    clear(dl);
    field(dl, 'User id', u.id);
    field(dl, 'Phone', '••••' + (u.phoneLast4 || ''));
    field(dl, 'Created', fmtDateTime(u.createdAt));
    field(dl, 'Onboarding done', u.onboardingCompleted ? 'yes' : 'no');
    field(dl, 'Pro', u.proActive ? 'active (' + u.proSource + ')' : 'no');
    field(dl, 'Paid until', fmtDateTime(u.paidUntil));
    field(dl, 'Bonus until', fmtDateTime(u.bonusUntil));
    field(dl, 'Bills', u.billCount);
    field(dl, 'Last scan', fmtDateTime(u.lastScanAt));
    field(dl, 'Push', u.pushEnabled ? 'on' : 'off');
    field(dl, 'Referral (as invitee)', u.referralStatus);
    field(dl, 'Qualified referrals', u.qualifiedReferrals);
    $('user-panel').hidden = false;
  }

  async function lookup(phone) {
    const data = await api('/api/admin/users/lookup?phone=' + encodeURIComponent(phone));
    currentUser = data;
    renderUser(data);
    return data;
  }

  async function onLookup(e) {
    e.preventDefault();
    const phone = $('lookup-phone').value.trim();
    if (!phone) return;
    setMsg($('lookup-msg'), 'Looking up...');
    setMsg($('action-msg'), '');
    $('user-panel').hidden = true;
    currentUser = null;
    try {
      await lookup(phone);
      currentPhone = phone;
      setMsg($('lookup-msg'), '');
    } catch (err) {
      if (err.status !== 401 && !(err.status === 404 && err.message === 'Not available')) {
        setMsg($('lookup-msg'), err.message, 'error');
      }
    }
  }

  async function runAction(label, path, body) {
    if (!currentUser) return;
    setMsg($('action-msg'), label + '...');
    try {
      const data = await api(path, { method: 'POST', body });
      await lookup(currentPhone); // re-run so every field reflects the change
      return data;
    } catch (err) {
      if (err.status !== 401) setMsg($('action-msg'), label + ' failed: ' + err.message, 'error');
      return undefined;
    }
  }

  async function onGrant(e) {
    e.preventDefault();
    const days = parseInt($('grant-days').value, 10);
    const reason = $('grant-reason').value.trim();
    if (!(days >= 1 && days <= 90)) { setMsg($('action-msg'), 'Days must be 1-90.', 'error'); return; }
    if (reason.length < 3) { setMsg($('action-msg'), 'A reason (3+ characters) is required.', 'error'); return; }
    if (!window.confirm('Grant ' + days + ' bonus Pro days to user ' + currentUser.id.slice(0, 8) + '?')) return;
    const ok = await runAction('Grant', '/api/admin/users/' + encodeURIComponent(currentUser.id) + '/grant-pro',
      { days, reason });
    if (ok !== undefined) {
      setMsg($('action-msg'), 'Granted ' + days + ' days. Bonus Pro until ' + fmtDateTime(currentUser.bonusUntil) + '.', 'ok');
      $('grant-days').value = '';
      $('grant-reason').value = '';
    }
  }

  async function onRevoke(e) {
    e.preventDefault();
    const reason = $('revoke-reason').value.trim();
    if (reason.length < 3) { setMsg($('action-msg'), 'A reason (3+ characters) is required.', 'error'); return; }
    if (!window.confirm('Revoke ALL bonus Pro (referral and granted days) for user ' + currentUser.id.slice(0, 8) + '?')) return;
    const ok = await runAction('Revoke', '/api/admin/users/' + encodeURIComponent(currentUser.id) + '/revoke-bonus-pro',
      { reason });
    if (ok !== undefined) {
      setMsg($('action-msg'), 'Bonus Pro revoked.', 'ok');
      $('revoke-reason').value = '';
    }
  }

  async function onRegenerate() {
    if (!currentUser) return;
    if (!window.confirm("Regenerate last week's coach message for user " + currentUser.id.slice(0, 8)
        + '? The existing note is replaced and the user may get a push.')) return;
    const data = await runAction('Regenerate',
      '/api/admin/users/' + encodeURIComponent(currentUser.id) + '/coach/regenerate');
    if (data !== undefined) {
      setMsg($('action-msg'), 'Coach regenerate: ' + (data && data.result ? data.result : 'done') + '.', 'ok');
    }
  }

  // ---------- audit ----------

  async function loadAudit() {
    const rows = $('audit-rows');
    clear(rows);
    let list;
    try {
      list = await api('/api/admin/audit');
    } catch (err) {
      if (err.status !== 401 && err.status !== 404) showBanner(err.message);
      return;
    }
    (list || []).forEach((a) => {
      const tr = el('tr');
      tr.appendChild(el('td', fmtDateTime(a.createdAt)));
      tr.appendChild(el('td', a.action));
      tr.appendChild(el('td', a.targetUserId ? String(a.targetUserId).slice(0, 8) : '—', 'mono'));
      tr.appendChild(el('td', a.details || ''));
      rows.appendChild(tr);
    });
    if (!list || list.length === 0) {
      const tr = el('tr');
      const td = el('td', 'No admin actions yet.', 'muted');
      td.colSpan = 4;
      tr.appendChild(td);
      rows.appendChild(tr);
    }
  }

  // ---------- wiring ----------

  function init() {
    $('form-phone').addEventListener('submit', onSendCode);
    $('form-code').addEventListener('submit', onSignIn);
    $('change-phone').addEventListener('click', resetLogin);
    $('signout').addEventListener('click', () => signOut(''));
    $('denied-signout').addEventListener('click', () => signOut(''));
    document.querySelectorAll('.tab').forEach((t) => {
      t.addEventListener('click', () => openTab(t.dataset.tab));
    });
    $('refresh').addEventListener('click', loadOverview);
    $('audit-refresh').addEventListener('click', loadAudit);
    $('form-lookup').addEventListener('submit', onLookup);
    $('form-grant').addEventListener('submit', onGrant);
    $('form-revoke').addEventListener('submit', onRevoke);
    $('regenerate').addEventListener('click', onRegenerate);

    if (getToken()) openTab('overview');
    else { resetLogin(); showView('login'); }
  }

  init();
})();
