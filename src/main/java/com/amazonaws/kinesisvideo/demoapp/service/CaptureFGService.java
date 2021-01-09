// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.kinesisvideo.demoapp.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.kinesisvideo.demoapp.KinesisVideoWebRtcDemoApp;
import com.amazonaws.kinesisvideo.demoapp.R;
import com.amazonaws.kinesisvideo.demoapp.fragment.StreamWebRtcConfigurationFragment;
import com.amazonaws.kinesisvideo.signaling.SignalingListener;
import com.amazonaws.kinesisvideo.signaling.model.Event;
import com.amazonaws.kinesisvideo.signaling.model.Message;
import com.amazonaws.kinesisvideo.signaling.tyrus.SignalingServiceWebSocketClient;
import com.amazonaws.kinesisvideo.utils.AwsV4Signer;
import com.amazonaws.kinesisvideo.webrtc.KinesisVideoPeerConnection;
import com.amazonaws.kinesisvideo.webrtc.KinesisVideoSdpObserver;

import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceServer;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RTCStats;
import org.webrtc.RTCStatsCollectorCallback;
import org.webrtc.RTCStatsReport;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

import androidx.core.app.NotificationCompat;

//blog - Add
public class CaptureFGService extends Service{
    private static final String TAG = "CaptureFGService";
    private static final String VideoTrackID = "KvsVideoTrack";
    private static final String LOCAL_MEDIA_STREAM_LABEL = "KvsLocalMediaStream";
    private static final int VIDEO_SIZE_WIDTH = 400;
    private static final int VIDEO_SIZE_HEIGHT = 300;
    private static final int VIDEO_FPS = 30;
    private static final String CHANNEL_ID = "WebRtcDataChannel";
    private static final boolean ENABLE_INTEL_VP8_ENCODER = true;
    private static final boolean ENABLE_H264_HIGH_PROFILE = true;

    private static volatile SignalingServiceWebSocketClient client;
    private PeerConnectionFactory peerConnectionFactory;

    private VideoSource videoSource;
    private VideoTrack localVideoTrack;

    private PeerConnection localPeer;

    private EglBase rootEglBase = null;

    private static VideoCapturer videoCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;

    private final List<IceServer> peerIceServers = new ArrayList<>();

    private boolean gotException = false;

    private String recipientClientId;


    private boolean master = true;


    private String mChannelArn;
    private String mClientId;

    private String mWssEndpoint;
    private String mRegion;


    private AWSCredentials mCreds = null;

    private void initWsConnection() {

        Log.e(TAG,"initWsConnection()" );

        final String masterEndpoint = mWssEndpoint + "?X-Amz-ChannelARN=" + mChannelArn;

        final String viewerEndpoint = mWssEndpoint + "?X-Amz-ChannelARN=" + mChannelArn + "&X-Amz-ClientId=" + mClientId;

        URI signedUri;

        mCreds = KinesisVideoWebRtcDemoApp.getCredentialsProvider().getCredentials();


        signedUri = getSignedUri(masterEndpoint, viewerEndpoint);

        if (master) {
            createLocalPeerConnection();
        }

        final String wsHost = signedUri.toString();

        final SignalingListener signalingListener = new SignalingListener() {

            @Override
            public void onSdpOffer(final Event offerEvent) {
                Log.d(TAG, "Received SDP Offer: Setting Remote Description ");

                final String sdp = Event.parseOfferEvent(offerEvent);

                localPeer.setRemoteDescription(new KinesisVideoSdpObserver(),
                        new SessionDescription(SessionDescription.Type.OFFER, sdp));

                recipientClientId = offerEvent.getSenderClientId();

                Log.d(TAG, "Received SDP offer: Creating answer");

                createSdpAnswer();
            }

            @Override
            public void onSdpAnswer(final Event answerEvent) {

                Log.d(TAG, "SDP answer received from signaling");

                final String sdp = Event.parseSdpEvent(answerEvent);

                final SessionDescription sdpAnswer = new SessionDescription(SessionDescription.Type.ANSWER, sdp);

                localPeer.setRemoteDescription(new KinesisVideoSdpObserver(), sdpAnswer);

            }

            @Override
            public void onIceCandidate(Event message) {

                Log.d(TAG, "Received IceCandidate from remote ");

                final IceCandidate iceCandidate = Event.parseIceCandidate(message);

                if(iceCandidate != null) {
                    // Remote sent us ICE candidates, add to local peer connection
                    final boolean addIce = localPeer.addIceCandidate(iceCandidate);

                    Log.d(TAG, "Added ice candidate " + iceCandidate + " " + (addIce ? "Successfully" : "Failed"));
                } else {
                    Log.e(TAG, "Invalid Ice candidate");
                }
            }

            @Override
            public void onError(Event errorMessage) {

                Log.e(TAG, "Received error message" + errorMessage);

            }

            @Override
            public void onException(Exception e) {
                Log.e(TAG, "Signaling client returned exception " + e.getMessage());
                gotException = true;
            }
        };


        if (wsHost != null) {
            try {
                client = new SignalingServiceWebSocketClient(wsHost, signalingListener, Executors.newFixedThreadPool(10));

                Log.d(TAG, "Client connection " + (client.isOpen() ? "Successful" : "Failed"));
            } catch (Exception e) {
                gotException = true;
            }

            if (isValidClient()) {

                Log.d(TAG, "Client connected to Signaling service " + client.isOpen());

                if (!master) {
                    Log.d(TAG, "Signaling service is connected: " +
                            "Sending offer as viewer to remote peer"); // Viewer

                    createSdpOffer();
                }
            } else {
                Log.e(TAG, "Error in connecting to signaling service");
                gotException = true;
            }
        }


    }

