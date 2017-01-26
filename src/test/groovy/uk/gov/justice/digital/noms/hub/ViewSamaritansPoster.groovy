package uk.gov.justice.digital.noms.hub

import com.mashape.unirest.http.HttpResponse
import com.mashape.unirest.http.Unirest
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
import org.apache.http.HttpStatus

import java.security.InvalidKeyException

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.is
import static org.hamcrest.MatcherAssert.assertThat

/**
 *
 * Placeholder for License.
 */
@Slf4j
class ViewSamaritansPoster extends GebSpec {
    private static final String IMAGE_FILE_NAME = 'Listener caller awareness digi screens ENGLISH vB slide6.jpg'
    public static final String TITLE_STR = 'hub-function-test:Upload Samaritan Posters:-Automated Test - 1'

    private String mongoDbUrl
    private String azurePublicUrlBase
    private String adminRestUrl
    private String contentFeedAppUrl

    private File file

    private MongoDatabase mongoDatabase

    private static final String AZURE_CONTAINER_NAME = "content-items"
    private CloudBlobContainer container

    def setup() {
        file = new File(this.getClass().getResource('/' + IMAGE_FILE_NAME).toURI())
        setAdminRestUrl()
        setContentFeedUrl()
        setupMongoDB()
        setupAzureBlobStore()
    }

    def cleanup() {
        mongoDatabase.getCollection('contentItem').deleteMany(new BasicDBObject(title: TITLE_STR))
        removeFileInMediaStore()
    }

    def 'View Samaritans Poster'() {
        //setup

        given: 'that is content uploaded'
        uploadSamaritanImage()

        when: 'I view the hub'
        go contentFeedAppUrl

        then: 'I see the last image uploaded'
        assertThat($('h1').text(), is('Hub Content Feed'))
        sleep(1000)
        assertThat($('#uriId').text(), containsString('Listener caller awareness digi screens ENGLISH vB slide6'))

        and: 'its most recent title'
        assertThat($('#titleId').text(), is(TITLE_STR))
    }

    private void uploadSamaritanImage() {
        HttpResponse<String> response = Unirest.post(adminRestUrl+'/content-items')
                .header('accept', 'application/json')
                .field('title', TITLE_STR)
                .field('file', file)
                .asString();
        assertThat(response.getStatus(), is(HttpStatus.SC_CREATED));
    }

    private void setupMongoDB() {
        mongoDbUrl = System.getenv('mongoDbUrl')
        if(!mongoDbUrl) {
            mongoDbUrl = 'mongodb://localhost:27017'
            log.info('mongoDbUrl: local')
        }
        MongoClient mongoClient = new MongoClient(new MongoClientURI(mongoDbUrl))
        mongoDatabase = mongoClient.getDatabase('hub_metadata')
    }

    private void setAdminRestUrl() {
        adminRestUrl = System.getenv('adminRestUrl')
        if(!adminRestUrl) {
            adminRestUrl =  'http://hub-admin.herokuapp.com/hub-admin/'
            log.info('adminAppUrl: local')
        }
    }

    private void setContentFeedUrl() {
        contentFeedAppUrl = System.getenv('contentFeedAppUrl')
        if(!contentFeedAppUrl) {
            contentFeedAppUrl = 'https://noms-digital-studio.github.io/hub-content-feed-ui/'
            log.info('contentFeedAppUrl: local')
        }
    }

    private void setupAzureBlobStore() throws URISyntaxException, InvalidKeyException, StorageException {
        setupAzurePublicUrlBase()
        container = setupAzureCloudStorageAcccount().createCloudBlobClient().getContainerReference(AZURE_CONTAINER_NAME)
        container.createIfNotExists()

        BlobContainerPermissions containerPermissions = new BlobContainerPermissions()
        containerPermissions.setPublicAccess(BlobContainerPublicAccessType.CONTAINER)
        container.uploadPermissions(containerPermissions)
    }

    private void setupAzurePublicUrlBase() {
        azurePublicUrlBase = System.getenv('azureBlobStorePublicUrlBase')
        if (!azurePublicUrlBase) {
            azurePublicUrlBase = 'http://digitalhub2.blob.core.windows.net'
            log.info('azurePublicUrlBase: local')
        }
    }

    private CloudStorageAccount setupAzureCloudStorageAcccount() {
        String azureConnectionUri = System.getenv('azureBlobStoreConnUri')
        if (!azureConnectionUri) {
            throw new RuntimeException('azureBlobStoreConnUri environment variable was not set')
        }
        CloudStorageAccount.parse(azureConnectionUri)
    }

    private void removeFileInMediaStore() throws URISyntaxException, StorageException {
        CloudBlockBlob blob = container.getBlockBlobReference(IMAGE_FILE_NAME)
        blob.deleteIfExists()
    }

}