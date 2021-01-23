# Screencast your mobile app using Kinesis Video Stream with WebRTC in Android


## Note
It was implemented by adding only the screencast function while maintaining the function provided in the sample app - <a href ="https://github.com/awslabs/amazon-kinesis-video-streams-webrtc-sdk-android">KinesisVideoWebRTCDempApp</a>


## Pre-requisites

####    AWS account admin console access

####    Install the latest Amplify CLI version
- Open terminal and run npm install -g @aws-amplify/cli to update to the latest Amplify CLI.

####   Amplify CLI is already configured
- If you haven’t configured the Amplify CLI yet, follow <a href="https://docs.amplify.aws/cli/start/install#configure-the-amplify-cli" > this guide </a> on our documentation page.

####   Install <a href="https://developer.android.com/studio" >Android Studio</a>



## Instructions

### Step1. Source Download from GitHub.    


### Step2. Amazon Cognito Service for user authentication
#### 1. Create and integrate Cognito using amplify cli.

    
    cd amazon-kinesis-video-streams-webrtc-sdk-android
    amplify init
    

See below for ‘amplify init’ input values. 
            
    
    ? Enter a name for the environment dev
    
The downloaded source has been set up in advance so that the cognito service can be created by amplify push command

    
    amplify push

See below for ‘amplify push’ input values. 

    ? Are you sure you want to continue? Yes

The downloaded source has been set up in advance so that the cognito service can be created by amplify push command. 
After about 10 minutes, Amazon Cognito Service is created in your AWS account, and json file(res/raw/amplifyconfiguration.json) is created as metadata in your Android project.

#### 2. Add policy to IAM Role

Through Amazon Cognito service, we can authorize logged-in users to use the aws service. This app uses the kinesis video stream service for screen casting. It is necessary to add the AmazonKinesisVideoStreamFullAccess policy to the IAM role specified in the Authenticated role so that authenticated users can use the kinesis video stream service. 
Congito > Federated Identities > Edit identity pool > Authenticated role

IAM > Roles > select your authenticated role. ex) 'amplify-webrtcandroid-dev-220724-authRole’  > Attach policies, add “AmazonKinesisAnalyticsFullAccess” 

 
 
 ### Step3. Build and run the demo application using Android Studio
 Load the downloaded source into Android Studio through File> New> Import Project. Run the demo application in simulator or in Android device. 
 Press ‘start screencast’ button.

Connect to the Kinesis console. Select the created channel (Kinesis Video Streams> Signaling channels> demo-channel), you can see that the screen of the smartphone is cast after a while.
 

## Cleanup

 With the ‘amplify delete’ command, deletes all of the resources tied to the project from the cloud

    `
    amplify delete
    `

  See below for ‘amplify delete’ input values. 

    `
	 ? Are you sure you want to continue? This CANNOT be undone. (This would delete all the env
    ironments of the project from the cloud and wipe out all the local files created by Amplif
    y CLI) Yes
    `


