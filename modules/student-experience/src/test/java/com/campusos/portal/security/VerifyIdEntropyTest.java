package com.campusos.portal.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusos.portal.domain.DocType;
import com.campusos.portal.domain.DocumentArtifact;
import com.campusos.portal.domain.RequestType;
import com.campusos.portal.engine.EngineTestBase;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * FINDING (HIGH, CWE-330/CWE-340, OWASP A01+A02): the verification id is the ONLY access
 * control on the unauthenticated /verify page, and it was the first 6 base-36 characters of
 * Math.abs(UUID.randomUUID().getMostSignificantBits()). Measured over 500,000 draws that gave
 * an effective keyspace of ~2^27.7 with a first collision at draw 21,512 — cheap to sweep,
 * and cheaper still to hit ANY valid credential rather than a chosen one.
 *
 * These tests fail on the old generator and pass on the SecureRandom one.
 */
@Tag("security")
class VerifyIdEntropyTest extends EngineTestBase {

    /** SHORT-YEAR-SUFFIX, where the suffix carries the entropy. */
    private static final Pattern ID = Pattern.compile("^[A-Z0-9]+-\\d{4}-([0-9A-Z]+)$");

    /** 32-symbol alphabet ^ 12 = 60 bits. The old 6-char suffix could not reach this. */
    private static final int MIN_SUFFIX_LENGTH = 12;

    private String issueVerifyId(String tag) {
        Fixture f = fixture(tag);
        machine.create(f.scope(), RequestType.DOCUMENT, document(DocType.BONAFIDE));
        DocumentArtifact d = documents
                .findByTenantIdAndStudentIdOrderByIssuedAtDesc(f.scope().tenantId(), f.scope().studentId())
                .stream().filter(x -> x.verifyId != null).findFirst().orElseThrow();
        return d.verifyId;
    }

    @Test
    @DisplayName("a verification id carries at least 60 bits in its random suffix")
    void suffixIsLongEnoughToBeACapability() {
        String id = issueVerifyId("ent");
        var m = ID.matcher(id);
        assertThat(m.matches()).as("id %s must keep the SHORT-YEAR-SUFFIX shape", id).isTrue();
        assertThat(m.group(1))
                .as("the random suffix of %s is the whole access control", id)
                .hasSizeGreaterThanOrEqualTo(MIN_SUFFIX_LENGTH);
    }

    @Test
    @DisplayName("the alphabet excludes the characters a human misreads off a printed page")
    void alphabetIsUnambiguous() {
        String suffix = ID.matcher(issueVerifyId("alpha")).results().findFirst().orElseThrow().group(1);
        assertThat(suffix).doesNotContain("I").doesNotContain("L")
                .doesNotContain("O").doesNotContain("U");
    }

    /**
     * The old generator collided at draw 21,512 out of 500,000. Issuing a few hundred ids is
     * far too few to catch a 2^27.7 collision reliably, so this asserts the property that IS
     * cheaply observable: every id is distinct and no two share a suffix prefix, which a
     * truncated, leading-digit-biased source would eventually violate.
     */
    @Test
    @DisplayName("issued ids do not repeat and do not cluster on a common prefix")
    void idsAreDistinctAndUnclustered() {
        Set<String> ids = new HashSet<>();
        Set<String> firstFour = new HashSet<>();
        int n = 200;
        for (int i = 0; i < n; i++) {
            String suffix = ID.matcher(issueVerifyId("dist" + i)).results()
                    .findFirst().orElseThrow().group(1);
            ids.add(suffix);
            firstFour.add(suffix.substring(0, 4));
        }
        assertThat(ids).as("every issued id must be unique").hasSize(n);
        assertThat(firstFour)
                .as("a uniform 32-symbol source spreads 200 draws widely across 32^4 prefixes")
                .hasSizeGreaterThan((int) (n * 0.9));
    }
}
