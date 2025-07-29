package org.example.project

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.example.project.composeapp.generated.resources.Res
import org.example.project.composeapp.generated.resources.compose_multiplatform
import org.jetbrains.compose.resources.painterResource
import org.multipaz.cbor.Simple
import org.multipaz.compose.presentment.Presentment
import org.multipaz.compose.qrcode.generateQrCode
import org.multipaz.credential.Credential
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.document.Document
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.mdoc.engagement.EngagementGenerator
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.mdoc.transport.advertise
import org.multipaz.mdoc.transport.waitForConnection
import org.multipaz.models.presentment.MdocPresentmentMechanism
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.models.presentment.PresentmentSource
import org.multipaz.models.presentment.SimplePresentmentSource
import org.multipaz.request.Request
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.util.UUID
import org.multipaz.util.toBase64Url

// Expect function for platform-specific image decoding
expect fun decodeImageFromBytes(bytes: ByteArray): ImageBitmap?

@Composable
fun DocumentDetailFragment(
    document: Document,
    onClose: () -> Unit,
    presentmentModel: PresentmentModel,
    documentStore: DocumentStore,
    documentTypeRepository: DocumentTypeRepository,
    readerTrustManager: TrustManager?
) {
    var credentials by remember { mutableStateOf<List<Credential>>(emptyList()) }
    var cardArtBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val state = presentmentModel?.state?.collectAsState()
    val deviceEngagement = remember { mutableStateOf<ByteString?>(null) }
    var presentmentSource: PresentmentSource? = null
    var showQrOverlay by remember { mutableStateOf(false) }
    val documentTypeRepository = DocumentTypeRepository().apply {
        addDocumentType(DrivingLicense.getDocumentType())
    }

    @Composable
    fun showQrButton(showQrCode: MutableState<ByteString?>) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = {
                presentmentModel.reset()
                presentmentModel.setConnecting()
                presentmentModel.presentmentScope.launch() {
                    val connectionMethods = listOf(
                        MdocConnectionMethodBle(
                            supportsPeripheralServerMode = false,
                            supportsCentralClientMode = true,
                            peripheralServerModeUuid = null,
                            centralClientModeUuid = UUID.randomUUID(),
                        )
                    )
                    val eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256)
                    val advertisedTransports = connectionMethods.advertise(
                        role = MdocRole.MDOC,
                        transportFactory = MdocTransportFactory.Default,
                        options = MdocTransportOptions(bleUseL2CAP = true),
                    )
                    val engagementGenerator = EngagementGenerator(
                        eSenderKey = eDeviceKey.publicKey,
                        version = "1.0"
                    )
                    engagementGenerator.addConnectionMethods(advertisedTransports.map {
                        it.connectionMethod
                    })
                    val encodedDeviceEngagement = ByteString(engagementGenerator.generate())
                    showQrCode.value = encodedDeviceEngagement
                    val transport = advertisedTransports.waitForConnection(
                        eSenderKey = eDeviceKey.publicKey,
                        coroutineScope = presentmentModel.presentmentScope
                    )
                    presentmentModel.setMechanism(
                        MdocPresentmentMechanism(
                            transport = transport,
                            eDeviceKey = eDeviceKey,
                            encodedDeviceEngagement = encodedDeviceEngagement,
                            handover = Simple.NULL,
                            engagementDuration = null,
                            allowMultipleRequests = false
                        )
                    )
                    showQrCode.value = null
                }
            }) {
                Text("Present mDL via QR")
            }
        }
    }
    @Composable
    fun showQrCode(deviceEngagement: MutableState<ByteString?>) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            println("deviceEngagement showQrCode: ${deviceEngagement.value}")
            if (deviceEngagement.value != null) {
                val mdocUrl = "mdoc:" + deviceEngagement.value!!.toByteArray().toBase64Url()
                val qrCodeBitmap = remember { generateQrCode(mdocUrl) }
                Text(text = "Present QR code to mdoc reader")
                Image(
                    modifier = Modifier.fillMaxWidth(),
                    bitmap = qrCodeBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth
                )
                Button(
                    onClick = {
                        presentmentModel.reset()
                    }
                ) {
                    Text("Cancel")
                }
            }
        }
    }

    LaunchedEffect(document) {
        credentials = document.getCredentials()
        // Convert card art ByteString to ImageBitmap
        try {
            document.metadata.cardArt?.let { byteString ->
                cardArtBitmap = decodeImageFromBytes(byteString.toByteArray())
            }
        } catch (e: Exception) {
            // Handle any decoding errors
            cardArtBitmap = null
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeContentPadding()
            .padding(16.dp)
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onClose,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text("← Back")
            }
            
            Text(
                text = "Document Details",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(80.dp)) // Balance the layout
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Document Actions:")
            Button(
                onClick = {
                    showQrOverlay = true
                    // Generate QR code
                    presentmentModel.reset()
                    presentmentModel.setConnecting()
                    presentmentModel.presentmentScope.launch() {
                        val connectionMethods = listOf(
                            MdocConnectionMethodBle(
                                supportsPeripheralServerMode = false,
                                supportsCentralClientMode = true,
                                peripheralServerModeUuid = null,
                                centralClientModeUuid = UUID.randomUUID(),
                            )
                        )
                        val eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256)
                        val advertisedTransports = connectionMethods.advertise(
                            role = MdocRole.MDOC,
                            transportFactory = MdocTransportFactory.Default,
                            options = MdocTransportOptions(bleUseL2CAP = true),
                        )
                        val engagementGenerator = EngagementGenerator(
                            eSenderKey = eDeviceKey.publicKey,
                            version = "1.0"
                        )
                        engagementGenerator.addConnectionMethods(advertisedTransports.map {
                            it.connectionMethod
                        })
                        val encodedDeviceEngagement = ByteString(engagementGenerator.generate())
                        deviceEngagement.value = encodedDeviceEngagement
                    }
                }
            ) {
                Text("Present via QR")
            }
        }

        presentmentSource = SimplePresentmentSource(
            documentStore = documentStore,
            documentTypeRepository = documentTypeRepository,
            readerTrustManager = readerTrustManager!!,
            preferSignatureToKeyAgreement = true,
            domainMdocSignature = "mdoc",
        )

        /*presentmentSource = object : PresentmentSource(
            documentStore = documentStore,
            documentTypeRepository = documentTypeRepository!!,
            readerTrustManager = readerTrustManager!!
        ) {
            override suspend fun selectCredential(
                document: Document?,
                request: Request,
                keyAgreementPossible: List<EcCurve>
            ): Credential? {
                // Return only the credential from the specific document
                return document?.let { docId ->
                    println("Selecting credential for document: ${docId.identifier}")
                    val credential = documentStore.lookupDocument(document.identifier)
                        ?.getCertifiedCredentials()
                        ?.firstOrNull()
                    println("Selected credential: ${credential?.document?.identifier}")
                    return credential
                }
            }
        }*/


        when (state?.value) {
            PresentmentModel.State.IDLE -> {
                showQrButton(deviceEngagement)
            }

            PresentmentModel.State.CONNECTING -> {
                println("deviceEngagement CONNECTING: ${deviceEngagement.value}")
                showQrCode(deviceEngagement)
            }

            PresentmentModel.State.WAITING_FOR_SOURCE,
            PresentmentModel.State.PROCESSING,
            PresentmentModel.State.WAITING_FOR_DOCUMENT_SELECTION,
            PresentmentModel.State.WAITING_FOR_CONSENT,
            PresentmentModel.State.COMPLETED -> {
                println("deviceEngagement COMPLETED: ${deviceEngagement.value}")
                Presentment(
                    appName = "Multipaz Getting Started Sample",
                    appIconPainter = painterResource(Res.drawable.compose_multiplatform),
                    presentmentModel = presentmentModel,
                    presentmentSource = presentmentSource!!,
                    documentTypeRepository = documentTypeRepository!!,
                    onPresentmentComplete = {
                        presentmentModel.reset()
                    },
                )
            }
            null -> {
            }
        }
        
        // Main content in a scrollable card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Driving License Image
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(bottom = 24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Image(
                        painter = if (cardArtBitmap != null) {
                            BitmapPainter(cardArtBitmap!!)
                        } else {
                            painterResource(Res.drawable.compose_multiplatform)
                        },
                        contentDescription = "Driving License Card",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                
                // Display Name
                Text(
                    text = "Display Name",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = document.metadata.displayName!!,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Type
                Text(
                    text = "Document Type",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = document.metadata.typeDisplayName!!,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Document ID
                Text(
                    text = "Document ID",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = document.identifier,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Credentials Section
                Text(
                    text = "Associated Credentials",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                
                if (credentials.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "No credentials found for this document",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    Text(
                        text = "${credentials.size} credential(s) found:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    credentials.forEach { credential ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = credential.document.metadata.displayName!!,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Type: ${credential.document.metadata.typeDisplayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onClose,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
    
    // QR Code Overlay
    if (showQrOverlay) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (deviceEngagement.value != null) {
                    val mdocUrl = "mdoc:" + deviceEngagement.value!!.toByteArray().toBase64Url()
                    val qrCodeBitmap = remember { generateQrCode(mdocUrl) }
                    
                    Text(
                        text = "Present QR code to mdoc reader",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Image(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        bitmap = qrCodeBitmap,
                        contentDescription = "QR Code",
                        contentScale = ContentScale.FillWidth
                    )
                    
                    Button(
                        onClick = {
                            showQrOverlay = false
                            deviceEngagement.value = null
                            presentmentModel.reset()
                        },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("Close QR Code")
                    }
                } else {
                    Text(
                        text = "Generating QR Code...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            }
        }
    }
} 