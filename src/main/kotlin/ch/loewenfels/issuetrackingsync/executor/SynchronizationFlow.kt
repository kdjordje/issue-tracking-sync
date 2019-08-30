package ch.loewenfels.issuetrackingsync.executor

import ch.loewenfels.issuetrackingsync.*
import ch.loewenfels.issuetrackingsync.notification.NotificationObserver
import ch.loewenfels.issuetrackingsync.syncclient.IssueTrackingClient
import ch.loewenfels.issuetrackingsync.syncconfig.*
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.abs

typealias SyncActionName = String

/**
 * This class is the main work horse of issue synchronization. The internal behavior derives from the configured
 * [SyncFlowDefinition], delegating the work to a list of [SynchronizationAction].
 *
 * The internal state of this class must be immutable, as the [execute] method is called from multiple threads
 * and for multiple issues.
 */
class SynchronizationFlow(
    private val syncFlowDefinition: SyncFlowDefinition,
    private val actionDefinitions: List<SyncActionDefinition>,
    private val sourceClient: IssueTrackingClient<Any>,
    private val targetClient: IssueTrackingClient<Any>,
    private val notificationObserver: NotificationObserver
) : Logging {
    private val sourceApplication: TrackingApplicationName = syncFlowDefinition.source
    private val syncActions: Map<SyncActionName, SynchronizationAction>
    private val issueFilter: IssueFilter?
    private val defaultsForNewIssue: DefaultsForNewIssue?

    init {
        syncActions = syncFlowDefinition.actions.associateBy({ it }, { buildSyncAction(it, actionDefinitions) })
        issueFilter = syncFlowDefinition.filterClassname?.let {
            @Suppress("UNCHECKED_CAST")
            val filterClass = Class.forName(it) as Class<IssueFilter>
            filterClass.newInstance()
        }
        defaultsForNewIssue = syncFlowDefinition.defaultsForNewIssue
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildSyncAction(
        actionName: String,
        actionDefinitions: List<SyncActionDefinition>
    ): SynchronizationAction {
        val actionDefinition = actionDefinitions.first { it.name.equals(actionName, ignoreCase = true) }
        val actionClass = Class.forName(actionDefinition.classname) as Class<SynchronizationAction>
        return actionClass.newInstance()
    }

    fun applies(source: TrackingApplicationName, issue: Issue): Boolean {
        return Objects.equals(sourceApplication, source) &&
                issueFilter?.test(issue) ?: true
    }

    fun execute(issue: Issue) {
        try {
            loadInternalSourceIssue(issue)
            syncActions.forEach { execute(it, issue) }
            notificationObserver.notifySuccessfulSync(issue)
        } catch (ex: Exception) {
            logger().debug(ex.message, ex)
            notificationObserver.notifyException(issue, ex)
        }
    }

    private fun loadInternalSourceIssue(issue: Issue) {
        val sourceIssue =
            sourceClient.getProprietaryIssue(issue.key) ?: throw IllegalArgumentException("No source issue found")
        val gap = issue.lastUpdated.until(sourceClient.getLastUpdated(sourceIssue), ChronoUnit.SECONDS)
        if (abs(gap) > 5) {
            throw SynchronizationAbortedException("Issues has been updated since synchronization request")
        }
        issue.proprietarySourceInstance = sourceIssue
        val keyFieldMapping = FieldMappingFactory.getKeyMapping(syncFlowDefinition.keyFieldMappingDefinition)
        keyFieldMapping.loadSourceValue(issue, sourceClient)
        issue.keyFieldMapping = keyFieldMapping
    }

    private fun execute(syncActionEntry: Map.Entry<SyncActionName, SynchronizationAction>, issue: Issue) {
        val actionDefinition = actionDefinitions.first { it.name.equals(syncActionEntry.key, ignoreCase = true) }
        val fieldMappings = actionDefinition.fieldMappingDefinitions.map { FieldMappingFactory.getMapping(it) }.toList()
        syncActionEntry.value.execute(sourceClient, targetClient, issue, fieldMappings, defaultsForNewIssue)
    }
}