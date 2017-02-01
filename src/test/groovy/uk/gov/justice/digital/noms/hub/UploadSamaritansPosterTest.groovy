package uk.gov.justice.digital.noms.hub

import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.StorageException
import com.microsoft.azure.storage.blob.BlobContainerPermissions
import com.microsoft.azure.storage.blob.BlobContainerPublicAccessType
import com.microsoft.azure.storage.blob.CloudBlobContainer
import com.microsoft.azure.storage.blob.CloudBlockBlob
import com.mongodb.BasicDBObject
import com.mongodb.MongoClient
import com.mongodb.MongoClientURI
import com.mongodb.client.MongoDatabase
import geb.spock.GebSpec
import groovy.util.logging.Slf4j
import org.bson.Document

import java.security.InvalidKeyException
import java.util.concurrent.Callable

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.is
import static org.hamcrest.MatcherAssert.assertThat

@Slf4j
class UploadSamaritansPosterTest extends GebSpec {
    private static final String IMAGE_FILE_NAME = 'Listener caller awareness digi screens ENGLISH vB slide6.jpg'
    private static final String TITLE_STR = 'hub-function-test:Upload Samaritan Posters:-Automated Test - 1'
    private static final String CATEGORY_STR = 'education'
    private static final String AZURE_CONTAINER_NAME = "content-items"

    private String mongoDbUrl
    private String azurePublicUrlBase
    private String adminAppUrl
    private File file
    private MongoDatabase mongoDatabase
    private CloudBlobContainer container

    def setup() {
        file = new File(this.getClass().getResource('/' + IMAGE_FILE_NAME).toURI())
        setAdminUrl()
        setupMongoDB()
        setupAzureBlobStore()
    }

    def cleanup() {
        mongoDatabase.getCollection('contentItem').deleteMany(new BasicDBObject(title: TITLE_STR))
        removeFileInMediaStore()
    }

    def 'Upload Samaritan Posters'() {
        setup:
        go adminAppUrl
        assertThat($('h1').text(), is('The Hub Admin UI'))

        given: 'that I have selected an image'
        $('form').file = file.absolutePath
        assertThat($('form').file, containsString(IMAGE_FILE_NAME))

        and: 'provided a title'
        $('form').title = TITLE_STR
        assertThat($('form').title, is(TITLE_STR))

        and: 'provided a category'
        $('form').category = CATEGORY_STR
        assertThat($('form').category, is(CATEGORY_STR))

        when: 'I click Save'
        $('input[type=submit]').click()

        then: 'the image and title are published'
        org.awaitility.Awaitility.await().until(theDataIsPresentInMongo())

        Document document = mongoDatabase.getCollection('contentItem').find(new BasicDBObject(title: TITLE_STR)).first()
        document != null
        document.title == TITLE_STR
        document.category == CATEGORY_STR
        document.uri == azurePublicUrlBase + '/content-items/' + IMAGE_FILE_NAME
        container.getBlockBlobReference(IMAGE_FILE_NAME).exists()
    }

    def theDataIsPresentInMongo() {
        return new Callable<Boolean>() {
            Boolean call() throws Exception {
                Document document = mongoDatabase.getCollection('contentItem')
                                                 .find(new BasicDBObject(title: TITLE_STR)).first()
                return document != null
            }
        }
    }

    def setupMongoDB() {
        mongoDbUrl = System.getenv('mongoDbUrl')
        if (!mongoDbUrl) {
            mongoDbUrl = 'mongodb://localhost:27017'
            log.info('mongoDbUrl: local')
        }
        MongoClient mongoClient = new MongoClient(new MongoClientURI(mongoDbUrl))
        mongoDatabase = mongoClient.getDatabase('hub_metadata')
    }

    def setAdminUrl() {
        adminAppUrl = System.getenv('adminAppUrl')
        if (!adminAppUrl) {
            adminAppUrl = 'https://noms-digital-studio.github.io/hub-admin-ui/'
            log.info('adminAppUrl: local')
        }
    }

    def setupAzureBlobStore() throws URISyntaxException, InvalidKeyException, StorageException {
        setupAzurePublicUrlBase()
        container = setupAzureCloudStorageAcccount().createCloudBlobClient().getContainerReference(AZURE_CONTAINER_NAME)
        container.createIfNotExists()

        BlobContainerPermissions containerPermissions = new BlobContainerPermissions()
        containerPermissions.setPublicAccess(BlobContainerPublicAccessType.CONTAINER)
        container.uploadPermissions(containerPermissions)
    }

    def setupAzurePublicUrlBase() {
        azurePublicUrlBase = System.getenv('azureBlobStorePublicUrlBase')
        if (!azurePublicUrlBase) {
            azurePublicUrlBase = 'http://digitalhub2.blob.core.windows.net'
            log.info('azurePublicUrlBase: local')
        }
    }

    def setupAzureCloudStorageAcccount() {
        String azureConnectionUri = System.getenv('azureBlobStoreConnUri')
        if (!azureConnectionUri) {
            throw new RuntimeException('azureBlobStoreConnUri environment variable was not set')
        }
        CloudStorageAccount.parse(azureConnectionUri)
    }

    def removeFileInMediaStore() throws URISyntaxException, StorageException {
        CloudBlockBlob blob = container.getBlockBlobReference(IMAGE_FILE_NAME)
        blob.deleteIfExists()
    }
}
