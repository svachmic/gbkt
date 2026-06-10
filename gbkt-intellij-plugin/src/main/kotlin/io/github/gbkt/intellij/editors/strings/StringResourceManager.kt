/*
 * Copyright 2026 Michal Svacha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.gbkt.intellij.editors.strings

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.ListSelectionModel

/**
 * Manager panel for organizing multiple string resources.
 *
 * Features:
 * - Namespace organization (dialog, menu, battle, etc.)
 * - String list with search
 * - Individual string editing with preview
 * - Bulk export to code
 */
class StringResourceManager : JPanel(BorderLayout()) {

    /** Represents a string resource. */
    data class StringResource(var id: String, var namespace: String, var text: String) {
        val fullId: String
            get() = "$namespace.$id"
    }

    private val resources = mutableListOf<StringResource>()
    private val listModel = DefaultListModel<StringResource>()
    private val resourceList = JBList(listModel)

    private val namespaceCombo =
        ComboBox(arrayOf("All", "dialog", "menu", "battle", "npc", "item", "system"))
    private val searchField = JBTextField()
    private val stringEditor = StringEditorPanel()

    private val idField = JBTextField()
    private val namespaceField =
        ComboBox(arrayOf("dialog", "menu", "battle", "npc", "item", "system"))

    private var currentResource: StringResource? = null

    init {
        setupUI()
        setupListeners()

        // Add some example resources
        addResource(StringResource("greeting", "npc", "Hello traveler!\nWelcome to town."))
        addResource(StringResource("shop_buy", "menu", "What would you\nlike to buy?"))
        addResource(StringResource("attack", "battle", "%player attacks!\n%damage damage!"))
    }

