'use strict';

/*
 * Small single-page front end for the REST API.
 * - Logs in with POST /api/auth/login and keeps the JWT in sessionStorage (cleared when the tab closes).
 * - Sends it as "Authorization: Bearer <token>" on every request.
 * - Admins get CRUD screens; teachers and students get their own profile and a password form.
 * All dynamic text is inserted with textContent, never innerHTML, so user data cannot inject markup.
 */

const TOKEN_KEY = 'ums.token';

const RESOURCES = {
    teachers: {
        title: 'Teachers', singular: 'Teacher', path: '/api/teachers',
        fields: [
            { name: 'username', label: 'Username' },
            { name: 'fullName', label: 'Full name' },
            { name: 'email', label: 'Email', type: 'email' },
            { name: 'employeeId', label: 'Employee ID' },
            { name: 'department', label: 'Department' },
            { name: 'designation', label: 'Designation' },
            { name: 'phone', label: 'Phone', optional: true },
        ],
    },
    students: {
        title: 'Students', singular: 'Student', path: '/api/students',
        fields: [
            { name: 'username', label: 'Username' },
            { name: 'fullName', label: 'Full name' },
            { name: 'email', label: 'Email', type: 'email' },
            { name: 'studentId', label: 'Student ID' },
            { name: 'department', label: 'Department' },
            { name: 'semester', label: 'Semester', type: 'number', min: 1, max: 12 },
            { name: 'phone', label: 'Phone', optional: true },
        ],
    },
    admins: {
        title: 'Admins', singular: 'Admin', path: '/api/admins',
        fields: [
            { name: 'username', label: 'Username' },
            { name: 'fullName', label: 'Full name' },
            { name: 'email', label: 'Email', type: 'email' },
        ],
    },
};

const state = { me: null, tab: null, rows: [], filter: '' };

// ------------------------------------------------------------------ helpers

const $ = (sel) => document.querySelector(sel);

function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(props)) {
        if (key === 'class') node.className = value;
        else if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
        else if (value !== undefined && value !== null && value !== false) node.setAttribute(key, value);
    }
    for (const child of children.flat()) {
        if (child === null || child === undefined) continue;
        node.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return node;
}

function toast(message) {
    const t = $('#toast');
    t.textContent = message;
    t.hidden = false;
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => { t.hidden = true; }, 2500);
}

function getToken() {
    try { return sessionStorage.getItem(TOKEN_KEY); } catch { return null; }
}
function setToken(token) {
    try { token ? sessionStorage.setItem(TOKEN_KEY, token) : sessionStorage.removeItem(TOKEN_KEY); } catch { /* ignore */ }
}

class ApiError extends Error {
    constructor(status, body) {
        super((body && body.message) || `Request failed (${status})`);
        this.status = status;
        this.fieldErrors = (body && body.fieldErrors) || {};
    }
}

async function api(method, path, body) {
    const headers = { Accept: 'application/json' };
    const token = getToken();
    if (token) headers.Authorization = `Bearer ${token}`;
    if (body !== undefined) headers['Content-Type'] = 'application/json';

    const res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;

    if (res.status === 401 && path !== '/api/auth/login') {
        // Token expired, revoked or user deleted: back to the login screen.
        setToken(null);
        showLogin('Your session has ended. Please sign in again.');
        throw new ApiError(401, data);
    }
    if (!res.ok) throw new ApiError(res.status, data);
    return data;
}

// ------------------------------------------------------------------ login / logout

function showLogin(message) {
    state.me = null;
    state.rows = [];
    // Drop the previous user's data from the page, not just hide it.
    if ($('#edit-dialog').open) $('#edit-dialog').close();
    $('#edit-fields').replaceChildren();
    $('#main').replaceChildren();
    $('#nav').replaceChildren();
    $('#app-view').hidden = true;
    $('#login-view').hidden = false;
    const err = $('#login-error');
    err.hidden = !message;
    err.textContent = message || '';
    $('#login-form [name=email]').focus();
}

$('#login-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const form = e.currentTarget;
    const button = form.querySelector('button[type=submit]');
    const err = $('#login-error');
    err.hidden = true;
    button.disabled = true;
    try {
        const result = await api('POST', '/api/auth/login', {
            email: form.email.value.trim(),
            password: form.password.value,
        });
        setToken(result.accessToken);
        form.reset();
        await start();
    } catch (ex) {
        err.textContent = ex.status === 400 ? 'Please enter a valid email address and your password.' : ex.message;
        err.hidden = false;
    } finally {
        button.disabled = false;
    }
});

$('#logout-btn').addEventListener('click', async () => {
    try { await api('POST', '/api/auth/logout'); } catch { /* token may already be invalid */ }
    setToken(null);
    showLogin();
    toast('Logged out');
});

// ------------------------------------------------------------------ app shell

