package dev.eternalz.forge

import dev.eternalz.forge.domain.*
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    @Test fun pathSegmentsAreEncodedWithoutEscapingRepositoryBoundary() {
        assertEquals("a%20b%3Fref%3Dx", segment("a b?ref=x"))
        assertEquals("src/a%20b.kt", filePath("src/a b.kt"))
        assertEquals("feature%2Fmy-branch", segment("feature/my-branch"))
    }
    @Test fun editorPathsRejectTraversalAndControlCharacters() {
        listOf("", "/etc/passwd", "../secret", "a/../b", "a//b", "a/./b", "x\\y", "a\u0000").forEach { assertFalse(it, validPath(it)) }
        listOf("README.md", "src/main/App.kt", ".github/workflows/ci.yml", "a b.txt").forEach { assertTrue(it, validPath(it)) }
    }
    @Test fun branchNamesFollowGitRefRestrictions() {
        listOf("", "-bad", "bad..branch", "foo.lock", "a//b", "a@{b", "space branch", "foo?", ".hidden", "a/.", "a/").forEach { assertFalse(it, validBranch(it)) }
        listOf("main", "feature/mobile-editor", "release/1.0.0").forEach { assertTrue(it, validBranch(it)) }
    }
    @Test fun reviewShowsReplacementDeletionAndNoChanges() {
        assertTrue(reviewDiff("before", "after").contains("- before\n+ after"))
        assertTrue(reviewDiff("secret", null).contains("deleted file"))
        assertEquals("No changes", reviewDiff("same", "same"))
        assertTrue(reviewDiff("", "created").contains("+ created"))
    }
    @Test fun reviewHandlesEmptyAndIdenticalSuffixes() {
        assertTrue(reviewDiff("a\nb\nc", "a\nx\nc").contains("+ x"))
        assertTrue(reviewDiff("a\nb", "a").contains("- b"))
        assertTrue(reviewDiff("a", "a\nb").contains("+ b"))
    }
}
