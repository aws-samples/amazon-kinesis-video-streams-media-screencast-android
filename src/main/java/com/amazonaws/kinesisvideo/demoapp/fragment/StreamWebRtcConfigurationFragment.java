// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazonaws.kinesisvideo.demoapp.fragment;

import android.Manifest;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.amazonaws.kinesisvideo.demoapp.KinesisVideoWebRtcDemoApp;
import com.amazonaws.kinesisvideo.demoapp.R;
import com.amazonaws.kinesisvideo.demoapp.activity.SimpleNavActivity;
import com.amazonaws.kinesisvideo.demoapp.activity.WebRtcActivity;

import com.amazonaws.regions.Region;
import com.amazonaws.services.kinesisvideo.AWSKinesisVideoClient;
import com.amazonaws.services.kinesisvideo.model.ChannelRole;
import com.amazonaws.services.kinesisvideo.model.CreateSignalingChannelRequest;
import com.amazonaws.services.kinesisvideo.model.CreateSignalingChannelResult;
import com.amazonaws.services.kinesisvideo.model.DescribeSignalingChannelRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeSignalingChannelResult;
import com.amazonaws.services.kinesisvideo.model.GetSignalingChannelEndpointRequest;
import com.amazonaws.services.kinesisvideo.model.GetSignalingChannelEndpointResult;
import com.amazonaws.services.kinesisvideo.model.ResourceEndpointListItem;
import com.amazonaws.services.kinesisvideo.model.ResourceNotFoundException;
import com.amazonaws.services.kinesisvideo.model.SingleMasterChannelEndpointConfiguration;
import com.amazonaws.services.kinesisvideosignaling.AWSKinesisVideoSignalingClient;
import com.amazonaws.services.kinesisvideosignaling.model.GetIceServerConfigRequest;
import com.amazonaws.services.kinesisvideosignaling.model.GetIceServerConfigResult;
import com.amazonaws.services.kinesisvideosignaling.model.IceServer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//blog - Add
import android.app.ActivityManager;
import android.content.Context;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import com.amazonaws.kinesisvideo.demoapp.service.CaptureFGService;
public class StreamWebRtcConfigurationFragment extends Fragment {
    private static final String TAG = StreamWebRtcConfigurationFragment.class.getSimpleName();

    private static final String KEY_CHANNEL_NAME = "channelName";
    public static final String KEY_CLIENT_ID = "clientId";
    public static final String KEY_REGION = "region";
    public static final String KEY_CHANNEL_ARN = "channelArn";
    public static final String KEY_WSS_ENDPOINT = "wssEndpoint";
    public static final String KEY_IS_MASTER = "isMaster";
    public static final String KEY_ICE_SERVER_USER_NAME = "iceServerUserName";
    public static final String KEY_ICE_SERVER_PASSWORD = "iceServerPassword";
    public static final String KEY_ICE_SERVER_TTL = "iceServerTTL";
    public static final String KEY_ICE_SERVER_URI = "iceServerUri";
    public static final String KEY_CAMERA_FRONT_FACING = "cameraFrontFacing";

    private static final String KEY_SEND_VIDEO = "sendVideo";
    public static final String KEY_SEND_AUDIO = "sendAudio";

    private static final String[] WEBRTC_OPTIONS = {
            "Send Video",
            "Send Audio",
    };

    private static final String[] KEY_OF_OPTIONS = {
            KEY_SEND_VIDEO,
            KEY_SEND_AUDIO,
    };


    private EditText mChannelName;
    private EditText mClientId;
    private EditText mRegion;
    private Spinner mCameras;
    private final List<ResourceEndpointListItem> mEndpointList = new ArrayList<>();
    private final List<IceServer> mIceServerList = new ArrayList<>();
    private String mChannelArn = null;
    private ListView mOptions;

    private SimpleNavActivity navActivity;

    public static StreamWebRtcConfigurationFragment newInstance(SimpleNavActivity navActivity) {
        StreamWebRtcConfigurationFragment s = new StreamWebRtcConfigurationFragment();
        s.navActivity = navActivity;
        return s;
    }

