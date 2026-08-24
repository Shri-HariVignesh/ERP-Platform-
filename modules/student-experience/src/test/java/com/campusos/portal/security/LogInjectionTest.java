package com.campusos.portal.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusos.portal.domain.Actor;
import com.campusos.portal.domain.Event;
import com.campusos.portal.engine.EngineTestBase;
import com.campusos.portal.engine.IllegalTransitionException;
import com.campusos.portal.engine.RequestStateMachine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FINDING (MEDIUM, CWE-117, OWASP A09): the caller-supplied request id was interpolated raw
 * into an exception message that is then logged. A CRLF in the id ended the log record early
 * and turned the remainder into a standalone record. Reproduced against the running app with
 *
 *   POST /sim/requests/req_x%0d%0a2026-01-01T00:00:00.000+05:30++ERROR+FORGED/advance
 *
 * which produced a real record ending "guard rejected: request req_x" plus a separate,
 * genuine-looking forged entry.
 *
 * This system's whole claim is a complete audit trail, so a caller who can author log records
 * can fabricate history or bury their own activity.
 */
@Tag("security")
class LogInjectionTest extends EngineTestBase {

    private static final String CRLF_ID =
            "req_x\r\n2026-01-01T00:00:00.000+05:30  ERROR 1 --- [main] FORGED : granted";

    @Test
    @DisplayName("a CRLF-bearing request id cannot author a log record")
    void requestIdCannotForgeALogLine() {
        Fixture f = fixture("loginj");
        String message = null;
        try {
            machine.transition(f.scope(), CRLF_ID, Event.APPROVE, Actor.FACULTY, null);
        } catch (IllegalTransitionException e) {
            message = e.getMessage();
        }
        assertThat(message).as("the lookup must fail and produce the scope message").isNotNull();
        assertThat(message)
                .as("no control character may reach a message that gets logged")
                .doesNotContain("\r").doesNotContain("\n");
        assertThat(message.lines().count())
                .as("the message must stay a single log record").isEqualTo(1L);
    }

    @Test
    @DisplayName("every control character is stripped, not just CR and LF")
    void allControlCharactersAreStripped() {
        assertThat(RequestStateMachine.safeForMessage("req_\tabcd\re\nf\bg"))
                .isEqualTo("req_abcdefg");
    }

    @Test
    @DisplayName("an absurdly long id is truncated rather than flooding the log")
    void longIdIsTruncated() {
        assertThat(RequestStateMachine.safeForMessage("r".repeat(10_000)))
                .hasSizeLessThan(80).endsWith("...");
    }

    @Test
    @DisplayName("an ordinary id passes through untouched, so ids stay correlatable")
    void ordinaryIdIsUnchanged() {
        assertThat(RequestStateMachine.safeForMessage("req_7ee4bc42")).isEqualTo("req_7ee4bc42");
    }
}
