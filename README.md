# Local KYC QA runner

This reuses the IntelliJ workspace's Maven project (`org.example:Pennydroponly`). Java source and bytecode target **Java 17**. The portal serves from Spring Boot on `127.0.0.1:8080`. Startup and page loading do not create new runs; previously saved schedules can launch when they become due while the application is running. Automated tests never launch the live journey. The browser starts only from an explicit **Run journey** submission or a schedule saved by the user when it becomes due.

## Setup and launch

Quick launch on this Windows workspace: run `powershell -ExecutionPolicy Bypass -File .\start-portal.ps1` from the project folder. It starts the existing built JAR in the background, reuses the downloaded browser cache, and waits for an HTTP 200 portal response. It does not create a new KYC run; saved schedules are resumed according to the recovery rules below. Logs and the process ID are in `inspection/portal.log`, `portal-error.log`, and `portal.pid`.

1. Install a JDK 17 (or a supported newer JDK), and set `JAVA_HOME` to its installation directory. In IntelliJ, reload `pom.xml` as a Maven project, choose that JDK for the project and Maven runner, and use language level 17. This workspace's project SDK was changed from JDK 26 to its already registered `temurin-21` SDK, with language level 17; builds use `--release 17`.
2. In PowerShell, from this project directory:

   ```powershell
   .\mvnw.cmd clean verify
   java -jar .\target\Pennydroponly-1.0-SNAPSHOT.jar
   ```

   Or run `org.example.KycApplication` in IntelliJ. Maven does not need to be installed separately. The wrapper downloads Maven 3.9.9 on first use. Initial builds require Internet access to Maven Central.

   To reuse this workspace's already downloaded browser cache, set `$env:SE_CACHE_PATH = Join-Path $PWD '.mvn/selenium-cache'` before the `java -jar` command. Selenium Manager also supports its normal user cache when this is unset.

   Stop an already running instance before rebuilding the same JAR on Windows (`Ctrl+C` in its terminal or Stop in IntelliJ), since Windows locks the running archive.
3. Open **http://127.0.0.1:8080**. Enter your authorized QA values, exact dependency/account-type labels, OTP mode and browser mode. Click **Run journey** once. No applicant or bank details are prefilled by default.
4. Watch progress and per-request status codes. Download the JSON report when needed. A visible browser closes when the run ends; the report remains in the portal.

All routine values are editable in the portal for each run. Defaults are optional: configure `portal.defaults.*` properties or the `KYC_DEFAULT_MOBILE`, `KYC_DEFAULT_DEPENDENCY`, `KYC_DEFAULT_NAME`, `KYC_DEFAULT_IFSC`, `KYC_DEFAULT_ACCOUNT_NUMBER`, `KYC_DEFAULT_CONFIRM_ACCOUNT_NUMBER`, and `KYC_DEFAULT_ACCOUNT_TYPE` environment variables before startup. Settings supply initial defaults only; each run consumes the submitted form. OTP and browser defaults can also be set with Spring's `PORTAL_DEFAULTS_OTP_MODE` and `PORTAL_DEFAULTS_BROWSER_MODE` environment variables. Use strings throughout, including leading zeroes.

Dependency labels observed in the initial live DOM were `Self`, `Spouse`, `Child Dependent`, `Parent Dependent 2`, and `childdependent3`. Account-type options were not observed before OTP submission, so no account-type label is invented or prefilled.

The mobile rule is ten digits beginning 6–9; IFSC uses `[A-Z]{4}0[A-Z0-9]{6}`. These are basic format checks, not verification of actual ownership, existence or correctness. Random OTP uses a secure random integer formatted as four digits (`0000`–`9999`). Fixed OTP is exactly four ASCII digits. A rejected OTP fails without any retry or validation bypass.

## Pinned browser and network compatibility

