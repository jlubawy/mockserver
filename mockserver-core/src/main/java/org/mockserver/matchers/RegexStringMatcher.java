package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.NottableSchemaString;
import org.mockserver.model.NottableString;

import java.util.regex.PatternSyntaxException;

import static org.mockserver.model.NottableString.string;
import static org.slf4j.event.Level.DEBUG;

/**
 * @author jamesdbloom
 */
public class RegexStringMatcher extends BodyMatcher<NottableString> {

    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger"};
    private final MockServerLogger mockServerLogger;
    private final NottableString matcher;
    private final boolean controlPlaneMatcher;

    public RegexStringMatcher(MockServerLogger mockServerLogger, boolean controlPlaneMatcher) {
        this.mockServerLogger = mockServerLogger;
        this.controlPlaneMatcher = controlPlaneMatcher;
        this.matcher = null;
    }

    RegexStringMatcher(MockServerLogger mockServerLogger, NottableString matcher, boolean controlPlaneMatcher) {
        this.mockServerLogger = mockServerLogger;
        this.controlPlaneMatcher = controlPlaneMatcher;
        this.matcher = matcher;
    }

    public boolean matches(String matched) {
        return matches((MatchDifference) null, string(matched));
    }

    public boolean matches(final MatchDifference context, NottableString matched) {
        boolean result = matcher == null || matches(mockServerLogger, context, matcher, matched);
        return not != result;
    }

    public boolean matches(NottableString matcher, NottableString matched) {
        return matches(mockServerLogger, null, matcher, matched);
    }

    public boolean matches(MockServerLogger mockServerLogger, MatchDifference context, NottableString matcher, NottableString matched) {
        if (matcher instanceof NottableSchemaString && matched instanceof NottableSchemaString) {
            return controlPlaneMatcher && matchesByNottedStrings(mockServerLogger, context, matcher, matched);
        } else if (matcher instanceof NottableSchemaString) {
            return matchesBySchemas(mockServerLogger, context, (NottableSchemaString) matcher, matched);
        } else if (matched instanceof NottableSchemaString) {
            return controlPlaneMatcher && matchesBySchemas(mockServerLogger, context, (NottableSchemaString) matched, matcher);
        } else {
            return matchesByNottedStrings(mockServerLogger, context, matcher, matched);
        }
    }

    private boolean matchesByNottedStrings(MockServerLogger mockServerLogger, MatchDifference context, NottableString matcher, NottableString matched) {
        if (matcher.isNot() && matched.isNot()) {
            // mutual notted control plane match
            return matchesByStrings(mockServerLogger, context, matcher, matched);
        } else {
            // data plane & control plan match
            return (matcher.isNot() || matched.isNot()) ^ matchesByStrings(mockServerLogger, context, matcher, matched);
        }
    }

    private boolean matchesBySchemas(MockServerLogger mockServerLogger, MatchDifference context, NottableSchemaString schema, NottableString string) {
        return string.isNot() != schema.matches(mockServerLogger, context, string.getValue());
    }

    private boolean matchesByStrings(MockServerLogger mockServerLogger, MatchDifference context, NottableString matcher, NottableString matched) {
        final String matcherValue = matcher.getValue();
        if (StringUtils.isBlank(matcherValue)) {
            return true;
        } else {
            final String matchedValue = matched.getValue();
            if (matchedValue != null) {
                // match as exact string
                if (matchedValue.equals(matcherValue) || matchedValue.equalsIgnoreCase(matcherValue)) {
                    return true;
                }

                // match as regex - matcher -> matched (data plane or control plane)
                try {
                    if (matcher.matches(matchedValue)) {
                        return true;
                    }
                } catch (PatternSyntaxException pse) {
                    if (MockServerLogger.isEnabled(DEBUG) && mockServerLogger != null) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(DEBUG)
                                .setMessageFormat("error while matching regex [" + matcher + "] for string [" + matched + "] " + pse.getMessage())
                                .setThrowable(pse)
                        );
                    }
                }
                // match as regex - matched -> matcher (control plane only)
                try {
                    if (controlPlaneMatcher && matched.matches(matcherValue)) {
                        return true;
                    } else if (MockServerLogger.isEnabled(DEBUG) && matched.matches(matcherValue) && mockServerLogger != null) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(DEBUG)
                                .setMessageFormat("matcher{}would match{}if matcher was used for control plane")
                                .setArguments(matcher, matched)
                        );
                    }
                } catch (PatternSyntaxException pse) {
                    if (controlPlaneMatcher) {
                        if (MockServerLogger.isEnabled(DEBUG) && mockServerLogger != null) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(DEBUG)
                                    .setMessageFormat("error while matching regex [" + matched + "] for string [" + matcher + "] " + pse.getMessage())
                                    .setThrowable(pse)
                            );
                        }
                    }
                }
            }
        }
        if (context != null) {
            context.addDifference(mockServerLogger, "string or regex match failed expected:{}found:{}", matcher, matched);
        }

        return false;
    }

    public boolean isBlank() {
        return matcher == null || StringUtils.isBlank(matcher.getValue());
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
