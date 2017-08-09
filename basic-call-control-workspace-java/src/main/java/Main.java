import com.genesys.common.ApiClient;
import com.genesys.common.ApiResponse;
import com.genesys.common.ApiException;
import com.genesys.workspace.api.SessionApi;
import com.genesys.workspace.api.VoiceApi;
import com.genesys.workspace.model.*;

import com.genesys.authorization.api.AuthenticationApi;
import com.genesys.authorization.model.DefaultOAuth2AccessToken;

import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.client.BayeuxClient;
import org.cometd.client.transport.ClientTransport;
import org.cometd.client.transport.LongPollingTransport;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.net.URI;
import java.net.HttpCookie;
import java.net.CookieManager;
import java.util.Base64;

public class Main {
    public static void main(String[] args) {
    	
    	final String apiKey = "qalvWPemcr4Gg9xB9470n7n9UraG1IFN7hgxNjd1";
        
        final String clientId = "external_api_client";
        final String clientSecret = "secret";
        
        final String workspaceUrl = "https://gws-usw1.genhtcc.com/workspace/v3";
        final String authUrl = "https://gws-usw1.genhtcc.com/auth/v3";
        
        final String username = "agent-6504772888";
        final String password = "Agent123";
		
		
		//region Initialize Workspace Client
        //Create and setup an ApiClient instance with your ApiKey and Workspace API URL.
        final ApiClient client = new ApiClient();
        client.setBasePath(workspaceUrl);
        client.addDefaultHeader("x-api-key", apiKey);
        
        //region Initialize Authorization Client
        //Create and setup an ApiClient instance with your ApiKey and Authorization API URL.
        final ApiClient authClient = new ApiClient();
        authClient.setBasePath(authUrl);
        authClient.addDefaultHeader("x-api-key", apiKey);
        //endregion
        
        try {
        	
            //region Create SessionApi and VoiceApi instances
            //Creating instances of SessionApi and VoiceApi using the workspace ApiClient which will be used to make api calls.
            final SessionApi sessionApi = new SessionApi(client);
            final VoiceApi voiceApi = new VoiceApi(client);
            
            //region Create AuthenticationApi instance
            //Create instance of AuthenticationApi using the authorization ApiClient which will be used to retrieve access token.
            final AuthenticationApi authApi = new AuthenticationApi(authClient); 
			
			//region Oauth2 Authentication
			//Performing Oauth 2.0 authentication.
			System.out.println("Retrieving access token...");
            
            final String authorization = "Basic " + new String(Base64.getEncoder().encode( (clientId + ":" + clientSecret).getBytes()));
            final DefaultOAuth2AccessToken accessToken = authApi.retrieveToken("password", clientId, username, password, authorization);
            
            System.out.println("Retrieved access token");
            System.out.println("Initializing workspace...");
            
            final ApiResponse<ApiSuccessResponse> response = sessionApi.initializeWorkspaceWithHttpInfo("", "", "Bearer " + accessToken.getAccessToken());
            
            Optional<String> session = response.getHeaders().get("set-cookie").stream().filter(v -> v.startsWith("WORKSPACE_SESSIONID")).findFirst();
            
            if(session.isPresent()) {
            	client.addDefaultHeader("Cookie", session.get());
            } else {
            	throw new Exception("Could not find session");
            }
            
            System.out.println("Got workspace session id");
            
            //region Creating HttpClient
            //Conifuring a Jetty HttpClient which will be used for CometD.
            final SslContextFactory sslContextFactory = new SslContextFactory();
		
			final HttpClient httpClient = new HttpClient(sslContextFactory);
			httpClient.start();
			
			CookieManager manager = new CookieManager();
			httpClient.setCookieStore(manager.getCookieStore());
			httpClient.getCookieStore().add(new URI(workspaceUrl), new HttpCookie("WORKSPACE_SESSIONID", session.get().split(";")[0].split("=")[1]));
			
			//region Creating BayeuxClient (CometD Client) and Making CometD handshake
			//Here we configure CometD using long polling transport and making sure the api key is included in headers. The BayeuxClient instance is created and used to make the CometD handshake.
			ClientTransport transport = new LongPollingTransport(new HashMap(), httpClient) {
				@Override protected void customize(Request request) {
					request.header("x-api-key", apiKey);
				}
			};
			
			final BayeuxClient bayeuxClient = new BayeuxClient(workspaceUrl + "/notifications", transport);
			
			
			bayeuxClient.handshake((ClientSessionChannel handshakeChannel, Message handshakeMessage) -> {
					
				if(handshakeMessage.isSuccessful()) {
					//region Subscribing to Initialization Channel
					//Once the handshake is successful we can subscribe to a CometD channels to get events. 
					//Here we subscribe to initialization channel to get 'initializationComplete' event.
					bayeuxClient.getChannel("/workspace/v3/initialization").subscribe(new  ClientSessionChannel.MessageListener() {
						
						@Override public void onMessage(ClientSessionChannel channel, Message message) {
							
							Map<String, Object> messageData = message.getDataAsMap();
							if(messageData.get("messageType").equals("WorkspaceInitializationComplete")) {
								System.out.println("Workspace initialized");
								Map<String, Object> userData = (Map<String, Object>) messageData.get("data");
								Map<String, Object> user = (Map<String, Object>) userData.get("user");
								String agentLogin = (String) user.get("agentLogin");
								String employeeId = (String) user.get("employeeId");
								System.out.println("Activating channels...");
								activateChannels(sessionApi, employeeId, agentLogin);
							}
						}
						
					}, (ClientSessionChannel channel, Message message) -> {
						//region Subscription Event
						//If the CometD subscription is unsuccessful we end the program.
						if(message.isSuccessful()) {
							System.out.println("Subscribed to initialization events");
						} else {
							System.out.println("Initialization subscription failed");
							System.exit(1);
						}
						//endregion
					});
					
					//region Subscribing is Voice Channel
					//Here we subscribe to voice channel to get call events. 	
					bayeuxClient.getChannel("/workspace/v3/voice").subscribe(new  ClientSessionChannel.MessageListener() {
						
						private boolean hasHeld = false;
						private boolean hasActivatedChannels = false;
						
						@Override public void onMessage(ClientSessionChannel channel, Message message) {
							//region Receiving Events
							//Here CometD events are handled. The Message object contains data that is stored as a map. Getting the 'messageType' will tell us the type of message.
							Map<String, Object> messageData = (Map<String, Object>) message.getDataAsMap();
							
							if(messageData.get("messageType").equals("CallStateChanged")) {
								//region Get Call Info
								//The call id and state are stored in a map with the key 'call' in message data.
								Map<String, Object> call = (Map<String, Object>) messageData.get("call");
								String callId = call.get("id").toString();
								String callState = call.get("state").toString();
								String capabilities = "";
								capabilities = Arrays.asList((Object[]) call.get("capabilities")).toString();
								//endregion
								
								if(callState.equals("Ringing")) {
									//region Ringing
									//If the call state is changed to 'Ringing' this means there is an incoming call and we can answer it.
									System.out.println("Received call");
									System.out.println("CallId: " + callId + ", State: " + callState + ", Capabilities: " + capabilities);
									
									System.out.println("Answering...");
									answerCall(voiceApi, callId);
									//endregion
									
								} else if(callState.equals("Established")) {
									//region Established
									//When the call state is 'Established' this means that the call is in progress. The state is set to 'Established' both when the call is first answered and when the call is retrieved after holding so we must specify which scenario is taking place by setting the 'hasHeld' variable to true after the call has been held.
									if(!hasHeld) {
										System.out.println("Answered");
										System.out.println("CallId: " + callId + ", State: " + callState + ", Capabilities: " + capabilities);
										
										System.out.println("Holding...");
										holdCall(voiceApi, callId);
										hasHeld = true;
									} else {
										System.out.println("Retrieved");
										System.out.println("CallId: " + callId + ", State: " + callState + ", Capabilities: " + capabilities);
										
										System.out.println("Releasing...");
										releaseCall(voiceApi, callId);
									}
									//endregion
									
								} else if(callState.equals("Held")) {
									//region Held
									//The call state is changed to 'Held' when we hold the call. Now we can retrieve the call when we're ready.
									System.out.println("Held");
									System.out.println("CallId: " + callId + ", State: " + callState + ", Capabilities: " + capabilities);
									
									System.out.println("Retrieving...");
									retrieveCall(voiceApi, callId);
									//endregion
									
								}  else if(callState.equals("Released")) {
									//region Released
									//The call state is changed to 'Released' when the call is ended. Now we can make the agent 'NotReady' and change their work mode to 'AfterCallWork'.
									System.out.println("Released");
									
									System.out.println("Setting agent state to not ready...");
									makeAgentNotReadyAfterCall(voiceApi);
									//endregion
								}
							
							} else if(messageData.get("messageType").equals("DnStateChanged")) {
								
								Map<String, Object> dn = (Map<String, Object>) messageData.get("dn");
								
								if(!hasActivatedChannels && (dn.get("agentState").equals("Ready") || dn.get("agentState").equals("NotReady")) ) {
									System.out.println("Channels activated");
									//region Activating Channels Event
									//The first DnStateChanged event that is either 'Ready' or 'NotReady' is assumed to be cause by the server activating channels.
									System.out.println("Waiting for incoming calls...");
									hasActivatedChannels = true;
									//endregion
								} else if(dn.get("agentState").equals("NotReady") && dn.get("agentWorkMode").equals("AfterCallWork")) {
									System.out.println("Agent state set to not ready");
									//region Finishing up
									//Now that the call has been handled to program is done so you can disconnect CometD and logout.
									System.out.println("Disconnecting and logging out...");
									disconnectAndLogout(bayeuxClient, sessionApi);
								
									System.out.println("done");
									System.exit(0);
									//endregion
								}
							}
						}
						
					}, (ClientSessionChannel channel, Message message) -> {
						//region Subscription Event
						//If the CometD subscription is unsuccessful we end the program.
						if(message.isSuccessful()) {
							System.out.println("Subscribed to voice events");
						} else {
							System.out.println("Voice subscription failed");
							System.exit(1);
						}
						//endregion
					});
				} else {
					System.err.println("Handshake failed");
				}
			});
			
			
            
        } catch(Exception ex) {
            System.err.println(ex);
            System.exit(1);
        }
    }
    
