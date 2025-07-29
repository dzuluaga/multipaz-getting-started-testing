package org.example.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import org.example.project.composeapp.generated.resources.Res
import org.example.project.composeapp.generated.resources.compose_multiplatform
import org.example.project.composeapp.generated.resources.driving_license_card_art
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.multipaz.compose.prompt.PromptDialogs
import org.multipaz.document.Document
import org.multipaz.document.DocumentStore
import org.multipaz.document.buildDocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.prompt.PromptModel
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.SecureAreaRepository
import kotlinx.io.bytestring.encodeToByteString
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Simple
import org.multipaz.compose.permissions.rememberBluetoothPermissionState
import org.multipaz.credential.Credential
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509CertChain
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.compose.presentment.Presentment
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.models.presentment.PresentmentSource
import org.multipaz.models.presentment.SimplePresentmentSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days


@Composable
private fun showQrButton(
    showQrCode: MutableState<ByteString?>,
    presentmentModel: PresentmentModel?,
    documentStore: DocumentStore?,
    documentList: List<Document>
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = {
            presentmentModel?.reset()
            presentmentModel?.setConnecting()
            presentmentModel?.presentmentScope?.launch() {
                // QR code generation logic would go here
                // For now, just show a placeholder
            }
        }) {
            Text("Present mDL via QR")
        }
    }
}
@Composable
@Preview
fun App(promptModel: PromptModel) {
    var document by remember { mutableStateOf<Document?>(null) }
    var mdocCredential by remember { mutableStateOf<MdocCredential?>(null) }
    var documentStore by remember { mutableStateOf<DocumentStore?>(null) }
    var documentList by remember { mutableStateOf<List<Document>>(emptyList()) }
    var showDocuments by remember { mutableStateOf(false) }
    var selectedDocument by remember { mutableStateOf<Document?>(null) }
    var showDocumentDetail by remember { mutableStateOf(false) }

    val blePermissionState = rememberBluetoothPermissionState()

    var readerTrustManager: TrustManager? = null
    var presentmentModel: PresentmentModel? = null
    var presentmentSource: PresentmentSource? = null
    var documentTypeRepository: DocumentTypeRepository? = null

    LaunchedEffect(Unit) {
        // Initialize platform components
        val storage = org.multipaz.util.Platform.getNonBackedUpStorage()
        val secureArea = org.multipaz.util.Platform.getSecureArea(storage)
        val secureAreaRepository = SecureAreaRepository.Builder().add(secureArea).build()

        // Initialize TrustManager
        readerTrustManager = TrustManager().apply {
            addTrustPoint(
                TrustPoint(
                    certificate = X509Cert.fromPem(
                        """
                        -----BEGIN CERTIFICATE-----
                        MIICUTCCAdegAwIBAgIQppKZHI1iPN290JKEA79OpzAKBggqhkjOPQQDAzArMSkwJwYDVQQDDCBP
                        V0YgTXVsdGlwYXogVGVzdEFwcCBSZWFkZXIgUm9vdDAeFw0yNDEyMDEwMDAwMDBaFw0zNDEyMDEw
                        MDAwMDBaMCsxKTAnBgNVBAMMIE9XRiBNdWx0aXBheiBUZXN0QXBwIFJlYWRlciBSb290MHYwEAYH
                        KoZIzj0CAQYFK4EEACIDYgAE+QDye70m2O0llPXMjVjxVZz3m5k6agT+wih+L79b7jyqUl99sbeU
                        npxaLD+cmB3HK3twkA7fmVJSobBc+9CDhkh3mx6n+YoH5RulaSWThWBfMyRjsfVODkosHLCDnbPV
                        o4G/MIG8MA4GA1UdDwEB/wQEAwIBBjASBgNVHRMBAf8ECDAGAQH/AgEAMFYGA1UdHwRPME0wS6BJ
                        oEeGRWh0dHBzOi8vZ2l0aHViLmNvbS9vcGVud2FsbGV0LWZvdW5kYXRpb24tbGFicy9pZGVudGl0
                        eS1jcmVkZW50aWFsL2NybDAdBgNVHQ4EFgQUq2Ub4FbCkFPx3X9s5Ie+aN5gyfUwHwYDVR0jBBgw
                        FoAUq2Ub4FbCkFPx3X9s5Ie+aN5gyfUwCgYIKoZIzj0EAwMDaAAwZQIxANN9WUvI1xtZQmAKS4/D
                        ZVwofqLNRZL/co94Owi1XH5LgyiBpS3E8xSxE9SDNlVVhgIwKtXNBEBHNA7FKeAxKAzu4+MUf4gz
                        8jvyFaE0EUVlS2F5tARYQkU6udFePucVdloi
                        -----END CERTIFICATE-----
                        """.trimIndent().trim()
                    ),
                    displayName = "OWF Multipaz TestApp",
                    displayIcon = null,
                    privacyPolicyUrl = "https://apps.multipaz.org"
                )
            )
        }

        // Initialize document components
        documentTypeRepository = DocumentTypeRepository().apply {
            addDocumentType(DrivingLicense.getDocumentType())
        }
        val docStore =
            buildDocumentStore(storage = storage, secureAreaRepository = secureAreaRepository) {}
        documentStore = docStore

        // Initialize PresentmentModel and PresentmentSource
        presentmentModel = PresentmentModel().apply { setPromptModel(promptModel) }
        presentmentSource = SimplePresentmentSource(
            documentStore = documentStore!!,
            documentTypeRepository = documentTypeRepository!!,
            readerTrustManager = readerTrustManager!!,
            preferSignatureToKeyAgreement = true,
            domainMdocSignature = "mdoc",
        )

        // Create document
        document = docStore.createDocument(
            displayName = "Erika's Driving License",
            typeDisplayName = "Utopia Driving License",
            cardArt = kotlinx.io.bytestring.ByteString(
                getDrawableResourceBytes(
                    getSystemResourceEnvironment(),
                    Res.drawable.driving_license_card_art,
                )
            ),
        )

        // Time setup
        val now = kotlinx.datetime.Clock.System.now()
        val signedAt = now
        val validFrom = now
        val validUntil = now + 365.days

        // IACA Certificate
        val iacaKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val iacaCert = MdocUtil.generateIacaCertificate(
            iacaKey = iacaKey,
            subject = X500Name.fromName(name = "CN=Test IACA Key"),
            serial = ASN1Integer.fromRandom(numBits = 128),
            validFrom = validFrom,
            validUntil = validUntil,
            issuerAltNameUrl = "https://issuer.example.com",
            crlUrl = "https://issuer.example.com/crl"
        )

        // Document Signing (DS) Certificate
        val dsKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val dsCert = MdocUtil.generateDsCertificate(
            iacaCert = iacaCert,
            iacaKey = iacaKey,
            dsKey = dsKey.publicKey,
            subject = X500Name.fromName(name = "CN=Test DS Key"),
            serial = ASN1Integer.fromRandom(numBits = 128),
            validFrom = validFrom,
            validUntil = validUntil
        )

        // Create the mDoc Credential object
        mdocCredential = DrivingLicense.getDocumentType().createMdocCredentialWithSampleData(
            document = document!!,
            secureArea = secureArea,
            createKeySettings = CreateKeySettings(
                algorithm = Algorithm.ESP256,
                nonce = "Challenge".encodeToByteString(),
                userAuthenticationRequired = true
            ),
            dsKey = dsKey,
            dsCertChain = X509CertChain(listOf(dsCert)),
            signedAt = signedAt,
            validFrom = validFrom,
            validUntil = validUntil,
        )
    }

    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        val deviceEngagement = remember { mutableStateOf<ByteString?>(null) }
        val state = presentmentModel?.state?.collectAsState() ?: remember { mutableStateOf(null) }

        // Show document detail fragment as full screen
        if (showDocumentDetail && selectedDocument != null) {
            DocumentDetailFragment(
                document = selectedDocument!!,
                onClose = {
                    showDocumentDetail = false
                    selectedDocument = null
                }
            )
        } else {
            // Handle PresentmentModel states
            when (state.value) {
                PresentmentModel.State.IDLE -> {
                    showQrButton(deviceEngagement, presentmentModel, documentStore, documentList)
                }

                PresentmentModel.State.WAITING_FOR_SOURCE,
                PresentmentModel.State.PROCESSING,
                PresentmentModel.State.WAITING_FOR_DOCUMENT_SELECTION,
                PresentmentModel.State.WAITING_FOR_CONSENT,
                PresentmentModel.State.COMPLETED -> {
                    if (presentmentModel != null && presentmentSource != null && documentTypeRepository != null) {
                        Presentment(
                            appName = "Multipaz Getting Started Sample",
                            appIconPainter = painterResource(Res.drawable.compose_multiplatform),
                            presentmentModel = presentmentModel,
                            presentmentSource = presentmentSource,
                            documentTypeRepository = documentTypeRepository,
                            onPresentmentComplete = {
                                presentmentModel.reset()
                            },
                        )
                    }
                }

                else -> {
                    // Main app content
                    Column(
                        modifier = Modifier
                            .safeContentPadding()
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // BLE Permission Button
                        if (!blePermissionState.isGranted) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        blePermissionState.launchPermissionRequest()
                                    }
                                },
                                modifier = Modifier.padding(bottom = 16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Request BLE permissions")
                            }
                        } else {
                            Text(
                                text = "✅ BLE permissions granted",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                        Button(onClick = { showContent = !showContent }) {
                            Text("Click me-ABC!")
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    documentStore?.let { store ->
                                        val documentIds = store.listDocuments()
                                        val documents = mutableListOf<Document>()

                                        documentIds.forEach { documentId ->
                                            store.lookupDocument(documentId)?.let { doc ->
                                                documents.add(doc)
                                            }
                                        }

                                        documentList = documents
                                        showDocuments = true
                                    }
                                }
                            },
                            enabled = documentStore != null
                        ) {
                            Text("Show Documents")
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    documentStore?.let { store ->
                                        val documentCount = documentList.size + 1
                                        val newDocument = store.createDocument(
                                            displayName = "Document #$documentCount",
                                            typeDisplayName = "Utopia Driving License",
                                            cardArt = kotlinx.io.bytestring.ByteString(
                                                getDrawableResourceBytes(
                                                    getSystemResourceEnvironment(),
                                                    Res.drawable.driving_license_card_art,
                                                )
                                            ),
                                        )

                                        // Refresh the document list if it's currently showing
                                        if (showDocuments) {
                                            val documentIds = store.listDocuments()
                                            val documents = mutableListOf<Document>()

                                            documentIds.forEach { documentId ->
                                                store.lookupDocument(documentId)?.let { doc ->
                                                    documents.add(doc)
                                                }
                                            }

                                            documentList = documents
                                        }
                                    }
                                }
                            },
                            enabled = documentStore != null
                        ) {
                            Text("Create Document")
                        }

                        // Debug info
                        Text(
                            text = "Debug: showDocumentDetail = $showDocumentDetail, selectedDocument = ${selectedDocument?.identifier ?: "null"}",
                            style = MaterialTheme.typography.bodySmall
                        )

                        AnimatedVisibility(showDocuments) {
                            Column(
                                Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "DOCUMENTS LIST (${documentList.size}) - UPDATED!",
                                    style = MaterialTheme.typography.headlineSmall
                                )

                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(documentList) { doc ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                            onClick = {
                                                selectedDocument = doc
                                                showDocumentDetail = true
                                                println("Document selected: ${doc.identifier}") // Debug log
                                            }
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp)
                                            ) {
                                                Text(
                                                    text = doc.metadata.displayName!!,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    text = doc.metadata.typeDisplayName!!,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                Spacer(modifier = Modifier.height(8.dp))

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "ID123: ${doc.identifier}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )

                                                    Text(
                                                        text = "Tap for details →",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))

                                                Text(
                                                    text = "🔥 DELETE BUTTON SHOULD BE HERE 🔥",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.error
                                                )

                                                Button(
                                                    onClick = {
                                                        println("Delete button clicked for document: ${doc.identifier}")
                                                        coroutineScope.launch {
                                                            documentStore?.deleteDocument(doc.identifier)
                                                            // Refresh the document list
                                                            val documentIds =
                                                                documentStore?.listDocuments()
                                                                    ?: emptyList()
                                                            val documents =
                                                                mutableListOf<Document>()

                                                            documentIds.forEach { documentId ->
                                                                documentStore?.lookupDocument(
                                                                    documentId
                                                                )?.let { document ->
                                                                    documents.add(document)
                                                                }
                                                            }

                                                            documentList = documents
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.error
                                                    )
                                                ) {
                                                    Text("🔥 DELETE BUTTON 🔥")
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(onClick = { showDocuments = false }) {
                                    Text("Hide Documents")
                                }
                            }
                        }

                        AnimatedVisibility(showContent) {
                            val greeting = remember { Greeting().greet() }
                            Column(
                                Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(painterResource(Res.drawable.compose_multiplatform), null)
                                Text("Compose: $greeting")
                            }
                        }
                    }
                }
            }
        }
        
        PromptDialogs(promptModel)
    }
}