    private boolean isValidClient() {
        return client != null && client.isOpen();
    }

    @Override
    public void onDestroy() {
        Thread.setDefaultUncaughtExceptionHandler(null);

        Log.d(TAG, "onDestroy ");


        if (rootEglBase != null) {
            rootEglBase.release();
            rootEglBase = null;
        }



        if (localPeer != null) {
            localPeer.dispose();
            localPeer = null;
        }

        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop webrtc video capture. ", e);
            }
            videoCapturer = null;
        }


        if (client != null) {
            client.disconnect();
            client = null;
        }


        super.onDestroy();
    }

    @androidx.annotation.Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startWsConnection() {
        Log.d(TAG, "startWsConnection ");

        // Start websocket after adding local audio/video tracks
        initWsConnection();

        if (!gotException && isValidClient()) {
            Toast.makeText(this, "Signaling Connected", Toast.LENGTH_LONG).show();
        } else {
            notifySignalingConnectionFailed();
        }
    }

    private void notifySignalingConnectionFailed() {
       // finish();
        Toast.makeText(this, "Connection error to signaling", Toast.LENGTH_LONG).show();
    }


    void runAsForeground() {
        Log.d(TAG, "runAsForeground ");

        Intent notificationIntent = new Intent(this, NotificationListener.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, 0);


        if (Build.VERSION.SDK_INT >= 26) {

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Kinesis Screen Capturer Channel",
                    NotificationManager.IMPORTANCE_DEFAULT);

            NotificationManager manager = getSystemService(NotificationManager.class);

            manager.createNotificationChannel(channel);

        }
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Kinesis Screen Capturer")
                .setContentText("Screen Casting.. If you want to stop, please touch this notification")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .build();


        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(TAG, "onStartCommand()");

        runAsForeground();

        mChannelArn = intent.getStringExtra(StreamWebRtcConfigurationFragment.KEY_CHANNEL_ARN);
        mWssEndpoint = intent.getStringExtra(StreamWebRtcConfigurationFragment.KEY_WSS_ENDPOINT);

        mClientId = intent.getStringExtra(StreamWebRtcConfigurationFragment.KEY_CLIENT_ID);
        if (mClientId == null || mClientId.isEmpty()) {
            mClientId = UUID.randomUUID().toString();
        }
        master = intent.getBooleanExtra(StreamWebRtcConfigurationFragment.KEY_IS_MASTER, true);
        ArrayList<String> mUserNames = intent.getStringArrayListExtra(StreamWebRtcConfigurationFragment.KEY_ICE_SERVER_USER_NAME);
        ArrayList<String> mPasswords = intent.getStringArrayListExtra(StreamWebRtcConfigurationFragment.KEY_ICE_SERVER_PASSWORD);
        ArrayList<List<String>> mUrisList = (ArrayList<List<String>>) intent.getSerializableExtra(StreamWebRtcConfigurationFragment.KEY_ICE_SERVER_URI);

        mPermissionResultData = (Intent) intent.getParcelableExtra("PermissionResultData");
        mRegion = intent.getStringExtra(StreamWebRtcConfigurationFragment.KEY_REGION);

        rootEglBase = EglBase.create();


        PeerConnection.IceServer stun = PeerConnection
                .IceServer
                .builder(String.format("stun:stun.kinesisvideo.%s.amazonaws.com:443", mRegion))
                .createIceServer();

        peerIceServers.add(stun);

        if (mUrisList != null) {
            for (int i = 0; i < mUrisList.size(); i++) {
                String turnServer = mUrisList.get(i).toString();
                if( turnServer != null) {
                    IceServer iceServer = IceServer.builder(turnServer.replace("[", "").replace("]", ""))
                            .setUsername(mUserNames.get(i))
                            .setPassword(mPasswords.get(i))
                            .createIceServer();
                    Log.d(TAG, "IceServer details (TURN) = " + iceServer.toString());
                    peerIceServers.add(iceServer);
                }
            }
        }

        PeerConnectionFactory.initialize(PeerConnectionFactory
                .InitializationOptions
                .builder(this)
                .createInitializationOptions());

        peerConnectionFactory =
                PeerConnectionFactory.builder()
                        .setVideoDecoderFactory(new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext()))
                        .setVideoEncoderFactory(new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(), ENABLE_INTEL_VP8_ENCODER, ENABLE_H264_HIGH_PROFILE))
                        .createPeerConnectionFactory();


        videoSource = peerConnectionFactory.createVideoSource(true);
        localVideoTrack = peerConnectionFactory.createVideoTrack(VideoTrackID, videoSource);

        startScreenCast();

        startWsConnection();

        return START_NOT_STICKY;
    }
    private void startScreenCast() {
        Log.d(TAG, "startScreenCast()");
        surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().getName(), rootEglBase.getEglBaseContext());

        videoCapturer = createScreenCapturerAndroid();
        videoCapturer.initialize(surfaceTextureHelper, this.getApplicationContext(), videoSource.getCapturerObserver());
        videoCapturer.startCapture(VIDEO_SIZE_WIDTH, VIDEO_SIZE_HEIGHT, VIDEO_FPS);
    }

    private static Intent mPermissionResultData;

    private VideoCapturer createScreenCapturerAndroid() {

        Log.d(TAG, "createScreenCapturerAndroid()");

        return new ScreenCapturerAndroid(
                mPermissionResultData, new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.e(TAG, "user select cancel ");
            }
        });
    }



    private void createLocalPeerConnection() {
        Log.d(TAG, "createLocalPeerConnection()");

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(peerIceServers);

        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;

        localPeer = peerConnectionFactory.createPeerConnection(rtcConfig, new KinesisVideoPeerConnection() {

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {

                super.onIceCandidate(iceCandidate);

                Message message = createIceCandidateMessage(iceCandidate);
                Log.d(TAG, "Sending IceCandidate to remote peer " + iceCandidate.toString());
                client.sendIceCandidate(message);  /* Send to Peer */

            }

            @Override
            public void onAddStream(MediaStream mediaStream) {

                super.onAddStream(mediaStream);

                Log.d(TAG, "Adding remote video stream (and audio) to the view");

            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {
                super.onDataChannel(dataChannel);

            }
        });

        if (localPeer != null) {

            localPeer.getStats(new RTCStatsCollectorCallback() {

                @Override
                public void onStatsDelivered(RTCStatsReport rtcStatsReport) {

                    Map<String, RTCStats> statsMap = rtcStatsReport.getStatsMap();

                    Set<Map.Entry<String, RTCStats>> entries = statsMap.entrySet();

                    for (Map.Entry<String, RTCStats> entry : entries) {

                        Log.d(TAG, "Stats: " + entry.getKey() + " ," + entry.getValue());

                    }
                }
            });
        }

        addDataChannelToLocalPeer();
        addStreamToLocalPeer();
    }

    private Message createIceCandidateMessage(IceCandidate iceCandidate) {
        Log.e(TAG, "createIceCandidateMessage()");

        String sdpMid = iceCandidate.sdpMid;
        int sdpMLineIndex = iceCandidate.sdpMLineIndex;
        String sdp = iceCandidate.sdp;

        String messagePayload =
                "{\"candidate\":\""
                        + sdp
                        + "\",\"sdpMid\":\""
                        + sdpMid
                        + "\",\"sdpMLineIndex\":"
                        + sdpMLineIndex
                        + "}";

        String senderClientId = (master) ? "" : mClientId;

        return new Message("ICE_CANDIDATE", recipientClientId, senderClientId,
                new String(Base64.encode(messagePayload.getBytes(),
                        Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP)));
    }

    private void addStreamToLocalPeer() {
        Log.e(TAG, "addStreamToLocalPeer()");

        MediaStream stream = peerConnectionFactory.createLocalMediaStream(LOCAL_MEDIA_STREAM_LABEL);

        if (!stream.addTrack(localVideoTrack)) {

            Log.e(TAG, "Add video track failed");
        }

        localPeer.addTrack(stream.videoTracks.get(0), Collections.singletonList(stream.getId()));



    }

    private void addDataChannelToLocalPeer() {
        Log.d(TAG, "Data channel addDataChannelToLocalPeer");
        DataChannel localDataChannel = localPeer.createDataChannel("data-channel-of-" + mClientId, new DataChannel.Init());
        localDataChannel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long l) {
                Log.d(TAG, "Local Data Channel onBufferedAmountChange called with amount " + l);
            }

            @Override
            public void onStateChange() {
                Log.d(TAG, "Local Data Channel onStateChange: state: " + localDataChannel.state().toString());


            }

            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                // Send out data, no op on sender side
            }
        });


    }

    // when mobile sdk is viewer
    private void createSdpOffer() {
        Log.e(TAG, "createSdpOffer()");

        MediaConstraints sdpMediaConstraints = new MediaConstraints();

        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));

        if (localPeer == null) {

            createLocalPeerConnection();
        }

        localPeer.createOffer(new KinesisVideoSdpObserver() {

            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {

                super.onCreateSuccess(sessionDescription);

                localPeer.setLocalDescription(new KinesisVideoSdpObserver(), sessionDescription);

                Message sdpOfferMessage = Message.createOfferMessage(sessionDescription, mClientId);

                if (isValidClient()) {
                    client.sendSdpOffer(sdpOfferMessage);
                } else {
                    notifySignalingConnectionFailed();
                }
            }
        }, sdpMediaConstraints);
    }


    // when local is set to be the master
    private void createSdpAnswer() {
        Log.e(TAG, "createSdpAnswer()");

        localPeer.createAnswer(new KinesisVideoSdpObserver() {

            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "Creating answer : success");
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new KinesisVideoSdpObserver(), sessionDescription);
                Message answer = Message.createAnswerMessage(sessionDescription, master, recipientClientId);
                client.sendSdpAnswer(answer);
            }
        }, new MediaConstraints());

    }




    private URI getSignedUri(String masterEndpoint, String viewerEndpoint) {
        Log.e(TAG, "getSignedUri()");

        URI signedUri;

        if (master) {
            signedUri = AwsV4Signer.sign(URI.create(masterEndpoint), mCreds.getAWSAccessKeyId(),
                    mCreds.getAWSSecretKey(), mCreds instanceof AWSSessionCredentials ? ((AWSSessionCredentials) mCreds).getSessionToken() : "", URI.create(mWssEndpoint), mRegion);
        } else {
            signedUri = AwsV4Signer.sign(URI.create(viewerEndpoint), mCreds.getAWSAccessKeyId(),
                    mCreds.getAWSSecretKey(), mCreds instanceof AWSSessionCredentials ? ((AWSSessionCredentials)mCreds).getSessionToken() : "", URI.create(mWssEndpoint), mRegion);
        }
        return signedUri;
    }

    public static class NotificationListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "NotificationListener onReceive");
            Toast.makeText(context, "capture stopped!", Toast.LENGTH_LONG).show();
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }


}
