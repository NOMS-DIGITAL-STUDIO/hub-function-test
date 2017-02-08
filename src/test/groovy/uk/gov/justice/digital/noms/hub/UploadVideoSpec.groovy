package uk.gov.justice.digital.noms.hub

import com.mongodb.BasicDBObject
import geb.spock.GebSpec
import org.bson.Document
import uk.gov.justice.digital.noms.hub.util.MediaStore
import uk.gov.justice.digital.noms.hub.util.MetadataStore

import static org.awaitility.Awaitility.await
import static MediaStore.AZURE_CONTAINER_NAME
import static MetadataStore.CONTENT_ITEM_COLLECTION

class UploadVideoSpec extends GebSpec {

    private static final String MP4_FILENAME = 'hub-feature-specs-test-video_400KB.mp4'
    private static final String TITLE = 'hub-feature-specs:Upload Video'
    private static final String CATEGORY = 'Science'

    private File file
    private MetadataStore metadataStore = new MetadataStore()
    private MediaStore mediaStore = new MediaStore()
    private String adminUiUrl
    private String videoUploadUrl

    def setup() {
        metadataStore.connect()
        mediaStore.connect()
        adminUiUrl = System.getenv('adminUiUrl') ?: 'http://localhost:3000/'
        videoUploadUrl = adminUiUrl + 'video'
        file = new File(this.getClass().getResource("/${MP4_FILENAME}").toURI())
    }

    def 'Upload video'() {
        given: 'that I am on the Upload Video page'
        go videoUploadUrl
        verifyThatTheCurrentPageTitleIs('Upload - Video')

        and: 'have provided a title'
        $('form').videoTitle = TITLE

        and: 'picked a subject'
        $('form').videoSubject = CATEGORY

        and: 'and chosen a video'
        $('form').videoFile = file.absolutePath

        when: 'I click the Save button'
        $('#upload').click()
        await().until{ $('#uploadSuccess').text() == 'Saved successfully' }

        then: 'the video is published'
        Document document = metadataStore.getDatabase().getCollection(CONTENT_ITEM_COLLECTION)
                .find(new BasicDBObject(filename: MP4_FILENAME)).first()
        document != null
        document.title == TITLE
        document.category == CATEGORY
        document.uri == "${mediaStore.getMediaStorePublicUrlBase()}/${AZURE_CONTAINER_NAME}/${MP4_FILENAME}"

        mediaStore.getContainer().getBlockBlobReference(MP4_FILENAME).exists()
    }

    private void verifyThatTheCurrentPageTitleIs(String aTitle) {
        assert title == aTitle
    }

    def cleanup() {
        metadataStore.documentIsPresentWithFilename MP4_FILENAME
        mediaStore.removeContentWithFilenames MP4_FILENAME
    }



}