async function start() {
    if (!getToken()) return showLogin();
    try {
        state.me = await api('GET', '/api/auth/me');
    } catch {
        return; // api() already redirected to login on 401
    }
    $('#login-view').hidden = true;
    $('#app-view').hidden = false;
    $('#who-name').textContent = state.me.fullName;
    $('#who-role').textContent = state.me.role;

    // Mirrors the server rules; the server still enforces them on every request.
    const TABS_BY_ROLE = {
        ADMIN: [['teachers', 'Teachers'], ['students', 'Students'], ['admins', 'Admins'], ['profile', 'My account']],
        TEACHER: [['teachers', 'Teachers'], ['profile', 'My account']],
        STUDENT: [['students', 'Students'], ['profile', 'My account']],
    };
    const tabs = TABS_BY_ROLE[state.me.role];
    const nav = $('#nav');
    nav.replaceChildren(...tabs.map(([key, label]) =>
        el('button', { type: 'button', 'data-tab': key, onclick: () => openTab(key) }, label)));
    openTab(tabs[0][0]);
}

function openTab(key) {
    state.tab = key;
    state.filter = '';
    document.querySelectorAll('#nav button').forEach((b) => b.classList.toggle('active', b.dataset.tab === key));
    if (key === 'profile') renderProfile();
    else loadResource(key);
}

// ------------------------------------------------------------------ admin CRUD screens

async function loadResource(key) {
    const res = RESOURCES[key];
    const main = $('#main');
    main.replaceChildren(el('p', { class: 'empty' }, 'Loading…'));
    try {
        state.rows = await api('GET', res.path);
    } catch (ex) {
        if (ex.status !== 401) main.replaceChildren(el('div', { class: 'alert error' }, ex.message));
        return;
    }
    renderResource(key);
}

function renderResource(key) {
    const res = RESOURCES[key];
    const search = el('input', {
        class: 'search', type: 'search', placeholder: `Search ${res.title.toLowerCase()}…`, value: state.filter,
        oninput: (e) => { state.filter = e.target.value; renderRows(key, tbody); },
    });
    const tbody = el('tbody');
    const table = el('table', { class: 'data' },
        el('thead', {}, el('tr', {}, el('th', {}, 'ID'), res.fields.map((f) => el('th', {}, f.label)), el('th'))),
        tbody);
    renderRows(key, tbody);

    $('#main').replaceChildren(
        el('div', { class: 'section-head' },
            el('h1', {}, res.title, ' ', el('span', { class: 'badge' }, String(state.rows.length))),
            el('div', { class: 'who' }, search,
                el('button', { class: 'btn primary', onclick: () => openEditor(key, null) }, `+ Add ${res.singular.toLowerCase()}`))),
        el('div', { class: 'card table-wrap', style: 'padding:0' }, table));
}

function renderRows(key, tbody) {
    const res = RESOURCES[key];
    const q = state.filter.trim().toLowerCase();
    const rows = state.rows.filter((r) => !q || res.fields.some((f) => String(r[f.name] ?? '').toLowerCase().includes(q)));
    if (!rows.length) {
        tbody.replaceChildren(el('tr', {}, el('td', { class: 'empty', colspan: res.fields.length + 2 },
            q ? 'No matches.' : `No ${res.title.toLowerCase()} yet.`)));
        return;
    }
    tbody.replaceChildren(...rows.map((row) => el('tr', {},
        el('td', {}, String(row.id)),
        res.fields.map((f) => el('td', {}, row[f.name] ?? '—')),
        el('td', { class: 'actions' },
            el('button', { class: 'btn ghost small', onclick: () => openEditor(key, row) }, 'Edit'), ' ',
            // The server refuses to let anyone delete their own account, so don't offer it.
            row.username === state.me.username
                ? el('span', { class: 'badge' }, 'You')
                : el('button', { class: 'btn danger small', onclick: () => remove(key, row) }, 'Delete')))));
}

async function remove(key, row) {
    const res = RESOURCES[key];
    if (!confirm(`Delete ${res.singular.toLowerCase()} "${row.fullName}" (${row.username})? This cannot be undone.`)) return;
    try {
        await api('DELETE', `${res.path}/${row.id}`);
        toast(`${res.singular} deleted`);
        loadResource(key);
    } catch (ex) {
        if (ex.status !== 401) alert(ex.message);
    }
}

// ------------------------------------------------------------------ create / edit dialog

const dialog = $('#edit-dialog');
$('#edit-cancel').addEventListener('click', () => dialog.close());

