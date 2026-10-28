package com.yg.liquidink.markdown

enum class CodeTokenKind {
    KEYWORD,
    STRING,
    COMMENT,
    NUMBER,
    FUNCTION,
    TAG,
    ATTRIBUTE,
    PROPERTY,
    VARIABLE,
}

data class CodeToken(
    val start: Int,
    val end: Int,
    val kind: CodeTokenKind,
)

/**
 * A small, dependency-free highlighter. It is intentionally not a full parser:
 * comments/strings are lexed first and everything else is layered around them.
 * This keeps large code blocks bounded and predictable on mobile devices.
 */
object SyntaxHighlighter {

    fun highlight(code: String, language: String, maxChars: Int = 48_000): List<CodeToken> {
        if (code.isEmpty() || code.length > maxChars) return emptyList()
        val normalized = normalize(language)
        val tokens = mutableListOf<CodeToken>()
        val claimed = BooleanArray(code.length)

        highlightCommentsAndStrings(code, normalized, tokens, claimed)

        when (normalized) {
            "json" -> highlightJson(code, tokens, claimed)
            "html", "xml", "svg" -> highlightMarkup(code, tokens, claimed)
            "css", "scss" -> highlightCss(code, tokens, claimed)
            else -> {
                highlightNumbers(code, tokens, claimed)
                highlightKeywords(code, normalized, tokens, claimed)
                highlightFunctions(code, tokens, claimed)
                if (normalized == "kotlin" || normalized == "java") {
                    highlightAnnotations(code, tokens, claimed)
                }
                if (normalized in setOf("shell", "bash", "zsh")) {
                    highlightVariables(code, tokens, claimed)
                }
            }
        }

        tokens.sortBy { it.start }
        return tokens
    }

    private fun highlightCommentsAndStrings(
        code: String,
        language: String,
        tokens: MutableList<CodeToken>,
        claimed: BooleanArray,
    ) {
        val pattern = baseLexerPattern(language) ?: return
        for (match in pattern.findAll(code)) {
            if (match.value.isEmpty()) continue
            val kind = if (isComment(match.value)) CodeTokenKind.COMMENT else {
                if (
                    language == "json" &&
                    match.value.startsWith('"') &&
                    nextNonWhitespace(code, match.range.last + 1) == ':'
                ) {
                    CodeTokenKind.PROPERTY
                } else {
                    CodeTokenKind.STRING
                }
            }
            addToken(tokens, claimed, match.range.first, match.range.last + 1, kind)
        }
    }

    private fun highlightNumbers(
        code: String,
        tokens: MutableList<CodeToken>,
        claimed: BooleanArray,
    ) {
        for (match in NUMBER.findAll(code)) {
            addToken(tokens, claimed, match.range.first, match.range.last + 1, CodeTokenKind.NUMBER)
        }
    }

    private fun highlightKeywords(
        code: String,
        language: String,
        tokens: MutableList<CodeToken>,
        claimed: BooleanArray,
    ) {
        val words = KEYWORDS[language] ?: KEYWORDS.getValue("generic")
        val pattern = Regex(
            """\b(?:${words.joinToString("|") { Regex.escape(it) }})\b""",
            if (language == "sql") setOf(RegexOption.IGNORE_CASE) else emptySet(),
        )
        for (match in pattern.findAll(code)) {
            addToken(tokens, claimed, match.range.first, match.range.last + 1, CodeTokenKind.KEYWORD)
        }
    }

    private fun highlightFunctions(
        code: String,
        tokens: MutableList<CodeToken>,
        claimed: BooleanArray,
    ) {
        for (match in FUNCTION.findAll(code)) {
            addToken(tokens, claimed, match.range.first, match.range.last + 1, CodeTokenKind.FUNCTION)
        }
    }

    private fun highlightAnnotations(
        code: String,
        tokens: MutableList<CodeToken>,
        claimed: BooleanArray,
    ) {
        for (match in ANNOTATION.findAll(code)) {
            addToken(tokens, claimed, match.range.first, match.range.last + 1, CodeTokenKind.ATTRIBUTE)
        }
    }

    private fun highlightVariables(
        code: String,
        tokens: MutableList<CodeToken>,
        claimed: BooleanArray,
    ) {
        for (match in SHELL_VARIABLE.findAll(code)) {
            addToken(tokens, claimed, match.range.first, match.range.last + 1, CodeTokenKind.VARIABLE)
        }
    }

