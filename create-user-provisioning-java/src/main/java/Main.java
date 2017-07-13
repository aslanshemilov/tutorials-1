import com.genesys.common.ApiClient;
import com.genesys.common.ApiResponse;
import com.genesys.provisioning.api.LoginApi;
import com.genesys.provisioning.api.UsersApi;
import com.genesys.provisioning.model.AddUser;
import com.genesys.provisioning.model.ApiSuccessResponse;
import com.genesys.provisioning.model.Login;
import com.genesys.provisioning.model.LoginSuccessResponse;
import java.util.Arrays;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {

        //region 1 Initialize API Client
        //Create and setup ApiClient instance with your ApiKey and Provisioning API URL.
        final String apiKey = "your_API_key";
        final String provisionUrl = "https://api-usw1.genhtcc.com";

        final ApiClient client = new ApiClient();
        client.setBasePath(provisionUrl);
        client.addDefaultHeader("x-api-key", apiKey);
        //endregion

        try {
            //region 2 Create LoginApi instance
            //Creating instance of LoginApi using the ApiClient.
            final LoginApi loginApi = new LoginApi(client);
            //endregion

            //region 3 Logging in Provisioning API
            //Logging in using our username and password
            Login loginData = new Login();
            loginData.setDomainUsername("username");
            loginData.setPassword("password");
            ApiResponse<LoginSuccessResponse> loginResp = loginApi.loginWithHttpInfo(loginData);
            if (loginResp.getData().getStatus().getCode() != 0) {
                    throw new Exception("Cannot log in");
            }
            //endregion

            //region 4 Obtaining Provisioning API Session
            //Obtaining sessionId and setting PROVISIONING_SESSIONID cookie to the client
            Optional<String> session = loginResp.getHeaders().get("set-cookie").stream().filter(v -> v.startsWith("PROVISIONING_SESSIONID")).findFirst();
            if (session.isPresent()) {
                    client.addDefaultHeader("Cookie", session.get());
            } 
            else {
                    throw new Exception("Session not found");
            }
            //endregion

            //region 5 Creating UsersApi instance
            //Creating instance of UsersApi using the ApiClient
            final UsersApi usersApi = new UsersApi(client);
            //endregion

            //region 6 Describing and creating a user
            //Filling necessary information and creating a user using UsersApi instance
            AddUser usersData = new AddUser();
            usersData.setUserName("username");
            usersData.setPassword("password");
            usersData.setFirstName("FirstName");
            usersData.setLastName("LastName");
            usersData.setAccessGroups(Arrays.asList("accessGroup"));
            ApiSuccessResponse resp = usersApi.addUser(usersData);
            if (resp.getStatus().getCode().equals(0)) {
                    System.out.println("user created");
            } 
            else {
                    System.err.println("Cannot create user");
            }
            //endregion

            //region 7 Logging out
            //Ending our Provisioning API session
            loginApi.logout();
            //endregion
        } 
        catch (Exception ex) {
                System.err.println(ex);
        }
    }
}