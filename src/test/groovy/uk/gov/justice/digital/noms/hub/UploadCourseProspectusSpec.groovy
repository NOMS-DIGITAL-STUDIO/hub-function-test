package uk.gov.justice.digital.noms.hub

import com.mongodb.BasicDBObject
import geb.spock.GebSpec
import groovy.util.logging.Slf4j
import org.bson.Document
import uk.gov.justice.digital.noms.hub.util.MediaStore
import uk.gov.justice.digital.noms.hub.util.MetadataStore
import uk.gov.justice.digital.noms.hub.util.Hub

import static org.awaitility.Awaitility.await
import static MediaStore.AZURE_CONTAINER_NAME
import static MetadataStore.CONTENT_ITEM_COLLECTION

@Slf4j
class UploadCourseProspectusSpec extends GebSpec {
    private static final String PDF_FILENAME = 'hub-feature-specs-test-prospectus1.pdf'
    private static final String TITLE = 'hub-feature-specs:Upload Course Prospectus'
    private static final String CATEGORY = 'Science'

    private File file
    private MetadataStore metadataStore = new MetadataStore()
    private MediaStore mediaStore = new MediaStore()
    private Hub theHub = new Hub()

    def setup() {
        metadataStore.connect()
        mediaStore.connect()
        file = new File(this.getClass().getResource("/${PDF_FILENAME}").toURI())
    }

    def 'Upload Course Prospectus'() {
        given: 'that I am on the Upload Prospectus page'
        go theHub.adminUiUri + 'prospectus'
        verifyThatTheCurrentPageTitleIs('Upload - Prospectus')

        and: 'have provided a title'
        $('form').title = TITLE

        and: 'picked a subject'
        $('form').category = CATEGORY

        and: 'and chosen a prospectus'
        $('form').mainFile = file.absolutePath

        when: 'I click the Save button'
        $('#upload').click()
        await().until{ $('#uploadSuccess').text() == 'Saved successfully' }

        then: 'the prospectus is published'
        Document document = metadataStore.getDatabase().getCollection(CONTENT_ITEM_COLLECTION)
                                .find(new BasicDBObject(filename: PDF_FILENAME)).first()
        document != null
        document.metadata.title == TITLE
        document.metadata.category == CATEGORY
        document.uri == "${mediaStore.getMediaStorePublicUrlBase()}/${AZURE_CONTAINER_NAME}/${PDF_FILENAME}"

        mediaStore.getContainer().getBlockBlobReference(PDF_FILENAME).exists()
    }

    private void verifyThatTheCurrentPageTitleIs(String aTitle) {
        assert title == aTitle
    }

    def cleanup() {
        metadataStore.documentIsPresentWithFilename PDF_FILENAME
        mediaStore.removeContentWithFilenames PDF_FILENAME
    }

}