Dependencies are Spring Boot **3.5.16**, Selenium Java **4.49.0**, and `selenium-devtools-v151` **4.49.0**. Chrome major **151** is requested through `ChromeOptions.setBrowserVersion("151")`. Selenium Manager supplies matching Chrome for Testing and ChromeDriver if needed, regardless of the currently installed Chrome major. There is no manually downloaded driver or `webdriver.chrome.driver` setting. First browser use needs access to Chrome for Testing distribution endpoints, download space, and permission to execute Chrome and ChromeDriver. Corporate proxy/firewall rules may need configuration. Chrome's concrete patch and Selenium Manager diagnostics appear in its startup output.

Do not change the Chrome major alone: update the Selenium/CDP dependencies to matching supported versions too. CDP is version-dependent; see [Selenium DevTools documentation](https://www.selenium.dev/documentation/webdriver/bidirectional/chrome_devtools/) and [Selenium Manager documentation](https://www.selenium.dev/documentation/selenium_manager/). Java 17 is supported by [Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/system-requirements.html).

`BrowserNetwork` uses Selenium's supported `DevTools`, `Command` and `Event` APIs with the pinned CDP module. It creates the session, installs lifecycle listeners, and enables `Network` **before** target navigation. It does not make independent HTTP API calls, use response-body interception, disable CORS, or change OTP validation.

## Workflow and locator verification limits

The supplied field XPaths appear literally in `TargetLocators`, including `select_dependancy`. Dependency and account type use `Select.selectByVisibleText` after checking for an exact matching option. No positional button locators are used. Each consequential click happens once, outside polling waits. The runner types IFSC and uses a normal Tab blur, waits for automatic MICR/address, then fills and verifies account fields and type. Following the updated requested workflow, it then clicks the active third-page `//button[@id='SaveData']` once, waits for visible `#bankPopUp`, saves its screenshot, and clicks `//button[@onclick="popUp_save('true','bankPopUp');this.disabled = true;"]` once. It waits for the resulting page and displays both screenshots inline in the portal. The earlier stop-after-bank-entry behavior has been replaced by this explicitly requested continuation. Automated tests do not perform these live actions; user-saved schedules can perform them when due.

The initial computer-use browser surface was unavailable. After Selenium Manager installed Chrome, a separate read-only Selenium utility inspected the live initial DOM; its evidence is in `inspection/observed-dom.md`. The initial HTML and public scripts are also in `inspection/`. No live field was filled, no live button was clicked, and no OTP was submitted. Initial DOM presence is verified; post-OTP fields and the live end-to-end workflow remain untested:

| Element | Locator / evidence | Verification status |
|---|---|---|
| Send OTP | `//button[@id='SaveData' and normalize-space(.)='SEND OTP']`; button `onclick="SaveData('mobile')"` | Present and visible in initial Chrome DOM; click not exercised |
| Loading | `#page-loader`, `#workflow-otp-loader`, `.overlay-bg`, `.overlay-bg-meon` | IDs/classes present in initial Chrome DOM; both overlays hidden after page load |
| Validation | `.error_msg`, `#successFalse_msg`, `#errorIfscifsc`, `#notification` | `.error_msg` observed in initial Chrome DOM; remaining selectors from HTML/public script; bank messages unobserved |
| OTP Submit | `SaveData` button with exact text `SUBMIT` or `VERIFY OTP` (case-insensitive) | **Provisional**: public `index.js` calls `$('#SaveData').click()` in `submitOtp`; text and post-OTP layout unobserved |
| MICR | `#micr1` | **Provisional**: IFSC success handler in `inspection/index.js` sets its value from `data.micr`; bank DOM unobserved |
| Bank address | `#bank_address` | **Provisional**: same handler sets `data.address`; bank DOM unobserved |

The provisional locators are isolated under `portal.locators.*` in `application.properties`. Verify them against the authorized post-OTP DOM before expecting a live success. A missing/unusable locator fails with the current step and available validation messages, without trying alternative buttons or submitting twice.

Because the target reuses `SaveData` in later stages, the OTP click requires a visible, enabled OTP input and a visible, enabled Submit button in the same exposed page container. An unrelated IFSC element is not used to prohibit the OTP click. A changed OTP section stops the run without another click.

The runner follows `APPLICANT_DETAILS → WAITING_FOR_OTP → ENTERING_OTP → WAITING_FOR_BANK → FILLING_BANK → SUBMITTING_BANK → WAITING_FOR_BANK_POPUP → CONFIRMING_BANK_POPUP → WAITING_FOR_RESULT → COMPLETED`. OTP entry is recorded after the typed value is verified. OTP submission is recorded immediately after the actual Submit click returns, before another browser command; only then does the runner enter `WAITING_FOR_BANK`. The OTP guard cannot run after submission. The active bank page requires the four visible, enabled bank fields. `BankDetailsPage` uses this run's portal DTO, then `BankPopupFlow` completes the specified clicks/captures. With admin cleanup enabled, `COMPLETED` is set only after the matched maker-checker deletion success screenshot is saved.

Container detection checks normal CSS visibility plus `hidden`, `aria-hidden="true"`, and `inert` on the element's ancestors. DOM counts and visibility alone do not define the page. Hidden duplicate fields are ignored when filling, selecting or verifying values. Bank fields appearing genuinely active before this run enters and submits OTP produce **Unexpected direct bank-page transition** with observed diagnostics; that message makes no assertion about a security bypass.

The portal exposes current stage, OTP flags, container/field state, sanitized URL and API outcomes. Popup and result captures are stored separately in `target/run-artifacts/<run-id>/bank-popup.png` and `result-page.png`; failures use `failure.png`. `/api/runs/<run-id>/screenshots/<label>` serves each PNG. `screenshot.png` and the legacy endpoint refer to the latest capture. Popup screenshots remain available if Proceed or navigation subsequently fails. The gallery shows the popup and the resulting page as image previews plus the resulting URL; it does not embed remote HTML or share Chrome's session cookies. Screenshots may contain entered QA details and stay local. **Cancel run** requests cooperative cancellation, followed by bounded network settling and browser cleanup.

The current public script also includes segmented OTP fields (`.dynLengthSet input[name='extraNameKey']`) that auto-submit on entry. These conflict with the supplied single `//input[@id='mobile_otp']` and explicit one-click Submit workflow. The runner requires the supplied OTP input to become visible and refuses segmented auto-submission if present. It does not replace the supplied XPath, disable event handlers, invoke page functions, or bypass OTP validation. If the live demo now uses only that segmented layout, the live run will stop; its intended OTP workflow must be reconciled with the supplied requirements. No unobserved locator is claimed to have been tested.

## Reports and lifecycle semantics

In addition to the initial popup capture, `bank-popup-before-proceed.png` is captured after the exact Proceed button becomes visible and enabled, immediately before its click. The portal displays it as **Bank popup immediately before Proceed**. That image is retained separately from the initial popup and the resulting-page images, including when navigation subsequently fails.

Each request row includes run ID, Chrome request ID, redirect-hop number, workflow step at request start, method, endpoint, timestamps, resource type, HTTP response status, optional wire status from response extra-info, lifecycle state, cache/service-worker flags, and available network/CORS error information. Identical URLs and concurrent calls remain separate. Redirect hops preserve available intermediate codes. `status` is Chrome's response status; `wireStatus` can show an underlying `304` while a cached response appears as `200`. Failed calls without responses keep a null status (`—` in the portal).

Listeners remain active while the UI advances and during final settling. Settling ends after 1.5 seconds of quiet with no tracked requests outstanding, or after the configured `portal.settle-seconds` bound (8 seconds by default). Outstanding rows become `UNFINISHED`, retaining any already observed status. Events after the snapshot cutoff are intentionally excluded. Browser closure does not fabricate failures or statuses for pending requests. CDP coverage is for this journey's page session, including cross-origin Fetch/XHR; requests from separate browser targets (such as an independently created service worker) are not promised. Page-session responses served by a service worker are flagged when Chrome reports them. Disk/prefetch caches and `requestServedFromCache` events are retained where observable.

For privacy, query strings, URL user-info/fragments, headers, full request/response bodies, input DTOs, and OTP values are not stored in reports. During the OTP transition, Selenium reads the already completed Send OTP response through CDP (not an additional HTTP call) and retains only boolean success/redirect flags and sanitized application messages. Four-or-more-digit runs and email addresses in those messages are redacted. Failure diagnostics include the current page URL without query strings, visible field IDs/names/types (never values), frame counts, and OTP visibility. Endpoint paths and target validation messages may still contain information; treat downloaded reports accordingly. Immediate-run reports are in memory, limited to the last 20 runs, and cleared by restart. Scheduled-run reports are also saved with their schedules after completion. One run is allowed at a time. The local server does not enable CORS and requires a per-process portal token for launches.

### Troubleshooting “Waiting for OTP”

HTTP 200 confirms an HTTP response, not that the application accepted the mobile step. The runner checks the actual response for `success=false`, visible validation/error containers, and redirect confirmation before continuing. It searches for the supplied XPath in the main document and visible frames, retaining the selected frame for typing. Read-only polling tolerates DOM replacement during navigation. If OTP remains absent, expand **Page and application diagnostics** in the portal or inspect `diagnostics` in the JSON report. These show whether the original mobile page remained visible, the input exists but is hidden, a confirmation is blocking advancement, or another screen appeared. Send OTP is never retried. Segmented auto-submit OTPs still stop before entry because the original workflow requires the supplied single input and one explicit Submit.

## Tests

`./mvnw.cmd verify` runs validation/portal and network-reducer tests. The journey component is mocked in portal tests, so they cannot open Chrome or hit the live URL. Lifecycle cases include concurrency, repeated endpoints, redirect hops, extra-info ordering, cache status differences, no-response CORS failures, response-then-transport-failure, and incomplete runs.

The optional real-browser tests open **only localhost fixtures**. They verify coexisting inactive fields, iframe OTP, normal OTP-to-bank navigation, input propagation, stage order, single clicks for Send OTP/Submit/bank SaveData/popup Proceed, delayed popup visibility, screenshot persistence before Proceed, resulting-page capture and API monitoring after navigation. Negative cases cover missing popups and unsuccessful navigation without retried clicks. Same-URL page reloads, inline portal image rendering and cancellation are also tested.

```powershell
.\mvnw.cmd '-DlocalBrowserTest=true' '-Dtest=LocalBrowserTest,LocalBankPopupFlowTest,LocalPortalBrowserTest,LocalAdminCleanupTest' test
```

This requires the pinned browser downloads; it never uses the live URL. The second test renders the real local portal, checks the conditional OTP field, and clicks Run with the workflow mocked, asserting that leading-zero values are submitted once. It writes `target/portal-preview.png`. Ordinary tests skip both browser tests. Runtime reports, build outputs and locally downloaded tools are excluded by `.gitignore`.

A separate manual `ReadOnlyTargetInspector` utility (not a JUnit test) inspects the live initial DOM without typing or clicking. It cannot observe post-OTP fields. Its evidence, when available, is saved to `inspection/observed-dom.md`.

## Schedule a journey in IST

Fill the **same Run inputs**, including OTP and browser modes. Choose **Schedule date and time (IST)** and click **Save schedule**. The date/time is interpreted as `Asia/Kolkata` (UTC+05:30), regardless of the browser or computer timezone. It must be in the future. The saved schedule captures those form values; subsequent form edits do not change it. **Run journey** still launches immediately and ignores the schedule date/time.

**Saved schedules** shows status and masked mobile/account numbers. **Load inputs** restores saved values into the existing form; cancel the old pending schedule and save a new one to reschedule. **Cancel schedule** prevents a pending launch. Use **Cancel run** in the monitor for an already launched journey. **View run** opens its progress, screenshots and report. **Delete** removes the stored schedule and its saved inputs/report; it does not delete screenshot files.

Keep Spring Boot running and the computer awake. This is an application scheduler, not a Windows task/wake service. Due jobs are checked every second and use the same single-run worker as manual launches. Busy jobs show `WAITING_FOR_RUNNER` and launch when that runner becomes free. Future pending schedules survive a restart. Schedules whose time passed while the portal was stopped, or by more than 60 seconds before dispatch after a suspend/stall, are marked `MISSED`. Jobs claimed/running during a restart become `INTERRUPTED` and never retry automatically. Review their report and target state before scheduling again. No Selenium action is retried by the scheduler.

Schedules and completed scheduled-run reports are saved with atomic replacement in `data/scheduler/schedules.enc` using AES-GCM. The local key is `data/scheduler/schedule.key`; retain both files to preserve schedules. Both are excluded from Git. The key is stored on the same computer, so anyone with access to both can decrypt the data. Restrict access to this workspace and protect any OneDrive backups. Reports/screenshots retain the existing local privacy and cleanup behavior. Screenshots are in `target/run-artifacts`; `mvn clean` removes them.

Optional startup properties: `portal.scheduler-directory` (default `data/scheduler`), `portal.scheduler-enabled` (default `true`), and `portal.scheduler-poll-ms` (default `1000`). Routine inputs and times are edited entirely in the portal. A corrupt or missing-key store stops startup rather than discarding jobs or launching them again.

Scheduler tests use a controlled clock, temporary storage and mocked runners to check IST conversion, encrypted persistence, leading-zero inputs, future-only validation, due-only dispatch, busy queues, cancellation, restart recovery and saved reports. Spring portal tests disable schedule dispatch and use isolated temporary directories. The local Chrome portal test saves, loads, cancels and deletes a future schedule from the shared input form; its journey is mocked. None of these tests submit the live journey.

## Automatic maker-checker cleanup after penny-drop capture

Successful manual and scheduled journeys now retain the penny-drop popup/result screenshots, wait **10 seconds**, and continue in the same monitored Chrome session to `https://live.meon.co.in/admin/democapital`. The runner signs in using locally configured admin credentials, opens maker-checker, selects **PENNYDROP ONLY** and **VERIFY**, and searches using this run's mobile value. Existing selected checkboxes are checked before clicking, so an already selected Verify filter is not accidentally cleared.

Cleanup targets one matched row. It prefers an identity observed in the journey's `#user_identity` field or an actual journey response's `identity`/`user_identity`. The post-OTP identity field is source-backed and has not been observed live during development. If identity is unavailable, cleanup requires one exact mobile/name/account-number/IFSC match in a complete browser-produced search result. Different workflows, duplicate matches, incomplete search pages, a changed row, or a deletion dialog belonging to another identity stop the run without a confirmation click. No first-row or bulk deletion is performed.

The actual Delete option is scoped to that row's three-dot menu and its `deleteUser(...)` context. The supplied `//div[@class='modal-footer']` is a container: live inspection observed its real confirmation button as `#deleteModal .modal-footer #confirmDeleteBtn`. Select uses **Duplicate entry**, a non-placeholder reason observed in the live dropdown. Confirm Delete is clicked once. The runner waits **2 seconds**, then waits for both the browser's successful deletion response and the visible `#success_model` popup and saves its screenshot. The public admin script shows deletion removes the row from Verify and supports a **Trash** status. No live deletion was exercised during development.

The gallery adds **Matched maker-checker entry**, **Deletion reason before Confirm Delete**, and **Admin deletion success popup**, while preserving earlier penny-drop captures. All admin Fetch/XHR requests use the existing CDP network ledger, including sign-in, filters/search and deletion; there are no standalone API requests for cleanup or status codes. Monitoring remains active through the bounded settling period. Failure screenshots, cooperative cancellation (including during the delay), single-worker scheduling, and browser cleanup remain in effect. A penny-drop capture followed by cleanup failure is reported as a failed overall run, with `pennyDropCaptured: true` and the original screenshots retained. Deletion is never automatically retried; review the target if a response/navigation fails after its confirmation click.

New stages: `WAITING_FOR_ADMIN_CLEANUP → ADMIN_LOGIN → FILTERING_MAKER_CHECKER → MATCHING_ADMIN_ENTRY → OPENING_DELETE_DIALOG → CONFIRMING_ADMIN_DELETE → WAITING_FOR_DELETE_SUCCESS → COMPLETED`. The portal reports these along with the actual browser response codes and sanitized cleanup diagnostics. An admin onboarding guided tour was observed once intercepting a filter click; if it covers controls, cleanup uses the observed visible End tour control once and waits for the overlay to leave. If the close control fails, bounded fallbacks use a JavaScript click, the verified Tourguide stop API, then remove only tour elements and its scroll-lock classes.

Credentials are kept in `data/admin/credentials.enc` with a local AES-GCM key in `data/admin/credentials.key`; `data/` is ignored by Git. The credentials supplied for this workspace have been configured there. These files are not included in reports or portal configuration. Access to both files allows decryption, so protect workspace/backups. To replace credentials, set `KYC_ADMIN_USERNAME` and `KYC_ADMIN_PASSWORD` in the launching process, or run the compiled local credential utility using those environment variables:

```powershell
java -cp .\target\classes org.example.AdminCredentials data/admin
```

Do not put credentials in tracked properties/source. Environment variables take precedence over the local store. Optional startup properties are `portal.admin-cleanup.enabled` (default true), `portal.admin-cleanup.url`, and `portal.admin-cleanup.credentials-directory` (default `data/admin`). Disable cleanup before startup with `PORTAL_ADMIN_CLEANUP_ENABLED=false` if a capture-only run is explicitly wanted.

`AdminTargetInspector` is a manual read-only utility that signs in and reads filter/modal structures and actual table-response **field names only**. It never clicks a row or Delete. Evidence is in `inspection/admin-dom.md`; public admin JS is saved under `inspection/admin-*.js`. The live Verify table was empty during inspection, so row action bindings and success behavior were verified from the public script and local fixtures, not by deleting a live record. `AdminEntryMatcherTest` and `LocalAdminCleanupTest` verify matching, ambiguity/incomplete-page rejection, wrong-dialog protection, actual timing, one deletion click, success/failure handling, encryption and cancellation. Real browser tests use only localhost with fake credentials/records.

## Delete an existing entry independently

Fill **Mobile**, **Name**, **IFSC**, **Account number**, and **Browser mode** in the existing form, then click **Delete entry only**. The button uses only those fields; dependency, account type, confirmation, OTP settings and the scheduled time do not affect this action. It starts a fresh monitored Chrome session directly at admin login, using the configured admin credentials. It does not create or submit a penny-drop journey and does not wait the post-journey 10-second gap.

The same exact matching, Verify/workflow filters, Duplicate entry reason, one Confirm Delete click, 2-second success-popup delay and screenshot capture apply. Missing, duplicate or incomplete matches stop without a delete confirmation. API responses, screenshots, progress, JSON download and cancellation appear in the same Run monitor. Standalone reports have `kind: DELETE_ONLY`, start at `ADMIN_LOGIN`, and keep OTP flags false. Automatic cleanup after full journeys still runs as before. `portal.admin-cleanup.enabled` controls automatic continuation; the explicit delete-only button remains available using the configured admin URL and credentials.

`POST /api/runs/delete-only` accepts the five-field JSON DTO and requires the portal token/local origin just like Run. Both actions and scheduled journeys share one worker: an active run blocks a second launch. Frontend and backend validate required fields, basic mobile/IFSC/account formats and browser enum, preserving account numbers as strings. Local Chrome tests execute standalone deletion against fixture admin pages and verify screenshot/API output without visiting the penny-drop URL. Portal tests mock both runners to verify the separate button dispatches only the deletion runner, even when unrelated journey fields are blank. No live deletion is performed by development tests.

## Optional browser and tour popups

The attached **Save password? / No thanks** popup is Chrome browser UI, outside the website DOM. Fresh automation Chrome profiles now set `credentials_enable_service=false` through Selenium `ChromeOptions` preferences, preventing this prompt for both full journeys and **Delete entry only**, in Visible and Headless modes. The setting applies to the temporary automation profile, not the normal Chrome profile. The automation does not claim to click a native No thanks button with a page XPath. Chromium documents that this preference disables password-save prompts: https://chromium.googlesource.com/chromium/src/+/HEAD/components/password_manager/core/common/password_manager_pref_names.h

For the website tour, the exact supplied XPath is `(//span[@title='End tour'])[1]`.

The handler tries a Selenium click, waits for dismissal, then escalates to a JavaScript click, the verified Tourguide 0.2.0 `tourguide.stop()` API, and finally removal of only `.guided-tour-step`, `.guided-tour-step-highlight`, and `.guided-tour-step-tooltip` elements and tour scroll-lock classes. Each attempt is logged, with 700 milliseconds for closure to take effect. Hidden first matches can be clicked through JavaScript. The handler confirms the tour has disappeared before allowing the waiting action. Loading overlays and business dialogs remain intact; Login, OTP, bank submission and Confirm Delete are never retried by this handler.

Local Chrome fixtures cover normal closure, intercepted clicks, hidden first controls, library stop, force closure, and preserving unrelated dialogs. No live record deletion is performed during these tests.


If the legacy makerCheckerSearch input is hidden, cleanup matches browser-produced table responses from the current admin session. Exact matching and result completeness checks still apply. Three-dot and Delete selectors are scoped to the matched row. The reason selector uses the supplied first select XPath, selects Duplicate entry, and clicks confirmDeleteBtn once. The success popup is captured after the existing two-second delay.

## Saved inputs and success emails

Enter multiple notification addresses in the existing input form, separated by commas, semicolons, or new lines (up to 20). Click **Save details** to retain all form values, including leading zeroes and a fixed test OTP. Run, Delete entry only, and Save schedule also save the current form. Unsaved edits are not persisted on refresh. Values survive browser and application restarts in AES-GCM encrypted `data/profile/profile.enc` with its local `profile.key`; keep the pair together. They do not expire or clear after a run. **Clear saved details** explicitly clears the saved form and recipients; existing schedules retain their own immutable snapshots. Use defaults changes the form only until you save it.

Successful manual journeys, scheduled journeys, and delete-only runs notify the addresses submitted for that run. Full journeys notify only after all enabled admin cleanup finishes successfully. Failed/cancelled runs send no success email. Each recipient receives a separate message; duplicate addresses are deduplicated. Emails contain a run summary, applicant name, masked mobile/account, IFSC and request count. The message body omits OTP, passwords and full account numbers. Captured screenshots are attached as PNG files and may display the entered test details. An Excel workbook of the actual browser API responses is also attached. Screenshots/API reports remain in the portal. Notification status appears in Run monitor/JSON and is saved in scheduled reports. SMTP acceptance does not guarantee inbox delivery. A mail failure never repeats the journey/deletion or changes automation success, and is not automatically retried.

Configure an SMTP sender once before starting the portal (SMTP settings are not routine applicant inputs):

```powershell
$env:PORTAL_SMTP_HOST = 'smtp.example.com'   # Your provider's actual SMTP host
$env:PORTAL_SMTP_PORT = '587'
$env:PORTAL_SMTP_SECURITY = 'STARTTLS'      # STARTTLS, SSL (typically 465), or NONE for a trusted local relay
$env:PORTAL_SMTP_FROM = 'your-verified-sender@example.com'
$env:PORTAL_SMTP_USERNAME = 'your-smtp-username'
$env:PORTAL_SMTP_PASSWORD = '<your SMTP/app password>'
.\start-portal.ps1
```

Use the provider's SMTP/app password requirements. Set these variables in the shell that launches the portal; do not commit credentials. If the portal is already running, restart it while idle for changed sender settings to apply. Without a host and valid sender address, the UI explicitly shows setup is needed and successful runs report `NOT_CONFIGURED` rather than claiming email was sent. Connection/read/write timeouts are five seconds. Addresses can still be saved before SMTP setup. Tests use a localhost SMTP server and fixture recipients; no real emails are sent during development.

Saved-details endpoints are GET/PUT/DELETE `/api/saved-details`, protected by the current local portal token and local-origin check. The PUT body contains string form values; it may save an incomplete draft, while Run/Schedule retain their full validation requirements. Recipient lists are validated on frontend/backend. SMTP settings and recipient addresses are omitted from public configuration and run diagnostics.

The SMTP sender can also be saved locally in encrypted `data/mail/credentials.enc` and `credentials.key` (excluded from source control). The application automatically loads this pair when `PORTAL_SMTP_HOST` is unset, including when started from IntelliJ. Environment SMTP settings take precedence when a host is supplied. Preserve the encrypted file/key together. Gmail app-password display spaces are removed when settings are saved. Tests explicitly disable loading the real sender.

After exporting the SMTP environment variables, save them without putting a password in source or process arguments:

```powershell
java -Dloader.main=org.example.SmtpConfiguration -cp target/Pennydroponly-1.0-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher save
```

Check TLS connectivity and SMTP authentication without sending an email:

```powershell
java -Dloader.main=org.example.SmtpConfiguration -cp target/Pennydroponly-1.0-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher check
```

Restart the idle portal after saving changed sender settings. No test email or live automation run is required to check authentication. Sender configuration uses STARTTLS on port 587 for the configured Gmail server; password values are omitted from logs and diagnostics.


## Email attachments after successful runs

Each success email now contains all screenshots registered for that run as separate PNG attachments, plus `api-responses-<run-id>.xlsx`. This applies to manual journeys, scheduled journeys and Delete entry only. The workbook is generated by Java using Apache POI 5.5.1 (https://poi.apache.org/download.html), with no desktop Excel installation required.

The single **API responses** sheet contains run metadata and one row per captured Fetch/XHR request ID and redirect hop: method, sanitized endpoint, observed HTTP/wire status, lifecycle, workflow step, resource type, cache/service-worker flags, UTC timestamps and errors. HTTP status cells stay blank when no HTTP response was observed. Repeated endpoints remain separate, intermediate redirects are retained, and failed/unfinished requests remain explicit. The export uses only the completed run's browser ledger; no extra API requests are made. Headers/bodies and URL query strings remain excluded. Captured text is written as literal cells, never spreadsheet formulas. Headers are filterable and frozen.

Attachments are prepared once and reused for the individual recipient messages. A missing registered screenshot, absence of screenshots, or exceeding `portal.mail.max-attachment-bytes` (18 MiB unencoded by default) reports `ATTACHMENT_FAILED` and sends no incomplete email. Email diagnostics list attachment filenames/count/bytes, never credentials. Automation success is preserved and the journey/deletion is never repeated. SMTP failures remain separate; there is no automatic email retry. Attachment tests use only a localhost SMTP server, fixture screenshots and fixture API events; they decode MIME attachments and reopen the Excel workbook to verify its contents. No live emails or automation submissions are used in these tests.

#   p e n n y d r o p _ o n l y  
 