    public static void activateChannels(SessionApi sessionApi, String employeeId, String agentLogin) {
		
		//region Activate Channels
		//Activating channels for the user using employee ID and agent login.
		try {
			
			ActivatechannelsData data = new ActivatechannelsData();
			data.setAgentId(employeeId);
			data.setDn(agentLogin);
			ChannelsData channelsData = new ChannelsData();
			channelsData.data(data);
			ApiSuccessResponse response = sessionApi.activateChannels(channelsData);
			if(response.getStatus().getCode() != 0) {
				System.err.println("Cannot activate channels");
				System.exit(1);
			}
			
		} catch(ApiException ex) {
			System.err.println("Cannot activate channels");
			System.err.println(ex);
			System.exit(1);
		}
		//endregion
	}
    
    
    public static void answerCall(VoiceApi voiceApi, String callId) {
    	//region Answering Call
    	//Answer call using voice api and call id.
    	try {
			VoicereadyData answerData = new VoicereadyData();
			voiceApi.answer(callId, new AnswerData().data(answerData));
		} catch(ApiException ex) {
			System.err.println("Cannot answer call");
			System.err.println(ex);
			System.exit(1);
		}
		//endregion
    }
    
    public static void holdCall(VoiceApi voiceApi, String callId) {
    	//region Holding Call
    	//Hold call using voice api and call id.
    	try {
			VoicereadyData data = new VoicereadyData();
			voiceApi.hold(callId, new HoldData().data(data));
		} catch(ApiException ex) {
			System.err.println("Cannot hold call");
			System.err.println(ex);
			System.exit(1);
		}
		//endregion
    }
    
