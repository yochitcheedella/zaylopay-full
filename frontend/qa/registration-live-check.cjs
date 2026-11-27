const { qaUrl, artifactPath, browserExecutable } = require('./support/runtime.cjs');
// Read-only checks against the running local app. Never submits applicant data.
const { chromium } = require('playwright-core');
const assert = require('node:assert/strict');
const fs = require('node:fs');

(async () => {
    const browser = await chromium.launch({ headless: true, executablePath: browserExecutable() });
    try {
        const context = await browser.newContext({ viewport: { width: 1366, height: 720 } });
        const page = await context.newPage();
        const errors = [];
        let statusRequests = 0;
        const isStatus = response => response.url() === 'http://localhost:8080/api/v1/applicants/me/verification/status' && response.request().method() === 'GET';
        page.on('pageerror', error => errors.push(error.message));
        page.on('request', request => { if (request.url().endsWith('/verification/status') && request.method() === 'GET') statusRequests++; });
        const initial = page.waitForResponse(isStatus);
        await page.goto(qaUrl('/register'));
        const response = await initial;
        assert.equal(response.status(), 401);
        const problem = await response.json();
        assert.equal(problem.code, 'UNAUTHENTICATED');
        assert.equal(problem.sessionState, 'NONE');
        const headers = await response.allHeaders();
        assert.equal(headers['access-control-allow-origin'], qaUrl(''));
        assert.equal(headers['access-control-allow-credentials'], 'true');
        assert.match(headers['cache-control'], /no-store/);
        await page.waitForFunction(() => !document.querySelector('.registration-checking'));
        assert.equal(await page.locator('.registration-notice').count(), 0);
        await page.getByRole('heading', { name: 'Create your account.', exact: true }).waitFor();
        await page.evaluate(() => { window.dispatchEvent(new Event('online')); window.dispatchEvent(new Event('online')); });
        await page.waitForTimeout(200);
        assert.equal(statusRequests, 1);
        assert.equal(new URL(page.url()).pathname, '/register');
        await page.screenshot({ path: artifactPath('registration-live-1366.png') });

        // An intentionally invalid fixture cookie must not restore anyone's details.
        await context.addCookies([{ name: 'ZAYLOPE_APPLICANT', value: 'invalid-session-fixture', domain: 'localhost', path: '/api/v1', httpOnly: true, sameSite: 'Strict' }]);
        const invalid = page.waitForResponse(isStatus);
        await page.reload();
        const rejected = await invalid;
        assert.equal(rejected.status(), 401);
        assert.equal((await rejected.json()).sessionState, 'EXPIRED_OR_INVALID');
        await page.locator('.registration-notice-session').waitFor();
        assert.equal(await page.getByLabel('Full name', { exact: true }).inputValue(), '');
        assert.equal(new URL(page.url()).pathname, '/register');
        const forbidden = await context.request.get('http://localhost:8080/api/v1/applicants/me/verification/status', { headers: { Origin: 'https://untrusted.example' } });
        assert.equal(forbidden.status(), 403);
        assert.equal(forbidden.headers()['access-control-allow-origin'], undefined);
        assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
        assert.deepEqual(errors, []);
        const result = { realFrontend5173: true, realBackend8080: true, expected401Readable: true, newApplicantClean: true, noAutomaticRedirect: true, noUnauthorizedRetries: true, invalidSessionRecovery: true, untrustedOriginRejected: true, noStore: true, noBrowserStorage: true, applicationRecordsWritten: false, errors };
        fs.writeFileSync('qa/registration-live-results.json', JSON.stringify(result, null, 2));
        console.log(JSON.stringify(result));
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
