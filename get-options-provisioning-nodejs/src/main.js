const ProvisioningApi = require('genesys-provisioning-client-js').ProvisioningApi;

const apiKey = "<apiKey>";
const apiUrl = "<apiUrl>";

//region Authorization code grant
//You'll need to use the Authentication API to get an authorization token. See https://github.com/GenesysPureEngage/authorization-code-grant-sample-app for an example of how to do this.
const authorizationToken = "<authorizationToken>";
//endregion

//region Create an instance of ProvisioningApi
//Here we create out ProvisioningApi object using the **apiKey** (required to submit API requests) and **apiUrl** (base URL that provides access to the PureEngage Cloud APIs). 
//You can get the values for both of these from your PureEngage Cloud representative.
const provisioningApi = new ProvisioningApi(apiKey, apiUrl);
//endregion


async function main() {
	//region Initialize API
	//Initialize the API using the authorization token (or authorization code)
	await provisioningApi.initialize({token: authorizationToken});
	//endregion
	
	//region Get Options
	//Get the options.
	const options = (await provisioningApi.options.get({personDbid: "<personDBID>", agentGroupDbid: "<agentGroupDBID>"})).options;
	//endregion
	
	options["<property>"] =  "<value>";
	
	//region Modify Options
	//After the options object has been changed, update them using the API.
	await provisioningApi.options.modify(options);
	//endregion
	
	//region Log Out
	//Log out of your Provisioning API session.
	await provisioningApi.destroy();
	//endregion
}

main().catch((e) => console.error(e));
