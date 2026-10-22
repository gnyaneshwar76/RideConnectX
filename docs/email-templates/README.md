# Firebase Auth email templates

Public-safe. Placeholders used: %LINK% %EMAIL% %NEW_EMAIL% %APP_NAME% %DISPLAY_NAME% (APP_NAME is not used; the name is written out).
Apply with the Identity Platform config PATCH, field `notification.sendEmail.<template>`, updateMask on `body`, `subject`, `bodyFormat`.
Keep `senderLocalPart`, `senderDisplayName`, `replyTo` as they are.

| File | Template object | `subject` | `body` / `bodyFormat` |
|---|---|---|---|
| verify-email.html / .txt | `verifyEmailTemplate` | Confirm your email for RideConnectX | .html = `HTML`; .txt = `PLAIN_TEXT` |
| reset-password.html / .txt | `resetPasswordTemplate` | Reset your RideConnectX password | same |
| change-email.html / .txt | `changeEmailTemplate` | Your RideConnectX sign-in email was changed | same |

- A template holds ONE body. Use the .html files; the .txt files are the alternative if the owner wants plain text (set `bodyFormat` to `PLAIN_TEXT`). Firebase does not send both.
- Order: verify-email, reset-password, change-email. Send a test of each afterwards.
- Not editable: the email-link sign-in mail ("Sign in to ...") has no template object in this config schema (only reset, verify, change email and revertSecondFactorAddition exist). It uses Firebase's fixed text.
- Expiry is set by Firebase, so the mails say "a limited time", not a number.
