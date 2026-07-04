package tech.rawden.ara.ui;

import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight syntax colouring for fenced code blocks. Language is taken from the flexmark
 * {@code FencedCodeBlock} info string (e.g. {@code ```java}).
 */
final class CodeSyntaxHighlighter {

    private static final Font CODE_FONT = Font.font("Menlo", 12);

    private static final String KW =
            "\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|"
                    + "do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|"
                    + "instanceof|int|interface|long|native|new|package|private|protected|public|return|"
                    + "short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|"
                    + "void|volatile|while|true|false|null|var|record|sealed|permits|yield|async|await|"
                    + "def|elif|elif|except|from|lambda|pass|raise|None|True|False|function|let|const|"
                    + "export|import|return|typeof|undefined|null|void|interface|type|namespace|struct|"
                    + "fn|mut|pub|use|impl|trait|match|loop|move|ref|self|Self|where|dyn|crate|mod|"
                    + "SELECT|FROM|WHERE|INSERT|UPDATE|DELETE|CREATE|DROP|TABLE|JOIN|ON|AND|OR|NOT|NULL|"
                    + "AS|INTO|VALUES|SET|ORDER|BY|GROUP|HAVING|LIMIT|IN|IS|LIKE|BETWEEN|CASE|WHEN|THEN|"
                    + "END|PRIMARY|KEY|FOREIGN|REFERENCES|INDEX|UNIQUE|IF|ELSE|ENDIF|fi|echo|cd|pwd|"
                    + "sudo|chmod|chown|grep|sed|awk|cat|ls|mkdir|rm|cp|mv|source|export|alias)\\b";

    private static final Pattern COMMENT =
            Pattern.compile("//[^\n]*|/\\*[\\s\\S]*?\\*/|#[^\n]*|<!--[\\s\\S]*?-->");
    private static final Pattern STRING =
            Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`(?:\\\\.|[^`\\\\])*`");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b");
    private static final Pattern KEYWORD = Pattern.compile(KW, Pattern.CASE_INSENSITIVE);

    private CodeSyntaxHighlighter() {}

    static List<Text> highlight(String code, String language) {
        if (code == null || code.isBlank()) {
            return List.of(plain(code == null ? "" : code));
        }
        var lang = normalizeLanguage(language);
        if ("text".equals(lang) || "plain".equals(lang) || lang.isBlank()) {
            return splitLinesPlain(code);
        }
        return splitLinesHighlighted(code, lang);
    }

    private static List<Text> splitLinesPlain(String code) {
        var lines = code.split("\n", -1);
        var out = new ArrayList<Text>(lines.length * 2);
        for (int i = 0; i < lines.length; i++) {
            out.add(plain(lines[i]));
            if (i < lines.length - 1) {
                out.add(plain("\n"));
            }
        }
        return out;
    }

    private static List<Text> splitLinesHighlighted(String code, String lang) {
        var lines = code.split("\n", -1);
        var out = new ArrayList<Text>();
        if (lines.length == 1) {
            return highlightLine(lines[0], lang);
        }
        for (var line : lines) {
            out.addAll(highlightLine(line, lang));
        }
        return out;
    }

    private static List<Text> highlightLine(String line, String lang) {
        if ("json".equals(lang) || "yaml".equals(lang) || "yml".equals(lang)) {
            return highlightJsonLike(line);
        }
        if ("bash".equals(lang) || "sh".equals(lang) || "shell".equals(lang) || "zsh".equals(lang)) {
            return highlightWithPatterns(line, List.of(COMMENT, STRING, KEYWORD, NUMBER));
        }
        if ("sql".equals(lang)) {
            return highlightWithPatterns(line, List.of(COMMENT, STRING, KEYWORD, NUMBER));
        }
        return highlightWithPatterns(line, List.of(COMMENT, STRING, KEYWORD, NUMBER));
    }

    private static List<Text> highlightJsonLike(String line) {
        var trimmed = line.trim();
        if (trimmed.startsWith("//") || trimmed.startsWith("#")) {
            return List.of(comment(line));
        }
        var keyMatch = Pattern.compile("^\\s*(\"[^\"]+\"|'[^']+')\\s*:").matcher(line);
        if (keyMatch.find()) {
            var out = new ArrayList<Text>();
            out.add(key(line.substring(0, keyMatch.end() - 1)));
            out.add(plain(":"));
            out.addAll(highlightWithPatterns(line.substring(keyMatch.end()), List.of(STRING, NUMBER)));
            return out;
        }
        return highlightWithPatterns(line, List.of(STRING, NUMBER, KEYWORD));
    }

    private static List<Text> highlightWithPatterns(String line, List<Pattern> patterns) {
        var spans = new ArrayList<Span>();
        for (var pattern : patterns) {
            var matcher = pattern.matcher(line);
            while (matcher.find()) {
                spans.add(new Span(matcher.start(), matcher.end(), styleFor(pattern)));
            }
        }
        spans.sort((a, b) -> Integer.compare(a.start, b.start));
        var merged = mergeSpans(spans);

        var out = new ArrayList<Text>();
        int pos = 0;
        for (var span : merged) {
            if (span.start > pos) {
                out.add(plain(line.substring(pos, span.start)));
            }
            out.add(styled(line.substring(span.start, span.end), span.style));
            pos = span.end;
        }
        if (pos < line.length()) {
            out.add(plain(line.substring(pos)));
        }
        if (out.isEmpty()) {
            out.add(plain(line));
        }
        return out;
    }

    private static List<Span> mergeSpans(List<Span> spans) {
        var merged = new ArrayList<Span>();
        for (var span : spans) {
            boolean overlaps = false;
            for (var existing : merged) {
                if (span.start < existing.end && span.end > existing.start) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                merged.add(span);
            }
        }
        return merged;
    }

    private static String styleFor(Pattern pattern) {
        if (pattern == COMMENT) {
            return "comment";
        }
        if (pattern == STRING) {
            return "string";
        }
        if (pattern == KEYWORD) {
            return "keyword";
        }
        if (pattern == NUMBER) {
            return "number";
        }
        return "plain";
    }

    private static String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "";
        }
        var lang = language.trim().toLowerCase(Locale.ROOT);
        var space = lang.indexOf(' ');
        if (space > 0) {
            lang = lang.substring(0, space);
        }
        return switch (lang) {
            case "js", "nodejs", "node" -> "javascript";
            case "ts", "tsx" -> "typescript";
            case "py" -> "python";
            case "sh", "shell", "zsh" -> "bash";
            case "yml" -> "yaml";
            case "c#", "cs" -> "csharp";
            case "cpp", "c++" -> "cpp";
            case "golang" -> "go";
            default -> lang;
        };
    }

    private static Text plain(String value) {
        return styled(value, "plain");
    }

    private static Text comment(String value) {
        return styled(value, "comment");
    }

    private static Text key(String value) {
        return styled(value, "type");
    }

    private static Text styled(String value, String styleClass) {
        var text = new Text(value);
        text.setFont(CODE_FONT);
        text.getStyleClass().add("ara-syntax-" + styleClass);
        return text;
    }

    private record Span(int start, int end, String style) {}
}