    private fun highlightJson(
        code: String,
        tokens: MutableList<CodeToken>,
        claimed: BooleanArray,
    ) {
        for (match in JSON_LITERAL.findAll(code)) {
            addToken(tokens, claimed, match.range.first, match.range.last + 1, CodeTokenKind.KEYWORD)
        }
        highlightNumbers(code, tokens, claimed)
    }

    private fun highlightMarkup(
        code: String,
        tokens: MutableList<CodeToken>,
        claimed: BooleanArray,
    ) {
        for (match in MARKUP_TAG.findAll(code)) {
            addToken(tokens, claimed, match.range.first, match.range.last + 1, CodeTokenKind.TAG)
        }
        for (match in MARKUP_ATTRIBUTE.findAll(code)) {
            // A dot/colon inside a tag name should not be reported as an attribute.
            val before = code.getOrNull(match.range.first - 1)
            if (before == '<' || before == '/' || before == '!' || before == '?') continue
            addToken(
                tokens,
                claimed,
                match.range.first,
                match.range.last + 1,
                CodeTokenKind.ATTRIBUTE,
            )
        }
    }

    private fun highlightCss(
        code: String,
        tokens: MutableList<CodeToken>,
        claimed: BooleanArray,
    ) {
        for (match in CSS_PROPERTY.findAll(code)) {
            addToken(
                tokens,
                claimed,
                match.range.first,
                match.range.last + 1,
                CodeTokenKind.PROPERTY,
            )
        }
        for (match in CSS_AT_RULE.findAll(code)) {
            addToken(tokens, claimed, match.range.first, match.range.last + 1, CodeTokenKind.KEYWORD)
        }
        for (match in CSS_COLOR.findAll(code)) {
            addToken(tokens, claimed, match.range.first, match.range.last + 1, CodeTokenKind.NUMBER)
        }
        highlightNumbers(code, tokens, claimed)
    }

    private fun addToken(
        tokens: MutableList<CodeToken>,
        claimed: BooleanArray,
        start: Int,
        end: Int,
        kind: CodeTokenKind,
    ) {
        if (start < 0 || end > claimed.size || start >= end) return
        for (i in start until end) {
            if (claimed[i]) return
        }
        for (i in start until end) claimed[i] = true
        tokens += CodeToken(start = start, end = end, kind = kind)
    }

    private fun isComment(value: String): Boolean =
        value.startsWith("//") ||
            value.startsWith("/*") ||
            value.startsWith("<!--") ||
            value.startsWith("#") ||
            value.startsWith("--") ||
            (value.startsWith("(*") && value.endsWith("*)"))

    private fun nextNonWhitespace(code: String, from: Int): Char? {
        var i = from
        while (i < code.length && code[i].isWhitespace()) i++
        return code.getOrNull(i)
    }

    private fun baseLexerPattern(language: String): Regex? {
        val commentAlternatives = mutableListOf<String>()
        val stringAlternatives = mutableListOf<String>()

        when (language) {
            "python" -> {
                commentAlternatives += """#[^\n]*"""
                stringAlternatives += """[rRbBuUfF]{0,2}'''[\s\S]*?'''"""
                stringAlternatives += """[rRbBuUfF]{0,2}$TRIPLE_QUOTED"""
                stringAlternatives += """[rRbBuUfF]{0,2}$DOUBLE_QUOTED"""
                stringAlternatives += """[rRbBuUfF]{0,2}$SINGLE_QUOTED"""
            }
            "shell", "bash", "zsh" -> {
                commentAlternatives += """#[^\n]*"""
                stringAlternatives += """$DOUBLE_QUOTED_NO_NEWLINE"""
                stringAlternatives += """'(?:\\.|[^'\\])*'"""
            }
            "html", "xml", "svg" -> {
                commentAlternatives += """<!--[\s\S]*?-->"""
                stringAlternatives += """$DOUBLE_QUOTED_NO_NEWLINE"""
                stringAlternatives += """'(?:\\.|[^'\\])*'"""
            }
            "sql" -> {
                commentAlternatives += """--[^\n]*"""
                commentAlternatives += """/\*[\s\S]*?\*/"""
                stringAlternatives += """'(?:''|\\.|[^'\\])*'"""
                stringAlternatives += """$DOUBLE_QUOTED_NO_NEWLINE"""
            }
            "css", "scss" -> {
                commentAlternatives += """/\*[\s\S]*?\*/"""
                stringAlternatives += """$DOUBLE_QUOTED_NO_NEWLINE"""
                stringAlternatives += """'(?:\\.|[^'\\])*'"""
            }
            "json" -> {
                stringAlternatives += """$DOUBLE_QUOTED_NO_NEWLINE"""
            }
            "javascript", "typescript", "kotlin", "java", "c", "cpp", "csharp",
            "rust", "go", "swift", "php", "generic",
            -> {
                commentAlternatives += """//[^\n]*"""
                commentAlternatives += """/\*[\s\S]*?\*/"""
                stringAlternatives += """$TRIPLE_QUOTED"""
                stringAlternatives += """`(?:\\.|[^`\\])*`"""
                stringAlternatives += """$DOUBLE_QUOTED_NO_NEWLINE"""
                stringAlternatives += """$SINGLE_QUOTED"""
            }
            else -> {
                commentAlternatives += """//[^\n]*"""
                commentAlternatives += """/\*[\s\S]*?\*/"""
                commentAlternatives += """#[^\n]*"""
                stringAlternatives += """$DOUBLE_QUOTED_NO_NEWLINE"""
                stringAlternatives += """$SINGLE_QUOTED"""
            }
        }

        val alternatives = commentAlternatives + stringAlternatives
        if (alternatives.isEmpty()) return null
        return Regex(alternatives.joinToString("|") { "(?:$it)" })
    }

