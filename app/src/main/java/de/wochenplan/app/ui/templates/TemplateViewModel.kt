package de.wochenplan.app.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.wochenplan.app.AppContainer
import de.wochenplan.app.WochenplanApp
import de.wochenplan.app.core.IsoWeek
import de.wochenplan.app.data.db.TaskTemplateItemEntity
import de.wochenplan.app.data.db.TaskTemplateWithItems
import de.wochenplan.app.data.repo.TaskTemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ein Eintrag im Vorlagen-Editor. [key] haelt die Zeilen beim Bearbeiten stabil. */
data class EditorItem(
    val key: Long,
    val title: String = "",
    val weekday: Int? = null,
    val plannedPomodoros: Int = 1,
)

data class TemplateEditorState(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val items: List<EditorItem> = emptyList(),
) {
    val isNew: Boolean get() = id == 0L
    val canSave: Boolean get() = name.isNotBlank() && items.any { it.title.isNotBlank() }
}

data class TemplateUiState(
    val week: IsoWeek = IsoWeek.current(),
    val templates: List<TaskTemplateWithItems> = emptyList(),
    val editor: TemplateEditorState? = null,
    val message: String? = null,
)

class TemplateViewModel(
    private val repository: TaskTemplateRepository,
    private val container: AppContainer,
) : ViewModel() {

    private val editor = MutableStateFlow<TemplateEditorState?>(null)
    private val message = MutableStateFlow<String?>(null)

    /** Zaehler fuer die Schluessel neuer Editor-Zeilen. */
    private var nextItemKey = 1L

    val uiState: StateFlow<TemplateUiState> = combine(
        container.selectedWeek,
        repository.observeTemplates(),
        editor,
        message,
    ) { week, templates, editorState, currentMessage ->
        TemplateUiState(
            week = week,
            templates = templates,
            editor = editorState,
            message = currentMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplateUiState())

    fun clearMessage() = message.update { null }

    /** Fuegt die Aufgaben der Vorlage in die gerade gewaehlte Woche ein. */
    fun applyToCurrentWeek(templateId: Long, templateName: String) {
        viewModelScope.launch {
            val week = container.selectedWeek.value
            val count = repository.applyToWeek(templateId, week)
            message.value = when (count) {
                0 -> "„$templateName“ enthaelt keine Aufgaben."
                1 -> "Eine Aufgabe in ${week.label} eingefuegt."
                else -> "$count Aufgaben in ${week.label} eingefuegt."
            }
        }
    }

    /** Speichert die Aufgaben der aktuellen Woche als neue Vorlage. */
    fun saveCurrentWeekAsTemplate(name: String) {
        viewModelScope.launch {
            val week = container.selectedWeek.value
            val id = repository.saveWeekAsTemplate(week, name)
            message.value = if (id < 0) {
                "${week.label} enthaelt keine Aufgaben zum Uebernehmen."
            } else {
                "Vorlage aus ${week.label} erstellt."
            }
        }
    }

    fun delete(templateId: Long, templateName: String) {
        viewModelScope.launch {
            repository.delete(templateId)
            message.value = "„$templateName“ geloescht."
        }
    }

    // --- Editor ----------------------------------------------------------

    fun startNewTemplate() {
        editor.value = TemplateEditorState(items = listOf(EditorItem(key = nextItemKey++)))
    }

    fun startEditing(template: TaskTemplateWithItems) {
        editor.value = TemplateEditorState(
            id = template.template.id,
            name = template.template.name,
            description = template.template.description.orEmpty(),
            items = template.orderedItems.map { item ->
                EditorItem(
                    key = nextItemKey++,
                    title = item.title,
                    weekday = item.weekday,
                    plannedPomodoros = item.plannedPomodoros,
                )
            },
        )
    }

    fun cancelEditing() {
        editor.value = null
    }

    fun setName(value: String) = editor.update { it?.copy(name = value) }

    fun setDescription(value: String) = editor.update { it?.copy(description = value) }

    fun addItem() = editor.update { state ->
        state?.copy(items = state.items + EditorItem(key = nextItemKey++))
    }

    fun updateItem(key: Long, transform: (EditorItem) -> EditorItem) = editor.update { state ->
        state?.copy(items = state.items.map { if (it.key == key) transform(it) else it })
    }

    fun removeItem(key: Long) = editor.update { state ->
        state?.copy(items = state.items.filterNot { it.key == key })
    }

    fun saveEditor() {
        val state = editor.value ?: return
        if (!state.canSave) return

        viewModelScope.launch {
            repository.save(
                id = state.id,
                name = state.name,
                description = state.description,
                items = state.items.mapIndexed { index, item ->
                    TaskTemplateItemEntity(
                        templateId = state.id,
                        title = item.title,
                        weekday = item.weekday,
                        plannedPomodoros = item.plannedPomodoros,
                        sortIndex = index,
                    )
                },
            )
            editor.value = null
            message.value = if (state.isNew) "Vorlage angelegt." else "Vorlage gespeichert."
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WochenplanApp
                TemplateViewModel(app.container.taskTemplateRepository, app.container)
            }
        }
    }
}
