package ch.loewenfels.issuetrackingsync.testcontext

import ch.loewenfels.issuetrackingsync.any
import ch.loewenfels.issuetrackingsync.syncclient.ClientFactory
import ch.loewenfels.issuetrackingsync.syncconfig.IssueTrackingApplication
import org.mockito.Mockito
import org.springframework.context.annotation.*

/**
 * Where necessary, this class adds secondary bean definitions, and enforces their use by marking
 * them as [Primary]
 */
@Profile("test")
@Configuration
open class TestSpringConfiguration {
    @Bean
    @Primary
    open fun clientFactoryMock(): ClientFactory {
        val mockClientFactory = Mockito.mock(ClientFactory::class.java)
        Mockito.`when`(mockClientFactory.getClient(any(IssueTrackingApplication::class.java))).thenAnswer {
            val setting = it.arguments[0] as IssueTrackingApplication
            when {
                setting.className.endsWith("JiraClient") -> MockJiraClient(setting)
                setting.className.endsWith("RtcClient") -> MockRtcClient(setting)
                else -> throw IllegalArgumentException("Unknown client: " + setting.className)
            }
        }
        return mockClientFactory
    }
}