    fun normalize(language: String): String {
        val key = language.trim().lowercase().substringBefore(' ').substringBefore(',')
        return when (key) {
            "kt", "kts", "kotlin" -> "kotlin"
            "java" -> "java"
            "js", "jsx", "javascript", "mjs", "cjs" -> "javascript"
            "ts", "tsx", "typescript" -> "typescript"
            "py", "python", "python3" -> "python"
            "sh", "bash", "zsh", "shell", "console" -> "shell"
            "c", "h" -> "c"
            "cc", "cpp", "c++", "hpp", "cxx" -> "cpp"
            "cs", "csharp" -> "csharp"
            "rs", "rust" -> "rust"
            "go", "golang" -> "go"
            "json", "jsonc" -> "json"
            "html", "htm" -> "html"
            "xml", "svg", "xhtml" -> "xml"
            "css" -> "css"
            "scss", "sass" -> "scss"
            "sql", "mysql", "postgresql", "sqlite" -> "sql"
            "md", "markdown" -> "markdown"
            "yml", "yaml" -> "yaml"
            "swift" -> "swift"
            "php" -> "php"
            "" -> "generic"
            else -> key
        }
    }

    private val NUMBER = Regex(
        """(?<![\w.])(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|\d[\d_]*(?:\.\d+)?(?:[eE][+-]?\d+)?[fFdDlLuU]?)(?![\w.])""",
    )
    private val FUNCTION = Regex("""(?<![\w.])([A-Za-z_]\w*)(?=\s*\()""")
    private val ANNOTATION = Regex("""@[A-Za-z_]\w*""")
    private val SHELL_VARIABLE = Regex("""\$(?:\{[^}\n]+}|\w+)""")
    private val JSON_LITERAL = Regex("""\b(?:true|false|null)\b""")
    private val MARKUP_TAG = Regex("""</?[A-Za-z][\w:.-]*(?:\s|/?>)""")
    private val MARKUP_ATTRIBUTE = Regex("""[A-Za-z_:][\w:.-]*(?=\s*=)""")
    private val CSS_PROPERTY = Regex("""[-\w]+(?=\s*:)""")
    private val CSS_AT_RULE = Regex("""@[-\w]+""")
    private val CSS_COLOR = Regex("""#[0-9a-fA-F]{3,8}\b""")

    private const val DOUBLE_QUOTED = "\"(?:\\\\.|[^\"\\\\])*\""
    private const val DOUBLE_QUOTED_NO_NEWLINE = "\"(?:\\\\.|[^\"\\\\\\n])*\""
    private const val SINGLE_QUOTED = "'(?:\\\\.|[^'\\\\\\n])*'"
    private const val TRIPLE_QUOTED = "\"\"\"[\\s\\S]*?\"\"\""

    private val COMMON_KEYWORDS = listOf(
        "if", "else", "for", "while", "do", "return", "break", "continue", "true", "false",
        "null", "nil", "class", "interface", "enum", "struct", "fun", "function", "var",
        "val", "let", "const", "new", "this", "super", "try", "catch", "finally", "throw",
        "throws", "import", "package", "public", "private", "protected", "internal",
        "static", "final", "override", "open", "abstract", "sealed", "data", "object",
        "when", "switch", "case", "default", "typealias", "async", "await", "yield",
        "in", "is", "as", "out", "by", "where", "extends", "implements", "instanceof",
        "void", "long", "int", "short", "byte", "float", "double", "boolean", "char",
        "String", "List", "Map", "Set",
    )