function openEditor(key, row) {
    const res = RESOURCES[key];
    const isNew = row === null;
    $('#edit-title').textContent = isNew ? `Add ${res.singular.toLowerCase()}` : `Edit ${res.singular.toLowerCase()} #${row.id}`;
    $('#edit-error').hidden = true;

    const fields = [...res.fields];
    fields.splice(1, 0, {
        name: 'password', label: 'Password', type: 'password', optional: !isNew,
        hint: isNew ? '8–72 characters' : 'leave blank to keep current',
    });
    $('#edit-fields').replaceChildren(...fields.map((f) => el('label', {},
        el('span', {}, f.label, f.optional ? el('span', { class: 'hint' }, ` (${f.hint || 'optional'})`) : null),
        el('input', {
            name: f.name, type: f.type || 'text', min: f.min, max: f.max,
            required: !f.optional, autocomplete: f.type === 'password' ? 'new-password' : 'off',
            value: !isNew && f.name !== 'password' ? (row[f.name] ?? '') : '',
        }),
        el('span', { class: 'field-error', 'data-error-for': f.name }))));

    const form = $('#edit-form');
    form.onsubmit = async (e) => {
        e.preventDefault();
        const body = {};
        for (const f of fields) {
            const value = form.elements[f.name].value;
            if (f.type === 'number') body[f.name] = value === '' ? null : Number(value);
            else if (f.name === 'password' && !isNew && value === '') continue;
            else body[f.name] = f.name === 'password' ? value : value.trim();
        }
        form.querySelectorAll('.field-error').forEach((s) => { s.textContent = ''; });
        $('#edit-error').hidden = true;
        const saveBtn = form.querySelector('button[type=submit]');
        saveBtn.disabled = true;
        try {
            if (isNew) await api('POST', res.path, body);
            else await api('PUT', `${res.path}/${row.id}`, body);
            dialog.close();
            if (!isNew && row.username === state.me.username && (body.password || body.username !== row.username)) {
                // Changing your own password or username invalidates your current token.
                setToken(null);
                showLogin('Your login details changed. Please sign in again.');
                return;
            }
            toast(`${res.singular} ${isNew ? 'created' : 'updated'}`);
            loadResource(key);
        } catch (ex) {
            if (ex.status === 401) { dialog.close(); return; }
            for (const [name, msg] of Object.entries(ex.fieldErrors)) {
                const slot = form.querySelector(`[data-error-for="${CSS.escape(name)}"]`);
                if (slot) slot.textContent = msg;
            }
            $('#edit-error').textContent = ex.message;
            $('#edit-error').hidden = false;
        } finally {
            saveBtn.disabled = false;
        }
    };
    dialog.showModal();
    form.elements[fields[0].name].focus();
}

// ------------------------------------------------------------------ own profile + password

const PROFILE_LABELS = {
    id: 'ID', username: 'Username', fullName: 'Full name', email: 'Email', role: 'Role',
    employeeId: 'Employee ID', department: 'Department', designation: 'Designation',
    studentId: 'Student ID', semester: 'Semester', phone: 'Phone', createdAt: 'Member since',
};

function renderProfile() {
    const me = state.me;
    const items = Object.entries(PROFILE_LABELS)
        .filter(([key]) => key in me)
        .flatMap(([key, label]) => [
            el('dt', {}, label),
            el('dd', {}, key === 'createdAt' ? new Date(me[key]).toLocaleDateString() : (me[key] ?? '—')),
        ]);

    const pwForm = el('form', { class: 'stack', novalidate: true },
        el('label', {}, 'Current password', el('input', { name: 'currentPassword', type: 'password', required: true, autocomplete: 'current-password' })),
        el('label', {}, 'New password', el('input', { name: 'newPassword', type: 'password', required: true, autocomplete: 'new-password', minlength: 8 })),
        el('div', { class: 'alert error', hidden: true }),
        el('button', { class: 'btn primary', type: 'submit' }, 'Change password'));
    pwForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const err = pwForm.querySelector('.alert');
        err.hidden = true;
        try {
            await api('PUT', '/api/auth/password', {
                currentPassword: pwForm.currentPassword.value,
                newPassword: pwForm.newPassword.value,
            });
            setToken(null);
            showLogin();
            toast('Password changed — please sign in with your new password');
        } catch (ex) {
            if (ex.status === 401) return;
            const fieldMsg = Object.values(ex.fieldErrors)[0];
            err.textContent = fieldMsg ? `${ex.message}: new password ${fieldMsg}` : ex.message;
            err.hidden = false;
        }
    });

    const NOTES = {
        ADMIN: 'As an admin you can manage admins, teachers and students.',
        TEACHER: 'As a teacher you can manage teacher records only. Student and admin records are managed by others.',
        STUDENT: 'As a student you can manage student records only. Teacher and admin records are managed by others.',
    };
    const note = el('div', { class: 'alert info' }, NOTES[me.role]);

    $('#main').replaceChildren(
        el('div', { class: 'section-head' }, el('h1', {}, 'My account')),
        el('div', { class: 'stack' }, note,
            el('div', { class: 'profile' },
                el('div', { class: 'card' }, el('h2', {}, 'Profile'), el('dl', { class: 'kv' }, items)),
                el('div', { class: 'card' }, el('h2', {}, 'Change password'), pwForm))));
}

start();
