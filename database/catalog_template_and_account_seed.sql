-- Biller catalog, email template and account opening-balance updates.
-- Oracle SQL / PL/SQL. This script is idempotent and can be rerun from DB Navigator.

DECLARE
    PROCEDURE upsert_biller(
        p_id       VARCHAR2,
        p_code     VARCHAR2,
        p_name     VARCHAR2,
        p_category VARCHAR2
    ) IS
    BEGIN
        MERGE INTO BILLER_CATALOG target
        USING (SELECT p_code AS BILLER_CODE FROM dual) source
        ON (UPPER(target.BILLER_CODE) = UPPER(source.BILLER_CODE))
        WHEN MATCHED THEN UPDATE SET
            target.BILLER_NAME = p_name,
            target.CATEGORY = p_category,
            target.STATUS = 'ACTIVE',
            target.UPDATED_AT = SYSTIMESTAMP
        WHEN NOT MATCHED THEN INSERT (
            BILLER_ID, BILLER_CODE, BILLER_NAME, CATEGORY, STATUS, CREATED_AT, UPDATED_AT
        ) VALUES (
            p_id, p_code, p_name, p_category, 'ACTIVE', SYSTIMESTAMP, SYSTIMESTAMP
        );
    END;
BEGIN
    upsert_biller('10000000-0000-0000-0000-000000000001', 'TANGEDCO',
        'Tamil Nadu Generation and Distribution Corporation', 'ELECTRICITY');
    upsert_biller('10000000-0000-0000-0000-000000000002', 'BESCOM',
        'Bangalore Electricity Supply Company', 'ELECTRICITY');
    upsert_biller('10000000-0000-0000-0000-000000000003', 'CMWSSB',
        'Chennai Metropolitan Water Supply and Sewerage Board', 'WATER');
    upsert_biller('10000000-0000-0000-0000-000000000004', 'BWSSB',
        'Bangalore Water Supply and Sewerage Board', 'WATER');
    upsert_biller('10000000-0000-0000-0000-000000000005', 'IGL',
        'Indraprastha Gas Limited', 'GAS');
    upsert_biller('10000000-0000-0000-0000-000000000006', 'MGL',
        'Mahanagar Gas Limited', 'GAS');
    upsert_biller('10000000-0000-0000-0000-000000000007', 'JIO',
        'Reliance Jio', 'TELECOM');
    upsert_biller('10000000-0000-0000-0000-000000000008', 'AIRTEL',
        'Bharti Airtel', 'TELECOM');
    upsert_biller('10000000-0000-0000-0000-000000000009', 'ACT_FIBERNET',
        'ACT Fibernet', 'INTERNET');
    upsert_biller('10000000-0000-0000-0000-000000000010', 'TATA_PLAY_FIBER',
        'Tata Play Fiber', 'INTERNET');
    upsert_biller('10000000-0000-0000-0000-000000000011', 'LIC',
        'Life Insurance Corporation of India', 'INSURANCE');
    upsert_biller('10000000-0000-0000-0000-000000000012', 'HDFC_LIFE',
        'HDFC Life Insurance', 'INSURANCE');
    upsert_biller('10000000-0000-0000-0000-000000000013', 'NHAI_FASTAG',
        'NHAI FASTag Recharge', 'OTHER');
    upsert_biller('10000000-0000-0000-0000-000000000014', 'BAJAJ_FINSERV',
        'Bajaj Finserv Loan Repayment', 'OTHER');
END;
/

-- Preserve historical references while removing old placeholder billers from the active catalog.
UPDATE BILLER_CATALOG
SET STATUS = 'INACTIVE', UPDATED_AT = SYSTIMESTAMP
WHERE UPPER(BILLER_CODE) IN ('CODEX_ELEC', 'CODEX_NET', 'CODEX_TEMP');

DECLARE
    PROCEDURE upsert_template(
        p_id      VARCHAR2,
        p_name    VARCHAR2,
        p_type    VARCHAR2,
        p_subject VARCHAR2,
        p_html    CLOB,
        p_plain   CLOB
    ) IS
    BEGIN
        MERGE INTO EMAIL_TEMPLATE target
        USING (SELECT p_name AS TEMPLATE_NAME FROM dual) source
        ON (target.TEMPLATE_NAME = source.TEMPLATE_NAME)
        WHEN MATCHED THEN UPDATE SET
            target.TEMPLATE_TYPE = p_type,
            target.SUBJECT_TEMPLATE = p_subject,
            target.HTML_BODY = p_html,
            target.PLAIN_TEXT_BODY = p_plain,
            target.VERSION = 2,
            target.ACTIVE = 1
        WHEN NOT MATCHED THEN INSERT (
            TEMPLATE_ID, TEMPLATE_NAME, TEMPLATE_TYPE, SUBJECT_TEMPLATE,
            HTML_BODY, PLAIN_TEXT_BODY, VERSION, ACTIVE, CREATED_AT
        ) VALUES (
            p_id, p_name, p_type, p_subject, p_html, p_plain, 2, 1, SYSTIMESTAMP
        );
    END;
