package com.yg.liquidink.core

import java.util.ArrayDeque

/**
 * Grouped, bounded undo history. Text is immutable, so snapshots only retain a
 * reference; trimming is based on both entry count and retained character count.
 */
class UndoRedoManager(
    private val maxEntries: Int = 80,
    private val maxRetainedCharacters: Int = 24_000_000,
    private val groupingWindowMillis: Long = 900L,
) {
    data class Snapshot(
        val text: String,
        val selectionStart: Int,
        val selectionEnd: Int,
    )

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var undoCharacters = 0
    private var redoCharacters = 0
    private var lastEditAt = 0L
    private var groupOpen = false

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Called once for every text-changing edit. Consecutive typing inside the
     * grouping window produces a single undo step. Structural/programmatic edits
     * close the group immediately.
     */
    fun recordBeforeChange(
        before: Snapshot,
        nowMillis: Long = System.currentTimeMillis(),
        forceNewGroup: Boolean = false,
        structural: Boolean = false,
    ) {
        val newGroup = forceNewGroup ||
            structural ||
            !groupOpen ||
            nowMillis - lastEditAt > groupingWindowMillis
        if (newGroup) {
            push(undoStack, before)
            undoCharacters += before.text.length
            trim(undoStack) { undoCharacters -= it.text.length }
        }
        clear(redoStack) { redoCharacters -= it.text.length }
        lastEditAt = nowMillis
        groupOpen = !structural && !forceNewGroup
    }

    fun undo(current: Snapshot): Snapshot? {
        val previous = undoStack.pollLast() ?: return null
        undoCharacters -= previous.text.length
        push(redoStack, current)
        redoCharacters += current.text.length
        trim(redoStack) { redoCharacters -= it.text.length }
        groupOpen = false
        return previous
    }

    fun redo(current: Snapshot): Snapshot? {
        val next = redoStack.pollLast() ?: return null
        redoCharacters -= next.text.length
        push(undoStack, current)
        undoCharacters += current.text.length
        trim(undoStack) { undoCharacters -= it.text.length }
        groupOpen = false
        return next
    }

    /** A caret-only move ends the current typing group. */
    fun breakGroup() {
        groupOpen = false
    }

    fun reset() {
        undoStack.clear()
        redoStack.clear()
        undoCharacters = 0
        redoCharacters = 0
        lastEditAt = 0L
        groupOpen = false
    }

    private fun push(stack: ArrayDeque<Snapshot>, snapshot: Snapshot) {
        stack.addLast(snapshot)
    }

    private fun trim(stack: ArrayDeque<Snapshot>, onRemoved: (Snapshot) -> Unit) {
        while (stack.size > maxEntries) {
            val removed = stack.removeFirst()
            onRemoved(removed)
        }
        while (undoCharacters + redoCharacters > maxRetainedCharacters && stack.size > 1) {
            val removed = stack.removeFirst()
            onRemoved(removed)
        }
    }

    private fun clear(stack: ArrayDeque<Snapshot>, onRemoved: (Snapshot) -> Unit) {
        while (stack.isNotEmpty()) {
            onRemoved(stack.removeFirst())
        }
    }
}
