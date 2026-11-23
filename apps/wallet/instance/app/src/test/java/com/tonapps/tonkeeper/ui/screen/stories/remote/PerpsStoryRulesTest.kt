package com.tonapps.tonkeeper.ui.screen.stories.remote

import com.tonapps.perps.screens.common.PERPS_STORY_ID
import com.tonapps.wallet.api.entity.StoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PerpsStoryRulesTest {

    @Test
    fun confirmsGateCloseWhenTermsNotAccepted() {
        assertTrue(perpsCloseNeedsConfirm(stories(), isGate = true, termsAccepted = false))
    }

    @Test
    fun skipsConfirmWhenTermsAccepted() {
        assertFalse(perpsCloseNeedsConfirm(stories(), isGate = true, termsAccepted = true))
    }

    @Test
    fun skipsConfirmOutsideGate() {
        assertFalse(perpsCloseNeedsConfirm(stories(), isGate = false, termsAccepted = false))
    }

    @Test
    fun skipsConfirmForOtherStory() {
        assertFalse(perpsCloseNeedsConfirm(stories(id = "other"), isGate = true, termsAccepted = false))
    }

    @Test
    fun skipsConfirmWhenLastButtonIsMissingOrBlank() {
        assertFalse(perpsCloseNeedsConfirm(stories(lastButton = null), isGate = true, termsAccepted = false))
        assertFalse(perpsCloseNeedsConfirm(stories(lastButton = button(" ")), isGate = true, termsAccepted = false))
    }

    @Test
    fun dropsPerpsAgreementPage() {
        val stories = stories()

        val result = stories.withoutPerpsAgreement()

        assertEquals(stories.list.take(2), result?.list)
        assertEquals(stories.id, result?.id)
    }

    @Test
    fun keepsOtherStory() {
        val stories = stories(id = "other")

        assertSame(stories, stories.withoutPerpsAgreement())
    }

    @Test
    fun keepsPerpsStoryWithoutAgreementButton() {
        val withoutButton = stories(lastButton = null)
        val blankButton = stories(lastButton = button(" "))

        assertSame(withoutButton, withoutButton.withoutPerpsAgreement())
        assertSame(blankButton, blankButton.withoutPerpsAgreement())
    }

    @Test
    fun dropsOnePagePerpsAgreementToNull() {
        val stories = StoryEntity.Stories(id = PERPS_STORY_ID, list = listOf(story(button("Start trading"))))

        assertNull(stories.withoutPerpsAgreement())
    }

    private fun button(title: String) = StoryEntity.Button(type = "deeplink", payload = "tonkeeper://perps", title = title)

    private fun story(button: StoryEntity.Button?) = StoryEntity(
        title = "Title",
        description = "Description",
        image = "https://example.com/image.png",
        button = button,
        links = emptyList()
    )

    private fun stories(
        id: String = PERPS_STORY_ID,
        lastButton: StoryEntity.Button? = button("Start trading")
    ) = StoryEntity.Stories(
        id = id,
        list = listOf(story(null), story(null), story(lastButton))
    )
}