    private fun setupUI() {
        // Left panel - resource list
        val listPanel = JPanel(BorderLayout())
        listPanel.border = BorderFactory.createTitledBorder("String Resources")

        // Search and filter toolbar
        val filterPanel = JPanel(GridBagLayout())
        val gbc =
            GridBagConstraints().apply {
                insets = JBUI.insets(2)
                fill = GridBagConstraints.HORIZONTAL
            }

        gbc.gridx = 0
        gbc.gridy = 0
        filterPanel.add(JBLabel("Namespace:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        filterPanel.add(namespaceCombo, gbc)

        gbc.gridx = 0
        gbc.gridy = 1
        gbc.weightx = 0.0
        filterPanel.add(JBLabel("Search:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        filterPanel.add(searchField, gbc)

        listPanel.add(filterPanel, BorderLayout.NORTH)

        // Resource list
        resourceList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        resourceList.cellRenderer = javax.swing.DefaultListCellRenderer().apply { setText("") }
        resourceList.setCellRenderer { _, value, _, isSelected, _ ->
            JBLabel(value.fullId).apply {
                isOpaque = true
                background =
                    if (isSelected) JBColor.background().brighter() else JBColor.background()
                border = BorderFactory.createEmptyBorder(2, 5, 2, 5)
            }
        }
        listPanel.add(JBScrollPane(resourceList), BorderLayout.CENTER)

        // List buttons
        val listButtons = JPanel(FlowLayout(FlowLayout.LEFT))
        val addButton = JButton("Add")
        val removeButton = JButton("Remove")
        val exportButton = JButton("Export All")
        listButtons.add(addButton)
        listButtons.add(removeButton)
        listButtons.add(exportButton)
        listPanel.add(listButtons, BorderLayout.SOUTH)

        addButton.addActionListener { addNewResource() }
        removeButton.addActionListener { removeSelectedResource() }
        exportButton.addActionListener { exportAllResources() }

        // Right panel - editor
        val editorPanel = JPanel(BorderLayout())
        editorPanel.border = BorderFactory.createTitledBorder("Edit Resource")

        // ID and namespace fields
        val idPanel = JPanel(GridBagLayout())
        val idGbc =
            GridBagConstraints().apply {
                insets = JBUI.insets(2)
                fill = GridBagConstraints.HORIZONTAL
            }

        idGbc.gridx = 0
        idGbc.gridy = 0
        idPanel.add(JBLabel("ID:"), idGbc)
        idGbc.gridx = 1
        idGbc.weightx = 1.0
        idPanel.add(idField, idGbc)

        idGbc.gridx = 0
        idGbc.gridy = 1
        idGbc.weightx = 0.0
        idPanel.add(JBLabel("Namespace:"), idGbc)
        idGbc.gridx = 1
        idGbc.weightx = 1.0
        idPanel.add(namespaceField, idGbc)

        val saveButton = JButton("Save Changes")
        idGbc.gridx = 0
        idGbc.gridy = 2
        idGbc.gridwidth = 2
        idPanel.add(saveButton, idGbc)

        saveButton.addActionListener { saveCurrentResource() }

        editorPanel.add(idPanel, BorderLayout.NORTH)
        editorPanel.add(stringEditor, BorderLayout.CENTER)

        // Main split pane
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listPanel, editorPanel)
        splitPane.resizeWeight = 0.3
        splitPane.dividerLocation = 250

        add(splitPane, BorderLayout.CENTER)
    }

    private fun setupListeners() {
        resourceList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                loadSelectedResource()
            }
        }

        namespaceCombo.addActionListener { filterResources() }

        searchField.document.addDocumentListener(
            object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent) = filterResources()

                override fun removeUpdate(e: javax.swing.event.DocumentEvent) = filterResources()

                override fun changedUpdate(e: javax.swing.event.DocumentEvent) = filterResources()
            }
        )
    }

    private fun filterResources() {
        val namespace = namespaceCombo.selectedItem as String
        val search = searchField.text.lowercase()

        listModel.clear()
        resources
            .filter { namespace == "All" || it.namespace == namespace }
            .filter {
                search.isEmpty() ||
                    it.fullId.lowercase().contains(search) ||
                    it.text.lowercase().contains(search)
            }
            .forEach { listModel.addElement(it) }
    }

    private fun loadSelectedResource() {
        val selected = resourceList.selectedValue ?: return
        currentResource = selected
        idField.text = selected.id
        namespaceField.selectedItem = selected.namespace
        stringEditor.setText(selected.text)
    }

    private fun saveCurrentResource() {
        val resource = currentResource ?: return
        resource.id = idField.text
        resource.namespace = namespaceField.selectedItem as String
        resource.text = stringEditor.getText()
        filterResources()
    }

    private fun addNewResource() {
        val newResource = StringResource("new_string", "dialog", "Enter text here")
        addResource(newResource)
        resourceList.setSelectedValue(newResource, true)
    }

    private fun addResource(resource: StringResource) {
        resources.add(resource)
        filterResources()
    }

    private fun removeSelectedResource() {
        val selected = resourceList.selectedValue ?: return
        resources.remove(selected)
        currentResource = null
        filterResources()
        idField.text = ""
        stringEditor.clear()
    }

    private fun exportAllResources() {
        val code = StringBuilder()
        code.append("// Generated string resources\n\n")

        val byNamespace = resources.groupBy { it.namespace }
        for ((namespace, strings) in byNamespace) {
            code.append("object ${namespace.replaceFirstChar { it.uppercase() }}Strings {\n")
            for (string in strings) {
                val escapedText =
                    string.text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                code.append("    val ${string.id} = \"$escapedText\"\n")
            }
            code.append("}\n\n")
        }

        // Show in a dialog
        val dialog =
            com.intellij.openapi.ui.DialogBuilder().apply {
                setTitle("Exported Strings")
                setCenterPanel(
                    JBScrollPane(
                        com.intellij.ui.components.JBTextArea(code.toString()).apply {
                            isEditable = false
                            font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)
                        }
                    )
                )
            }
        dialog.show()
    }

    /** Gets all resources. */
    fun getResources(): List<StringResource> = resources.toList()

    /** Clears all resources. */
    fun clearResources() {
        resources.clear()
        currentResource = null
        filterResources()
    }
}
