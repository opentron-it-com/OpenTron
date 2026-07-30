package org.opentron.backend.connectors.impl;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import org.opentron.backend.connectors.DataConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Gmail connector via IMAP using an App Password.
 * credentialToken format: "email:apppassword"
 * Fetches up to 500 messages per sync from INBOX, sorting newest-first.
 */
@Component
public class GmailImapConnector implements DataConnector {

    private static final Logger logger = LoggerFactory.getLogger(GmailImapConnector.class);
    private static final String IMAP_HOST = "imap.gmail.com";
    private static final int    IMAP_PORT = 993;
    private static final int    MAX_MESSAGES = 500;

    private String lastError;

    @Override public String id()        { return "gmail_imap"; }
    @Override public String name()      { return "Gmail"; }
    @Override public String lastError() { return lastError; }

    @Override
    public boolean connect(String credentialToken) {
        if (credentialToken == null || !credentialToken.contains(":")) {
            lastError = "Credential must be email:app-password";
            return false;
        }
        String[] parts = credentialToken.split(":", 2);
        try (Store store = openStore(parts[0].trim(), parts[1].trim())) {
            logger.info("[GmailImap] Connection verified for {}", parts[0]);
            return true;
        } catch (Exception e) {
            lastError = "IMAP auth failed: " + e.getMessage();
            logger.warn("[GmailImap] connect failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<Map<String, Object>> fetchDocuments(String credentialToken, String since) {
        List<Map<String, Object>> docs = new ArrayList<>();
        if (credentialToken == null || !credentialToken.contains(":")) return docs;

        String[] parts = credentialToken.split(":", 2);
        String email    = parts[0].trim();
        String password = parts[1].trim();

        try (Store store = openStore(email, password)) {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            Message[] messages;
            if (since != null) {
                Date sinceDate = Date.from(Instant.parse(since));
                messages = inbox.search(new ReceivedDateTerm(ComparisonTerm.GE, sinceDate));
            } else {
                int total = inbox.getMessageCount();
                int start = Math.max(1, total - MAX_MESSAGES + 1);
                messages = inbox.getMessages(start, total);
            }

            // Fetch in bulk (avoids per-message round trips)
            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.ENVELOPE);
            fp.add(FetchProfile.Item.FLAGS);
            inbox.fetch(messages, fp);

            for (Message msg : messages) {
                try {
                    String subject = msg.getSubject() != null ? msg.getSubject() : "(no subject)";
                    String from    = msg.getFrom() != null && msg.getFrom().length > 0
                                     ? msg.getFrom()[0].toString() : "";
                    String ts      = msg.getReceivedDate() != null
                                     ? msg.getReceivedDate().toInstant().toString()
                                     : Instant.now().toString();
                    String content = extractText(msg);
                    String msgId   = String.valueOf(((UIDFolder) inbox).getUID(msg));

                    Map<String, Object> doc = new HashMap<>();
                    doc.put("id",      "gmail:" + msgId);
                    doc.put("title",   "From " + from + ": " + subject);
                    doc.put("content", "Subject: " + subject + "\nFrom: " + from + "\n\n" + content);
                    doc.put("url",     "");
                    doc.put("ts",      ts);
                    docs.add(doc);
                } catch (Exception e) {
                    logger.debug("[GmailImap] Skipping message: {}", e.getMessage());
                }
            }

            inbox.close(false);
            logger.info("[GmailImap] Fetched {} messages", docs.size());
        } catch (Exception e) {
            lastError = e.getMessage();
            logger.error("[GmailImap] fetchDocuments failed", e);
        }
        return docs;
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Store openStore(String email, String password) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.store.protocol",    "imaps");
        props.put("mail.imaps.host",        IMAP_HOST);
        props.put("mail.imaps.port",        String.valueOf(IMAP_PORT));
        props.put("mail.imaps.ssl.enable",  "true");
        props.put("mail.imaps.timeout",     "30000");
        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");
        store.connect(IMAP_HOST, email, password);
        return store;
    }

    private String extractText(Message msg) throws Exception {
        Object content = msg.getContent();
        if (content instanceof String s) return s;
        if (content instanceof MimeMultipart mp) return extractFromMultipart(mp);
        return "";
    }

    private String extractFromMultipart(MimeMultipart mp) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mp.getCount(); i++) {
            BodyPart part = mp.getBodyPart(i);
            String ct = part.getContentType().toLowerCase();
            if (ct.startsWith("text/plain")) {
                sb.append(part.getContent());
            } else if (ct.startsWith("multipart/")) {
                sb.append(extractFromMultipart((MimeMultipart) part.getContent()));
            }
        }
        return sb.toString();
    }
}
