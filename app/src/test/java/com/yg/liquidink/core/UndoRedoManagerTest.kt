package com.yg.liquidink.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoRedoManagerTest {

    private fun snapshot(text: String, cursor: Int = text.length) =
        UndoRedoManager.Snapshot(text, cursor, cursor)

    @Test
    fun groupedTypingIsOneUndoStep() {
        val manager = UndoRedoManager()
        manager.recordBeforeChange(snapshot(""), nowMillis = 1_000)
        manager.recordBeforeChange(snapshot("a"), nowMillis = 1_100)
        manager.recordBeforeChange(snapshot("ab"), nowMillis = 1_200)

        val undo = manager.undo(snapshot("abc"))
        assertEquals("", undo?.text)
        assertFalse(manager.canUndo)
        assertTrue(manager.canRedo)

        val redo = manager.redo(snapshot(""))
        assertEquals("abc", redo?.text)
        assertEquals(TextCursor(text = "abc", cursor = 3), TextCursor.from(redo!!))
    }

    @Test
    fun structuralEditStartsNewGroup() {
        val manager = UndoRedoManager()
        manager.recordBeforeChange(snapshot("a"), nowMillis = 1_000)
        manager.recordBeforeChange(snapshot("ab"), nowMillis = 1_050, structural = true)

        val undo = manager.undo(snapshot("ab\n"))
        assertEquals("ab", undo?.text)
        val secondUndo = manager.undo(snapshot("ab"))
        assertEquals("a", secondUndo?.text)
    }

    @Test
    fun pauseStartsNewGroup() {
        val manager = UndoRedoManager(groupingWindowMillis = 800)
        manager.recordBeforeChange(snapshot("a"), nowMillis = 1_000)
        manager.recordBeforeChange(snapshot("ab"), nowMillis = 2_500)
        val undo = manager.undo(snapshot("abc"))
        assertEquals("ab", undo?.text)
    }

    @Test
    fun newEditClearsRedo() {
        val manager = UndoRedoManager()
        manager.recordBeforeChange(snapshot(""), nowMillis = 1_000, structural = true)
        manager.undo(snapshot("a"))
        manager.recordBeforeChange(snapshot(""), nowMillis = 2_000, structural = true)
        assertFalse(manager.canRedo)
    }

    @Test
    fun historyIsCapped() {
        val manager = UndoRedoManager(maxEntries = 3)
        repeat(10) { index ->
            manager.recordBeforeChange(
                snapshot("text-$index"),
                nowMillis = 1_000L + index,
                structural = true,
            )
        }
        var current = snapshot("final")
        var undoCount = 0
        while (manager.canUndo) {
            current = manager.undo(current) ?: break
            undoCount++
        }
        assertEquals(3, undoCount)
    }

    @Test
    fun resetClearsEverything() {
        val manager = UndoRedoManager()
        manager.recordBeforeChange(snapshot("a"), structural = true)
        manager.reset()
        assertFalse(manager.canUndo)
        assertFalse(manager.canRedo)
        assertNull(manager.undo(snapshot("a")))
    }

    private data class TextCursor(val text: String, val cursor: Int) {
        companion object {
            fun from(snapshot: UndoRedoManager.Snapshot) =
                TextCursor(snapshot.text, snapshot.selectionStart)
        }
    }
}
