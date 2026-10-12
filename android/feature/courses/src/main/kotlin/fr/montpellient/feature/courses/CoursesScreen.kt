package fr.montpellient.feature.courses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.montpellient.core.data.AppContainer
import fr.montpellient.core.designsystem.component.EmptyState
import fr.montpellient.core.designsystem.component.RefreshableContent
import fr.montpellient.core.designsystem.component.StatusChip
import fr.montpellient.core.designsystem.util.DateFormats
import fr.montpellient.core.model.Assignment
import fr.montpellient.core.model.MoodleCourse
import fr.montpellient.core.model.SubmissionStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesRoute(container: AppContainer) {
    val courses by container.courses.courses.collectAsStateWithLifecycle(emptyList())
    val assignments by container.courses.assignments.collectAsStateWithLifecycle(emptyList())
    var selectedTab by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.courses_title)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.courses_tab_courses)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.courses_tab_assignments)) }
                )
            }

            RefreshableContent(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        runCatching { container.courses.refresh() }
                        isRefreshing = false
                    }
                }
            ) {
                if (selectedTab == 0) {
                    CoursesList(courses = courses)
                } else {
                    AssignmentsList(assignments = assignments)
                }
            }
        }
    }
}

@Composable
private fun CoursesList(courses: List<MoodleCourse>) {
    if (courses.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.School,
            title = stringResource(R.string.courses_empty_courses)
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(courses, key = { it.id }) { course ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            course.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        course.category?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    course.progressPercent?.let { progress ->
                        Text(
                            "$progress%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssignmentsList(assignments: List<Assignment>) {
    if (assignments.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Assignment,
            title = stringResource(R.string.courses_empty_assignments)
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(assignments, key = { it.id }) { assignment ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            assignment.courseName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        SubmissionBadge(status = assignment.status)
                    }

                    Text(
                        assignment.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    assignment.dueDate?.let { due ->
                        Text(
                            stringResource(R.string.due_date, DateFormats.dateTime(due)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    assignment.grade?.let { grade ->
                        Text(
                            "Note: $grade",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmissionBadge(status: SubmissionStatus) {
    val cs = MaterialTheme.colorScheme
    when (status) {
        SubmissionStatus.SUBMITTED -> StatusChip(
            stringResource(R.string.status_submitted),
            cs.primaryContainer,
            cs.onPrimaryContainer
        )
        SubmissionStatus.DRAFT -> StatusChip(
            stringResource(R.string.status_draft),
            cs.tertiaryContainer,
            cs.onTertiaryContainer
        )
        SubmissionStatus.NOT_SUBMITTED -> StatusChip(
            stringResource(R.string.status_not_submitted),
            cs.errorContainer,
            cs.onErrorContainer
        )
        SubmissionStatus.GRADED -> StatusChip(
            stringResource(R.string.status_graded),
            cs.secondaryContainer,
            cs.onSecondaryContainer
        )
    }
}