BEGIN
    upsert_template('20000000-0000-0000-0000-000000000001', 'WELCOME', 'WELCOME',
        'Welcome to Oracle Banking, {{customerName}}',
        q'~<div style="font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937"><h2>Welcome to Oracle Banking</h2><p>Hello {{customerName}},</p><p>Your internet banking profile has been created successfully. You can now sign in, complete your customer profile, open accounts and use the banking services available to you.</p><p>For your security, never share your password, OTP or card credentials with anyone.</p></div>~',
        'Hello {{customerName}}, your Oracle Banking profile is ready. Never share your password or OTP.');

    upsert_template('20000000-0000-0000-0000-000000000002', 'LOGIN_ALERT', 'LOGIN_ALERT',
        'New login to your Oracle Banking account',
        q'~<div style="font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937"><h2>New login detected</h2><p>Hello {{customerName}},</p><p>A successful login to your Oracle Banking account was recorded at <strong>{{currentTime}}</strong>.</p><p>If this was not you, reset your password immediately and contact support.</p></div>~',
        'Hello {{customerName}}, a successful login was recorded at {{currentTime}}. If this was not you, reset your password immediately.');

    upsert_template('20000000-0000-0000-0000-000000000003', 'PASSWORD_RESET', 'PASSWORD_RESET',
        'Reset your Oracle Banking password',
        q'~<div style="font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937"><h2>Password reset requested</h2><p>We received a request to reset your Oracle Banking password.</p><p><a href="{{verificationLink}}">Use this secure link to reset your password</a>.</p><p>If you did not request this change, ignore this email.</p></div>~',
        'We received a password reset request. Continue at {{verificationLink}}. Ignore this email if you did not request it.');

    upsert_template('20000000-0000-0000-0000-000000000004', 'PASSWORD_RESET_OTP', 'PASSWORD_RESET',
        'Your Oracle Banking password reset code',
        q'~<div style="font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937"><h2>Password reset code</h2><p>Hello {{customerName}},</p><p>Enter this one-time code to verify your request:</p><div style="font-size:30px;font-weight:700;letter-spacing:8px">{{otpCode}}</div><p>This code expires in {{expiresInMinutes}} minutes. Never share it with anyone.</p></div>~',
        'Hello {{customerName}}, your password reset code is {{otpCode}}. It expires in {{expiresInMinutes}} minutes.');

    upsert_template('20000000-0000-0000-0000-000000000005', 'PASSWORD_CHANGED', 'PASSWORD_RESET',
        'Your Oracle Banking password was changed',
        q'~<div style="font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937"><h2>Password changed</h2><p>Hello {{customerName}},</p><p>Your Oracle Banking password was changed successfully at <strong>{{changedAt}}</strong>.</p><p>If you did not make this change, contact support immediately.</p></div>~',
        'Hello {{customerName}}, your password changed at {{changedAt}}. Contact support immediately if this was not you.');

    upsert_template('20000000-0000-0000-0000-000000000006', 'GENERIC_NOTIFICATION', 'GENERIC',
        'Oracle Banking notification',
        q'~<div style="font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937"><h2>Oracle Banking notification</h2><p>{{message}}</p><p>Sign in to Oracle Banking if this notification requires action.</p><p style="font-size:12px;color:#6b7280">This is an automated message. Please do not reply.</p></div>~',
        'Oracle Banking notification: {{message}}');

    upsert_template('20000000-0000-0000-0000-000000000007', 'LOAN_CREATED', 'LOAN',
        'Your Oracle Banking loan {{loanNumber}} is active',
        q'~<div style="font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937"><h2>Your loan is active</h2><p>Loan account <strong>{{loanNumber}}</strong> has been created.</p><p>Principal: <strong>{{principalAmount}}</strong><br>EMI: <strong>{{emiAmount}}</strong><br>Maturity: <strong>{{maturityDate}}</strong></p><p>Maintain sufficient balance before every EMI due date.</p></div>~',
        'Loan {{loanNumber}} for {{principalAmount}} is active. EMI: {{emiAmount}}. Maturity: {{maturityDate}}.');

    upsert_template('20000000-0000-0000-0000-000000000008', 'SCHEDULE_TRIGGERED', 'SCHEDULE',
        'Scheduled payment started',
        q'~<div style="font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937"><h2>Scheduled payment started</h2><p>Your scheduled payment is being processed.</p><p>{{message}}</p><p>You will receive another notification when processing completes.</p></div>~',
        'Scheduled payment started. {{message}} You will be notified when processing completes.');

    upsert_template('20000000-0000-0000-0000-000000000009', 'SCHEDULE_COMPLETED', 'SCHEDULE',
        'Scheduled payment completed',
        q'~<div style="font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937"><h2>Scheduled payment completed</h2><p>Your scheduled payment completed successfully.</p><p>{{message}}</p><p>Open Oracle Banking to review its final reference and account details.</p></div>~',
        'Scheduled payment completed successfully. {{message}}');

    upsert_template('20000000-0000-0000-0000-000000000010', 'SCHEDULE_FAILED', 'SCHEDULE',
        'Scheduled payment failed',
        q'~<div style="font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937"><h2>Scheduled payment failed</h2><p>We could not complete your scheduled payment.</p><p>{{message}}</p><p>Check the payment details and available balance before retrying.</p></div>~',
        'Scheduled payment failed. {{message}} Check the details and available balance before retrying.');

    upsert_template('20000000-0000-0000-0000-000000000011', 'CARD_APPLICATION_RECEIVED', 'CARD',
        'Card application received',
        q'~<div style="font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937"><h2>Card application received</h2><p>We received your {{cardProduct}} {{cardType}} card application.</p><p>It is pending administrative review. We will notify you when a decision is recorded.</p><p>No card has been issued at this stage.</p></div>~',
        'We received your {{cardProduct}} {{cardType}} card application. It is pending review.');

    upsert_template('20000000-0000-0000-0000-000000000012', 'CARD_APPLICATION_APPROVED', 'CARD',
        'Card application approved',
        q'~<div style="font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937"><h2>Card application approved</h2><p>Your {{cardProduct}} {{cardType}} card application was approved.</p><p>Card issuance will proceed using the approved details.</p><p>Review the card status and controls when it appears in Oracle Banking.</p></div>~',
        'Your {{cardProduct}} {{cardType}} card application was approved. Card issuance will now proceed.');

    upsert_template('20000000-0000-0000-0000-000000000013', 'CARD_APPLICATION_REJECTED', 'CARD',
        'Card application update',
        q'~<div style="font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937"><h2>Card application update</h2><p>Your card application was reviewed and was not approved.</p><p>No card was issued for this application.</p><p>Review the submitted details before applying again or contact support.</p></div>~',
        'Your card application was not approved. No card was issued.');

    upsert_template('20000000-0000-0000-0000-000000000014', 'LOAN_APPLICATION_RECEIVED', 'LOAN',
        'Loan application received',
        q'~<div style="font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937"><h2>Loan application received</h2><p>We received your {{loanType}} loan application.</p><p>Your financial and eligibility details are pending review.</p><p>No loan account exists until the application is approved.</p></div>~',
        'We received your {{loanType}} loan application. It is pending review and no loan is active yet.');

    upsert_template('20000000-0000-0000-0000-000000000015', 'LOAN_APPLICATION_APPROVED', 'LOAN',
        'Loan application approved',
        q'~<div style="font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937"><h2>Loan application approved</h2><p>Your {{loanType}} loan application was approved.</p><p>Loan creation and repayment scheduling will proceed using the approved terms.</p><p>Review the final EMI and maturity details when processing completes.</p></div>~',
        'Your {{loanType}} loan application was approved. Review the final terms when processing completes.');

    upsert_template('20000000-0000-0000-0000-000000000016', 'LOAN_APPLICATION_REJECTED', 'LOAN',
        'Loan application update',
        q'~<div style="font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937"><h2>Loan application update</h2><p>Your loan application was reviewed and was not approved.</p><p>No loan account or repayment schedule was created.</p><p>Review your information before applying again or contact support.</p></div>~',
        'Your loan application was not approved. No loan account was created.');

    upsert_template('20000000-0000-0000-0000-000000000017', 'REPORT_READY', 'REPORT',
        'Your {{reportType}} report is ready',
        q'~<div style="font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937"><h2>Your report is ready</h2><p>Your requested <strong>{{reportType}}</strong> report was generated successfully.</p><p>Use report reference <strong>{{reportId}}</strong> in Oracle Banking to view or download it.</p></div>~',
        'Your {{reportType}} report is ready. Use report reference {{reportId}} in Oracle Banking.');

    upsert_template('20000000-0000-0000-0000-000000000018', 'REPORT_FAILED', 'REPORT',
        'Your {{reportType}} report could not be generated',
        q'~<div style="font-family:Arial,sans-serif;max-width:640px;margin:auto;color:#1f2937"><h2>Report generation failed</h2><p>We could not generate your <strong>{{reportType}}</strong> report.</p><p>Reference: <strong>{{reportId}}</strong></p><p>Reason: {{reason}}</p><p>Correct the request details or try again later.</p></div>~',
        'Report {{reportId}} for {{reportType}} could not be generated: {{reason}}. Please try again later.');
END;
/

ALTER TABLE ACCOUNTS MODIFY (
    AVAILABLE_BALANCE DEFAULT 100000,
    LEDGER_BALANCE DEFAULT 100000
);

-- The balance rule now uses column defaults; remove the earlier trigger if it exists.
BEGIN
    EXECUTE IMMEDIATE 'DROP TRIGGER TRG_ACCOUNTS_AVAILABLE_BALANCE';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -4080 THEN
            RAISE;
        END IF;
END;
/

COMMIT;
