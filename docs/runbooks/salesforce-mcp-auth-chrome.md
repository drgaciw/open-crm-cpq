# Fix Salesforce MCP authentication

Instructions for the Claude Chrome extension. Do this in the signed-in user's browser. The cloud agent cannot finish Salesforce login: `sf org login web` is refused in that headless environment, and both Cursor MCP servers stay at `needsAuth` until this browser flow succeeds.

Change Setup and the Cursor dashboard only. Do not create, edit, or delete CRM records (accounts, leads, contacts, opportunities, or any other data). Do not copy a consumer secret into Cursor, into git, or into this file.

## What is already known

| Fact | Value |
| --- | --- |
| Org | Sandbox `aciworldwide--rcaaipoc` |
| Login | `https://aciworldwide--rcaaipoc.sandbox.my.salesforce.com` |
| Lightning | `https://aciworldwide--rcaaipoc.sandbox.lightning.force.com/` |
| App name on the callback | `H360_MCP` |
| CLI org list | No authorized org |
| Client-credentials attempt | `400 invalid_grant` — `no client credentials user enabled` |
| Device-flow attempt | `400 invalid_grant` — `device flow is not enabled for the app` |
| Hosted MCP with no token | `401` — `JWT Token is required` |
| Cursor servers still blocked | `Salesforce`, `Salesforce-headless-360` |

The URL currently stored as the MCP endpoint is the OAuth callback:

`https://aciworldwide--rcaaipoc.sandbox.my.salesforce.com/services/authcallback/H360_MCP`

That address returns HTTP 200. It is not an MCP server. The MCP server URL has to come from Setup → MCP Servers and look like `https://api.salesforce.com/platform/mcp/v1/sandbox/...`.

Hosted MCP requires an **External Client App**. A Connected App cannot be the Cursor MCP client. The existing `H360_MCP` app may be either type. Check both places before creating a second app.

## Stop conditions

Stop and tell the user, without changing Setup, if any of these are true:

- The browser is not logged into `aciworldwide--rcaaipoc`. Ask the user to sign in as an admin in this browser. Do not ask them to paste a password into chat.
- Quick Find has no **External Client App Manager** and no **App Manager** result for `H360_MCP`, and the user cannot create an External Client App.
- The only available run-as user would be a new user that does not exist yet. Ask which existing username to use.

## 1. Open the sandbox

1. Open `https://aciworldwide--rcaaipoc.sandbox.lightning.force.com/`.
2. Confirm the domain is `aciworldwide--rcaaipoc`. If Salesforce shows a different org, stop.
3. Open Setup (gear → Setup).

## 2. Find the app

1. In Quick Find, enter `External Client App Manager` and open it.
2. Search for `H360_MCP`.
3. If it is not there, Quick Find `App Manager`, search `H360_MCP`, and open that Connected App.
4. Write down which one you found: **External Client App** or **Connected App**.

### When it is only a Connected App

Hosted MCP will not use it. Do both of the following:

