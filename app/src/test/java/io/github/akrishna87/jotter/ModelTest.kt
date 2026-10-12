package io.github.akrishna87.jotter

import io.github.akrishna87.jotter.model.DEFAULT_NOTEBOOK_ID
import io.github.akrishna87.jotter.model.Data
import io.github.akrishna87.jotter.model.Note
import io.github.akrishna87.jotter.model.NoteSort
import io.github.akrishna87.jotter.model.Notebook
import io.github.akrishna87.jotter.model.Settings
import io.github.akrishna87.jotter.model.ThemeMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    @Test
    fun everythingSurvivesSavingAndLoading() {
        val ideas = Notebook(name = "Ideas")
        val data = Data(
            notes = listOf(
                Note(title = "Plan", body = "[ ] a", notebookId = ideas.id, tags = listOf("q4"), pinned = true),
                Note(title = "Old", body = "gone", trashedAt = 5),
            ),
            notebooks = listOf(Notebook.Default, ideas),
            settings = Settings(theme = ThemeMode.DARK, noteSort = NoteSort.TITLE),
        )
        assertEquals(data, Data.fromJson(JSONObject(data.toJson().toString())))
    }

    @Test
    fun daybookBackupsRestoreTheirNotes() {
        // A Daybook backup has tasks too; Jotter takes the notes and notebooks and leaves the rest.
        val daybook = """
            {"version":2,
             "tasks":[{"id":"t1","title":"Buy milk","noteId":"n1"}],
             "projects":[{"id":"p1","name":"Home"}],
             "notes":[{"id":"n1","title":"Shopping","body":"[ ] milk","notebookId":"nb","tags":["home"],"updatedAt":7}],
             "notebooks":[{"id":"nb","name":"Lists"}],
             "settings":{"theme":"DARK","weekStartsSunday":true,"noteSort":"TITLE"}}
        """.trimIndent()
        val d = Data.fromJson(JSONObject(daybook))
        val n = d.notes.single()
        assertEquals("Shopping", n.title)
        assertEquals("nb", n.notebookId)
        assertEquals(listOf("home"), n.tags)
        assertEquals(7L, n.createdAt)
        assertEquals("Lists", d.notebooks.single().name)
        assertEquals(Settings(ThemeMode.DARK, NoteSort.TITLE), d.settings)
    }

    @Test
    fun orphansFindAHome() {
        val json = """{"notes":[{"id":"n","notebookId":"gone","body":"b"}],"notebooks":[{"id":"nb","name":"Only"}]}"""
        assertEquals("nb", Data.fromJson(JSONObject(json)).notes.single().notebookId)
        val bare = Data.fromJson(JSONObject("""{"notes":[{"id":"n","body":"b"}]}"""))
        assertEquals(DEFAULT_NOTEBOOK_ID, bare.notes.single().notebookId)
        assertEquals(listOf(Notebook.Default), bare.notebooks)
    }

    @Test
    fun tagsAreCollectedFromNotesNotInTheTrash() {
        val d = Data(notes = listOf(Note(tags = listOf("b", "a")), Note(tags = listOf("B")), Note(tags = listOf("hidden"), trashedAt = 5)))
        assertEquals(listOf("a", "b"), d.tags)
    }

    @Test
    fun firstRunHasAWelcomeNote() {
        val n = Data.firstRun().notes.single()
        assertEquals("Welcome to Jotter", n.title)
        assertTrue(n.pinned)
    }
}
