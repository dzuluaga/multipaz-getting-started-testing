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
import org.multipaz.securearea.SecureAreaRepository
import kotlinx.io.bytestring.encodeToByteString
import org.multipaz.asn1.ASN1Integer
import org.multipaz.compose.permissions.rememberBluetoothPermissionState
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509CertChain
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.securearea.SecureArea
import org.multipaz.util.fromHex
import kotlin.time.Duration.Companion.days
import org.multipaz.mdoc.issuersigned.buildIssuerNamespaces
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.RawCbor
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.addCborMap
import org.multipaz.cbor.Tstr
import kotlinx.datetime.LocalDate
import org.multipaz.cbor.toDataItem
import org.multipaz.cbor.toDataItemFullDate
import org.multipaz.cose.Cose
import org.multipaz.cose.CoseLabel
import org.multipaz.cose.CoseNumberLabel
import org.multipaz.mdoc.mso.MobileSecurityObjectGenerator
import kotlinx.datetime.Instant
import org.example.project.composeapp.generated.resources.driver_license_male_1
import org.example.project.composeapp.generated.resources.driver_license_male_2
import org.example.project.composeapp.generated.resources.driver_license_male_3
import org.example.project.composeapp.generated.resources.driver_license_male_4
import org.example.project.composeapp.generated.resources.driver_license_woman_1
import org.example.project.composeapp.generated.resources.driver_license_woman_2
import org.example.project.composeapp.generated.resources.driver_license_woman_3
import org.example.project.composeapp.generated.resources.driver_license_woman_4
import org.multipaz.claim.MdocClaim
import org.multipaz.crypto.Crypto


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
    var documentTypeRepository: DocumentTypeRepository? = null
    var secureArea: SecureArea? = null
    val dsKey = Crypto.createEcPrivateKey(EcCurve.P256)
    // IACA Certificate
    val iacaCert = X509Cert.fromPem(
        """
                        -----BEGIN CERTIFICATE-----
                        MIICYzCCAemgAwIBAgIQ36kOae8cfvOqQ+mO4YhnpDAKBggqhkjOPQQDAzAuMQswCQYDVQQGDAJV
                        UzEfMB0GA1UEAwwWT1dGIE11bHRpcGF6IFRFU1QgSUFDQTAeFw0yNTA3MjQxMTE3MTlaFw0zMDA3
                        MjQxMTE3MTlaMC4xCzAJBgNVBAYMAlVTMR8wHQYDVQQDDBZPV0YgTXVsdGlwYXogVEVTVCBJQUNB
                        MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEQQJf9BH+fJytVI4K4nQvHJAfzapvuT6jo+19fo+o9+zV
                        PFnOYtsbPXB5sPeuMMv5ZkQGmn9yWCgpbZHAS2pJ/eJXAcLp9uH8BGo6pYhkPomx9cwgMX0YUXoB
                        4wiO6w9eo4HLMIHIMA4GA1UdDwEB/wQEAwIBBjASBgNVHRMBAf8ECDAGAQH/AgEAMC0GA1UdEgQm
                        MCSGImh0dHBzOi8vaXNzdWVyLmV4YW1wbGUuY29tL3dlYnNpdGUwMwYDVR0fBCwwKjAooCagJIYi
                        aHR0cHM6Ly9pc3N1ZXIuZXhhbXBsZS5jb20vY3JsLmNybDAdBgNVHQ4EFgQUPbetw5QkxGKjazN0
                        qI9YfaexD+0wHwYDVR0jBBgwFoAUPbetw5QkxGKjazN0qI9YfaexD+0wCgYIKoZIzj0EAwMDaAAw
                        ZQIxAKizj2YexKf1+CTBCOV4ehyiUU5MSi9iPScW32+halSCVUtbmW63fpG+37obLGivegIwb38g
                        xhIRxDdIk1CBVsqANCFUvdBuSoORRV5928xo/B9he5ZFyb8b6UauJS70AMD8
                        -----END CERTIFICATE-----
                    """.trimIndent()
    )

    val iacaKey = EcPrivateKey.fromPem(
        """
                        -----BEGIN PRIVATE KEY-----
                        MFcCAQAwEAYHKoZIzj0CAQYFK4EEACIEQDA+AgEBBDBEPQnb6xr3p0XKGucrf3iVI/sDF2fc55vs
                        T31kxam8x8ocKu4ETouTZM+DZKu0cD+gBwYFK4EEACI=
                        -----END PRIVATE KEY-----
                    """.trimIndent(),
        iacaCert.ecPublicKey
    )
    // Time setup
    val now = kotlinx.datetime.Clock.System.now()
    val signedAt = now
    val validFrom = now
    val validUntil = now + 365.days

    // Document Signing (DS) Certificate
    val dsCert = MdocUtil.generateDsCertificate(
        iacaCert = iacaCert,
        iacaKey = iacaKey,
        dsKey = dsKey.publicKey,
        subject = X500Name.fromName(name = "CN=Test DS Key"),
        serial = ASN1Integer.fromRandom(numBits = 128),
        validFrom = validFrom,
        validUntil = validUntil
    )

    LaunchedEffect(Unit) {
        // Initialize platform components
        val storage = org.multipaz.util.Platform.getNonBackedUpStorage()
        secureArea = org.multipaz.util.Platform.getSecureArea(storage)
        val secureAreaRepository = SecureAreaRepository.Builder().add(secureArea!!).build()

        // Initialize TrustManager
        readerTrustManager = TrustManager().apply {
            addTrustPoint(
                TrustPoint(
                    certificate = X509Cert.fromPem( // Multipaz TestApp certificate
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
            /*addTrustPoint(
                TrustPoint(
                    certificate = X509Cert.fromPem( // Multipaz Reader Root certificate
                        """
                        -----BEGIN CERTIFICATE-----
                        MIICiTCCAg+gAwIBAgIQQd/7PXEzsmI+U14J2cO1bjAKBggqhkjOPQQDAzBHMQswCQYDVQQGDAJV
                        UzE4MDYGA1UEAwwvTXVsdGlwYXogSWRlbnRpdHkgUmVhZGVyIENBIChVbnRydXN0ZWQgRGV2aWNl
                        cykwHhcNMjUwNzE5MjMwODE0WhcNMzAwNzE5MjMwODE0WjBHMQswCQYDVQQGDAJVUzE4MDYGA1UE
                        AwwvTXVsdGlwYXogSWRlbnRpdHkgUmVhZGVyIENBIChVbnRydXN0ZWQgRGV2aWNlcykwdjAQBgcq
                        hkjOPQIBBgUrgQQAIgNiAATqihOe05W3nIdyVf7yE4mHJiz7tsofcmiNTonwYsPKBbJwRTHa7AME
                        +ToAfNhPMaEZ83lBUTBggsTUNShVp1L5xzPS+jK0tGJkR2ny9+UygPGtUZxEOulGK5I8ZId+35Gj
                        gb8wgbwwDgYDVR0PAQH/BAQDAgEGMBIGA1UdEwEB/wQIMAYBAf8CAQAwVgYDVR0fBE8wTTBLoEmg
                        R4ZFaHR0cHM6Ly9naXRodWIuY29tL29wZW53YWxsZXQtZm91bmRhdGlvbi1sYWJzL2lkZW50aXR5
                        LWNyZWRlbnRpYWwvY3JsMB0GA1UdDgQWBBSbz9r9IFmXjiGGnH3Siq90geurxTAfBgNVHSMEGDAW
                        gBSbz9r9IFmXjiGGnH3Siq90geurxTAKBggqhkjOPQQDAwNoADBlAjEAomqjfJe2k162S5Way3sE
                        BTcj7+DPvaLJcsloEsj/HaThIsKWqQlQKxgNu1rE/XryAjB/Gq6UErgWKlspp+KpzuAAWaKk+bMj
                        cM4aKOKOU3itmB+9jXTQ290Dc8MnWVwQBs4=
                        -----END CERTIFICATE-----""".trimIndent().trim()
                    ),
                    displayName = "Multipaz Reader Root",
                    displayIcon = null,
                    privacyPolicyUrl = "https://apps.multipaz.org/reader-root-policy"
                )
            )*/

            // This is for Multipaz Identity Reader app from https://apps.multipaz.org on devices in
            // the GREEN boot state.
            addTrustPoint(
                            TrustPoint(
                                certificate = X509Cert( // Multipaz Reader Root certificate
                                    "30820261308201E7A00302010202103925792727AC38B28778373ED2A9ADB9300A06082A8648CE3D0403033033310B300906035504060C0255533124302206035504030C1B4D756C746970617A204964656E7469747920526561646572204341301E170D3235303730353132323032315A170D3330303730353132323032315A3033310B300906035504060C0255533124302206035504030C1B4D756C746970617A204964656E74697479205265616465722043413076301006072A8648CE3D020106052B81040022036200043E145F98DA6C32EE4688C4A7DAEC6640046CFF0872E8F7A8DE3005462AE9488E92850B30E2D46FEEFC620A279BEB09470AB20C9F66C584E396A9625BC3E90DFBA54197A3668D901AAA41F493C89E4AC20689794FED1352CD2086413965006C54A381BF3081BC300E0603551D0F0101FF04040302010630120603551D130101FF040830060101FF02010030560603551D1F044F304D304BA049A047864568747470733A2F2F6769746875622E636F6D2F6F70656E77616C6C65742D666F756E646174696F6E2D6C6162732F6964656E746974792D63726564656E7469616C2F63726C301D0603551D0E04160414CFA4AF87907312962E4D7A17646ACC1C45719B21301F0603551D23041830168014CFA4AF87907312962E4D7A17646ACC1C45719B21300A06082A8648CE3D040303036800306502310090FB8F814BCC87DB42957D22B54D20BF45F44CE0CF5734167ED27F5E3E0F5FB57505B797B894175D2BD98BF16CE726EA02305BA4F1ECB894A9DBE27B9BBF988F233C2E0BB0B4BADAA3EC5B3EA9D99C58DAD26128A4B363849E32626A9D5C3CE3E4DA".fromHex()
                                ),
                                displayName = "Multipaz Verifier",
                                displayIcon = null,
                                privacyPolicyUrl = "https://apps.multipaz.org"
                            )
                        )

            // This is for Multipaz Identity Reader either compiled locally or the APK from https://apps.multipaz.org
            // but running on a device that isn't in the GREEN boot state.
            addTrustPoint(
                TrustPoint(
                    certificate = X509Cert( // Multipaz Reader Root certificate
                        "308202893082020FA003020102021041DFFB3D7133B2623E535E09D9C3B56E300A06082A8648CE3D0403033047310B300906035504060C0255533138303606035504030C2F4D756C746970617A204964656E74697479205265616465722043412028556E74727573746564204465766963657329301E170D3235303731393233303831345A170D3330303731393233303831345A3047310B300906035504060C0255533138303606035504030C2F4D756C746970617A204964656E74697479205265616465722043412028556E747275737465642044657669636573293076301006072A8648CE3D020106052B8104002203620004EA8A139ED395B79C877255FEF2138987262CFBB6CA1F72688D4E89F062C3CA05B2704531DAEC0304F93A007CD84F31A119F3794151306082C4D4352855A752F9C733D2FA32B4B462644769F2F7E53280F1AD519C443AE9462B923C64877EDF91A381BF3081BC300E0603551D0F0101FF04040302010630120603551D130101FF040830060101FF02010030560603551D1F044F304D304BA049A047864568747470733A2F2F6769746875622E636F6D2F6F70656E77616C6C65742D666F756E646174696F6E2D6C6162732F6964656E746974792D63726564656E7469616C2F63726C301D0603551D0E041604149BCFDAFD2059978E21869C7DD28AAF7481EBABC5301F0603551D230418301680149BCFDAFD2059978E21869C7DD28AAF7481EBABC5300A06082A8648CE3D0403030368003065023100A26AA37C97B6935EB64B959ACB7B04053723EFE0CFBDA2C972C96812C8FF1DA4E122C296A909502B180DBB5AC4FD7AF202307F1AAE9412B8162A5B29A7E2A9CEE00059A2A4F9B32370CE1A28E28E5378AD981FBD8D74D0DBDD0373C327595C1006CE".fromHex()
                    ),
                    displayName = "Multipaz Identity Reader (Untrusted Devices)",
                    privacyPolicyUrl = "https://apps.multipaz.org",
                    displayIcon = null
                )
            )
        }


        // Initialize document components
        documentTypeRepository = DocumentTypeRepository().apply {
            addDocumentType(DrivingLicense.getDocumentType())
        }
        documentStore = buildDocumentStore(storage = storage, secureAreaRepository = secureAreaRepository) {}

        // Initialize PresentmentModel and PresentmentSource
        presentmentModel = PresentmentModel().apply { setPromptModel(promptModel) }
        
        val specificDocumentId = documentStore!!.listDocuments().firstOrNull()

        //Print the pem of the IACA key
        println("IACA Key: ${iacaCert.toPem()}")

        /*// Create the mDoc Credential object
        mdocCredential = DrivingLicense.getDocumentType().createMdocCredentialWithSampleData(
            document = document!!,
            secureArea = secureArea!!,
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
        )*/
    }

    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()


        // Show document detail fragment as full screen
        if (showDocumentDetail && selectedDocument != null) {
            DocumentDetailFragment(
                selectedDocument = selectedDocument!!,
                onClose = {
                    showDocumentDetail = false
                    selectedDocument = null
                },
                presentmentModel = presentmentModel!!,
                documentStore = documentStore!!,
                documentTypeRepository = documentTypeRepository!!,
                readerTrustManager = readerTrustManager,
                promptModel = promptModel
            )
        } else {

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
                                // Create a new document with credential
                                val newDocument = createSampleDocument(
                                    documentStore = store,
                                    secureArea = secureArea!!,
                                    dsKey = dsKey,
                                    dsCert = dsCert,
                                    signedAt = signedAt,
                                    validFrom = validFrom,
                                    validUntil = validUntil
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
                    enabled = documentStore != null && secureArea != null
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
                                        // Print family name from document credentials
                                        coroutineScope.launch {
                                            val credentials = doc.getCredentials()
                                            val familyName = credentials.firstOrNull()?.let { credential ->
                                                credential.getClaims(documentTypeRepository!!)
                                                    .filterIsInstance<MdocClaim>()
                                                    .find { it.dataElementName == "family_name" }
                                                    ?.render()
                                            } ?: "Not available"
                                            println("Family name App.kt: $familyName")
                                        }
                                        
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

            PromptDialogs(promptModel)
        }
    }
}

private suspend fun createSampleDocument(
    documentStore: DocumentStore,
    secureArea: SecureArea,
    dsKey: EcPrivateKey,
    dsCert: X509Cert,
    signedAt: Instant,
    validFrom: Instant,
    validUntil: Instant
): Document {
    val documentCount = documentStore.listDocuments().size + 1
    val newDocument = documentStore.createDocument(
        displayName = "Document #$documentCount",
        typeDisplayName = "Utopia Driving License",
        cardArt = kotlinx.io.bytestring.ByteString(
            getDrawableResourceBytes(
                getSystemResourceEnvironment(),
                Res.drawable.driving_license_card_art,
            )
        ),
    )

    // Generate random realistic data
    val familyNames = listOf("Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas", "Taylor", "Moore", "Jackson", "Martin", "Lee")
    val maleGivenNames = listOf("James", "John", "Robert", "Michael", "William", "David", "Richard", "Joseph", "Thomas", "Christopher", "Charles", "Daniel", "Matthew", "Anthony", "Mark", "Donald", "Steven", "Paul", "Andrew", "Joshua")
    val femaleGivenNames = listOf("Mary", "Patricia", "Jennifer", "Linda", "Elizabeth", "Barbara", "Susan", "Jessica", "Sarah", "Karen", "Nancy", "Lisa", "Betty", "Helen", "Sandra", "Donna", "Carol", "Ruth", "Sharon", "Michelle")
    val cities = listOf("New York", "Los Angeles", "Chicago", "Houston", "Phoenix", "Philadelphia", "San Antonio", "San Diego", "Dallas", "San Jose", "Austin", "Jacksonville", "Fort Worth", "Columbus", "Charlotte", "San Francisco", "Indianapolis", "Seattle", "Denver", "Washington")
    val states = listOf("NY", "CA", "IL", "TX", "AZ", "PA", "FL", "OH", "NC", "WA", "CO", "GA", "MI", "VA", "NJ", "OR", "TN", "MA", "IN", "MO")
    val streets = listOf("Main St", "Oak Ave", "Maple Dr", "Cedar Ln", "Pine Rd", "Elm St", "Washington Ave", "Park Dr", "Lake Rd", "River St", "Hill Ave", "Spring Ln", "Forest Dr", "Meadow Rd", "Sunset Blvd", "Ocean Ave", "Mountain Dr", "Valley Rd", "Bridge St", "Center Ave")
    
    // Randomly select gender (0 = female, 1 = male)
    val randomGender = (0..1).random()
    val randomFamilyName = familyNames.random()
    val randomGivenName = if (randomGender == 1) maleGivenNames.random() else femaleGivenNames.random()
    println("Generated document for ${if (randomGender == 1) "male" else "female"}: $randomGivenName $randomFamilyName")
    
    // Read the portrait image based on gender with random selection from multiple options
    val portraitBytes = getDrawableResourceBytes(
        getSystemResourceEnvironment(),
        if (randomGender == 1) {
            // Randomly select from male images
            val resource = when ((1..4).random()) {
                1 -> Res.drawable.driver_license_male_1
                2 -> Res.drawable.driver_license_male_2
                3 -> Res.drawable.driver_license_male_3
                4 -> Res.drawable.driver_license_male_4
                else -> Res.drawable.driver_license_male_1
            }
            resource
        } else {
            // Randomly select from female images
            when ((1..4).random()) {
                1 -> Res.drawable.driver_license_woman_1
                2 -> Res.drawable.driver_license_woman_2
                3 -> Res.drawable.driver_license_woman_3
                4 -> Res.drawable.driver_license_woman_4
                else -> Res.drawable.driver_license_woman_1
            }
        }
    )
    
    val randomCity = cities.random()
    val randomState = states.random()
    val randomStreet = streets.random()
    val randomHouseNumber = (100..9999).random()
    val randomZipCode = (10000..99999).random()
    val randomBirthYear = (1980..2000).random()
    val randomBirthMonth = (1..12).random()
    val randomBirthDay = (1..28).random()
    val randomHeight = (150..200).random()
    val randomWeight = (50..120).random()
    val randomDocumentNumber = (100000000..999999999).random().toString()
    val randomAdminNumber = (100000000..999999999).random().toString()
    
    val mdocCredential = MdocCredential.create(
        document = newDocument!!,
        asReplacementForIdentifier = null,
        domain = "mdoc",
        secureArea = secureArea,
        docType = DrivingLicense.MDL_DOCTYPE,
        createKeySettings = CreateKeySettings(
            algorithm = Algorithm.ESP256,
            nonce = "Challenge".encodeToByteString(),
            userAuthenticationRequired = true
        )
    )

    // Create your own issuer namespaces with custom data
    val issuerNamespaces = buildIssuerNamespaces {
        addNamespace(DrivingLicense.MDL_NAMESPACE) {
            // Add your custom data here
            addDataElement("family_name", randomFamilyName.toDataItem())
            addDataElement("given_name", randomGivenName.toDataItem())
            addDataElement("birth_date", LocalDate.parse("$randomBirthYear-${randomBirthMonth.toString().padStart(2, '0')}-${randomBirthDay.toString().padStart(2, '0')}").toDataItemFullDate())
            addDataElement("issue_date", LocalDate.parse("2024-01-01").toDataItemFullDate())
            addDataElement("expiry_date", LocalDate.parse("2029-01-01").toDataItemFullDate())
            addDataElement("issuing_country", "US".toDataItem())
            addDataElement("issuing_authority", "$randomState Department of Motor Vehicles".toDataItem())
            addDataElement("document_number", randomDocumentNumber.toDataItem())
            addDataElement("portrait", portraitBytes.toDataItem())
            addDataElement("driving_privileges", buildCborArray {
                addCborMap {
                    put("vehicle_category_code", "A")
                    put("issue_date", Tagged(1004, Tstr("2024-01-01")))
                    put("expiry_date", Tagged(1004, Tstr("2029-01-01")))
                }
            })
            addDataElement("un_distinguishing_sign", "USA".toDataItem())
            addDataElement("administrative_number", randomAdminNumber.toDataItem())
            addDataElement("sex", randomGender.toDataItem()) // 0 = female, 1 = male
            addDataElement("height", randomHeight.toDataItem()) // height in cm
            addDataElement("weight", randomWeight.toDataItem()) // weight in kg
            addDataElement("birth_place", randomCity.toDataItem())
            addDataElement("birth_state", randomState.toDataItem())
            addDataElement("birth_city", randomCity.toDataItem())
            addDataElement("resident_address", "$randomHouseNumber $randomStreet, $randomZipCode $randomCity, $randomState".toDataItem())
            addDataElement("portrait_capture_date", "2024-01-01".toDataItem())
        }
    }

    // Generate MSO and certify the credential
    val msoGenerator = MobileSecurityObjectGenerator(
        Algorithm.SHA256,
        DrivingLicense.MDL_DOCTYPE,
        mdocCredential.getAttestation().publicKey
    )
    msoGenerator.setValidityInfo(signedAt, validFrom, validUntil, null)
    msoGenerator.addValueDigests(issuerNamespaces)

    val mso = msoGenerator.generate()
    val taggedEncodedMso = Cbor.encode(Tagged(24, Bstr(mso)))

    // Create issuer authentication data
    val protectedHeaders = mapOf<CoseLabel, DataItem>(
        Pair(
            CoseNumberLabel(Cose.COSE_LABEL_ALG),
            Algorithm.ES256.coseAlgorithmIdentifier!!.toDataItem()
        )
    )
    val unprotectedHeaders = mapOf<CoseLabel, DataItem>(
        Pair(
            CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN),
            X509CertChain(listOf(dsCert)).toDataItem()
        )
    )
    val encodedIssuerAuth = Cbor.encode(
        Cose.coseSign1Sign(
            dsKey,
            taggedEncodedMso,
            true,
            dsKey.publicKey.curve.defaultSigningAlgorithm,
            protectedHeaders,
            unprotectedHeaders
        ).toDataItem()
    )
    val issuerProvidedAuthenticationData = Cbor.encode(
        buildCborMap {
            put("nameSpaces", issuerNamespaces.toDataItem())
            put("issuerAuth", RawCbor(encodedIssuerAuth))
        }
    )

    // Certify the credential
    mdocCredential.certify(
        issuerProvidedAuthenticationData,
        validFrom,
        validUntil
    )

    return newDocument
}