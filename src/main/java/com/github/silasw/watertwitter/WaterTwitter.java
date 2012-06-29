package com.github.silasw.watertwitter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Date;
import java.util.Scanner;

import org.grailrtls.libworldmodel.client.ClientWorldConnection;
import org.grailrtls.libworldmodel.client.StepResponse;
import org.grailrtls.libworldmodel.client.WorldState;
import org.grailrtls.libworldmodel.client.protocol.messages.Attribute;
import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.TwitterApi;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.grailrtls.libworldmodel.types.BooleanConverter;

public class WaterTwitter {
	/**
	 * Logger for this class.
	 */
	private static final Logger log = LoggerFactory.getLogger(WaterTwitter.class);
	private static final String PROTECTED_RESOURCE_URL = "https://api.twitter.com/1/statuses/update.json";
	
/**
 * Updates the status of a Twitter account when the "wet" attribute of any object with a URI matching
 * .*watersensor.* changes to "true."
 * 
 * Each object should have: <ul><li>wet (boolean)</li></ul>
 * 
 * The first time this program is run, it will ask for authorization by a Twitter account.
 * You may change accounts by deleting twitterauth.txt.
 * 
 * @param args World Model Host, World Model Client Port
 */
	public static void main(String[] args) {
		// Check if there are enough arguments
		if (args.length != 2) {
			System.out.println("I need 2 things: <World Model Host> <World Model Port>");
			return;
		}
		// Check if twitterauth.txt exists. If it does not, do authorization procedure.
		File f = new File("twitterauth.txt");
		if (!f.exists()){
			authorize();
		}
		else
			log.info("twitterauth.txt found.");
		ClientWorldConnection wmc = new ClientWorldConnection();
		wmc.setHost(args[0]);
		wmc.setPort(Integer.parseInt(args[1]));
		do{
		// Connect to the world model as a client.

			if (!wmc.connect()) {
				System.err
						.println("Couldn't connect to the world model!  Check your connection parameters.");
				continue;
			}
			// Begin streaming request
			long now = System.currentTimeMillis();
			long oneSecond = 1000l;
			log.info("Requesting from " + new Date(now) + " every " + oneSecond + " ms.");
			StepResponse response = wmc.getStreamRequest(".*watersensor.*", now, oneSecond, "wet");
			
			WorldState state = null;
			// The next line will block until the response gets at least one state
			// Streaming requests only complete when they are cancelled
			while (!response.isComplete()) {
				try {
				state = response.next();
				}catch(Exception e){
					System.err.println("Error occured during request: " + e);
					e.printStackTrace();
					break;
				}
				Collection<String> uris = state.getURIs();
				if (uris != null) {
					for (String uri : uris) {
						System.out.println("URI: " + uri);
						Collection<Attribute> attribs = state.getState(uri);
						long timestamp = 0;
						Attribute attfinal = null;
						for (Attribute att : attribs) {
							if(att.getCreationDate()>timestamp){
								timestamp = att.getCreationDate();
								attfinal = att;
							}
						}
						if(attfinal!=null && BooleanConverter.CONVERTER.decode(attfinal.getData())){
							log.debug(uri);
							log.debug(Boolean.toString(BooleanConverter.CONVERTER.decode(attfinal.getData())));
							tweet(uri);
						}
					}
				}
			}
		}while(!wmc.isConnected());
		wmc.disconnect();
	}
	private static String getSecret(){
		FileInputStream sstream = null;
		String secret = "";
		try{
			  sstream = new FileInputStream("secretkey.txt");
			  DataInputStream in = new DataInputStream(sstream);
			  BufferedReader br = new BufferedReader(new InputStreamReader(in));
			  secret = br.readLine();
		}catch (Exception e){
			  log.error("Exception when reading secret key file: "+e);
		}
		return secret;
	}
	private static boolean authorize(){
		OAuthService service = new ServiceBuilder()
        .provider(TwitterApi.class)
        .apiKey("3UkmhFhR8TuPTg3tEtW7g")
        .apiSecret(getSecret())
        .build();
		
		Scanner in = new Scanner(System.in);

	    // Obtain the Request Token
	    Token requestToken = service.getRequestToken();
	    // Manual Authorization
	    System.out.println("Visit this URL and authorize this application:");
	    System.out.println(service.getAuthorizationUrl(requestToken));
	    System.out.println("And paste the verifier here");
	    System.out.print(">>");
	    Verifier verifier = new Verifier(in.nextLine());

	    // Trade the Request Token and Verifier for the Access Token
	    log.debug("Trading the Request Token for an Access Token...");
	    Token accessToken = service.getAccessToken(requestToken, verifier);
	    log.debug("Access token:" + accessToken + " )");
		try{
			  // Create file 
			  FileWriter fstream = new FileWriter("twitterauth.txt");
			  BufferedWriter out = new BufferedWriter(fstream);
			  out.write(accessToken.getToken());
			  out.write("\n");
			  out.write(accessToken.getSecret());
			  //Close the output stream
			  out.close();
		  }catch (Exception e){//Catch exception if any
		  log.error("Error: " + e.getMessage());
		  return false;
		  }
		return true;
	}
	private static boolean tweet(String uri){
		String token = "";
		String tokensecret = "";
		FileInputStream fstream = null;
		try{
			  fstream = new FileInputStream("twitterauth.txt");
		}catch (FileNotFoundException e){
			  authorize();
		}
			  // Get the object of DataInputStream
		try{
			  DataInputStream in = new DataInputStream(fstream);
			  BufferedReader br = new BufferedReader(new InputStreamReader(in));
			  token = br.readLine();
			  tokensecret = br.readLine();
			  if(token==null || tokensecret==null){
				  log.error("twitterauth.txt incorrectly formatted");
				  return false;
			  }
			  //Close the input stream
			  in.close();
		}catch (Exception e){//Catch exception if any
			  log.error("Error: " + e.getMessage() + "\nTry deleting twitterauth.txt and restarting the application.");
		}
		OAuthService service = new ServiceBuilder()
        .provider(TwitterApi.class)
        .apiKey("3UkmhFhR8TuPTg3tEtW7g")
        .apiSecret(getSecret())
        .build();
	    // Makes a request to Twitter to update status
	    OAuthRequest request = new OAuthRequest(Verb.POST, PROTECTED_RESOURCE_URL);
	    String tweetcontent = new Date(System.currentTimeMillis()).toString() + " Water has been sensed by " + uri;
	    log.info("Tweeting: "+tweetcontent);
	    request.addBodyParameter("status",tweetcontent);
	    Token accessToken = new Token(token, tokensecret);
	    service.signRequest(accessToken, request);
	    Response response = request.send();
	    // Log the response
	    log.debug(response.getBody());
		return false;
	}
}