    public static void retrieveCall(VoiceApi voiceApi, String callId) {
    	//region Retrieving Call
    	//Retrieve call using voice api and call id.
    	try {
			VoicereadyData data = new VoicereadyData();
			voiceApi.retrieve(callId, new RetrieveData().data(data));
		} catch(ApiException ex) {
			System.err.println("Cannot retrieve call");
			System.err.println(ex);
			System.exit(1);
		}
		//endregion
    }
    
    public static void releaseCall(VoiceApi voiceApi, String callId) {
    	//region Release Call
    	//End call using voice api and call id.
    	try {
			VoicereadyData data = new VoicereadyData();
			voiceApi.release(callId, new ReleaseData().data(data));
		} catch(ApiException ex) {
			System.err.println("Cannot release call");
			System.err.println(ex);
			System.exit(1);
		}
		//endregion
    }
    
    public static void makeAgentNotReadyAfterCall(VoiceApi voiceApi) {
    	//region Making Agent NotReady and Setting Agent Work Mode
    	//Setting agent state to 'NotReady' using the voice api and specifying the agent's work mode using the agent work mode enum.
    	try {
			VoicenotreadyData data = new VoicenotreadyData()
				.agentWorkMode(VoicenotreadyData.AgentWorkModeEnum.AFTERCALLWORK);
		
			voiceApi.setAgentStateNotReady(new NotReadyData().data(data));
		} catch(ApiException ex) {
			System.err.println("Cannot set state!");
			System.err.println(ex);
			System.exit(1);
		}
		//endregion
    }
    
    public static void disconnectAndLogout(BayeuxClient bayeuxClient, SessionApi sessionApi) {
    	//region Disconnecting and Logging Out
		//Using the BayeuxClient and SessionApi to disconnect CometD and logout of the workspace session.
		bayeuxClient.disconnect();
		
		try {
			sessionApi.logout();
		} catch(ApiException ex) {
			System.err.println("Cannot log out");
			System.err.println(ex);
			System.exit(1);
		}
		//endregion
    }
    
}