    @Override
    public View onCreateView(final LayoutInflater inflater,
                             final ViewGroup container,
                             final Bundle savedInstanceState) {
        if (getActivity() != null) {
            if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this.getActivity(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this.getActivity(), new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 9393);
            }

            getActivity().setTitle(getActivity().getString(R.string.title_fragment_channel));
        }


        return inflater.inflate(R.layout.fragment_stream_webrtc_configuration, container, false);
    }

    //Blog - Add <
    private Intent intent;
    private static int mResultCode;
    private static Intent mResultData;
    private static final int RETURN_CODE = 1;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult " + data);
        if (requestCode != RETURN_CODE || data ==null){
            return;
        }
        mResultCode = resultCode;
        mResultData = data;

        startFGService();

    }


    private void createMediaProjection() {
        MediaProjectionManager mediaProjectionManager =
                (MediaProjectionManager) getContext().getSystemService(
                        Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(), RETURN_CODE);
    }

    private void startFGService(){
        updateSignalingChannelInfo(mRegion.getText().toString(),
                mChannelName.getText().toString(),
                ChannelRole.MASTER);

        intent = new Intent(getContext(), CaptureFGService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            Bundle extras = setExtras(true);
            intent.putExtras(extras);
            intent.putExtra("PermissionResultData", mResultData);
            getContext().startForegroundService(intent);
        }
        else {
            getContext().startService(intent);
        }
    }

    public void stopService(){
        if(getContext() !=null && isServiceRunning(CaptureFGService.class)){
            Log.e(TAG, "stop service");

            getContext().stopService(intent);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopService();

    }
    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
//Blog - Add >

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        Button mStartMasterButton = view.findViewById(R.id.start_master);
        mStartMasterButton.setOnClickListener(startMasterActivityWhenClicked());
        Button mStartViewerButton = view.findViewById(R.id.start_viewer);
        mStartViewerButton.setOnClickListener(startViewerActivityWhenClicked());

        //Blog - Add <
        Button mStartServicerButton = view.findViewById(R.id.start_screencast);
        mStartServicerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createMediaProjection();
            }
        });
        //Blog - Add >

        mChannelName = view.findViewById(R.id.channel_name);
        mClientId = view.findViewById(R.id.client_id);
        mRegion = view.findViewById(R.id.region);
        setRegionFromCognito();

        mOptions = view.findViewById(R.id.webrtc_options);
        mOptions.setAdapter(new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_multiple_choice, WEBRTC_OPTIONS) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                if(convertView == null) {
                    View v = getLayoutInflater().inflate(android.R.layout.simple_list_item_multiple_choice, null);

                    final CheckedTextView ctv = v.findViewById(android.R.id.text1);
                    ctv.setText(WEBRTC_OPTIONS[position]);

                    // Send video is enabled by default and cannot uncheck
                    if (position == 0) {
                        ctv.setEnabled(false);
                        ctv.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                ctv.setChecked(true);
                            }
                        });
                    }
                    return v;
                }

                return convertView;
            }
        });
        mOptions.setItemsCanFocus(false);
        mOptions.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        mOptions.setItemChecked(0, true);

        mCameras = view.findViewById(R.id.camera_spinner);

        List<String> cameraList = new ArrayList<>(Arrays.asList("Front Camera", "Back Camera"));

        if (getContext() != null) {
            mCameras.setAdapter(new ArrayAdapter<>(getContext(),
                    android.R.layout.simple_spinner_dropdown_item,
                    cameraList));
        }
    }

    private void setRegionFromCognito() {
        String region = KinesisVideoWebRtcDemoApp.getRegion();
        if (region != null) {
            mRegion.setText(region);
        }
    }

    private View.OnClickListener startMasterActivityWhenClicked() {
        return new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                startMasterActivity();
            }
        };
    }

    private void startMasterActivity() {
        updateSignalingChannelInfo(mRegion.getText().toString(),
                mChannelName.getText().toString(),
                ChannelRole.MASTER);
        if (mChannelArn != null) {
            Bundle extras = setExtras(true);
            Intent intent = new Intent(getActivity(), WebRtcActivity.class);
            intent.putExtras(extras);
            startActivity(intent);
        }
    }

    private View.OnClickListener startViewerActivityWhenClicked() {
        return new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                startViewerActivity();
            }
        };
    }

    private void startViewerActivity() {
        updateSignalingChannelInfo(mRegion.getText().toString(),
                mChannelName.getText().toString(),
                ChannelRole.VIEWER);

        if (mChannelArn != null) {
            Bundle extras = setExtras(false);
            Intent intent = new Intent(getActivity(), WebRtcActivity.class);
            intent.putExtras(extras);
            startActivity(intent);
        }
    }

    private Bundle setExtras(boolean isMaster) {
        final Bundle extras = new Bundle();
        final String channelName = mChannelName.getText().toString();
        final String clientId = mClientId.getText().toString();
        final String region = mRegion.getText().toString();

        extras.putString(KEY_CHANNEL_NAME, channelName);
        extras.putString(KEY_CLIENT_ID, clientId);
        extras.putString(KEY_REGION, region);
        extras.putString(KEY_REGION, region);
        extras.putString(KEY_CHANNEL_ARN, mChannelArn);
        extras.putBoolean(KEY_IS_MASTER, isMaster);

        if (mIceServerList.size() > 0) {
            ArrayList<String> userNames = new ArrayList<>(mIceServerList.size());
            ArrayList<String> passwords = new ArrayList<>(mIceServerList.size());
            ArrayList<Integer> ttls = new ArrayList<>(mIceServerList.size());
            ArrayList<List<String>> urisList = new ArrayList<>();
            for (IceServer iceServer : mIceServerList) {
                userNames.add(iceServer.getUsername());
                passwords.add(iceServer.getPassword());
                ttls.add(iceServer.getTtl());
                urisList.add(iceServer.getUris());
            }
            extras.putStringArrayList(KEY_ICE_SERVER_USER_NAME, userNames);
            extras.putStringArrayList(KEY_ICE_SERVER_PASSWORD, passwords);
            extras.putIntegerArrayList(KEY_ICE_SERVER_TTL, ttls);
            extras.putSerializable(KEY_ICE_SERVER_URI, urisList);
        } else {
            extras.putStringArrayList(KEY_ICE_SERVER_USER_NAME, null);
            extras.putStringArrayList(KEY_ICE_SERVER_PASSWORD, null);
            extras.putIntegerArrayList(KEY_ICE_SERVER_TTL, null);
            extras.putSerializable(KEY_ICE_SERVER_URI, null);
        }

        for (ResourceEndpointListItem endpoint : mEndpointList) {
            if (endpoint.getProtocol().equals("WSS")) {
                extras.putString(KEY_WSS_ENDPOINT, endpoint.getResourceEndpoint());
            }
        }

        final SparseBooleanArray checked = mOptions.getCheckedItemPositions();
        for (int i = 0; i < mOptions.getCount(); i++) {
            extras.putBoolean(KEY_OF_OPTIONS[i], checked.get(i));
        }

        extras.putBoolean(KEY_CAMERA_FRONT_FACING, mCameras.getSelectedItem().equals("Front Camera"));

        return extras;
    }

    private AWSKinesisVideoClient getAwsKinesisVideoClient(final String region) {
        final AWSKinesisVideoClient awsKinesisVideoClient = new AWSKinesisVideoClient(
                KinesisVideoWebRtcDemoApp.getCredentialsProvider().getCredentials());
        awsKinesisVideoClient.setRegion(Region.getRegion(region));
        awsKinesisVideoClient.setSignerRegionOverride(region);
        awsKinesisVideoClient.setServiceNameIntern("kinesisvideo");
        return awsKinesisVideoClient;
    }

    private AWSKinesisVideoSignalingClient getAwsKinesisVideoSignalingClient(final String region, final String endpoint) {
        final AWSKinesisVideoSignalingClient client = new AWSKinesisVideoSignalingClient(
                KinesisVideoWebRtcDemoApp.getCredentialsProvider().getCredentials());
        client.setRegion(Region.getRegion(region));
        client.setSignerRegionOverride(region);
        client.setServiceNameIntern("kinesisvideo");
        client.setEndpoint(endpoint);
        return client;
    }

    private void updateSignalingChannelInfo(final String region, final String channelName, final ChannelRole role) {
        mEndpointList.clear();
        mIceServerList.clear();
        mChannelArn = null;
        UpdateSignalingChannelInfoTask task = new UpdateSignalingChannelInfoTask(this);
        try {
            task.execute(region, channelName, role).get();
        } catch (Exception e) {
            Log.e(TAG, "Failed to wait for response of UpdateSignalingChannelInfoTask", e);
        }
    }

    static class UpdateSignalingChannelInfoTask extends AsyncTask<Object, String, String> {
        final WeakReference<StreamWebRtcConfigurationFragment> mFragment;

        UpdateSignalingChannelInfoTask(final StreamWebRtcConfigurationFragment fragment) {
            mFragment = new WeakReference<>(fragment);
        }

        @Override
        protected String doInBackground(Object... objects) {
            final String region = (String) objects[0];
            final String channelName = (String) objects[1];
            final ChannelRole role = (ChannelRole) objects[2];
            AWSKinesisVideoClient awsKinesisVideoClient = null;
            try {
                awsKinesisVideoClient = mFragment.get().getAwsKinesisVideoClient(region);
            } catch (Exception e) {
                return "Create client failed with " + e.getLocalizedMessage();
            }

            try {
                DescribeSignalingChannelResult describeSignalingChannelResult = awsKinesisVideoClient.describeSignalingChannel(
                        new DescribeSignalingChannelRequest()
                                .withChannelName(channelName));

                Log.i(TAG, "Channel ARN is " + describeSignalingChannelResult.getChannelInfo().getChannelARN());
                mFragment.get().mChannelArn = describeSignalingChannelResult.getChannelInfo().getChannelARN();
            } catch (final ResourceNotFoundException e) {
                if (role.equals(ChannelRole.MASTER)) {
                    try {
                        CreateSignalingChannelResult createSignalingChannelResult = awsKinesisVideoClient.createSignalingChannel(
                                new CreateSignalingChannelRequest()
                                        .withChannelName(channelName));

                        mFragment.get().mChannelArn = createSignalingChannelResult.getChannelARN();
                    } catch (Exception ex) {
                        return "Create Signaling Channel failed with Exception " + ex.getLocalizedMessage();
                    }
                } else {
                    return "Signaling Channel " + channelName +" doesn't exist!";
                }
            } catch (Exception ex) {
                return "Describe Signaling Channel failed with Exception " + ex.getLocalizedMessage();
            }

            try {
                GetSignalingChannelEndpointResult getSignalingChannelEndpointResult = awsKinesisVideoClient.getSignalingChannelEndpoint(
                        new GetSignalingChannelEndpointRequest()
                                .withChannelARN(mFragment.get().mChannelArn)
                                .withSingleMasterChannelEndpointConfiguration(
                                        new SingleMasterChannelEndpointConfiguration()
                                                .withProtocols("WSS", "HTTPS")
                                                .withRole(role)));

                Log.i(TAG, "Endpoints " + getSignalingChannelEndpointResult.toString());
                mFragment.get().mEndpointList.addAll(getSignalingChannelEndpointResult.getResourceEndpointList());
            } catch (Exception e) {
                return "Get Signaling Endpoint failed with Exception " + e.getLocalizedMessage();
            }

            String dataEndpoint = null;
            for (ResourceEndpointListItem endpoint : mFragment.get().mEndpointList) {
                if (endpoint.getProtocol().equals("HTTPS")) {
                    dataEndpoint = endpoint.getResourceEndpoint();
                }
            }

            try {
                final AWSKinesisVideoSignalingClient awsKinesisVideoSignalingClient = mFragment.get().getAwsKinesisVideoSignalingClient(region, dataEndpoint);
                GetIceServerConfigResult getIceServerConfigResult = awsKinesisVideoSignalingClient.getIceServerConfig(
                        new GetIceServerConfigRequest().withChannelARN(mFragment.get().mChannelArn).withClientId(role.name()));
                mFragment.get().mIceServerList.addAll(getIceServerConfigResult.getIceServerList());
            } catch (Exception e) {
                return "Get Ice Server Config failed with Exception " + e.getLocalizedMessage();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                AlertDialog.Builder diag = new AlertDialog.Builder(mFragment.get().getContext());
                diag.setPositiveButton("OK", null).setMessage(result).create().show();
            }
        }
    }
}