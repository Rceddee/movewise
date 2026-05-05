package com.example.movewise.util

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

/**
 * Lightweight, dependency-free Markdown-to-SpannableString renderer.
 * Handles: **bold**, *italic*, # Heading, - and * bullet lists, --- dividers, and newlines.
 */
object MarkdownRenderer {

    fun render(raw: String): SpannableStringBuilder {
        val ssb = SpannableStringBuilder()

        val lines = raw.split("\n")
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()

            when {
                // Heading 1: # Title
                trimmed.startsWith("# ") -> {
                    val text = trimmed.removePrefix("# ")
                    val start = ssb.length
                    ssb.append(renderInline(text))
                    ssb.setSpan(StyleSpan(Typeface.BOLD), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(RelativeSizeSpan(1.4f), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                // Heading 2: ## Title
                trimmed.startsWith("## ") -> {
                    val text = trimmed.removePrefix("## ")
                    val start = ssb.length
                    ssb.append(renderInline(text))
                    ssb.setSpan(StyleSpan(Typeface.BOLD), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(RelativeSizeSpan(1.2f), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                // Heading 3: ### Title
                trimmed.startsWith("### ") -> {
                    val text = trimmed.removePrefix("### ")
                    val start = ssb.length
                    ssb.append(renderInline(text))
                    ssb.setSpan(StyleSpan(Typeface.BOLD), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(RelativeSizeSpan(1.1f), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                // Horizontal rule: --- or ___
                trimmed == "---" || trimmed == "___" || trimmed == "—" -> {
                    ssb.append("\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015")
                    val start = ssb.length - 15
                    ssb.setSpan(StyleSpan(Typeface.NORMAL), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                // Bullet: - item or * item
                (trimmed.startsWith("- ") || trimmed.startsWith("* ")) && trimmed.length > 2 -> {
                    val text = trimmed.substring(2)
                    val start = ssb.length
                    ssb.append(renderInline(text))
                    ssb.setSpan(BulletSpan(16), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                // Empty line → just add spacing
                trimmed.isEmpty() -> {
                    if (ssb.isNotEmpty() && ssb.last() != '\n') {
                        // only add a line break if there isn't one already
                        ssb.append("\n")
                    }
                }
                // Normal paragraph
                else -> {
                    ssb.append(renderInline(trimmed))
                }
            }

            // Newline after every non-empty line except the last
            if (trimmed.isNotEmpty() && index < lines.size - 1) {
                ssb.append("\n")
            }
        }

        return ssb
    }

    /**
     * Handles inline styles within a single line: **bold**, *italic*, `code`.
     */
    private fun renderInline(text: String): SpannableStringBuilder {
        val ssb = SpannableStringBuilder()
        var i = 0
        while (i < text.length) {
            when {
                // **bold**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        val start = ssb.length
                        ssb.append(text.substring(i + 2, end))
                        ssb.setSpan(StyleSpan(Typeface.BOLD), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = end + 2
                    } else {
                        ssb.append(text[i])
                        i++
                    }
                }
                // *italic*
                text.startsWith("*", i) && !text.startsWith("**", i) -> {
                    val end = text.indexOf("*", i + 1)
                    if (end != -1 && end != i + 1) {
                        val start = ssb.length
                        ssb.append(text.substring(i + 1, end))
                        ssb.setSpan(StyleSpan(Typeface.ITALIC), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = end + 1
                    } else {
                        ssb.append(text[i])
                        i++
                    }
                }
                // `code`
                text.startsWith("`", i) -> {
                    val end = text.indexOf("`", i + 1)
                    if (end != -1) {
                        val start = ssb.length
                        ssb.append(text.substring(i + 1, end))
                        ssb.setSpan(TypefaceSpan("monospace"), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = end + 1
                    } else {
                        ssb.append(text[i])
                        i++
                    }
                }
                else -> {
                    ssb.append(text[i])
                    i++
                }
            }
        }
        return ssb
    }
}
