package com.github.okwrtdsh.nearbytest

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*


class MainActivity : AppCompatActivity() {

    var mRemoteEndpointId: String? = null
    lateinit var mConnectionsClient: ConnectionsClient

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1
        private const val TAG: String = "nearbytest"
        private const val SERVICE_ID: String = "com.github.okwrtdsh.nearbytest"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        this.applicationContext
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mConnectionsClient = Nearby.getConnectionsClient(this)

        buttonStartAdvertising.setOnClickListener {
            startAdvertising()
        }

        buttonStopAdvertising.setOnClickListener {
            stopAdvertising()
        }

        buttonStartDiscovery.setOnClickListener {
            startDiscovery()
        }

        buttonStopDiscovery.setOnClickListener {
            stopDiscovery()
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
            REQUEST_CODE_PERMISSIONS
        )
    }

    override fun onStop() {
        super.onStop()
        mConnectionsClient.stopAdvertising()
        mConnectionsClient.stopDiscovery()
        mConnectionsClient.stopAllEndpoints()
    }

    private fun startAdvertising() {
        mConnectionsClient.startAdvertising(
            getNickName(),
            SERVICE_ID,
            mConnectionLifecycleCallback,
            AdvertisingOptions(Strategy.P2P_CLUSTER)
        )
            .addOnSuccessListener {
                debug("Success startAdvertising: $it")
            }
            .addOnFailureListener {
                debug("Failure startDiscovery: $it")
            }
    }

    private fun stopAdvertising() {
        mConnectionsClient.stopAdvertising()
        debug("Success stopAdvertising")
    }

    private fun startDiscovery() {
        mConnectionsClient.startDiscovery(
            packageName,
            mEndpointDiscoveryCallback,
            DiscoveryOptions(Strategy.P2P_CLUSTER)
        )
            .addOnSuccessListener {
                debug("Success startDiscovery: $it")
            }
            .addOnFailureListener {
                debug("Failure startDiscovery: $it")
            }
    }

    private fun stopDiscovery() {
        mConnectionsClient.stopDiscovery()
        debug("Success stopDiscovery")
    }

    private fun sendString(content: String) {
        mConnectionsClient.sendPayload(
            mRemoteEndpointId!!,
            Payload.fromBytes(content.toByteArray(Charsets.UTF_8))
        )
    }

    private fun disconnectFromEndpoint() {
        mConnectionsClient.disconnectFromEndpoint(mRemoteEndpointId!!)
        mRemoteEndpointId = null
    }


    private val mEndpointDiscoveryCallback = object : EndpointDiscoveryCallback() {

        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // An endpoint was found. We request a connection to it.
            debug("mEndpointDiscoveryCallback.onEndpointFound $endpointId:$info:${info.endpointName}")

            mConnectionsClient.requestConnection(
                getNickName(),
                endpointId,
                mConnectionLifecycleCallback
            )
                .addOnSuccessListener {
                    // We successfully requested a connection. Now both sides
                    // must accept before the connection is established.
                    debug("Success requestConnection: $it")
                }
                .addOnFailureListener {
                    // Nearby Connections failed to request the connection.
                    // TODO: retry
                    debug("Failure requestConnection: $it")
                }
        }

        override fun onEndpointLost(endpointId: String) {
            // A previously discovered endpoint has gone away.
            debug("mEndpointDiscoveryCallback.onEndpointLost $endpointId")
        }
    }

    private val mConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {

        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            debug("mConnectionLifecycleCallback.onConnectionInitiated $endpointId:${connectionInfo.endpointName}")

            // Automatically accept the connection on both sides.
            mConnectionsClient.acceptConnection(endpointId, mPayloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            debug("mConnectionLifecycleCallback.onConnectionResult $endpointId")

            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    debug("onnectionsStatusCodes.STATUS_OK $endpointId")
                    // We're connected! Can now start sending and receiving data.
                    mRemoteEndpointId = endpointId

                    sendString("Hello ${mRemoteEndpointId}!")
                }

                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    // The connection was rejected by one or both sides.
                    debug("onnectionsStatusCodes.STATUS_CONNECTION_REJECTED $endpointId")
                }

                ConnectionsStatusCodes.STATUS_ERROR -> {
                    // The connection broke before it was able to be accepted.
                    debug("onnectionsStatusCodes.STATUS_ERROR $endpointId")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            // We've been disconnected from this endpoint. No more data can be
            // sent or received.
            debug("mConnectionLifecycleCallback.onDisconnected $endpointId")
            disconnectFromEndpoint()
        }

    }

    private val mPayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            debug("mPayloadCallback.onPayloadReceived $endpointId")

            when (payload.type) {
                Payload.Type.BYTES -> {
                    val data = payload.asBytes()!!
                    debug("Payload.Type.BYTES: ${data.toString(Charsets.UTF_8)}")
                }
                Payload.Type.FILE -> {
                    val file = payload.asFile()!!
                    debug("Payload.Type.FILE: size: ${file.size}")
                    // TODO:

                }
                Payload.Type.STREAM -> {
                    val stream = payload.asStream()!!
                    debug("Payload.Type.STREAM")
                    // TODO:
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Bytes payloads are sent as a single chunk, so you'll receive a SUCCESS update immediately
            // after the call to onPayloadReceived().
            // debug("mPayloadCallback.onPayloadTransferUpdate $endpointId")
        }
    }


    private fun debug(message: String) {
        textViewLog.append(message + "\n")
        Log.d(TAG, message)
    }

    private fun getNickName() = UUID.randomUUID().toString()
}
