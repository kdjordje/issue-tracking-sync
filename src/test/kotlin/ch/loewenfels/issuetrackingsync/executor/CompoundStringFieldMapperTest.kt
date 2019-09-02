package ch.loewenfels.issuetrackingsync.executor

import ch.loewenfels.issuetrackingsync.AbstractSpringTest
import ch.loewenfels.issuetrackingsync.syncclient.ClientFactory
import ch.loewenfels.issuetrackingsync.syncconfig.FieldMappingDefinition
import ch.loewenfels.issuetrackingsync.testcontext.TestObjects
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired

internal class CompoundStringFieldMapperTest : AbstractSpringTest() {
    @Autowired
    private lateinit var clientFactory: ClientFactory

    @Test
    fun getValue() {
        // arrange
        val testee = buildTestee()
        val issue = TestObjects.buildIssue("MK-1")
        val sourceClient =
            TestObjects.buildIssueTrackingClient(TestObjects.buildIssueTrackingApplication("RtcClient"), clientFactory)
        // act
        val result = testee.getValue(issue, "text,text2,text3", sourceClient)
        // assert
        assertNotNull(result)
        assertEquals(
            "foobar\n" +
                    "h4. Text 2\n" +
                    "foobar\n" +
                    "h4. Text 3\n" +
                    "foobar", (result as String)
        )
    }

    @Test
    fun setValue() {
        // arrange
        val testee = buildTestee()
        val issue = TestObjects.buildIssue("MK-1")
        val targetClient =
            TestObjects.buildIssueTrackingClient(TestObjects.buildIssueTrackingApplication("JiraClient"), clientFactory)
        val value = "foobar\n\nh4. Text 2\nSome more text\nh4. Text 3\nAnd still some more"
        // act
        testee.setValue(issue, "text,text2,text3", targetClient, value)
        // assert
        Mockito.verify(targetClient).setValue(issue, "text", "foobar")
        Mockito.verify(targetClient).setValue(issue, "text2", "Some more text")
        Mockito.verify(targetClient).setValue(issue, "text3", "And still some more")
    }

    private fun buildTestee(): CompoundStringFieldMapper {
        val associations =
            mutableMapOf(
                "text2" to "h4. Text 2",
                "text3" to "h4. Text 3"
            )
        val fieldDefinition = FieldMappingDefinition(
            "text,text2,text3", "description",
            CompoundStringFieldMapper::class.toString(), associations
        )
        return CompoundStringFieldMapper(fieldDefinition)
    }
}