- On that Connected App, still complete [section 4](#4-assign-the-client-credentials-user) so the headless CLI can log in with the consumer key it already has.
- Create a new External Client App for Cursor, using [section 3](#3-oauth-settings-on-the-external-client-app). Name it `H360 MCP Cursor`. Its consumer key is new. Cursor must use that new key, not the Connected App key.

### When `H360_MCP` is already an External Client App

Edit that app. Do not create a second one.

## 3. OAuth settings on the External Client App

Open the app, edit OAuth settings, and set all of the following. Add callback URLs. Do not remove callback URLs that are already saved.

**Enable OAuth** must be on.

Callback URLs to have on the app:

- `https://www.cursor.com/agents/mcp/oauth/callback` (required for this cloud agent)
- `http://localhost:8787/callback`
- `cursor://anysphere.cursor-mcp/oauth/callback`

OAuth scopes. Select these two and leave broader scopes off. Do not add **Full access** (`full`).

- **Access Salesforce hosted MCP servers** (`mcp_api`). The picker may label this **Access MCP servers**.
- **Perform requests at any time** (`refresh_token` / `offline_access`).

Security:

- Select **Issue JSON Web Token (JWT)-based access tokens for named users**. Without this, MCP calls fail with `JWT Token is required` even after login.
- Leave **Require Secret for Web Server Flow** off. Cursor signs in as a public client with PKCE and does not send the consumer secret.
- Leave **Require Secret for Refresh Token Flow** off so Cursor can refresh tokens without a consumer secret.
- Enable **Require Proof Key for Code Exchange (PKCE) extension for Supported Authorization Flows**.
- Leave **JWT Bearer Flow** off. That flow needs a certificate and is not this setting.

Flows that the CLI diagnosis showed were missing:

- Enable **Client Credentials Flow**.
- Enable **Device Flow**.

Save.

A brand-new External Client App can take up to 30 minutes to propagate. `invalid_client_id` during that window means wait, not create another app.

## 4. Assign the client-credentials user

This is the setting behind `no client credentials user enabled`. Enabling the flow checkbox is not enough. A run-as user has to be saved.

1. Open the app's **Policies** or **Manage** page (on a Connected App this is **Manage** → **Edit Policies**).
2. Find the client-credentials user field. The label is one of **Run As**, **Run As (Username)**, or **Client Credentials User**.
3. Set it to an existing active user who has **API Enabled**. Ask the user which username if more than one integration user exists. Do not create a user and do not create CRM records.
4. Save.
5. Re-open the policy and confirm the username is still displayed.

If **Permitted Users** is **Admin approved users are pre-authorized**, do not switch it to all users. Assign the matching permission set to the admin who will complete the Cursor login, and to the client-credentials run-as user.

## 5. Confirm device flow

The CLI error was `device flow is not enabled for the app`.

1. Re-open OAuth settings.
2. Confirm **Enable Device Flow** is checked.
3. If it was off, check it and save again.

## 6. Copy the consumer key

1. Open **Settings** → **Consumer Key and Secret** (or the Connected App's consumer key page).
2. Copy the **Consumer Key** only.
3. Leave the consumer secret where it already is. Do not paste the secret into Cursor, into `sfdc-consumer-key.md`, or into chat.

If you created `H360 MCP Cursor` in section 2, this new key is the one Cursor must use.

## 7. Activate the hosted MCP server and copy its URL

1. In Setup, Quick Find `MCP Servers` and open it.
2. Activate a server if none is active. Prefer the read-only standard server. Use a broader server only if the user explicitly wants create, update, or delete through MCP.
3. Copy the **Server URL** from that page.

Use this URL when the read-only standard server is the one you activated:

`https://api.salesforce.com/platform/mcp/v1/sandbox/platform/sobject-reads`

Sandbox variants, if Setup shows a different server name:

| Server | URL |
| --- | --- |
| Read-only | `https://api.salesforce.com/platform/mcp/v1/sandbox/platform/sobject-reads` |
| All operations | `https://api.salesforce.com/platform/mcp/v1/sandbox/platform/sobject-all` |
| Custom `H360_MCP` | `https://api.salesforce.com/platform/mcp/v1/sandbox/custom/H360_MCP` |

The copied URL must start with `https://api.salesforce.com/platform/mcp/v1/sandbox/`. Reject the callback URL `https://aciworldwide--rcaaipoc.sandbox.my.salesforce.com/services/authcallback/H360_MCP` if it appears in an MCP URL field.

## 8. Point Cursor at that URL and key

1. Open `https://cursor.com/dashboard`.
2. Go to **Plugins** → **Salesforce** → **Configure**.
3. Set **Salesforce MCP server URL** to the Server URL from section 7.
4. Set **Salesforce Consumer Key** to the consumer key from section 6.
5. Save.
6. Open **Integrations**.
7. Reconnect **Salesforce**.
8. Reconnect **Salesforce-headless-360**.
9. Complete the Salesforce login prompt in this browser and approve the app. Stay on `aciworldwide--rcaaipoc`.

If **Salesforce-headless-360** has its own URL field, set that field to the same hosted Server URL. Do not leave it on the auth callback.

## 9. Check the result before you finish

All of these must be true:

- The External Client App used by Cursor has both callback `https://www.cursor.com/agents/mcp/oauth/callback` and scopes `mcp_api` and `refresh_token`.
- **Issue JSON Web Token (JWT)-based access tokens for named users** is selected.
- **Require Secret for Web Server Flow** is off.
- **Require Secret for Refresh Token Flow** is off and PKCE is required.
- Client-credentials **Run As** shows an existing username.
- **Device Flow** is enabled.
- An MCP server is active and its Server URL is the hosted sandbox URL, not the auth callback.
- Cursor Integrations shows **Salesforce** and **Salesforce-headless-360** connected, or the Salesforce login page was completed without an error.

Then tell the user the Server URL you saved (not the consumer secret) and ask them to rerun the cloud agent's read-only Salesforce check.

## If login still fails

| What you see | What to do |
| --- | --- |
| `invalid_client_id` | The new External Client App is still propagating. Wait up to 30 minutes and retry the Cursor login. Do not create another app. |
| `invalid_scope` | Add **Access Salesforce hosted MCP servers** and **Perform requests at any time**, then save and reconnect. |
| `JWT Token is required` or `Invalid token` after login | Turn on **Issue JSON Web Token (JWT)-based access tokens for named users**, save, and sign in again. |
| MCP URL returns 404 after login | The server is not active, or the URL is a production URL (`/platform/mcp/v1/platform/...`) for this sandbox. Use a `/sandbox/` URL from Setup. |
| `no client credentials user enabled` on a later CLI retry | Section 4 did not stick. Re-open Policies and set Run As again. |
| `device flow is not enabled for the app` | Section 5 did not stick. Re-open OAuth settings and save Enable Device Flow. |

Cursor's browser login is what connects the MCP servers. Client credentials and device flow are what let the headless `sf` CLI log in afterward. Finish section 8 even after sections 4 and 5 succeed.
