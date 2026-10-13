package fr.montpellient.feature.courses

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    var selectedCourse by remember { mutableStateOf<MoodleCourse?>(null) }

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
                    CoursesList(
                        courses = courses,
                        onCourseClick = { selectedCourse = it }
                    )
                } else {
                    AssignmentsList(assignments = assignments)
                }
            }
        }
    }

    selectedCourse?.let { course ->
        CourseDetailModal(
            course = course,
            container = container,
            onDismiss = { selectedCourse = null }
        )
    }
}

@Composable
private fun CoursesList(
    courses: List<MoodleCourse>,
    onCourseClick: (MoodleCourse) -> Unit
) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCourseClick(course) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseDetailModal(
    course: MoodleCourse,
    container: AppContainer,
    onDismiss: () -> Unit
) {
    val sections by container.courses.sections(course.id).collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = course.fullName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    course.category?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Bouton pour ouvrir directement l'espace Moodle dans le navigateur
            OutlinedButton(
                onClick = {
                    val moodleSearchQuery = Uri.encode(course.shortName.ifBlank { course.fullName })
                    val moodleUrl = "https://moodle.umontpellier.fr/course/search.php?search=$moodleSearchQuery"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(moodleUrl))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ouvrir dans Moodle UM")
            }

            HorizontalDivider()

            Text(
                text = "Ressources & Documents du cours",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (sections.isEmpty() || sections.all { it.modules.isEmpty() }) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Aucun document téléchargé hors-ligne pour cette UE.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Accédez à l'espace officiel Moodle via le bouton ci-dessus pour consulter tous les supports de cours, polycopiés et PDF en ligne.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    sections.forEach { section ->
                        if (section.modules.isNotEmpty()) {
                            item(key = "sec_${section.id}") {
                                Text(
                                    text = section.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            items(section.modules, key = { it.id }) { mod ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            mod.url?.let { fileUrl ->
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fileUrl))
                                                runCatching { context.startActivity(intent) }
                                            }
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (mod.type == fr.montpellient.core.model.ModuleType.FILE) Icons.Outlined.Description
                                            else Icons.Outlined.Link,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = mod.fileName ?: mod.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            mod.fileSizeBytes?.let { size ->
                                                Text(
                                                    text = DateFormats.fileSize(size),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