    private val KEYWORDS: Map<String, List<String>> = mapOf(
        "generic" to COMMON_KEYWORDS,
        "kotlin" to COMMON_KEYWORDS + listOf(
            "suspend", "inline", "reified", "crossinline", "noinline", "lateinit", "companion",
            "init", "constructor", "get", "set", "field", "actual", "expect", "infix",
            "operator", "tailrec", "vararg", "annotation", "crossinline", "external",
        ),
        "java" to COMMON_KEYWORDS + listOf(
            "synchronized", "volatile", "transient", "native", "strictfp", "assert",
            "record", "permits",
        ),
        "javascript" to COMMON_KEYWORDS + listOf(
            "undefined", "typeof", "delete", "export", "from", "of", "get", "set",
            "implements", "interface", "static", "yield", "debugger", "with",
        ),
        "typescript" to COMMON_KEYWORDS + listOf(
            "type", "namespace", "declare", "readonly", "keyof", "infer", "unknown", "never",
            "any", "satisfies", "abstract", "implements", "interface", "module",
        ),
        "python" to listOf(
            "and", "as", "assert", "async", "await", "break", "class", "continue", "def",
            "del", "elif", "else", "except", "False", "finally", "for", "from", "global",
            "if", "import", "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass",
            "raise", "return", "True", "try", "while", "with", "yield", "match", "case",
        ),
        "shell" to listOf(
            "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done",
            "case", "esac", "function", "in", "select", "time", "local", "export", "readonly",
            "return", "exit", "echo", "cd", "source", "alias", "unset",
        ),
        "sql" to listOf(
            "select", "from", "where", "and", "or", "not", "null", "insert", "into", "values",
            "update", "set", "delete", "create", "table", "alter", "drop", "join", "left",
            "right", "inner", "outer", "on", "as", "group", "by", "order", "having", "limit",
            "offset", "distinct", "union", "all", "case", "when", "then", "else", "end",
            "primary", "key", "foreign", "references", "index", "exists", "between", "like",
        ),
        "c" to COMMON_KEYWORDS + listOf(
            "auto", "register", "typedef", "sizeof", "union", "volatile", "extern", "goto",
        ),
        "cpp" to COMMON_KEYWORDS + listOf(
            "namespace", "template", "typename", "using", "virtual", "friend", "operator",
            "delete", "new", "constexpr", "noexcept", "nullptr", "auto", "uint8_t",
        ),
        "csharp" to COMMON_KEYWORDS + listOf(
            "using", "namespace", "readonly", "record", "async", "await", "nameof",
            "delegate", "event", "foreach", "params", "out", "ref", "lock",
        ),
        "rust" to listOf(
            "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else",
            "enum", "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop",
            "match", "mod", "move", "mut", "pub", "ref", "return", "self", "Self", "static",
            "struct", "super", "trait", "true", "type", "unsafe", "use", "where", "while",
            "Box", "Option", "Result", "String", "Vec", "Some", "None", "Ok", "Err",
        ),
        "go" to listOf(
            "break", "case", "chan", "const", "continue", "default", "defer", "else",
            "fallthrough", "for", "func", "go", "goto", "if", "import", "interface", "map",
            "package", "range", "return", "select", "struct", "switch", "type", "var",
            "true", "false", "nil", "nil", "string", "int", "bool", "error", "byte", "rune",
        ),
        "swift" to listOf(
            "associatedtype", "class", "deinit", "enum", "extension", "fileprivate", "func",
            "import", "init", "inout", "internal", "let", "open", "operator", "private",
            "protocol", "public", "rethrows", "static", "struct", "subscript", "typealias",
            "var", "break", "case", "continue", "default", "defer", "do", "else", "fallthrough",
            "for", "guard", "if", "in", "repeat", "return", "switch", "where", "while",
            "as", "Any", "catch", "false", "is", "nil", "super", "self", "Self", "throw",
            "throws", "true", "try", "async", "await",
        ),
        "php" to COMMON_KEYWORDS + listOf(
            "echo", "elseif", "endif", "endforeach", "function", "global", "include",
            "include_once", "require", "require_once", "namespace", "use", "trait", "yield",
        ),
        "json" to listOf("true", "false", "null"),
        "yaml" to listOf("true", "false", "null", "yes", "no", "on", "off"),
    )
}
