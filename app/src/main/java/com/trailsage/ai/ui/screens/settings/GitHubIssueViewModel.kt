package com.charles.trailsage.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.trailsage.data.github.GitHubIssueRepository
import com.charles.trailsage.data.github.GitHubService
import com.charles.trailsage.data.github.SubmittedIssue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

data class GitHubComment(
    val body: String,
    val author: String,
    val createdAt: String
)

sealed interface SubmitStatus {
    object Idle : SubmitStatus
    object Loading : SubmitStatus
    data class Success(val issue: SubmittedIssue) : SubmitStatus
    data class Error(val message: String) : SubmitStatus
}

sealed interface CommentsStatus {
    object Idle : CommentsStatus
    object Loading : CommentsStatus
    data class Success(val comments: List<GitHubComment>) : CommentsStatus
    data class Error(val message: String) : CommentsStatus
}

sealed interface CommentPostStatus {
    object Idle : CommentPostStatus
    object Loading : CommentPostStatus
    object Success : CommentPostStatus
    data class Error(val message: String) : CommentPostStatus
}

@HiltViewModel
class GitHubIssueViewModel @Inject constructor(
    private val repository: GitHubIssueRepository,
    private val gitHubService: GitHubService
) : ViewModel() {

    val submittedIssues: StateFlow<List<SubmittedIssue>> = repository.submittedIssues
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _submitStatus = MutableStateFlow<SubmitStatus>(SubmitStatus.Idle)
    val submitStatus: StateFlow<SubmitStatus> = _submitStatus.asStateFlow()

    private val _commentsStatus = MutableStateFlow<CommentsStatus>(CommentsStatus.Idle)
    val commentsStatus: StateFlow<CommentsStatus> = _commentsStatus.asStateFlow()

    private val _commentPostStatus = MutableStateFlow<CommentPostStatus>(CommentPostStatus.Idle)
    val commentPostStatus: StateFlow<CommentPostStatus> = _commentPostStatus.asStateFlow()

    init {
        refreshIssues()
    }

    fun refreshIssues() {
        viewModelScope.launch {
            repository.refreshStatuses()
        }
    }

    fun resetSubmitStatus() {
        _submitStatus.value = SubmitStatus.Idle
    }

    fun resetCommentPostStatus() {
        _commentPostStatus.value = CommentPostStatus.Idle
    }

    fun submitIssue(
        title: String,
        description: String,
        name: String,
        email: String,
        includeDiagnostics: Boolean,
        screenshotUri: Uri?
    ) {
        viewModelScope.launch {
            _submitStatus.value = SubmitStatus.Loading
            val result = repository.submitIssue(
                title = title,
                description = description,
                name = name,
                email = email,
                includeDiagnostics = includeDiagnostics,
                screenshotUri = screenshotUri
            )
            result.onSuccess { issue ->
                _submitStatus.value = SubmitStatus.Success(issue)
            }.onFailure { exception ->
                _submitStatus.value = SubmitStatus.Error(exception.localizedMessage ?: "Unknown error occurred")
            }
        }
    }

    fun loadComments(issueNumber: Int) {
        viewModelScope.launch {
            _commentsStatus.value = CommentsStatus.Loading
            val result = runCatching { gitHubService.fetchComments(issueNumber) }
            result.onSuccess { jsonArray ->
                val list = parseComments(jsonArray)
                _commentsStatus.value = CommentsStatus.Success(list)
            }.onFailure { exception ->
                _commentsStatus.value = CommentsStatus.Error(exception.localizedMessage ?: "Failed to fetch comments")
            }
        }
    }

    fun postComment(
        issueNumber: Int,
        bodyText: String,
        screenshotUri: Uri?
    ) {
        viewModelScope.launch {
            _commentPostStatus.value = CommentPostStatus.Loading
            val result = repository.postComment(issueNumber, bodyText, screenshotUri)
            result.onSuccess {
                _commentPostStatus.value = CommentPostStatus.Success
                loadComments(issueNumber)
            }.onFailure { exception ->
                _commentPostStatus.value = CommentPostStatus.Error(exception.localizedMessage ?: "Failed to post comment")
            }
        }
    }

    private fun parseComments(array: JSONArray): List<GitHubComment> {
        val list = mutableListOf<GitHubComment>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val body = obj.getString("body")
            val userObj = obj.getJSONObject("user")
            val author = userObj.getString("login")
            val createdAt = obj.optString("created_at", "")
            list.add(GitHubComment(body, author, createdAt))
        }
        return list
    }
}
