package com.endava.cats.model;

import com.endava.cats.model.strategy.*;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encapsulates various fuzzing strategies:
 * <ul>
 * <li>REPLACE - when the fuzzed value replaces the one generated by the PayloadGenerator</li>
 * <li>TRAIL - trails the current value with the given string</li>
 * <li>PREFIX - prefixes the current value with the given string</li>
 * <li>SKIP - doesn't do anything to the current value</li>
 * <li>NOOP - returns the given string</li>
 * </ul>
 */
public abstract class FuzzingStrategy {
    private static final Pattern ALL = Pattern.compile("^[\\p{C}\\p{Z}\\p{So}]+[\\p{C}\\p{Z}\\p{So}]*$");
    private static final Pattern WITHIN = Pattern.compile("[\\p{C}\\p{Z}\\p{So}]+");

    protected String data;

    public static FuzzingStrategy prefix() {
        return new PrefixFuzzingStrategy();
    }

    public static FuzzingStrategy noop() {
        return new NoopFuzzingStrategy();
    }

    public static FuzzingStrategy replace() {
        return new ReplaceFuzzingStrategy();
    }

    public static FuzzingStrategy skip() {
        return new SkipFuzzingStrategy();
    }

    public static FuzzingStrategy trail() {
        return new TrailFuzzingStrategy();
    }

    public static FuzzingStrategy insert() {
        return new InsertFuzzingStrategy();
    }

    public static String mergeFuzzing(String fuzzedValue, String suppliedValue) {
        FuzzingStrategy currentStrategy = fromValue(fuzzedValue);

        return currentStrategy.process(suppliedValue);
    }

    public static FuzzingStrategy fromValue(String value) {
        if (StringUtils.isBlank(value) || ALL.matcher(value).matches()) {
            return replace().withData(value);
        }
        if (isUnicodeControlChar(value.charAt(0)) || isUnicodeWhitespace(value.charAt(0)) || isUnicodeOtherSymbol(value.charAt(0))) {
            return prefix().withData(value.replaceAll("[^\\p{Z}\\p{C}\\p{So}]+", ""));
        }
        if (isUnicodeControlChar(value.charAt(value.length() - 1)) || isUnicodeWhitespace(value.charAt(value.length() - 1)) || isUnicodeOtherSymbol(value.charAt(value.length() - 1))) {
            return trail().withData(value.replaceAll("[^\\p{Z}\\p{C}\\p{So}]+", ""));
        }
        Matcher withinMatcher = WITHIN.matcher(value);
        if (withinMatcher.find()) {
            return insert().withData(withinMatcher.group());
        }

        return replace().withData(value);
    }

    public static String formatValue(String data) {
        if (data == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (char c : data.toCharArray()) {
            if (isUnicodeWhitespace(c) || isUnicodeControlChar(c) || isUnicodeOtherSymbol(c)) {
                builder.append(String.format("\\u%04x", (int) c));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static boolean isUnicodeControlChar(char c) {
        return Character.getType(c) == Character.CONTROL || Character.getType(c) == Character.FORMAT
                || Character.getType(c) == Character.PRIVATE_USE || Character.getType(c) == Character.SURROGATE;
    }

    private static boolean isUnicodeWhitespace(char c) {
        return Character.getType(c) == Character.LINE_SEPARATOR ||
                Character.getType(c) == Character.PARAGRAPH_SEPARATOR || Character.getType(c) == Character.SPACE_SEPARATOR;
    }

    private static boolean isUnicodeOtherSymbol(char c) {
        return Character.getType(c) == Character.OTHER_SYMBOL;
    }


    public FuzzingStrategy withData(String inner) {
        this.data = inner;
        return this;
    }

    public String getData() {
        return this.data;
    }

    public boolean isSkip() {
        return this.getClass().isAssignableFrom(SkipFuzzingStrategy.class);
    }

    @Override
    public String toString() {
        if (data != null) {
            return this.name() + " with " + data;
        }
        return this.name();
    }

    public String truncatedValue() {
        if (data != null) {
            String toPrint = data;
            if (data.length() > 30) {
                toPrint = data.substring(0, 30) + "...";
            }
            return this.name() + " with " + formatValue(toPrint);
        }
        return this.name();
    }

    public String process(Object value) {
        return this.process(String.valueOf(value));
    }

    public abstract String process(String value);

    public abstract String name();
}
