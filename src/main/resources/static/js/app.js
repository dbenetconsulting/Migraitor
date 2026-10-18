/* Migraitor UI. Vanilla JS, no build step. All server text is inserted with textContent (no innerHTML). */
(function () {
    'use strict';

    // ------------------------------------------------------------------ helpers
    const $ = (sel, root) => (root || document).querySelector(sel);
    const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));

    function h(tag, attrs, ...children) {
        const el = document.createElement(tag);
        for (const [k, v] of Object.entries(attrs || {})) {
            if (v === null || v === undefined || v === false) continue;
            if (k === 'class') el.className = v;
            else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
            else if (k === 'dataset') Object.assign(el.dataset, v);
            else el.setAttribute(k, v === true ? '' : v);
        }
        for (const c of children.flat()) {
            if (c === null || c === undefined || c === false) continue;
            el.append(c instanceof Node ? c : document.createTextNode(String(c)));
        }
        return el;
    }

    function csrf() {
        const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
        return m ? decodeURIComponent(m[1]) : '';
    }

    async function api(path, options) {
        const opts = Object.assign({ method: 'GET', headers: {} }, options || {});
        if (opts.json !== undefined) {
            opts.body = JSON.stringify(opts.json);
            opts.headers['Content-Type'] = 'application/json';
            delete opts.json;
        }
        if (opts.method !== 'GET') opts.headers['X-XSRF-TOKEN'] = csrf();
        let res;
        try {
            res = await fetch(path, Object.assign(opts, { credentials: 'same-origin' }));
        } catch (e) {
            throw new Error('The server is not reachable');
        }
        if (res.status === 401) { location.href = 'login'; throw new Error('Session expired'); }
        const type = res.headers.get('Content-Type') || '';
        const body = type.includes('json') ? await res.json() : await res.text();
        if (!res.ok) throw new Error((body && body.error) || ('HTTP ' + res.status));
        return { body, res };
    }
    const get = async p => (await api(p)).body;
    const send = async (method, p, json) => (await api(p, { method, json })).body;

    function toast(msg, kind) {
        const t = h('div', { class: 'toast ' + (kind || '') }, msg);
        $('#toasts').append(t);
        setTimeout(() => t.remove(), kind === 'err' ? 8000 : 4000);
    }

    /** Disables the button while the action runs and reports errors as toasts. */
    async function busy(btn, fn) {
        if (btn) btn.disabled = true;
        try { return await fn(); }
        catch (e) { toast(e.message, 'err'); }
        finally { if (btn) btn.disabled = false; }
    }

    const fmtDate = v => v ? String(v).replace('T', ' ').slice(0, 19) : '';
    const fmtNum = v => (v === null || v === undefined) ? '' : Number(v).toLocaleString('en-US');

    function badgeClass(s) {
        s = String(s || '').toUpperCase();
        if (['MIGRATED', 'FINALIZED', 'DONE', 'MATCHED', 'SUCCESS'].includes(s)) return 'ok';
        if (s.includes('WARNING') || ['ABORTED', 'INTERRUPTED', 'ALREADY_STAGED', 'NO_FOLDER', 'NO_EMPLOYEE', 'AMBIGUOUS_FOLDER'].includes(s)) return 'warn';
        if (s.startsWith('ERROR') || s === 'INVALID') return 'err';
        if (['INITIATED', 'RETRYING', 'RUNNING', 'IN_PROGRESS'].includes(s)) return 'info';
        return '';
    }
    const badge = s => h('span', { class: 'badge ' + badgeClass(s) }, s || '—');

    function table(columns, rows, opts) {
        opts = opts || {};
        if (!rows || rows.length === 0) return h('div', { class: 'empty' }, opts.empty || 'Nothing to show.');
        return h('div', { class: 'table-wrap' }, h('table', {},
            h('thead', {}, h('tr', {}, columns.map(c => h('th', { class: c.num ? 'num' : '' }, c.label)))),
            h('tbody', {}, rows.map(r => {
                const tr = h('tr', { class: opts.onRow ? 'clickable' : '' },
                    columns.map(c => h('td', { class: (c.num ? 'num ' : '') + (c.cls || '') }, c.render ? c.render(r) : (r[c.key] ?? ''))));
                if (opts.onRow) tr.addEventListener('click', () => opts.onRow(r, tr));
                return tr;
            }))));
    }

    function progress(done, total) {
        const pct = total ? Math.min(100, Math.round(100 * (done || 0) / total)) : 0;
        return h('div', { class: 'row' }, h('div', { class: 'progress' }, h('span', { style: 'width:' + pct + '%' })),
            h('span', { class: 'muted' }, fmtNum(done || 0) + ' / ' + fmtNum(total || 0)));
    }

    // ------------------------------------------------------------------ navigation
    const loaders = {};
    let current = null;
    let timer = null;

    function show(tab) {
        if (!document.getElementById('tab-' + tab)) tab = 'home';
        current = tab;
        $$('.tab').forEach(s => s.hidden = s.id !== 'tab-' + tab);
        $$('#nav a').forEach(a => a.classList.toggle('active', a.dataset.tab === tab));
        clearInterval(timer);
        const load = loaders[tab];
        if (load) {
            load();
            if (load.refresh) timer = setInterval(() => { if (!document.hidden) load(true); }, 5000);
        }
    }
    window.addEventListener('hashchange', () => show(location.hash.slice(1)));

    // ------------------------------------------------------------------ types
    let types = [];
    async function loadTypes() {
        types = await get('api/types');
        $$('.type-select').forEach(sel => {
            const list = sel.dataset.filter === 'import' ? types.filter(t => t.supportsImport) : types;
            sel.replaceChildren(...list.map(t => h('option', { value: t.code }, t.name + ' (' + t.code + ')')));
        });
    }

    // ------------------------------------------------------------------ home
    const STATUS_COLORS = { ok: '#1f9d55', warn: '#d9a21b', err: '#d23c3c', info: '#3b82c4', '': '#9aa3b5' };
    loaders.home = async function () {
        try {
            const s = await get('api/summary');
            const docs = s.documents || {};
            const total = Object.values(docs).reduce((a, b) => a + b, 0);
            const sum = pred => Object.entries(docs).filter(([k]) => pred(k)).reduce((a, [, v]) => a + v, 0);
            const migrated = sum(k => k.startsWith('MIGRATED'));
            const errors = sum(k => k.startsWith('ERROR'));
            const pending = sum(k => k === 'PENDING');
            $('#home-cards').replaceChildren(
                card('Documents', total, ''), card('Migrated', migrated, 'ok'), card('Pending', pending, 'info'),
                card('With errors', errors, 'err'), card('Running jobs', (s.activeJobs || []).length, 'info'),
                card('Scheduler', s.schedulerActive ? 'On' : 'Off', s.schedulerActive ? 'ok' : ''));
            const max = Math.max(1, ...Object.values(docs));
            $('#home-status').replaceChildren(...(Object.keys(docs).length ? Object.entries(docs).sort((a, b) => b[1] - a[1]).map(([k, v]) =>
                h('div', { class: 'status-bar' }, badge(k),
                    h('div', { class: 'bar' }, h('span', { style: `width:${100 * v / max}%;background:${STATUS_COLORS[badgeClass(k)]}` })),
                    h('span', { class: 'num' }, fmtNum(v)))) : [h('div', { class: 'empty' }, 'No documents staged yet.')]));
            $('#home-active').replaceChildren(table([
                { label: 'Job', render: j => '#' + j.jobId + ' ' + j.jobName },
                { label: 'Type', key: 'migType' },
                { label: 'Status', render: j => badge(j.status) },
                { label: 'Progress', render: j => progress(j.processedItems, j.totalItems) },
                { label: 'Started', render: j => fmtDate(j.startDate) }
            ], s.activeJobs, { empty: 'No job is running.', onRow: j => { location.hash = 'jobs'; setTimeout(() => openJob(j.jobId), 50); } }));
            $('#home-updated').textContent = 'Updated ' + new Date().toLocaleTimeString();
        } catch (e) { toast(e.message, 'err'); }
    };
    loaders.home.refresh = true;
    const card = (l, v, cls) => h('div', { class: 'card ' + cls }, h('div', { class: 'l' }, l), h('div', { class: 'v' }, typeof v === 'number' ? fmtNum(v) : v));

    // ------------------------------------------------------------------ connections
    loaders.connections = async function () {
        const type = $('#conn-type').value;
        if (!type) { $('#conn-forms').replaceChildren(h('div', { class: 'empty' }, 'No migration type needs connections here.')); return; }
        try {
            const data = await get('api/connections/' + encodeURIComponent(type));
            $('#conn-forms').replaceChildren(connForm(type, 'SOURCE', data.SOURCE), connForm(type, 'TARGET', data.TARGET));
        } catch (e) { toast(e.message, 'err'); }
    };
    $('#conn-type').addEventListener('change', () => loaders.connections());

    function connForm(type, role, c) {
        c = c || {};
        const f = h('form', { class: 'form' },
            h('label', {}, 'Base URL (up to cs.exe / cs)', h('input', { name: 'baseUrl', required: true, value: c.baseUrl || '', placeholder: role === 'SOURCE' ? 'https://cs-onprem.client.local/OTCS/cs.exe' : 'https://client.opentext.cloud/cs/cs' })),
            h('div', { class: 'form-row' },
                h('label', {}, 'Edition', h('select', { name: 'edition' },
                    h('option', { value: 'ONPREM', selected: c.edition === 'ONPREM' || (!c.edition && role === 'SOURCE') }, 'On-premise (REST v1)'),
                    h('option', { value: 'CLOUD', selected: c.edition === 'CLOUD' || (!c.edition && role === 'TARGET') }, 'Cloud (REST v2)'))),
                h('label', {}, 'WebReport id', h('input', { name: 'liveReportId', type: 'number', min: 1, value: c.liveReportId || '', placeholder: 'DataID' }))),
            h('div', { class: 'form-row' },
                h('label', {}, 'User', h('input', { name: 'username', required: true, value: c.username || '', autocomplete: 'off' })),
                h('label', {}, 'Password', h('input', { name: 'password', type: 'password', autocomplete: 'new-password', placeholder: c.passwordSet ? '•••••• (stored, leave empty to keep)' : '' }))),
            h('label', { class: 'check' }, h('input', { type: 'checkbox', name: 'useProxy', checked: !!c.useProxy }), 'Use the proxy from the secrets file'),
            h('div', { class: 'row' },
                h('button', { class: 'btn btn-primary', type: 'submit' }, 'Save'),
                h('button', { class: 'btn btn-ghost', type: 'button', 'data-test': '1' }, 'Test connection'),
                c.updatedAt ? h('span', { class: 'muted' }, 'Saved ' + fmtDate(c.updatedAt)) : null),
            h('div', { class: 'conn-result' }));
        const values = () => ({
            baseUrl: f.baseUrl.value.trim(), edition: f.edition.value, username: f.username.value.trim(),
            password: f.password.value, useProxy: f.useProxy.checked,
            liveReportId: f.liveReportId.value ? Number(f.liveReportId.value) : null
        });
        const url = 'api/connections/' + encodeURIComponent(type) + '/' + role;
        f.addEventListener('submit', ev => {
            ev.preventDefault();
            busy(ev.submitter, async () => { await send('PUT', url, values()); toast(role + ' connection saved', 'ok'); loaders.connections(); });
        });
        $('[data-test]', f).addEventListener('click', ev => busy(ev.target, async () => {
            const box = $('.conn-result', f);
            box.replaceChildren();
            try {
                const r = await send('POST', url + '/test', values());
                const params = r.reportParameters ? JSON.stringify(r.reportParameters).slice(0, 400) : null;
                box.replaceChildren(h('div', { class: 'alert ' + (r.ok ? 'alert-ok' : 'alert-error') }, r.message, params ? h('div', { class: 'muted' }, 'WebReport parameters: ' + params) : null));
            } catch (e) { box.replaceChildren(h('div', { class: 'alert alert-error' }, e.message)); }
        }));
        return h('div', { class: 'panel' }, h('h2', {}, role === 'SOURCE' ? 'Source Content Server' : 'Target Content Server'), f);
    }

    // ------------------------------------------------------------------ import
    let importRuns = [];
    const impBase = () => 'api/import/' + encodeURIComponent($('#imp-type').value);

    loaders.import = async function (quiet) {
        if (!$('#imp-type').value) return;
        try {
            importRuns = await get(impBase() + '/runs');
            $('#imp-runs').replaceChildren(table([
                { label: 'Run', render: r => '#' + r.runId },
                { label: 'Side', key: 'role' },
                { label: 'Origin', key: 'origin' },
                { label: 'Status', render: r => badge(r.status) },
                { label: 'Rows', num: true, render: r => fmtNum(r.rowCount) },
                { label: 'Started', render: r => fmtDate(r.startedAt) },
                { label: 'By', key: 'userName' },
                { label: 'Message', cls: 'msg', key: 'message' }
            ], importRuns, { empty: 'No export yet.', onRow: r => { if (r.role === 'SOURCE') showUnmatched(r.runId); } }));
            fillRunSelect('#match-source', 'SOURCE');
            fillRunSelect('#match-target', 'TARGET');
        } catch (e) { if (!quiet) toast(e.message, 'err'); }
    };
    loaders.import.refresh = true;

    function fillRunSelect(sel, role) {
        const el = $(sel);
        const prev = el.value;
        const runs = importRuns.filter(r => r.role === role && (r.status === 'DONE' || r.status === 'MATCHED'));
        el.replaceChildren(...(runs.length ? runs.map(r => h('option', { value: r.runId }, '#' + r.runId + ' · ' + fmtNum(r.rowCount) + ' rows · ' + fmtDate(r.startedAt))) : [h('option', { value: '' }, 'No finished run')]));
        if (prev && runs.some(r => String(r.runId) === prev)) el.value = prev;
    }

    $$('[data-import-report]').forEach(b => b.addEventListener('click', () => busy(b, async () => {
        const r = await send('POST', impBase() + '/' + b.dataset.importReport + '/report');
        toast('Export run #' + r.runId + ' started', 'ok');
        loaders.import();
    })));
    $$('[data-import-csv]').forEach(input => input.addEventListener('change', () => {
        if (!input.files.length) return;
        const fd = new FormData();
        fd.append('file', input.files[0]);
        busy(null, async () => {
            const { body } = await api(impBase() + '/' + input.dataset.importCsv + '/csv', { method: 'POST', body: fd });
            toast('CSV loaded: ' + fmtNum(body.rowCount) + ' rows (run #' + body.runId + ')', body.status === 'ERROR' ? 'err' : 'ok');
            input.value = '';
            loaders.import();
        });
    }));
    $('#imp-refresh').addEventListener('click', () => loaders.import());
    $('#imp-type').addEventListener('change', () => loaders.import());
    $('#match-btn').addEventListener('click', ev => busy(ev.target, async () => {
        const sourceRunId = Number($('#match-source').value), targetRunId = Number($('#match-target').value);
        if (!sourceRunId || !targetRunId) throw new Error('Pick a finished source run and target run');
        const r = await send('POST', impBase() + '/match', { sourceRunId, targetRunId });
        $('#match-result').replaceChildren(h('div', { class: 'row', style: 'margin-top:12px' },
            ...Object.entries(r.counts).map(([k, v]) => h('span', { class: 'badge ' + badgeClass(k) }, k + ': ' + fmtNum(v)))));
        toast('Match finished', 'ok');
        loaders.import();
        showUnmatched(sourceRunId);
    }));

    async function showUnmatched(runId, page) {
        page = page || 0;
        try {
            const p = await get(impBase() + '/runs/' + runId + '/unmatched?size=50&page=' + page);
            $('#unmatched-panel').hidden = false;
            $('#unmatched-run').textContent = '#' + runId + ' (' + fmtNum(p.totalElements) + ')';
            $('#unmatched-csv').href = impBase() + '/runs/' + runId + '/unmatched.csv';
            $('#unmatched').replaceChildren(table([
                { label: 'DataID', key: 'dataId' }, { label: 'Employee', key: 'employeeId' },
                { label: 'Document', cls: 'msg', key: 'name' }, { label: 'Folder', key: 'folderName' },
                { label: 'Reason', render: d => badge(d.matchStatus) }
            ], p.content, { empty: 'Every document of this run matched.' }), pager(p, n => showUnmatched(runId, n)));
        } catch (e) { toast(e.message, 'err'); }
    }

    function pager(p, go) {
        if (!p || p.totalPages <= 1) return h('span');
        return h('div', { class: 'pager' },
            h('button', { class: 'btn btn-ghost btn-sm', disabled: p.first, onclick: () => go(p.number - 1) }, '‹ Previous'),
            h('span', { class: 'muted' }, 'Page ' + (p.number + 1) + ' of ' + p.totalPages),
            h('button', { class: 'btn btn-ghost btn-sm', disabled: p.last, onclick: () => go(p.number + 1) }, 'Next ›'));
    }

    // ------------------------------------------------------------------ launch
    $('#launch-form').addEventListener('submit', ev => {
        ev.preventDefault();
        const f = ev.target;
        busy(ev.submitter, async () => {
            const job = await send('POST', 'api/jobs', {
                name: f.name.value.trim(), migType: f.migType.value, numItems: Number(f.numItems.value),
                logLevel: Number(f.logLevel.value), startAt: f.startAt.value ? f.startAt.value + ':00' : null
            });
            toast((job.status === 'PENDING' ? 'Job scheduled: #' : 'Job started: #') + job.jobId, 'ok');
            f.reset();
            location.hash = 'jobs';
            setTimeout(() => openJob(job.jobId), 50);
        });
    });

    // ------------------------------------------------------------------ jobs
    let jobsPage = 0, openJobId = null, jobDocFilter = 'ERRORS';
    loaders.jobs = async function (quiet) {
        try {
            const q = encodeURIComponent($('#jobs-q').value.trim());
            const p = await get('api/jobs?size=15&page=' + jobsPage + '&q=' + q);
            $('#jobs-table').replaceChildren(table([
                { label: '#', render: j => j.jobId },
                { label: 'Name', key: 'jobName' },
                { label: 'Type', key: 'migType' },
                { label: 'Status', render: j => badge(j.status) },
                { label: 'Progress', render: j => progress(j.processedItems, j.totalItems) },
                { label: 'Start', render: j => fmtDate(j.startDate) },
                { label: 'End', render: j => fmtDate(j.endDate) },
                { label: 'User', key: 'userName' }
            ], p.content, { empty: 'No jobs yet.', onRow: j => openJob(j.jobId) }), pager(p, n => { jobsPage = n; loaders.jobs(); }));
            $('#jobs-pager').replaceChildren();
            if (openJobId) openJob(openJobId, true);
        } catch (e) { if (!quiet) toast(e.message, 'err'); }
    };
    loaders.jobs.refresh = true;
    let searchTimer;
    $('#jobs-q').addEventListener('input', () => { clearTimeout(searchTimer); searchTimer = setTimeout(() => { jobsPage = 0; loaders.jobs(); }, 300); });

    async function openJob(id, quiet) {
        openJobId = id;
        try {
            const [d, docs] = await Promise.all([
                get('api/jobs/' + id),
                get('api/jobs/' + id + '/documents?size=25&status=' + encodeURIComponent(jobDocFilter))]);
            const j = d.job;
            const panel = $('#job-detail');
            panel.hidden = false;
            panel.replaceChildren(
                h('div', { class: 'panel-head' }, h('h2', {}, 'Job #' + j.jobId + ' · ' + j.jobName),
                    h('div', { class: 'row' },
                        d.running || j.status === 'PENDING' ? h('button', { class: 'btn btn-danger btn-sm', onclick: e => busy(e.target, async () => { await send('POST', 'api/jobs/' + id + '/abort'); toast('Abort requested', 'ok'); loaders.jobs(); }) }, 'Abort') : null,
                        !d.running ? h('button', { class: 'btn btn-ghost btn-sm', onclick: e => busy(e.target, async () => { const r = await send('POST', 'api/jobs/' + id + '/retry'); toast(r.documents ? 'Retrying ' + r.documents + ' documents' : 'Nothing to retry', 'ok'); loaders.jobs(); }) }, 'Retry errors') : null,
                        h('button', { class: 'btn btn-ghost btn-sm', onclick: () => { openJobId = null; panel.hidden = true; } }, 'Close'))),
                h('div', { class: 'kv' },
                    h('div', {}, h('b', {}, 'Status'), badge(j.status)),
                    h('div', {}, h('b', {}, 'Type'), j.migType),
                    h('div', {}, h('b', {}, 'Progress'), progress(j.processedItems, j.totalItems)),
                    h('div', {}, h('b', {}, 'Log level'), j.logLevel),
                    h('div', {}, h('b', {}, 'Started'), fmtDate(j.startDate)),
                    h('div', {}, h('b', {}, 'Ended'), fmtDate(j.endDate) || '—')),
                h('div', { class: 'row' }, ...Object.entries(d.documents).map(([k, v]) => h('span', { class: 'badge ' + badgeClass(k) }, k + ': ' + fmtNum(v)))),
                h('div', { class: 'subtabs' }, ...[['ERRORS', 'Errors'], ['', 'All documents'], ['MIGRATED_WITH_WARNINGS', 'Warnings']].map(([v, l]) =>
                    h('button', { class: 'btn btn-sm ' + (jobDocFilter === v ? 'btn-primary' : 'btn-ghost'), onclick: () => { jobDocFilter = v; openJob(id); } }, l))),
                table([
                    { label: 'mig_id', key: 'migId' }, { label: 'Document', cls: 'msg', key: 'fileName' },
                    { label: 'Folder', key: 'folderId' }, { label: 'Status', render: x => badge(x.status) },
                    { label: 'Target node', key: 'targetDocId' }, { label: 'Detail', cls: 'msg', key: 'errorDetail' }
                ], docs.content, { empty: jobDocFilter === 'ERRORS' ? 'No errors in this job.' : 'No documents.', onRow: x => openDocEvents(x.migId) }),
                h('div', { id: 'doc-events' }));
        } catch (e) { if (!quiet) toast(e.message, 'err'); }
    }

    async function openDocEvents(migId) {
        try {
            const ev = await get('api/documents/' + migId + '/events');
            $('#doc-events').replaceChildren(h('h2', { style: 'margin-top:16px' }, 'Steps of document ' + migId), table([
                { label: 'When', render: e => fmtDate(e.createdAt) }, { label: 'Step', key: 'step' },
                { label: 'Result', render: e => badge(e.outcome) }, { label: 'ms', num: true, key: 'durationMs' },
                { label: 'Message', cls: 'msg', key: 'message' }
            ], ev, { empty: 'No steps logged (log level 1 keeps only errors and warnings).' }));
        } catch (e) { toast(e.message, 'err'); }
    }

    // ------------------------------------------------------------------ scheduler
    loaders.schedule = async function (quiet) {
        try {
            const s = await get('api/scheduler');
            const el = $('#sched-status');
            el.textContent = s.active ? 'Running' : 'Stopped';
            el.className = 'badge ' + (s.active ? 'ok' : '');
            $('#sched-jobs').replaceChildren(table([
                { label: '#', key: 'jobId' }, { label: 'Name', key: 'jobName' }, { label: 'Type', key: 'migType' },
                { label: 'Documents', num: true, key: 'totalItems' }, { label: 'Starts', render: j => fmtDate(j.startDate) },
                { label: '', render: j => h('button', { class: 'btn btn-danger btn-sm', onclick: e => busy(e.target, async () => { await send('POST', 'api/jobs/' + j.jobId + '/abort'); loaders.schedule(); }) }, 'Cancel') }
            ], s.pending, { empty: 'No scheduled jobs.' }));
        } catch (e) { if (!quiet) toast(e.message, 'err'); }
    };
    loaders.schedule.refresh = true;
    $('#sched-on').addEventListener('click', e => busy(e.target, async () => { await send('PUT', 'api/scheduler', { active: true }); loaders.schedule(); }));
    $('#sched-off').addEventListener('click', e => busy(e.target, async () => { await send('PUT', 'api/scheduler', { active: false }); loaders.schedule(); }));

    // ------------------------------------------------------------------ categories
    /** Posts the form and saves the text answer as a file. */
    async function postAndSave(form, url, btn) {
        await busy(btn, async () => {
            const { body, res } = await api(url, { method: 'POST', body: new FormData(form) });
            const name = ((res.headers.get('Content-Disposition') || '').split('filename=')[1] || 'result.txt').trim();
            const a = h('a', { href: URL.createObjectURL(new Blob([body], { type: 'text/plain' })), download: name });
            document.body.append(a); a.click(); a.remove();
            toast('Downloaded ' + name, 'ok');
        });
    }
    $('#cat-parse').addEventListener('submit', ev => { ev.preventDefault(); postAndSave(ev.target, 'api/tools/categories/parse', ev.submitter); });
    $('#cat-map').addEventListener('submit', ev => { ev.preventDefault(); postAndSave(ev.target, 'api/tools/categories/map', ev.submitter); });

    // ------------------------------------------------------------------ start
    loadTypes().catch(e => toast(e.message, 'err')).finally(() => show(location.hash.slice(1) || 'home'));
})();
