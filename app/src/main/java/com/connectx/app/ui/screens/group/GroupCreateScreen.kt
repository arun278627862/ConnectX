package com.connectx.app.ui.screens.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connectx.app.data.local.entity.ContactEntity
import com.connectx.app.data.repository.ConnectXRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupCreateViewModel @Inject constructor(
    private val repository: ConnectXRepository
) : ViewModel() {
    val contacts = repository.allContacts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun createGroup(name: String, description: String, selectedContactIds: List<String>) {
        viewModelScope.launch {
            repository.createGroup(name, description, selectedContactIds)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCreateScreen(
    onNavigateBack: () -> Unit,
    viewModel: GroupCreateViewModel = hiltViewModel()
) {
    val contacts by viewModel.contacts.collectAsState()
    var groupName by remember { mutableStateOf("") }
    var groupDesc by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create New Group") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (groupName.isNotBlank() && selectedMembers.isNotEmpty()) {
                        viewModel.createGroup(groupName, groupDesc, selectedMembers.toList())
                        onNavigateBack()
                    }
                }
            ) {
                Icon(Icons.Default.Check, contentDescription = "Create Group")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Group Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = groupDesc,
                onValueChange = { groupDesc = it },
                label = { Text("Group Description") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Select Members (${selectedMembers.size})", style = MaterialTheme.typography.titleMedium)

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(contacts) { contact ->
                    val isSelected = selectedMembers.contains(contact.id)
                    ListItem(
                        headlineContent = { Text(contact.name) },
                        supportingContent = { Text(contact.phoneNumber) },
                        leadingContent = {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) selectedMembers.add(contact.id)
                                    else selectedMembers.remove(contact.id)
                                }
                            )
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
